package org.coursera.naptime.ari.engine

import javax.inject.Inject

import com.linkedin.data.schema.DataSchema
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.Response
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.schema.Resource

import scala.concurrent.Future

class EngineImpl @Inject() (
    naptimeRoutes: NaptimeRoutes,
    fetcher: FetcherApi) extends EngineApi {

  override val schemas: Seq[Resource] = naptimeRoutes.routerBuilders.map(_.schema).toList

  override val models: Map[String, DataSchema] =
    naptimeRoutes.routerBuilders.foldLeft(Map[String, DataSchema]()) { case (allTypes, routerBuilder) =>
      allTypes ++ routerBuilder.types.flatMap { keyedSchema =>
        keyedSchema.value match {
          case recordSchemaValue: DataSchema => Some(keyedSchema.key -> recordSchemaValue)
          case _ => None
        }
      }
    }

  override def execute(request: Request): Future[Response] = {
    fetcher.data(request)
  }
}
