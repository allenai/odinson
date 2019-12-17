package ai.lum.odinson

import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import javax.print.attribute.standard.NumberOfDocuments

class ExtractionQueryParams(
  var odinsonQuery: Option[String],
  var documentLuceneQuery: Option[String],
  var sentenceLuceneQuery: Option[String],
  var numDocuments: Option[Int],
  var continuation: Option[OdinsonScoreDoc],
) {

  def setOdinsonQuery(query: String): ExtractionQueryParams = {
    this.odinsonQuery = Some(query)
    this
  }

  def setOdinsonQuery(oq: Option[String]): ExtractionQueryParams = {
    this.odinsonQuery = oq
    this
  }

  def setDocumentQuery(luceneQuery: String): ExtractionQueryParams = {
    this.documentLuceneQuery = Some(luceneQuery)
    this
  }

  def setDocumentQuery(dq: Option[String]): ExtractionQueryParams = {
    this.documentLuceneQuery = dq
    this
  }

  def setSentenceQuery(luceneQuery: String): ExtractionQueryParams = {
    this.sentenceLuceneQuery = Some(luceneQuery)
    this
  }

  def setSentenceQuery(sq: Option[String]): ExtractionQueryParams = {
    this.sentenceLuceneQuery = sq
    this
  }

  def setLimit(numberOfDocuments: Int): ExtractionQueryParams = {
    this.numDocuments = Some(numberOfDocuments)
    this
  }

  def setLimit(nd: Option[Int]): ExtractionQueryParams = {
    this.numDocuments = nd
    this
  }

  def setContinuation(doc: Int, score: Float): ExtractionQueryParams = {
    this.continuation = Some(new OdinsonScoreDoc(doc, score))
    this
  }

}

object ExtractionQueryParams {

  def builder(): ExtractionQueryParams = {
    new ExtractionQueryParams(None, None, None, None, None)
  }

}
