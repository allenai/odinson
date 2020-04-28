package ai.lum.odinson.extra

import java.nio.charset.StandardCharsets.UTF_8

import ai.lum.odinson.extra.serialization._


/** Utilities for encoding/decoding [[org.clulab.serialization.json.SentenceOps]] to/from bytes */
object DocUtils {

  def sentenceToBytes(s: processors.Sentence): Array[Byte] = {
    s.json(pretty=false).getBytes(UTF_8)
  }

  def bytesToJsonString(bytes: Array[Byte]): String = {
    new String(bytes, UTF_8)
  }

}