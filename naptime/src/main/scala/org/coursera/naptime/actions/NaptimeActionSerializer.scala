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

import java.nio.charset.StandardCharsets

import com.linkedin.data.codec.JacksonDataCodec
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.NullDataSchema
import com.linkedin.data.schema.StringDataSchema
import com.linkedin.data.template.RecordTemplate
import play.api.http.ContentTypes
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes

/**
 * Actions have far fewer restrictions on the response type than other actions. We thus define a looser
 * serializer API (as compared to [[NaptimeSerializer]].
 */
trait NaptimeActionSerializer[T] {
  def serialize(t: T): Array[Byte]
  def contentType(t: T): String
  def schema(t: T): Option[DataSchema]
}

object NaptimeActionSerializer {
  implicit def courierModel[T <: RecordTemplate]: NaptimeActionSerializer[T] = {
    new NaptimeActionSerializer[T] {
      override def serialize(t: T): Array[Byte] = {
        val codec = new JacksonDataCodec()
        val bytes = codec.mapToBytes(t.data())
        bytes
      }
      override def contentType(t: T): String = ContentTypes.JSON
      override def schema(t: T): Option[DataSchema] = Some(t.schema())
    }
  }

  implicit object PlayJson extends NaptimeActionSerializer[JsValue] {
    override def serialize(t: JsValue): Array[Byte] =
      Json.stringify(t).getBytes(StandardCharsets.UTF_8)

    override def schema(t: JsValue): Option[DataSchema] = None

    override def contentType(t: JsValue): String = ContentTypes.JSON
  }

  implicit def playJson[T](implicit playJsonWrites: Writes[T]): NaptimeActionSerializer[T] = {
    new NaptimeActionSerializer[T] {
      override def serialize(t: T): Array[Byte] =
        PlayJson.serialize(Json.toJson(t))

      override def contentType(t: T): String = ContentTypes.JSON

      override def schema(t: T): Option[DataSchema] = None
    }
  }

  implicit object Strings extends NaptimeActionSerializer[String] {
    override def serialize(t: String): Array[Byte] =
      t.getBytes(StandardCharsets.UTF_8)

    override def schema(t: String): Option[DataSchema] =
      Some(new StringDataSchema)

    override def contentType(t: String): String = ContentTypes.TEXT
  }

  implicit object UnitWriter extends NaptimeActionSerializer[Unit] {
    override def serialize(t: Unit): Array[Byte] = Array.emptyByteArray

    override def contentType(t: Unit): String = ContentTypes.TEXT

    override def schema(t: Unit): Option[DataSchema] = Some(new NullDataSchema)
  }

  implicit def optionWriter[T](
      implicit objSerializer: NaptimeActionSerializer[T]): NaptimeActionSerializer[Option[T]] = {
    new NaptimeActionSerializer[Option[T]] {
      override def serialize(t: Option[T]): Array[Byte] = {
        t.map { elem =>
            objSerializer.serialize(elem)
          }
          .getOrElse(Array.emptyByteArray)
      }

      override def contentType(t: Option[T]): String = {
        t.map { elem =>
            objSerializer.contentType(elem)
          }
          .getOrElse(ContentTypes.TEXT)
      }

      override def schema(t: Option[T]): Option[DataSchema] = {
        t.flatMap { elem =>
          objSerializer.schema(elem)
        }
      }
    }
  }

  object AnyWrites {
    implicit object AnyWrites extends NaptimeActionSerializer[Any] {
      override def serialize(t: Any): Array[Byte] =
        t.toString.getBytes(StandardCharsets.UTF_8)

      override def contentType(t: Any): String = ContentTypes.TEXT

      override def schema(t: Any): Option[DataSchema] = None
    }
  }
}
