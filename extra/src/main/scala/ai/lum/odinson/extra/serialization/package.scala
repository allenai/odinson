package ai.lum.odinson.extra

import ai.lum.odinson.extra.processors.{Document, Entity, Sentence}
import ai.lum.odinson.extra.struct.{DirectedGraph, Edge, GraphMap}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson._


package object serialization {

  implicit val formats = DefaultFormats

  /** Method for debugging json */
  def stringify(json: JValue, pretty: Boolean): String = pretty match {
    case true => prettyJson(renderJValue(json))
    case false => compactJson(renderJValue(json))
  }

  // Arrays cannot be directly converted to JValue
  implicit class ArrayOps(s: Option[Array[String]]) {
    def toSerializableJSON: Option[List[String]] = s match {
      case Some(s) => Some(s.toList)
      case None => None
    }
  }

  implicit class ODirectedGraphOps(odg: Option[DirectedGraph[String]]) {
    def toSerializableJSON: Option[JValue] = odg match {
      case Some(g) => Some(g.jsonAST)
      case None => None
    }
  }

  implicit class DirectedGraphOps(dg: DirectedGraph[String]) extends JSONSerialization {
    def jsonAST: JValue = {
      ("edges" -> dg.edges.map(_.jsonAST)) ~
        ("roots" -> dg.roots)
    }
  }

  implicit class EdgeOps(edge: Edge[String]) extends JSONSerialization {
    def jsonAST: JValue = {
      ("source" -> edge.source) ~
        ("destination" -> edge.destination) ~
        ("relation" -> edge.relation.toString)
    }
  }

  implicit class GraphMapOps(gm: GraphMap) extends JSONSerialization {
    def jsonAST: JValue = Extraction.decompose(gm.toMap.mapValues(_.jsonAST))
  }

  /** For Document */
  implicit class DocOps(doc: Document) extends JSONSerialization {

    def jsonAST: JValue = {
      // field and value are removed when value is not present
      ("id" -> doc.id) ~
        ("text" -> doc.text) ~
        ("sentences" -> doc.sentences.map(_.jsonAST).toList)
      // TODO: handle discourse tree
      //("discourse-tree" -> discourseTree)
    }
  }


  /** For Entity */
  implicit class EntityOps(ent: Entity) extends JSONSerialization {

    def jsonAST: JValue = {
      ("first" -> ent.first) ~
        ("last" -> ent.last) ~
        ("label" -> ent.label) ~
        ("priority" -> ent.priority)
    }
  }

  /** For Sentence */
  implicit class SentenceOps(s: Sentence) extends JSONSerialization {

    def jsonAST: JValue =
      val overlappingEntitiesJSON = s.overlappingEntities match {
        case Some(oe) => JArray(oe.map(_.jsonAST).toList)
        case None => JNothing
      }
    {
      ("words" -> s.words.toList) ~
        ("startOffsets" -> s.startOffsets.toList) ~
        ("endOffsets" -> s.endOffsets.toList) ~
        ("raw" -> s.raw.toList) ~
        ("tags" -> s.tags.toSerializableJSON) ~
        ("lemmas" -> s.lemmas.toSerializableJSON) ~
        ("entities" -> s.entities.toSerializableJSON) ~
        ("overlappingEntities" -> overlappingEntitiesJSON) ~
        ("norms" -> s.norms.toSerializableJSON) ~
        ("chunks" -> s.chunks.toSerializableJSON) ~
        ("graphs" -> s.graphs.jsonAST)
      // TODO: handle tree
      //("syntactic-tree") -> syntacticTree)
    }

  }

}
