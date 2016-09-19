package org.coursera.naptime.ari.fetcher

import javax.inject.Inject

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.ResourceName
import org.coursera.naptime.actions.RestAction
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.schema.Resource
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * Executes data requests against local Naptime resources (requires the use of Engine2 engines).
 *
 * @param naptimeRoutes The routing data structures required for handling requests.
 */
class LocalFetcher @Inject() (
    naptimeRoutes: NaptimeRoutes)
  extends FetcherApi with StrictLogging {

  private[this] val schemas = naptimeRoutes.routerBuilders.map(_.schema)
  private[this] val models = naptimeRoutes.routerBuilders.flatMap(_.types).map(_.tuple).toMap

  private[this] val routers = naptimeRoutes.buildersToRouters.map { case (builder, router) =>
    naptimeRoutes.className(builder) -> router
  }

  override def data(request: Request): Future[Response] = {
    if (request.topLevelRequests.length != 1) {
      val msg = s"Too many top level requests passed to LocalFetcher: $request"
      logger.error(msg)
      Future.failed(new IllegalArgumentException(msg))
    } else {
      val topLevelRequest = request.topLevelRequests.head
      val resourceSchemaOpt = schemas.find { resourceSchema =>
        // TODO: Handle nested resources.
        resourceSchema.name == topLevelRequest.resource.topLevelName &&
          resourceSchema.version.contains(topLevelRequest.resource.version)
      }
      (for {
        resourceSchema <- resourceSchemaOpt
        router <- routers.get(resourceSchema.className)
        argMap = topLevelRequest.selection.args.toMap
        queryString = argMap.map { arg =>
          val strRepr = arg._2.toString().stripPrefix("\"").stripSuffix("\"")
          arg._1 -> List(strRepr)
        }
        fakePlayRequest = request.requestHeader.copy(
          method = "GET", // TODO: handle non-read-only request types.
          uri = topLevelRequest.resource.identifier, // Warning: uri is not consistent with queryString
          queryString = queryString,
          headers = request.requestHeader.headers.remove("content-type")) // TODO: handle header filtering more properly
        path = constructPath(resourceSchema, argMap)
        handler <- router.routeRequest(path, fakePlayRequest)
      } yield {
        val taggedRequest = handler.tagRequest(fakePlayRequest)
        handler match {
          case naptimeAction: RestAction[_, _, _, _, _, _] =>
            naptimeAction.localRun(fakePlayRequest,
              ResourceName(resourceSchema.name, resourceSchema.version.map(_.toInt).getOrElse(0)),
              topLevelRequest)
          case _ =>
            val msg = "Handler was not a RestAction"
            logger.error(msg)
            Future.failed(new IllegalArgumentException(msg))
        }
      }).getOrElse {
        val msg = s"Unknown resource: ${topLevelRequest.resource}"
        logger.warn(msg)
        Future.failed(new IllegalArgumentException(msg))
      }
    }
  }

  private[this] def constructPath(resourceSchema: Resource, argMap: Map[String, JsValue]): String = {
    val versionString = resourceSchema.version.map(v => s".v$v").getOrElse("")
    val basePath = s"${resourceSchema.name}$versionString"

    if (argMap.contains("id")) {
      val idStr = argMap("id").toString().stripPrefix("\"").stripSuffix("\"")
      s"/$basePath/$idStr"
    } else {
      s"/$basePath"
    }
  }
}
