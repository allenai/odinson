package ai.lum.odinson.extra

import java.io.File

import org.scalatest.{FlatSpec, Matchers}
import ai.lum.odinson.extra.IndexDocuments.mkParentDoc
import ai.lum.odinson.extra.IndexDocuments.addSentenceMetadata
import ai.lum.odinson.extra.IndexDocuments.deserializeDocs
import org.apache.lucene.document.Document
import org.json4s.JNothing
import org.json4s.JsonAST._
class IndexDocumentsTest extends FlatSpec with Matchers {


  "mkParentDoc" should "return a document with no metadata" in  {
    val parentDoc = mkParentDoc("001", JNothing)
    parentDoc.getFields.size() shouldBe 3 // type, docId and md-json are always added, but nothing else should be present.
  }

  "mkParentDoc" should "return a document with simple metadata fields" in  {
    val parentDoc = mkParentDoc("001", JObject(List(
      ("author", JString("John")),
      ("title", JString("Lucene tips and tricks")),
      ("yearlong", JLong(1981)),
      ("yearint", JInt(1981)),
      ("costdouble", JDouble(30.4)),
      ("costdecimal", JDecimal(30.4)),
      ("free", JBool(false))
    )))

    // We expect the author field to be added without the trailiing underscore (indicating a string value field)
    parentDoc.getField("author").stringValue shouldBe "John"
    parentDoc.getField("title").stringValue shouldBe "Lucene tips and tricks"
    parentDoc.getField("title").fieldType.tokenized shouldBe true
    parentDoc.getField("yearlong").numericValue shouldBe 1981l
    parentDoc.getField("yearint").numericValue shouldBe 1981
    parentDoc.getField("costdouble").numericValue shouldBe 30.4
    parentDoc.getField("costdecimal").numericValue shouldBe 30.4f
    parentDoc.getField("free").stringValue shouldBe "false"
  }

  "mkParentDoc" should "return a document with a multivalued field" in  {
    val parentDoc = mkParentDoc("001", JObject(List(
      ("author", JArray(List(JString("John"), JString("Jeff")))),
    )))
    parentDoc.getFields("author").size shouldBe 2
    parentDoc.getFields("author")(0).stringValue shouldBe "John"
    parentDoc.getFields("author")(1).stringValue shouldBe "Jeff"
  }


  "addSentenceMetadata" should "return a document with a metadata field which includes only fields with underscore" in {
    val sentenceDoc = new Document
    addSentenceMetadata(sentenceDoc,
      JObject(List(
        ("author", JString("John")),
        ("title_", JString("Lucene tips and tricks")),
        ("yearlong", JLong(1981)),
        ("yearint", JInt(1981)),
        ("costdouble", JDouble(30.4)),
        ("costdecimal", JDecimal(30.4)),
        ("free", JBool(false))
      )))

    // Make sure that we index only metadata fields that end with "_"
    sentenceDoc.getField("md-json").stringValue shouldBe "{\"title_\":\"Lucene tips and tricks\"}"
  }
  "addSentenceMetadata" should "return a document with no metadata field" in {

    val sentenceDoc = new Document
    addSentenceMetadata(sentenceDoc, JNothing)
    sentenceDoc.getField("md-json") shouldBe null
  }

  "deserializeDocs" should "read the .json file and return a document with metadata"  in {
    val resourcesPath = getClass.getResource("/doc.json")
    val docsFile = new File(resourcesPath.getPath)
    val docs = deserializeDocs(docsFile)
    docs.size shouldBe 1
    docs(0)._1.text.get shouldBe "What if Google Morphed Into GoogleOS? What if Google expanded on its search-engine (and now e-mail) wares into a full-fledged operating system? [via Microsoft Watch from Mary Jo Foley ] "
    docs(0)._2 shouldBe JObject(List(
      ("author", JArray(List(JString("jenny"), JString("john"))))
    ))
  }

  "deserializeDocs" should "read the .json.gz file and return a document with metadata"  in {
    val resourcesPath = getClass.getResource("/doc.json.gz")
    val docsFile = new File(resourcesPath.getPath)
    val docs = deserializeDocs(docsFile)
    docs.size shouldBe 1
    docs(0)._1.text.get shouldBe "What if Google Morphed Into GoogleOS? What if Google expanded on its search-engine (and now e-mail) wares into a full-fledged operating system? [via Microsoft Watch from Mary Jo Foley ] "
    docs(0)._2 shouldBe JObject(List(
      ("author", JArray(List(JString("jenny"), JString("john"))))
    ))
  }

  "deserializeDocs" should "read the .jsonl file and return 2 documents, the first one with authors metadata"  in {
    val resourcesPath = getClass.getResource("/docs.jsonl")
    val docsFile = new File(resourcesPath.getPath)
    val docs = deserializeDocs(docsFile)
    docs.size shouldBe 2
    docs(0)._1.text.get shouldBe "What if Google Morphed Into GoogleOS? What if Google expanded on its search-engine (and now e-mail) wares into a full-fledged operating system? [via Microsoft Watch from Mary Jo Foley ] "
    docs(0)._2 shouldBe JObject(List(
      ("author", JArray(List(JString("jenny"), JString("john"))))
    ))
    docs(1)._1.text.get shouldBe "(And, by the way, is anybody else just a little nostalgic for the days when that was a good thing?) This BuzzMachine post argues that Google's rush toward ubiquity might backfire -- which we've all heard before, but it's particularly well-put in this post. Google is a nice search engine. Does anybody use it for anything else? They own blogger, of course. Is that a money maker? I'm staying away from the stock. "
    docs(1)._2 shouldBe JNothing
  }

}
