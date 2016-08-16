package org.coursera.naptime.ari.engine

import javax.inject.Inject

import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsString

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EngineImpl @Inject() (
    naptimeRoutes: NaptimeRoutes,
    fetcher: FetcherApi)
    (implicit executionContext: ExecutionContext) extends EngineApi with StrictLogging {

  private[this] val resourceSchemas: Map[ResourceName, Resource] = naptimeRoutes.schemaMap.flatMap {
    case (_, schema) if schema.parentClass.isEmpty => // TODO: handle sub resources
      val resourceName = ResourceName(schema.name, version = schema.version.getOrElse(0L).toInt)
      Some(resourceName -> schema)
    case (_, schema) =>
      logger.warn(s"Cannot handle nested resource $schema")
      None
  }

  private[this] val mergedTypes = naptimeRoutes.routerBuilders.flatMap(_.types.map(_.tuple))
    .filter(_._2.isInstanceOf[RecordDataSchema])
    .map(tuple => tuple._1 -> tuple._2.asInstanceOf[RecordDataSchema]).toMap

  override def execute(request: Request): Future[Response] = {
    val responseFutures = request.topLevelRequests.map { topLevelRequest =>
      executeTopLevelRequest(request, topLevelRequest)
    }
    val futureResponses = Future.sequence(responseFutures)
    futureResponses.map { responses =>
      responses.foldLeft(Response.empty)(_ ++ _)
    }
  }

  private[this] def executeTopLevelRequest(request: Request, topLevelRequest: TopLevelRequest): Future[Response] = {
    val singleRequest = Request(request.requestHeader, List(topLevelRequest))
    val topLevelResponse = fetcher.data(singleRequest)

    topLevelResponse.flatMap { topLevelResponse =>
      // If we have a schema, we can perform automatic resource inclusion.
      (for {
        topLevelSchema <- resourceSchemas.get(topLevelRequest.resource)
        mergedType <- mergedTypes.get(topLevelSchema.mergedType)
      } yield {
        val topLevelData = topLevelResponse.data.get(topLevelRequest.resource).toIterable.flatMap { dataMap =>
          dataMap.values
        }
        val nestedLookups = topLevelRequest.selection.selections.filter(_.selections.nonEmpty)
        val additionalResponses = nestedLookups.map { nestedField =>
          val field = Option(mergedType.getField(nestedField.name)).getOrElse {
            logger.warn(s"Could not find field $nestedField on model $mergedType")
            throw new IllegalStateException("Could not find field on model")
          }
          val relatedResource = Option(field.getProperties.get(Relations.PROPERTY_NAME)).map {
            case idString: String =>
              ResourceName.parse(idString).getOrElse {
                throw new IllegalStateException(s"Could not parse identifier '$idString' for field '$field' in " +
                  s"merged type $mergedType")
              }
            case identifier =>
              throw new IllegalStateException(s"Unexpected type for identifier '$identifier' for field '$field' " +
                s"in merged type $mergedType")
          }.getOrElse {
            logger.warn(s"Could not find relation property information on field $field")
            throw new IllegalStateException(
              s"Could not find related field information on field '${field.getName}'")
          }

          val multiGetIds = topLevelData.flatMap { elem =>
            if (field.getType.getDereferencedDataSchema.isPrimitive) {
              Option(elem.get(nestedField.name))
            } else if (field.getType.getDereferencedDataSchema.isInstanceOf[ArrayDataSchema]) {
              val field = Option(elem.getDataList(nestedField.name)).map(_.asScala).getOrElse {
                logger.debug(s"Field $nestedField was not found / was not a data list in $elem for query $request")
                List.empty
              }
              field
            } else {
              throw new IllegalStateException(
                s"Cannot join on an unknown field type '${field.getType.getDereferencedType}' " +
                  s"for field '${field.getName}'")
            }
          }.toSet.map[String, Set[String]](_.toString).mkString(",") // TODO: escape appropriately.

          val relatedTopLevelRequest = TopLevelRequest(
            resource = relatedResource,
            selection = RequestField(
              name = "multiGet",
              alias = None,
              args = Set("ids" -> JsString(multiGetIds)), // TODO: pass through original request fields for pagination / etc.
              selections = nestedField.selections))
          val relatedRequest = Request(request.requestHeader, List(relatedTopLevelRequest))
          fetcher.data(relatedRequest).map { response =>
            // Exclude the top level ids in the response.
            Response(topLevelIds = Map.empty, data = response.data)
          }
        }
        Future.sequence(additionalResponses).map { additionalResponses =>
          additionalResponses.foldLeft(topLevelResponse)(_ ++ _)
        }
      }).getOrElse(Future.successful(topLevelResponse))
    }
  }
}
