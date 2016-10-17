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

package org.coursera.naptime

import com.linkedin.data.DataMap
import org.coursera.naptime.QueryStringParser.NaptimeParseError
import play.api.http.Status
import play.api.i18n.Lang
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc.RequestHeader

import scala.annotation.implicitNotFound
import play.api.libs.json.OFormat

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.parsing.combinator.RegexParsers

/**
 * All the contextual information about a naptime request.
 */
class RestContext[+AuthType, +BodyType] private[naptime] (
    val body: BodyType,
    val auth: AuthType,
    private[this] val _request: RequestHeader,
    val paging: RequestPagination,
    val includes: RequestIncludes,
    val fields: RequestFields) {

  def request(implicit evidence: RequestEvidence): RequestHeader = _request
  def acceptLanguages = _request.acceptLanguages

  /**
   * Compute the preferred language for this context given a set of available languages.
   *
   * @param availableLanguages The set of languages that are available for a given piece of content.
   * @param default The default language of the piece of content.
   * @return
   */
  def selectLanguage(availableLanguages: Set[Lang], default: Lang): Lang = {

      /**
       * Implementation of the tail-recursive algorithm.
       *
       * @param languagePreferences Preferences in decreasing preference order (from the request)
       * @return The language to use.
       */
      @tailrec
      def findPreferredLanguage(languagePreferences: Seq[Lang]): Lang = {
        if (languagePreferences.isEmpty) default
        else {
          val satisfiableLanguage = availableLanguages.find(_.satisfies(languagePreferences.head))
          if (satisfiableLanguage.isDefined) satisfiableLanguage.get
          else findPreferredLanguage(languagePreferences.tail)
        }
      }

    findPreferredLanguage(acceptLanguages)
  }

  private[naptime] def copyWithAuth[NewAuth](newAuth: NewAuth): RestContext[NewAuth, BodyType] = {
    new RestContext(body, newAuth, _request, paging, includes, fields)
  }
}

/**
 * Configure the defaults for pagination.
 */
case class PaginationConfiguration(defaultLimit: Int = 100)

/**
 * Calculates the request pagination.
 *
 * TODO: URL-safe base64 encoding
 */
case class RequestPagination(limit: Int, start: Option[String], isDefault: Boolean) {
  def startAsInt: Option[Int] = {
    try {
      start.map(_.toInt)
    } catch {
      case _: NumberFormatException =>
        Errors.BadRequest(msg = s"Paging start should be an integer, was $start")
    }
  }

  /**
   * A hashcode-like function that includes in its values only the things we actually care about
   * when computing an ETag.
   *
   * @return a stable integer used in an ETag computation.
   */
  def eTagHashCode(): Int = Set(limit, start.hashCode()).hashCode()
}

object RequestPagination {
  /**
   * Parse pagination from the request header, falling back to the configuration as requested.
   */
  def apply(rh: RequestHeader, configuration: PaginationConfiguration): RequestPagination = {
    // Parse out the start parameter.
    val start = rh.queryString.get("start").flatMap(_.headOption)
    // Parse out the limit string.
    val limit = rh.queryString.get("limit").flatMap(_.headOption).flatMap(str => Try(str.toInt).toOption)
    limit.map { limit =>
      RequestPagination(limit, start, isDefault = false)
    }.getOrElse(RequestPagination(configuration.defaultLimit, start, isDefault = true))
  }
}

/**
 * Information about one particular choice of value for one particular facet.
 *
 * @param id The id or code for this facet value.
 * @param name The display name for this facet value.
 * @param count The number of results for this facet.
 */
case class FacetFieldValue(id: String, name: Option[String], count: Long)
object FacetFieldValue {
  implicit val format: OFormat[FacetFieldValue] = Json.format[FacetFieldValue]
}

/**
 * Contains all of the information relating to a facet in a sophisticated search.
 *
 * @param facetEntries An ordered (potentially partial) list of facet entries.
 * @param fieldCardinality Not all potential values for this facet may be included.
 *                         The fieldCardinality is the count of all possible different values for
 *                         this facet.
 */
case class FacetField(facetEntries: immutable.Seq[FacetFieldValue], fieldCardinality: Option[Long])
object FacetField {
  implicit val format: OFormat[FacetField] = Json.format[FacetField]
}

/**
 * If there are subsequent pages, include a next 'pointer'. This is opaque to clients.
 *
 * TODO: consider base64 encoding over the wire for safety.
 *
 * @param facets If present, a map of field name to facet information.
 */
case class ResponsePagination(
    next: Option[String],
    total: Option[Long] = None,
    facets: Option[Map[String, FacetField]] = None)

object ResponsePagination {
  implicit val format = Json.format[ResponsePagination]
  val empty = ResponsePagination(None, None, None)
}

/**
 * Passed to Resources so if there are certain related objects that require more work to fetch,
 * they can skip that work if the request does not ask for those related resources.
 *
 * Note: the API is intentionally severely constrained to allow for future flexibility and internal
 * changes to the Naptime framework.
 */
trait RequestIncludes {
  /**
   * Whether related resource that `fieldName` references should be included in the response.
   *
   * @param fieldName The field name of the current resource
   * @return Whether the request asks for that fieldName to be followed and included in the response
   */
  def includeFieldsRelatedResource(fieldName: String): Boolean
  def forResource(resource: ResourceName): Option[RequestIncludes]
}
/**
 * The type safe structure of the requested includes for a request.
 *
 * Represents the requested related models that are requested (for field projections a.k.a. the
 * `fields` parameter) as well as the requested related resources (e.g. the `include` parameter.)
 *
 * @param fields The fields referenced for inclusion operations for the current resource.
 * @param resources A map containing the fields to include for other resources.
 */
private[naptime] case class QueryIncludes(
    fields: Set[String],
    resources: Map[ResourceName, Set[String]])
  extends RequestIncludes {


  override def includeFieldsRelatedResource(fieldName: String): Boolean = fields.contains(fieldName)

  override def forResource(resource: ResourceName): Option[QueryIncludes] = {
    resources.get(resource).map { relatedResourceFields =>
      QueryIncludes(relatedResourceFields, Map.empty)
    }
  }
}

object QueryIncludes {

  def apply(queryParam: String): Try[QueryIncludes] = {
    if (queryParam.isEmpty) {
      Success(empty)
    } else {
      val parseResult = QueryStringParser.parseQueryIncludes(queryParam)
      parseResult match {
        case QueryStringParser.Success(res, _) => Success(res)
        case QueryStringParser.NoSuccess(msg, _) =>
          Failure(new NaptimeParseError("includes", msg))
      }
    }
  }

  val empty = QueryIncludes(Set.empty, Map.empty)
}

/**
 * Contains all the required information related to a resource's fields.
 *
 * @param defaultFields The default fields that should be included in a response.
 * @param fieldsPermissionsFunction A function that, given a request header, determines if particular
 *                            fields are to be included in the response. This function can be used
 *                            to hide particular fields depending on arbitrary information about the
 *                            request. (e.g. Sensitive fields can be made unavailable in certain
 *                            contexts. (e.g. CORS, 3rd party requests to APIs, JSONP, etc.)
 *                            IMPORTANT NOTE: this functionality is not fully implemented yet!
 *                            WORK IN PROGRESS. DO NOT USE YET!
 * @param relations A map of field name to related resource's name and version pair. This
 *                  configuration is used to automatically join related resources as requested. For
 *                  example, if we have an "author" id field that references the "userBasicProfile"
 *                  v1 resource, and a "post" id field that references a "discoursePost" v1 the map
 *                  would look like: `Map("author" -> ("userBasicProfile", 1), "post" ->
 *                  ("discoursePost", 1))`.
 * @param format The JSON serialization formatter for the resource.
 * @tparam T The type of the resource in the collection.
 */
@implicitNotFound("""Implement an implicit Fields using the FieldsBuilder (ex: implicit val fields =
    Fields.withDefaultFields("name", "desc"). If you encounter this error in a `withRelated` clause,
    be sure to import that resource's fields object.""")
sealed case class Fields[T](
    defaultFields: Set[String],
    fieldsPermissionsFunction: FieldsFunction,
    relations: Map[String, ResourceName])
    (implicit format: OFormat[T]) {

  private[this] val relationsInJson = relations.map { case (field, resourceName) =>
    field -> JsString(resourceName.identifier)
  }.toList

  /**
   * Generates the JsObject that goes inside the `links` field in responses for
   * Play-Json engines.
   * @param fieldsToInclude
   * @return
   */
  private[naptime] def makeMetaRelationsMap(fieldsToInclude: Set[String]): JsObject = {
    val filtered = relationsInJson.filter(field => fieldsToInclude.contains(field._1))
    JsObject(filtered)
  }

  private[naptime] def makeLinksRelationsMap(
      links: DataMap,
      name: String,
      includes: RequestIncludes): Unit = {
    val relationMap = new DataMap()
    links.put(name, relationMap)
    relations.foreach { case (field, resourceName) =>
      relationMap.put(field, resourceName.identifier)
    }
  }

  private[naptime] def computeFields(rh: RequestHeader): Try[RequestFields] = {
    // Try the header override first
    rh.headers.get(Fields.FIELDS_HEADER).map { fieldsHeader =>
      fieldsHeader.toLowerCase match {
        case "all" => Success(AllFields)
        case _ => Failure(
          new NaptimeActionException(
            Status.BAD_REQUEST,
            Some("naptime.parse.fieldsHeader"),
            Some(s"Invalid ${Fields.FIELDS_HEADER} option")))
      }
    }.getOrElse {
      // Merge all query parameters together with a comma to take the union of them all.
      rh.queryString.get("fields").map { fieldsSeq =>
        val unparsed = fieldsSeq.mkString(",")
        QueryFields(unparsed).map { parsed =>
          // TODO: FieldFn should be applied here.
          parsed.mergeWithDefaults(defaultFields)
        }
      }.getOrElse {
        // TODO: FieldFn should be applied here?
        Success(QueryFields(defaultFields, Map.empty))
      }
    }
  }

  private[naptime] def computeIncludes(rh: RequestHeader): Try[QueryIncludes] = {
    rh.queryString.get("includes").map { includesSeq =>
      val unparsed = includesSeq.mkString(",")
      QueryIncludes(unparsed) // No default includes to deal with / etc.
    }.getOrElse {
      Success(QueryIncludes.empty)
    }
  }

  private[this] def withFieldsInternal(fieldNames: Set[String]): Fields[T] = {
    val intersection = fieldNames.intersect(defaultFields)
    require(intersection.isEmpty, s"Duplicate field names provided: $intersection")
    copy(defaultFields = defaultFields ++ fieldNames)
  }

  def withDefaultFields(fieldNames: String*): Fields[T] = {
    withFieldsInternal(fieldNames.toSet)
  }

  def withDefaultFields(fieldNames: Iterable[String]): Fields[T] = {
    withFieldsInternal(fieldNames.toSet)
  }

  def withRelated(newRelations: Map[String, ResourceName]): Fields[T] = {
    val intersection = newRelations.keySet.intersect(relations.keySet)
    require(intersection.isEmpty, s"Duplicate relations provided for: $intersection")
    copy(relations = relations ++ newRelations)
  }

  def withRelated(newRelations: (String, ResourceName)*): Fields[T] = {
    withRelated(newRelations.toMap)
  }
}

object Fields {
  def apply[T](implicit format: OFormat[T]): Fields[T] = {
    Fields(Set.empty, FieldsFunction.default, Map.empty)
  }

  val FIELDS_HEADER = "X-Coursera-Naptime-Fields"

  private[naptime] val FAKE_FIELDS: Fields[_] = Fields(Set.empty, FieldsFunction.default, Map.empty)(null)
}

// TODO(saeta): FieldFn should also take advantage of the authentication applied. This will require
// adding additional type information to many models.
sealed trait FieldsFunction extends ((RequestHeader, QueryFields) => QueryFields) {
  // Nothing here for now.
}

object FieldsFunction {
  /**
   * Default fields function that does not modify the query fields by default.
   *
   * @return
   */
  def default = new FieldsFunction {
    override def apply(rh: RequestHeader, qf: QueryFields) = qf
  }
}

/**
 * Passed to Resources so if there are certain fields that require more work to fetch, they can skip
 * that work if the request does not include it.
 *
 * Note: the API is intentionally severely constrained to allow for future flexibility and internal
 * changes to the Naptime framework.
 */
trait RequestFields {
  def hasField(name: String): Boolean
  def forResource(resource: ResourceName): Option[RequestFields]

  private[naptime] def mergeWithDefaults(defaults: Set[String]): RequestFields
}

object RequestFields {
  val empty = QueryFields.empty
}

/**
 * The structured name + version + path of a resource.
 *
 * @param topLevelName The first top level name.
 * @param version The version of the top level resource.
 * @param resourcePath The names of path selectors.
 */
case class ResourceName(
    topLevelName: String,
    version: Int,
    resourcePath: immutable.Seq[String] = List.empty) {
  def identifier: String = {
    val suffix = if (resourcePath.isEmpty) "" else resourcePath.mkString("/", "/", "")
    s"$topLevelName.v$version$suffix"
  }
}

object ResourceName {
  private[this] val PARSE_REGEX = "([^./]+).v(\\d+)(.*)".r

  def parse(unparsed: String): Option[ResourceName] = {
    unparsed match {
      case PARSE_REGEX(topLevelName, version, path) =>
        Try {
          val parsed = if (path.nonEmpty) {
            path.split("/").toList.tail
          } else {
            List.empty
          }
          new ResourceName(topLevelName, version.toInt, parsed)
        }.toOption
      case _ => None
    }
  }
}

/**
 * Used to disable all fields filtering.
 */
private[naptime] object AllFields extends RequestFields {
  override def hasField(name: String) = true

  override def forResource(resource: ResourceName) = Some(this)

  override private[naptime] def mergeWithDefaults(defaults: Set[String]) = this
}

/**
 * The type safe structure of a resources in the request.
 *
 * This data structure is used to represent the fields of models that are requested (for field
 * projections a.k.a. the `fields` parameter) as well as the requested related resources (e.g. the
 * `include` parameter.)
 *
 * @param fields The fields referenced for the current resource.
 * @param resources (Naptime-visible-only) the other referenced resources.
 */
private[naptime] case class QueryFields(
    fields: Set[String],
    resources: Map[ResourceName, Set[String]])
  extends RequestFields {

  override def hasField(name: String): Boolean = fields.contains(name)

  override def forResource(resource: ResourceName): Option[QueryFields] = {
    resources.get(resource).map { s =>
      QueryFields(s, Map.empty)
    }
  }

  override private[naptime] def mergeWithDefaults(defaults: Set[String]) = {
    val (negations, additions) = fields.partition(_.startsWith("-"))
    val excludeFields = negations.map(_.substring(1))
    val computed = additions.union(defaults).diff(excludeFields)
    copy(fields = computed)
  }
}

object QueryFields {

  def apply(queryParam: String): Try[QueryFields] = {
    if (queryParam.isEmpty) {
      Success(empty)
    } else {
      val parseResult = QueryStringParser.parseQueryFields(queryParam)
      parseResult match {
        case QueryStringParser.Success(res, _) => Success(res)
        case QueryStringParser.NoSuccess(msg, _) =>
          Failure(new NaptimeParseError("fields", msg))
      }
    }
  }

  val empty = QueryFields(Set.empty, Map.empty)
}

/**
 * Used to wrap a [[RequestFields]] but substitute out new related resources.
 */
private[naptime] case class DelegateFields(delegate: RequestFields,
    resources: Map[ResourceName, RequestFields])
  extends RequestFields {
  override def hasField(name: String): Boolean = delegate.hasField(name)

  override private[naptime] def mergeWithDefaults(defaults: Set[String]): RequestFields = {
    copy(delegate = delegate.mergeWithDefaults(defaults))
  }

  override def forResource(resource: ResourceName): Option[RequestFields] = {
    resources.get(resource)
  }
}

/**
 * Parses a query string into a format that contains a set of resource model fields.
 *
 * Examples:
 *  - Simple field selection: `fields=name,birthday,languagePreference`
 *  - Simple includes selection: `includes=authorId`
 *  - Complex field selection: `fields=name,profiles.v1(birthday,geoLocation)`
 *    The above query would ask for the `name` field of the current resource to be included in the
 *    response, as well as the `birthday` and `geoLocation` fields of the `profiles.v1` resource.
 *
 * The format is: a comma separated list of field names optionally interspersed with another
 * resource's field selections.
 *
 * Note: it is possible for field names to include the `.` character.
 */
private[naptime] object QueryStringParser extends RegexParsers {
  import scala.language.postfixOps

  class NaptimeParseError(source: String, msg: String) extends NaptimeActionException(
    httpCode = Status.BAD_REQUEST,
    errorCode = Some(s"naptime.parse.$source"),
    message = Some(s"Failed to parse includes query parameter. Error: $msg"))

  private[this] def field: Parser[String] = "-?[A-z0-9\\.]+".r
  private[this] def nonEmptyFields: Parser[Set[String]] = (field ~ (("," ~> field) *)) ^^ { parsed =>
    val elems = parsed._1 :: parsed._2
    elems.toSet
  }
  private[this] def subResourcePath: Parser[Seq[String]] = ("/" ~> "[A-z0-9]+".r).*
  private[this] def resourceVersion: Parser[Int] = (".v".r ~> "\\d+".r) ^^ (_.toInt)
  private[this] def resourceName: Parser[ResourceName] =
    ("[A-z0-9]+".r ~ resourceVersion ~ subResourcePath) ^^ { parsed =>
      ResourceName(parsed._1._1, parsed._1._2, parsed._2.toList)
    }
  private[this] def resource: Parser[(ResourceName, Set[String])] =
    resourceName ~ ("(" ~> nonEmptyFields <~ ")") ^^ { parsed =>
      parsed._1 -> parsed._2.toSet
    }
  private[this] def topLevelResource: Parser[Either[String, (ResourceName, Set[String])]] =
    resource ^^ (Right(_))
  private[this] def topLevelField: Parser[Either[String, (ResourceName, Set[String])]] =
    field ^^ (Left(_))
  private[this] def topLevelElement: Parser[Either[String, (ResourceName, Set[String])]] =
    topLevelResource | topLevelField


  private[this] def fieldsContent: Parser[QueryFields] =
    (topLevelElement ~ (("," ~ " ".? ~> topLevelElement) *)) ^^ { parsed =>
      val elements = parsed._1 :: parsed._2
      elements.foldLeft(QueryFields(Set.empty, Map.empty)) { (a, b) =>
        b match {
          case Left(field) => a.copy(fields = a.fields + field)
          case Right(resource) => a.copy(resources = a.resources + resource)
        }
      }
    }

  private[this] val fieldsAllContent = phrase(fieldsContent) // Marked as val for performance.

  private[this] def includesContent: Parser[QueryIncludes] =
    (topLevelElement ~ (("," ~ " ".? ~> topLevelElement) *)) ^^ { parsed =>
      val elements = parsed._1 :: parsed._2
      elements.foldLeft(QueryIncludes(Set.empty, Map.empty)) { (a, b) =>
        b match {
          case Left(field) => a.copy(fields = a.fields + field)
          case Right(resource) => a.copy(resources = a.resources + resource)
        }
      }
    }

  private[this] val includesAllContent = phrase(includesContent)

  def parseQueryFields(input: String): ParseResult[QueryFields] = parse(fieldsAllContent, input)
  def parseQueryIncludes(input: String): ParseResult[QueryIncludes] =
    parse(includesAllContent, input)
}
