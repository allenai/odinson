package ai.lum.odinson

import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import javax.print.attribute.standard.NumberOfDocuments

class ExtractionQuery(
  var odinsonQuery: Option[String],
  var documentLuceneQuery: Option[String],
  var sentenceLuceneQuery: Option[String],
  var numDocuments: Option[Int],
  var continuation: Option[OdinsonScoreDoc],
) {

  def setOdinsonQuery(query: String): ExtractionQuery = {
    this.odinsonQuery = Some(query)
    this
  }

  def setOdinsonQuery(oq: Option[String]): ExtractionQuery = {
    this.odinsonQuery = oq
    this
  }

  def setDocumentQuery(luceneQuery: String): ExtractionQuery = {
    this.documentLuceneQuery = Some(luceneQuery)
    this
  }

  def setDocumentQuery(dq: Option[String]): ExtractionQuery = {
    this.documentLuceneQuery = dq
    this
  }

  def setSentenceQuery(luceneQuery: String): ExtractionQuery = {
    this.sentenceLuceneQuery = Some(luceneQuery)
    this
  }

  def setSentenceQuery(sq: Option[String]): ExtractionQuery = {
    this.sentenceLuceneQuery = sq
    this
  }

  def setLimit(numberOfDocuments: Int): ExtractionQuery = {
    this.numDocuments = Some(numberOfDocuments)
    this
  }

  def setLimit(nd: Option[Int]): ExtractionQuery = {
    this.numDocuments = nd
    this
  }

  def setContinuation(doc: Int, score: Float): ExtractionQuery = {
    this.continuation = Some(new OdinsonScoreDoc(doc, score))
    this
  }

}

object ExtractionQuery {

  def builder(): ExtractionQuery = {
    new ExtractionQuery(None, None, None, None, None)
  }

}
