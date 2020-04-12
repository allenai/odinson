package ai.lum.odinson

import java.nio.file.Path

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document => LuceneDocument}
import org.apache.lucene.search.{MatchAllDocsQuery, Query, BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery}
import org.apache.lucene.store.{Directory, FSDirectory}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ConfigFactory
import ai.lum.odinson.digraph.{Optional, Vocabulary}
import org.apache.lucene.analysis.standard.StandardAnalyzer

import scala.util.Try

class ExtractorEngine(
  val indexSearcher: OdinsonIndexSearcher,
  val compiler: QueryCompiler,
  val state: State,
  val parentDocIdField: String
) {

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val analyzer = new WhitespaceAnalyzer()

  val indexReader = indexSearcher.getIndexReader()

  def doc(docID: Int): LuceneDocument = {
    indexSearcher.doc(docID)
  }

  def numDocs(): Int = {
    indexReader.numDocs()
  }

  /** Retrieves the parent Lucene Document by docId */
  def getParentDoc(docId: String): LuceneDocument = {
    val sterileDocID = docId.escapeJava
    val booleanQuery = new LuceneBooleanQuery.Builder()
    val q1 =
      new QueryParser(parentDocIdField, analyzer).parse(s""""$sterileDocID"""")
    booleanQuery.add(q1, LuceneBooleanClause.Occur.MUST)
    val q2 = new QueryParser("type", analyzer).parse("parent")
    booleanQuery.add(q2, LuceneBooleanClause.Occur.MUST)
    val q = booleanQuery.build
    val docs = indexSearcher
      .search(q, 10)
      .scoreDocs
      .map(sd => indexReader.document(sd.doc))
    //require(docs.size == 1, s"There should be only one parent doc for a docId, but ${docs.size} found.")
    docs.head
  }

  /** executes query and returns all results */
  def query(odinsonQuery: String): OdinResults = {
    query(odinsonQuery, numDocs())
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: String, n: Int): OdinResults = {
    query(compiler.mkQuery(odinsonQuery), n)
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: String, parentQuery: String): OdinResults = {
    query(odinsonQuery, parentQuery, numDocs())
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: String, parentQuery: String, n: Int): OdinResults = {
    query(compiler.mkQuery(odinsonQuery, parentQuery), n)
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: OdinsonQuery, n: Int): OdinResults = {
    indexSearcher.odinSearch(odinsonQuery, n)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    n: Int,
    afterDoc: Int,
    afterScore: Float
  ): OdinResults = {
    query(
      compiler.mkQuery(odinsonQuery),
      n,
      new OdinsonScoreDoc(afterDoc, afterScore)
    )
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    parentQuery: String,
    n: Int,
    afterDoc: Int,
    afterScore: Float
  ): OdinResults = {
    query(
      compiler.mkQuery(odinsonQuery, parentQuery),
      n,
      new OdinsonScoreDoc(afterDoc, afterScore)
    )
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    n: Int,
    after: OdinsonScoreDoc
  ): OdinResults = {
    query(compiler.mkQuery(odinsonQuery), n, after)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    parentQuery: String,
    n: Int,
    after: OdinsonScoreDoc
  ): OdinResults = {
    query(compiler.mkQuery(odinsonQuery, parentQuery), n, after)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: OdinsonQuery,
    n: Int,
    after: OdinsonScoreDoc
  ): OdinResults = {
    indexSearcher.odinSearch(after, odinsonQuery, n)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = {
    // TODO by default this should use the field that was stored for display
    // IMHO it should be `raw`, but it shouldn't be hardcoded
    getTokens(scoreDoc.doc, "raw")
  }

  def getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String] = {
    getTokens(scoreDoc.doc, fieldName)
  }

  def getTokens(docID: Int, fieldName: String): Array[String] = {
    TokenStreamUtils.getTokens(docID, fieldName, indexSearcher, analyzer)
  }

  def query(extractionQuery: ExtractionQueryParams)
    : Either[ExtractionError, OdinResults] =
    Try {

      // combine the odinson query if needed
      val odinsonQuery = (extractionQuery.odinsonQuery,
                          extractionQuery.documentLuceneQuery) match {
        case (None, None)         => None
        case (Some(oq), Some(dq)) => Some(compiler.mkQuery(oq, dq))
        case (Some(oq), None)     => Some(compiler.mkQuery(oq))
        case (None, Some(dq)) => None
      }

      // parse sentence query
      val sentenceQuery = extractionQuery.sentenceLuceneQuery.map { sq =>
        val queryParser =
          new QueryParser("word", // default to searching over words
                          analyzer)
        queryParser.setLowercaseExpandedTerms(false);
        queryParser.parse(sq)
      }

      val parentQuery = (extractionQuery.odinsonQuery,
        extractionQuery.documentLuceneQuery) match {
        case (None, Some(dq)) => Some(compiler.compileParentQuery(dq))
        case _ => None
      }

      // final combine of all query parts to create a single lucene query
      val fullQuery = (odinsonQuery, sentenceQuery, parentQuery) match {
        case (Some(oq), Some(sq), None) =>
          new OdinsonFilteredQuery(oq, sq)
        case (Some(oq), None, None) => oq
        case (None, Some(sq), None) =>
          sq // TODO: convert to some kind of odinson query
        case (None, Some(sq), Some(dq)) =>
          compiler.mkFilterQuery(sq, dq)
        case (None, None, Some(dq)) =>
          compiler.mkFilterQuery(new MatchAllDocsQuery, dq)
        case _ =>
          throw new IllegalArgumentException(
            "Either odinson query, a parent query or a sentence query have to be defined")
      }

      val numberOfDocuments = extractionQuery.numDocuments.getOrElse(numDocs())

      // run the query
      (fullQuery, extractionQuery.continuation) match {
        case (q: OdinsonQuery, Some(c)) =>
          indexSearcher.odinSearch(c, q, numberOfDocuments)
        case (q: OdinsonQuery, _) =>
          indexSearcher.odinSearch(q, numberOfDocuments)
        case (q: Query, Some(c)) =>
          indexSearcher.pureSearch(c, q, numberOfDocuments)
        case (q: Query, _) => indexSearcher.pureSearch(q, numberOfDocuments)
      }

    }.toEither.left.map(t => ExtractionError(t.getMessage))

}

object ExtractorEngine {

  def fromConfig(): ExtractorEngine = {
    fromConfig("odinson")
  }

  def fromConfig(path: String): ExtractorEngine = {
    val config = ConfigFactory.load()
    fromConfig(config[Config](path))
  }

  def fromConfig(config: Config): ExtractorEngine = {
    val indexPath = config[Path]("indexDir")
    val indexDir = FSDirectory.open(indexPath)
    fromDirectory(config, indexDir)
  }

  def fromDirectory(config: Config, indexDir: Directory): ExtractorEngine = {
    val indexReader = DirectoryReader.open(indexDir)
    val computeTotalHits = config[Boolean]("computeTotalHits")
    val indexSearcher = new OdinsonIndexSearcher(indexReader, computeTotalHits)
    val vocabulary = Vocabulary.fromDirectory(indexDir)
    val compiler = QueryCompiler(config, vocabulary)
    val jdbcUrl = config[String]("state.jdbc.url")
    val state = new State(jdbcUrl)
    state.init()
    compiler.setState(state)
    val parentDocIdField = config[String]("index.documentIdField")
    new ExtractorEngine(indexSearcher, compiler, state, parentDocIdField)
  }

}
