package org.coursera.naptime.ari.engine

import com.linkedin.data.schema.DataSchema
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.schema.Resource

import scala.concurrent.Future

class EngineImpl(
    override val schemas: Seq[Resource],
    override val models: Map[String, DataSchema],
    fetcher: FetcherApi) extends EngineApi {

  override def execute(request: Request): Future[Response] = {
    fetcher.data(request)
  }
}
