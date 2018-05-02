/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.actions

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.Null
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.template.RecordTemplate
import org.coursera.naptime.courier.CourierFormats
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.OFormat

/**
 * Abstracts over the Play-JSON API and the Courier underlying record template support for
 * serialization, allowing us to convert Play-JSON into Pegasus DataMaps.
 */
trait NaptimeSerializer[T] {
  def serialize(t: T): DataMap

  def schema(t: T): Option[DataSchema]
}

object NaptimeSerializer {

  /**
   * Retrieves the underlying DataMap from a RecordTemplate from a [Courier generated] object.
   *
   * @tparam T The type of the model.
   */
  implicit def courierModels[T <: RecordTemplate]: NaptimeSerializer[T] = {
    new NaptimeSerializer[T] {
      override def serialize(t: T): DataMap = t.data()

      override def schema(t: T): Option[DataSchema] = Some(t.schema())
    }
  }

  /**
   * Converts Play-JSON's JsObject to a DataMap.
   */
  implicit object PlayJson extends NaptimeSerializer[JsObject] {
    override def serialize(t: JsObject): DataMap = {

      /**
       * Structured in this manner to avoid the DataMap's cycle checker from wasting a bunch of time
       */
      def serializeMap(obj: JsObject, map: DataMap): Unit = {
        for {
          (name, value) <- obj.value
        } {
          try {
            value match {
              case s: JsString =>
                map.put(name, s.value)
              case i: JsNumber =>
                map.put(name, CourierFormats.bigDecimalToNumber(i.value))
              case b: JsBoolean =>
                map.put(name, Boolean.box(b.value))
              case JsNull =>
                map.put(name, Null.getInstance())
              case a: JsArray =>
                val list = new DataList() // Don't include initial size because Scala Lists are slow!
                map.put(name, list)
                serializeList(a, list)
              case o: JsObject =>
                val childMap = new DataMap(o.value.size) // TODO: verify Map.size is fast.
                map.put(name, childMap)
                serializeMap(o, childMap)
            }
          } catch {
            case e: NullPointerException =>
              throw new RuntimeException(
                s"Null encountered when serializing field $name -> $value in object $obj",
                e)
          }
        }
      }

      def serializeList(jsArray: JsArray, list: DataList): Unit = {
        for (i <- jsArray.value) {
          i match {
            case s: JsString =>
              list.add(s.value)
            case i: JsNumber =>
              list.add(CourierFormats.bigDecimalToNumber(i.value))
            case b: JsBoolean =>
              list.add(Boolean.box(b.value))
            case JsNull =>
              list.add(Null.getInstance())
            case a: JsArray =>
              val childList = new DataList() // Don't include initial size b/c Scala Lists are slow!
              list.add(childList)
              serializeList(a, childList)
            case o: JsObject =>
              val childMap = new DataMap(o.value.size)
              list.add(childMap)
              serializeMap(o, childMap)
          }
        }
      }

      val map = new DataMap(t.value.size)
      serializeMap(t, map)
      map
    }

    /**
     * Convenience function to invert serialization.
     *
     * @param dataMap The input dataMap to convert back.
     * @return The DataMap converted to a JsObject
     */
    def deserialize(dataMap: DataMap): JsObject = {
      CourierFormats.dataMapToObj(dataMap)
    }

    override def schema(t: JsObject): Option[DataSchema] = None
  }

  /**
   * Serializes a [case] class that has an implicit [[OFormat]] in scope.
   *
   * @param playJsonFormat Serializes the [case] class to Play-JSON.
   */
  implicit def playJsonFormats[T](implicit playJsonFormat: OFormat[T]): NaptimeSerializer[T] = {
    new NaptimeSerializer[T] {
      override def serialize(t: T): DataMap = {
        val json = playJsonFormat.writes(t)
        PlayJson.serialize(json)
      }

      override def schema(t: T): Option[DataSchema] = None
    }
  }

  /**
   * Useful for mocking tests / etc.
   */
  object AnyWrites {
    implicit val anyWrites: NaptimeSerializer[Any] =
      new NaptimeSerializer[Any] {
        override def serialize(t: Any): DataMap = {
          val m = new DataMap()
          m.put("id", t.toString)
          m
        }

        override def schema(t: Any): Option[DataSchema] = {
          None
        }
      }
  }
}
