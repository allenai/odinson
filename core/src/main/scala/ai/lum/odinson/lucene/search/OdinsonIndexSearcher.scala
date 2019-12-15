package ai.lum.odinson.lucene.search

import java.util.Collection
import java.util.concurrent.ExecutorService

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import org.apache.lucene.index._
import org.apache.lucene.search._
import ai.lum.odinson.lucene._
import ai.lum.odinson.utils.ExecutionContextExecutorServiceBridge
import org.apache.lucene.search.BooleanClause.Occur

class OdinsonIndexSearcher(
                            context: IndexReaderContext,
                            executor: ExecutorService,
                            computeTotalHits: Boolean,
                          ) extends IndexSearcher(context, executor) {

  def this(r: IndexReader, e: ExecutorService, computeTotalHits: Boolean) = this(r.getContext(), e, computeTotalHits)

  def this(r: IndexReader, e: ExecutionContext, computeTotalHits: Boolean) =
    this(r.getContext(), ExecutionContextExecutorServiceBridge(e), computeTotalHits)

  def this(r: IndexReader, computeTotalHits: Boolean) = this(r.getContext(), null, computeTotalHits)

  def odinSearch(query: OdinsonQuery): OdinResults = {
    val n = readerContext.reader().maxDoc()
    odinSearch(null, query, n)
  }

  def odinSearch(query: OdinsonQuery, n: Int): OdinResults = odinSearch(null, query, n)

  def odinSearch(after: OdinsonScoreDoc, query: OdinsonQuery, numHits: Int): OdinResults = {
    val limit = math.max(1, readerContext.reader().maxDoc())
    require(
      after == null || after.doc < limit,
      s"after.doc exceeds the number of documents in the reader: after.doc=${after.doc} limit=${limit}"
    )
    val cappedNumHits = math.min(numHits, limit)
    val manager = new CollectorManager[OdinsonCollector, OdinResults] {
      def newCollector() = new OdinsonCollector(cappedNumHits, after, computeTotalHits)

      def reduce(collectors: Collection[OdinsonCollector]): OdinResults = {
        val results = collectors.iterator.asScala.map(_.odinResults).toArray
        OdinResults.merge(0, cappedNumHits, results, true)
      }
    }
    search(query, manager)
  }

  def pureSearch(query: Query, n: Int): OdinResults = {

    // Modify query to only return sentence entries
    val builder = new BooleanQuery.Builder()
    builder.add(query, Occur.MUST)
    builder.add(new TermQuery(new Term("type", "parent")), Occur.MUST_NOT)

    OdinsonIndexSearcher.convertToOdinResult(search(builder.build(), n))
  }

  def pureSearch(after: OdinsonScoreDoc, query: Query, n: Int): OdinResults = {

    // Modify query to only return sentence entries
    val builder = new BooleanQuery.Builder()
    builder.add(query, Occur.MUST)
    builder.add(new TermQuery(new Term("type", "parent")), Occur.MUST_NOT)

    OdinsonIndexSearcher.convertToOdinResult(searchAfter(after, builder.build(), n))
  }

}


object OdinsonIndexSearcher {

  private def convertToOdinResult(topDocs: TopDocs): OdinResults = {
    return new OdinResults(
      topDocs.totalHits,
      topDocs.scoreDocs.map(convertToOdinScoreDoc),
      topDocs.getMaxScore
    )
  }


  private def convertToOdinScoreDoc(scoreDoc: ScoreDoc): OdinsonScoreDoc = {
    new OdinsonScoreDoc(scoreDoc.doc, scoreDoc.score, scoreDoc.shardIndex)
  }


}
