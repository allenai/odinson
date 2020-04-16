package ai.lum.odinson.lucene.analysis

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.{CharTermAttribute, PositionIncrementAttribute}

class OdinsonMultiTokenStream(val tokens: Seq[Seq[String]]) extends TokenStream {

  val termAtt = addAttribute(classOf[CharTermAttribute])
  val posIncrAtt = addAttribute(classOf[PositionIncrementAttribute])

  private var tokenIndex: Int = 0
  private var analysisIndex: Int = 0

  override def reset(): Unit = {
    super.reset()
    tokenIndex = 0
    analysisIndex = 0
  }

  final def incrementToken(): Boolean = {
    clearAttributes()

    if (tokenIndex >= tokens.length) return false

    termAtt.setEmpty().append(tokens(tokenIndex)(analysisIndex))
    if (analysisIndex > 0) {
      posIncrAtt.setPositionIncrement(0);
    }
    analysisIndex += 1
    if (analysisIndex == tokens(tokenIndex).length) {
      tokenIndex += 1
      analysisIndex = 0
    }


    true
  }

}
