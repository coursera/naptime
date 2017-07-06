package org.coursera.naptime.ari.graphql.resolvers

import com.linkedin.data.DataMap
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.schema.DataMapWithParent
import org.coursera.naptime.ari.graphql.schema.NaptimeResourceUtils
import org.coursera.naptime.ari.graphql.schema.ParentModel
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import sangria.execution.deferred.Deferred
import sangria.execution.deferred.DeferredResolver

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future


case class NaptimeRequest(
    idx: Int,
    resourceName: ResourceName,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema,
    paginationOverride: Option[ResponsePagination] = None)

case class NaptimeResponse(
    elements: List[DataMapWithParent],
    pagination: Option[ResponsePagination])

sealed trait DeferredNaptime {
  def toNaptimeRequest(idx: Int): NaptimeRequest
}

case class DeferredNaptimeRequest(
    resourceName: ResourceName,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema,
    paginationOverride: Option[ResponsePagination] = None)
  extends Deferred[NaptimeResponse] with DeferredNaptime {

  def toNaptimeRequest(idx: Int): NaptimeRequest = {
    NaptimeRequest(idx, resourceName, arguments, resourceSchema, paginationOverride)
  }
}

case class DeferredNaptimeElement(
    resourceName: ResourceName,
    id: JsValue,
    arguments: Set[(String, JsValue)],
    resourceSchema: RecordDataSchema)
  extends Deferred[DataMap] with DeferredNaptime {

  def toNaptimeRequest(idx: Int): NaptimeRequest = {
    NaptimeRequest(idx, resourceName, arguments + ("ids" -> JsArray(List(id))), resourceSchema)
  }
}

class NaptimeResolver extends DeferredResolver[SangriaGraphQlContext] {
  def resolve(
      deferred: Vector[Deferred[Any]],
      ctx: SangriaGraphQlContext,
      queryState: Any)
    (implicit ec: ExecutionContext): Vector[Future[Any]] = {
    println(deferred)

    val naptimeRequests = deferred.zipWithIndex.collect {
      case (d: DeferredNaptime, idx: Int) => d.toNaptimeRequest(idx)
    }

    val dataByResource = naptimeRequests.groupBy(_.resourceName).map { case (resourceName, requests) =>
      val (multigetRequests, nonMultigetRequests) =
        requests.partition(_.arguments.exists(_._1 == "ids"))

      // Handle MultiGet and Non-Multigets differently, since multigets can be batched
      val multigetData = Future.sequence {
        multigetRequests
          .groupBy(_.arguments.filterNot(_._1 == "ids"))
          .map { case (nonIdArguments, innerRequests) =>
            val ids = innerRequests.flatMap(_.arguments.find(_._1 == "ids").map(_._2)).distinct
            val topLevelRequest = TopLevelRequest(
              resourceName,
              RequestField(
                name = "",
                alias = None,
                args = nonIdArguments + ("ids" -> JsArray(ids)),
                selections = List.empty))
            ctx.fetcher.data(Request(ctx.requestHeader, List(topLevelRequest))).map { response =>
              // TODO(bryan): Clean up some of this json stuff
              val data = response.data.getOrElse(resourceName, Map.empty)
                .map { case (key, value) => NaptimeResourceUtils.parseToJson(key) -> value}
              innerRequests.map { innerRequest =>
                val ids = innerRequest.arguments
                  .find(_._1 == "ids")
                  .map(_._2)
                  .map {
                    case JsArray(idValues) => idValues
                    case _ => List.empty
                  }
                  .getOrElse(List.empty)
                println("data")
                println(data)
                println("ids")
                println(ids)
                val elements = ids.flatMap(data.get).toList.map { element =>
                  DataMapWithParent(
                    element,
                    ParentModel(innerRequest.resourceName, element, innerRequest.resourceSchema))
                }
                println("elements")
                println(elements)
                innerRequest.idx ->
                  NaptimeResponse(elements, innerRequest.paginationOverride)
              }.toMap
            }
          }
      }.map(_.flatten.toMap)

      val nonMultigetData = Future.sequence {
        nonMultigetRequests.map { request =>
          val topLevelRequest = TopLevelRequest(
            resourceName,
            RequestField(
              name = "",
              alias = None,
              args = request.arguments,
              selections = List.empty))
          ctx.fetcher.data(Request(ctx.requestHeader, List(topLevelRequest))).map { response =>
            (for {
              (topLevelRequest, topLevelResponse) <- response.topLevelResponses.headOption
              data <- response.data.get(topLevelRequest.resource)
            } yield {
              val elements = topLevelResponse.ids.asScala
                .flatMap(id => data.get(id))
                .toList
                .map { element =>
                  DataMapWithParent(
                    element,
                    ParentModel(request.resourceName, element, request.resourceSchema))
                }
              request.idx -> NaptimeResponse(elements, Some(topLevelResponse.pagination))
            }).toMap
          }
        }
      }.map(_.flatten.toMap)

      Future.sequence(List(multigetData, nonMultigetData)).map(_.flatten.toMap)

    }

    val allData = Future.sequence(dataByResource).map(_.flatten.toMap)

    deferred.zipWithIndex.map {
      case (_: DeferredNaptimeRequest, idx: Int) =>
        allData.map(_.getOrElse(idx, throw new RuntimeException("Could not find data")))
      case (_: DeferredNaptimeElement, idx: Int) =>
        allData.map(_.get(idx).flatMap(_.elements.headOption))
    }
  }
}
