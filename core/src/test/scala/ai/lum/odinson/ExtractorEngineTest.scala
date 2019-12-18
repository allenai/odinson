package ai.lum.odinson
import ai.lum.odinson.ExtractorEngine
import org.apache.lucene.store.FSDirectory
import org.scalatest._
import java.nio.file.Paths
import java.util.concurrent.Executors

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.TestUtils.odinsonConfig
import ai.lum.odinson.lucene.search.OdinsonIndexSearcher
import org.apache.lucene.index.DirectoryReader
import ai.lum.odinson.utils.ConfigFactory
import com.typesafe.config.Config

class ExtractorEngineTest extends FlatSpec with Matchers {

  def withExtractorEngine(testCode: ExtractorEngine => Any) {

    val config = ConfigFactory.load()
    val odinsonConfig = config[Config]("odinson")

    val resourcesPath = getClass.getResource("/dummy-index")
    val index = FSDirectory.open(Paths.get(resourcesPath.getPath))
    val engine = ExtractorEngine.fromDirectory(odinsonConfig, index)

    try {
      testCode(engine) // "loan" the fixture to the test
    } finally index.close()

  }

  "query" should "correctly return results when only an odinson query is defined" in withExtractorEngine {
    engine =>
      val results = engine.query(
        ExtractionQueryParams.builder().setOdinsonQuery("[] <conj_and []"))

      results.isRight shouldBe true
      // there is only one sentence with a conjunction in the dummy index (sentence id:0)
      results.right.get.scoreDocs.length shouldBe 1
      results.right.get.scoreDocs.head.doc shouldBe 0
  }

  it should "correctly return results when only an sentence query is defined" in withExtractorEngine {
    engine =>
      val results = engine.query(
        ExtractionQueryParams.builder().setSentenceQuery("word: 84"))

      results.isRight shouldBe true
      // there are two sentences with the word 84 in the dummy index (sentence ids: 3 and 4)
      results.right.get.scoreDocs.length shouldBe 2
      results.right.get.scoreDocs.map(_.doc) contains 3
      results.right.get.scoreDocs.map(_.doc) contains 4
  }

  it should "correctly limit the results based on the numDocuments param" in withExtractorEngine {
    engine =>
      val results = engine.query(
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("word: 84")
          .setLimit(1))

      results.isRight shouldBe true
      // there are two sentences with the word 84 in the dummy index (sentence ids: 3 and 4)
      results.right.get.scoreDocs.length shouldBe 1
  }

  it should "correctly default to words when term is not specified" in withExtractorEngine {
    engine =>
      val implicitResults =
        engine.query(ExtractionQueryParams.builder().setSentenceQuery("84"))

      implicitResults.isRight shouldBe true

      val explicitResults = engine.query(
        ExtractionQueryParams.builder().setSentenceQuery("word: 84"))

      explicitResults.isRight shouldBe true
      implicitResults.right.get.scoreDocs
        .map(_.doc) shouldEqual explicitResults.right.get.scoreDocs
        .map(_.doc)
  }

  it should "correctly return 0 results if odinson and sentence queries resolve different sentences" in withExtractorEngine {
    engine =>
      val results = engine.query(
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("84")
          .setOdinsonQuery("[] <conj_and []")
      )

      results.isRight shouldBe true
      results.right.get.totalHits shouldBe 0
      results.right.get.scoreDocs.length shouldBe 0
  }

  it should "correctly return the 1 result that matches both odinson and sentence queries" in withExtractorEngine {
    engine =>
      val results = engine.query(
        // compound only exists on sentence id: 3
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("84")
          .setOdinsonQuery("[] <compound []")
      )

      results.isRight shouldBe true
      results.right.get.totalHits shouldBe 1
      results.right.get.scoreDocs.length shouldBe 1
      results.right.get.scoreDocs.head.doc shouldBe 3
  }

  it should "correctly handle continuation when just odinson query is defined" in withExtractorEngine {
    engine =>
      val results = engine.query(
        ExtractionQueryParams.builder().setOdinsonQuery("[] <nsubj []"))

      results.isRight shouldBe true
      // all sentences have an nsubj
      results.right.get.scoreDocs.length shouldBe 4

      val page1 = engine.query(
        ExtractionQueryParams
          .builder()
          .setOdinsonQuery("[] <nsubj []")
          .setLimit(2))

      page1.isRight shouldBe true
      page1.right.get.scoreDocs.length shouldBe 2

      val page2 = engine.query(
        ExtractionQueryParams
          .builder()
          .setOdinsonQuery("[] <nsubj []")
          .setContinuation(page1.right.get.scoreDocs(1).doc,
                           page1.right.get.scoreDocs(1).score)
      )

      page2.isRight shouldBe true
      page2.right.get.scoreDocs.length shouldBe 2

      results.right.get.scoreDocs
        .map(_.doc) shouldEqual (page1.right.get.scoreDocs ++ page2.right.get.scoreDocs)
        .map(_.doc)

  }

  it should "correctly handle continuation when just sentence query is defined" in withExtractorEngine {
    engine =>
      val results =
        engine.query(ExtractionQueryParams.builder().setSentenceQuery("84"))

      results.isRight shouldBe true
      // only two sentences with word 84
      results.right.get.scoreDocs.length shouldBe 2

      val page1 = engine.query(
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("84")
          .setLimit(1))

      page1.isRight shouldBe true
      page1.right.get.scoreDocs.length shouldBe 1

      val page2 = engine.query(
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("84")
          .setContinuation(page1.right.get.scoreDocs(0).doc,
                           page1.right.get.scoreDocs(0).score)
      )

      page2.isRight shouldBe true
      page2.right.get.scoreDocs.length shouldBe 1

      results.right.get.scoreDocs
        .map(_.doc) shouldEqual (page1.right.get.scoreDocs ++ page2.right.get.scoreDocs)
        .map(_.doc)

  }

  it should "correctly handle continuation when both odinson and sentence queries are defined" in withExtractorEngine {
    engine =>
      val results =
        engine.query(
          ExtractionQueryParams
            .builder()
            .setSentenceQuery("84")
            .setOdinsonQuery("[] <nsubj []"))

      results.isRight shouldBe true
      // only two sentences with word 84
      results.right.get.scoreDocs.length shouldBe 2

      val page1 = engine.query(
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("84")
          .setOdinsonQuery("[] <nsubj []")
          .setLimit(1))

      page1.isRight shouldBe true
      page1.right.get.scoreDocs.length shouldBe 1

      val page2 = engine.query(
        ExtractionQueryParams
          .builder()
          .setSentenceQuery("84")
          .setOdinsonQuery("[] <nsubj []")
          .setContinuation(page1.right.get.scoreDocs(0).doc,
                           page1.right.get.scoreDocs(0).score)
      )

      page2.isRight shouldBe true
      page2.right.get.scoreDocs.length shouldBe 1

      results.right.get.scoreDocs
        .map(_.doc) shouldEqual (page1.right.get.scoreDocs ++ page2.right.get.scoreDocs)
        .map(_.doc)

  }

  it should "return an error in case the query invalid" in withExtractorEngine {
    engine =>
      val results =
        engine.query(ExtractionQueryParams.builder().setSentenceQuery("tag:*"))

      results.isLeft shouldBe true
  }

}
