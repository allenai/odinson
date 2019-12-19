package ai.lum.odinson.lucene.search

import org.apache.lucene.store.FSDirectory
import org.scalatest._
import java.nio.file.Paths
import java.util.concurrent.Executors

import ai.lum.odinson.lucene.search.OdinsonIndexSearcher
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.MatchAllDocsQuery

class OdinsonIndexSearcherTest extends FlatSpec with Matchers {

  def withIndexSearcher(testCode: OdinsonIndexSearcher => Any) {

    val resourcesPath = getClass.getResource("/dummy-index")
    val index = FSDirectory.open(Paths.get(resourcesPath.getPath))
    val pool = Executors.newFixedThreadPool(2)
    val indexSearcher =
      new OdinsonIndexSearcher(DirectoryReader.open(index), pool, false)
    try {
      testCode(indexSearcher) // "loan" the fixture to the test
    } finally index.close()
  }

  "pureSearch" should "return just the 4 sentences without the additional 2 docs" in withIndexSearcher {
    searcher =>
      val results = searcher.pureSearch(new MatchAllDocsQuery(), 10)
      results.totalHits shouldEqual 4
      results.scoreDocs.length shouldEqual 4
  }

  it should "correctly limit the returned results based on the 'n' parameter" in withIndexSearcher {
    searcher =>
      val results = searcher.pureSearch(new MatchAllDocsQuery(), 2)
      results.totalHits shouldEqual 4
      results.scoreDocs.length shouldEqual 2
  }

  it should "return the same results through paging as without" in withIndexSearcher {
    searcher =>
      val results = searcher.pureSearch(new MatchAllDocsQuery(), 10)
      results.totalHits shouldEqual 4
      results.scoreDocs.length shouldEqual 4

      val singlePageIds = results.scoreDocs.map(_.doc)

      val page1 = searcher.pureSearch(new MatchAllDocsQuery(), 2)

      page1.scoreDocs.length shouldBe 2

      val page2 =
        searcher.pureSearch(page1.scoreDocs(1), new MatchAllDocsQuery(), 2)

      page2.scoreDocs.length shouldBe 2

      val multiPageIds = (page1.scoreDocs ++ page2.scoreDocs).map(_.doc)

      singlePageIds shouldEqual multiPageIds

  }

}
