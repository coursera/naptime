package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.DataMap
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.ari.graphql.SangriaGraphQlSchemaBuilder
import org.coursera.naptime.ari.graphql.marshaller.NaptimeMarshaller._
import org.coursera.naptime.ari.graphql.models.FakeModel
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.resolvers.NaptimeResolver
import play.api.libs.json.JsObject
import sangria.execution.Executor
import sangria.parser.QueryParser
import sangria.schema.Schema

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ExecutorHelper {

  def executeQuery(
      queryString: String,
      resourceData: Map[String, Map[String, List[DataMap]]]): JsObject = {
    val schemaTypes = Map(
      "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.FakeModel" -> FakeModel.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA)
    val allResources =
      Set(
        Models.courseResource,
        Models.instructorResource,
        Models.partnersResource,
        Models.fakeModelResource)
    val builder = new SangriaGraphQlSchemaBuilder(allResources, schemaTypes)
    val schema = builder.generateSchema().data.asInstanceOf[Schema[SangriaGraphQlContext, Any]]

    val queryAst = QueryParser.parse(queryString).get

    val context = SangriaGraphQlContext(
      TestingFetcherApi(resourceData),
      null,
      ExecutionContext.global,
      debugMode = true)

    Await
      .result(
        Executor
          .execute(
            schema,
            queryAst,
            context,
            variables = JsObject(Map.empty[String, JsObject]),
            deferredResolver = new NaptimeResolver()),
        Duration.Inf)
      .asInstanceOf[JsObject]
  }

}
