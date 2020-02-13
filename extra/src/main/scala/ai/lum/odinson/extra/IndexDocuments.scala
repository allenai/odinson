package ai.lum.odinson.extra

import java.io._

import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.util.BytesRef
import org.apache.lucene.document._
import org.apache.lucene.document.Field.Store
import org.clulab.processors.{Sentence, Document => ProcessorsDocument}
import org.clulab.serialization.json.JSONSerializer
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.Serializer
import ai.lum.labrador.DocumentMetadata
import ai.lum.odinson.lucene.analysis._
import ai.lum.odinson.OdinsonIndexWriter

import scala.util.{Failure, Success, Try}


object IndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val indexDir = config[File]("odinson.indexDir")
  val docsDir  = config[File]("odinson.docsDir")
  val documentIdField = config[String]("odinson.index.documentIdField")
  val sentenceIdField = config[String]("odinson.index.sentenceIdField")
  val sentenceLengthField  = config[String]("odinson.index.sentenceLengthField")
  val rawTokenField        = config[String]("odinson.index.rawTokenField")
  val wordTokenField       = config[String]("odinson.index.wordTokenField")
  val normalizedTokenField = config[String]("odinson.index.normalizedTokenField")
  val lemmaTokenField      = config[String]("odinson.index.lemmaTokenField")
  val posTagTokenField     = config[String]("odinson.index.posTagTokenField")
  val chunkTokenField      = config[String]("odinson.index.chunkTokenField")
  val entityTokenField     = config[String]("odinson.index.entityTokenField")
  val incomingTokenField   = config[String]("odinson.index.incomingTokenField")
  val outgoingTokenField   = config[String]("odinson.index.outgoingTokenField")
  val dependenciesField    = config[String]("odinson.index.dependenciesField")
  val sortedDocValuesFieldMaxSize  = config[Int]("odinson.index.sortedDocValuesFieldMaxSize")
  val maxNumberOfTokensPerSentence = config[Int]("odinson.index.maxNumberOfTokensPerSentence")
  val synchronizeOrderWithDocumentId =    config[Boolean]("odinson.index.synchronizeOrderWithDocumentId")

  val storeSentenceJson   = config[Boolean]("odinson.extra.storeSentenceJson")

  implicit val formats: DefaultFormats = DefaultFormats

  val writer = OdinsonIndexWriter.fromConfig()

  // serialized org.clulab.processors.Document or Document json
  val SUPPORTED_EXTENSIONS = "(?i).*?\\.(ser|json|jsonl)$"
  // NOTE indexes the documents in parallel
  // FIXME: groupBy by extension-less basename?

  val documentFiles = if (synchronizeOrderWithDocumentId) {
    // files ordered by the id of the document
    docsDir.listFilesByRegex(SUPPORTED_EXTENSIONS, recursive = true)
      .withFilter(f => !f.getName.endsWith(".metadata.ser"))
      .map(f => (deserializeDocs(f)(0)._1.id.map(_.toInt), f))
      .toSeq
      .sortBy(_._1)
      .map(_._2)
  } else {
    docsDir.listFilesByRegex(SUPPORTED_EXTENSIONS, recursive = true)
      .toSeq.par
      .withFilter(f => !f.getName.endsWith(".metadata.ser"))
  }

  documentFiles.foreach{ f =>
    Try {
      deserializeDocs(f).foreach(r => {
        val block = mkDocumentBlock(r._1, r._2)
        writer.addDocuments(block)
      })
    } match {
      case Success(_) =>
        logger.info(s"Indexed ${f.getName}")
      case Failure(e) =>
        logger.error(s"Failed to index ${f.getName}", e)
    }
  }

  writer.close

  // fin


  def deserializeDocs(f: File): Seq[(ProcessorsDocument, JValue)] = f.getName.toLowerCase match {
    case jsonl if jsonl.endsWith(".jsonl") =>
      val source = scala.io.Source.fromFile(f)
      val docs = source.getLines.map(l => {
        val jast = parse(l)
        val metadata = jast \ "metadata"
        val doc = JSONSerializer.toDocument(jast)
        (doc, metadata)
      }).toList
      source.close()
      docs

    case json if json.endsWith(".json") =>
      val source = scala.io.Source.fromFile(f)
      val docJson = parse(source.getLines.mkString)

      source.close()
      val doc = JSONSerializer.toDocument(docJson)
      val metadata = docJson \ "metadata"
      List((doc, metadata))
    case ser if ser.endsWith(".ser") =>
      val doc = Serializer.deserialize[ProcessorsDocument](f)
      val md: Option[DocumentMetadata] = {
        val mdFile = new File(f.getCanonicalPath.replaceAll("\\.ser", ".metadata.ser"))
        if (mdFile.exists) {
          Some(Serializer.deserialize[DocumentMetadata](mdFile))
        } else None
      }
      List((doc, JNothing))
      // NOTE: we're assuming this is
    case gz if gz.endsWith("json.gz") =>
      val contents: String = GzipUtils.uncompress(f)
      val jast = parse(contents)
      val metadata = jast \ "metadata"
      val doc = JSONSerializer.toDocument(jast)

      List((doc, metadata))
    case other =>
      throw new Exception(s"Cannot deserialize ${f.getName} to org.clulab.processors.Document. Unsupported extension '$other'")
  }

  def generateUUID: String = {
    java.util.UUID.randomUUID().toString
  }

  // generates a lucene document per sentence
  def mkDocumentBlock(d: ProcessorsDocument, metadata: JValue): Seq[Document] = {
    // FIXME what should we do if the document has no id?
    val docId = d.id.getOrElse(generateUUID)

    val block = ArrayBuffer.empty[Document]
    for ((s, i) <- d.sentences.zipWithIndex) {
      if (s.size <= maxNumberOfTokensPerSentence) {
        block += mkSentenceDoc(s, docId, i.toString, metadata)
      } else {
        logger.warn(s"skipping sentence with ${s.size} tokens")
      }
    }
    block += mkParentDoc(docId, metadata)
    block
  }

  def indexKeyValueField(doc: Document, key: String, value: JValue): Unit ={
    // trailing underscore mark sentence level metadata, we remove them before indexing
    val key_ = if (key.endsWith("_")) key.dropRight(1) else key
    value match {
      case JString(s) => {
        doc.add(new TextField(key_, s, Store.NO))
      }
      case JLong(l) => {
        doc.add(new LongPoint(key_, l))
      }
      case JInt(i) => { // i is BigInteger, we truncate to int.
        doc.add(new IntPoint(key_, i.toInt))
      }
      case JDouble(d) => {
        doc.add(new DoublePoint(key_, d))
      }
      case JDecimal(f) => { // d is BigDecimal, we truncate to float.
        doc.add(new FloatPoint(key_, f.toFloat))
      }
      case JBool(b) => {
        doc.add(new TextField(key_, b.toString, Store.NO))
      }
      case _ => {
        logger.warn("Field skipped (type not supported): " + value.toString)
      }
    }
  }

  def mkParentDoc(docId: String, metadata: JValue): Document = {
    val parent = new Document
    // FIXME these strings should probably be defined in the config, not hardcoded
    parent.add(new StringField("type", "parent", Store.NO))
    parent.add(new StringField("docId", docId, Store.YES))
    implicit val formats = org.json4s.DefaultFormats
    parent.add(new StringField("md-json", compact(render(metadata)), Store.YES))
    metadata match {
      case JObject(fields) => {
        for (field <- fields) {
          if (field._1 == "type" || field._1 == "docId") {
            logger.warn("\"type\" and \"docId\" are reserved fields and will be ignored. Use differently named fields if needed.")
          } else {
            indexMetadataField(parent, field)
          }
        }
      }
      case JNothing =>
      case _ => {
        logger.warn("Metadata skipped (bad format: not an object)")
      }
    }

    parent
  }

  def indexMetadataField(doc: Document, field: JField): Unit = {
      field._2 match {
        case JArray(values) => {
          for (elem <- values) {
            indexKeyValueField(doc, field._1, elem)
          }
        }
        case _ => {
          indexKeyValueField(doc, field._1, field._2)
        }
      }
  }

  /**
    * Adds metadata to a sentence doc based on the metadata of the parent document
    *
    * the metadata is added as a json object string which contains the parent metadata fields which end with "_"
    * @param sentDoc the lucene sentence doc to which we want to add metadata
    * @param metadata the json metadata taken from the parent doc
    */
  def addSentenceMetadata(sentDoc: Document, metadata: JValue): Unit = {
    val sentenceMetadata = metadata match {
      case JObject(fields) => {
        JObject(fields.filter(_._1.endsWith("_")).map(f => JField(f._1.dropRight(1), f._2)))
      }
      case _ => JNothing

    }
    sentenceMetadata match {
      case JObject(fields) => {
        implicit val formats = org.json4s.DefaultFormats
        sentDoc.add(new StringField("md-json", compact(render(sentenceMetadata)), Store.YES))
      }
      case JNothing =>
      case _ => {
        logger.warn("Metadata skipped at sentence level (bad format: not an object)")
      }
    }
  }

  def mkSentenceDoc(s: Sentence, docId: String, sentId: String, metadata: JValue): Document = {
    val sent = new Document
    addSentenceMetadata(sent, metadata)

    sent.add(new StoredField(documentIdField, docId))
    sent.add(new StoredField(sentenceIdField, sentId))
    sent.add(new NumericDocValuesField(sentenceLengthField, s.size.toLong))
    sent.add(new TextField(rawTokenField, new OdinsonTokenStream(s.raw)))
    // we want to index and store the words for displaying in the shell
    sent.add(new TextField(wordTokenField, s.words.mkString(" "), Store.YES))
    sent.add(new TextField(normalizedTokenField, new NormalizedTokenStream(s.raw, s.words)))
    if (s.tags.isDefined) {
      sent.add(new TextField(posTagTokenField, new OdinsonTokenStream(s.tags.get)))
    }
    if (s.lemmas.isDefined) {
      sent.add(new TextField(lemmaTokenField, new OdinsonTokenStream(s.lemmas.get)))
    }
    if (s.entities.isDefined) {
      sent.add(new TextField(entityTokenField, new OdinsonTokenStream(s.entities.get)))
    }
    if (s.chunks.isDefined) {
      sent.add(new TextField(chunkTokenField, new OdinsonTokenStream(s.chunks.get)))
    }
    if (s.dependencies.isDefined) {
      val deps = s.dependencies.get
      sent.add(new TextField(incomingTokenField, new DependencyTokenStream(deps.incomingEdges)))
      sent.add(new TextField(outgoingTokenField, new DependencyTokenStream(deps.outgoingEdges)))
      val graph = writer.mkDirectedGraph(deps.incomingEdges, deps.outgoingEdges, deps.roots.toArray)
      val bytes = graph.toBytes
      if (bytes.length <= sortedDocValuesFieldMaxSize) {
        sent.add(new SortedDocValuesField(dependenciesField, new BytesRef(bytes)))
      } else {
        logger.warn(s"serialized dependencies too big for storage: ${bytes.length} > $sortedDocValuesFieldMaxSize bytes")
      }
    }

    if (storeSentenceJson) {
      // store sentence JSON in index for use in webapp
      // NOTE: this will **greatly** increase the size of the index
      sent.add(new StoredField("json-binary", DocUtils.sentenceToBytes(s)))
    }

    sent
  }

}
