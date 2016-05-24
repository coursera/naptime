package org.coursera.naptime.router2

import com.typesafe.scalalogging.StrictLogging
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.schema.Attribute
import org.coursera.naptime.schema.JsValue
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json

import scala.util.control.NonFatal

object AttributesProvider extends StrictLogging {

  lazy val scaladocs: Map[String, JsObject] = {
    val scaladocPath = "/naptime.scaladoc.json"
    (for {
      stream <- Option(getClass.getResourceAsStream(scaladocPath))
      json <- try {
        Some(Json.parse(stream))
      } catch {
        case NonFatal(exception) =>
          logger.warn(
            s"Could not parse contents of file " +
              s"$scaladocPath as JSON")
          None
      } finally {
        stream.close()
      }
      scaladocCollection <- json.validate[Map[String, JsObject]] match {
        case JsSuccess(deserialized, _) =>
          Some(deserialized)
        case JsError(_) =>
          logger.warn(
            s"Could not deserialize contents of file " +
              s"$scaladocPath as `Map[String, JsObject]`")
          None
      }
    } yield {
      scaladocCollection
    }).getOrElse(Map.empty)
  }

  def getResourceAttributes(className: String): Seq[Attribute] = {
    scaladocs
      .get(className)
      .map(value => Attribute("scaladocs", Some(jsObjToJsValue(value))))
      .toList
  }

  def getMethodAttributes(className: String, methodName: String): Seq[Attribute] = {
    scaladocs
      .get(s"$className.$methodName")
      .map(value => Attribute("scaladocs", Some(jsObjToJsValue(value))))
      .toList
  }

  private[this] def jsObjToJsValue(jsObj: JsObject): JsValue = {
    JsValue.apply(CourierFormats.objToDataMap(jsObj), DataConversion.SetReadOnly)
  }
}
