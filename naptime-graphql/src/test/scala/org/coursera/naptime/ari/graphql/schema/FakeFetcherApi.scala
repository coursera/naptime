package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import play.api.libs.json.JsString

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * An implementation of FetcherApi that allows one to store data keyed by resource name and ID.
 */
case class FakeFetcherApi(rawData: FakeFetcherApi.TestingFetcherApiData) extends FetcherApi {

  def data(request: Request, isDebugMode: Boolean)(
      implicit executionContext: ExecutionContext): Future[FetcherResponse] = {
    val key = request.arguments.find(_._1 == "id").head._2.as[JsString].value
    val result = rawData(request.resource.topLevelName)(key)
    Future.successful(Right(Response(result, ResponsePagination(None), None)))
  }

}

object FakeFetcherApi {

  type TestingFetcherApiData = Map[String, Map[String, List[DataMap]]]

}
