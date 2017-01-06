package org.coursera.naptime.ari.graphql.schema

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.PaginationConfiguration
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Argument
import sangria.schema.Context
import sangria.schema.Field
import sangria.schema.IntType
import sangria.schema.LongType
import sangria.schema.ObjectType
import sangria.schema.OptionInputType
import sangria.schema.OptionType
import sangria.schema.StringType
import sangria.schema.Value

import scala.collection.JavaConverters._

object NaptimePaginationField extends StrictLogging {

  def getField(
      resourceName: String,
      fieldName: String): ObjectType[SangriaGraphQlContext, ParentContext] = {
    val nextResolver = getResolver(resourceName, fieldName)
      .andThen(c => Value[SangriaGraphQlContext, Any](c.value.next))
    val totalResolver = getResolver(resourceName, fieldName)
      .andThen(c => Value[SangriaGraphQlContext, Any](c.value.total))

    ObjectType[SangriaGraphQlContext, ParentContext](
      name = "ResponsePagination",
      fields = List(
        Field.apply[SangriaGraphQlContext, ParentContext, Any, Any](
          name = "next",
          fieldType = OptionType(StringType),
          resolve = nextResolver),
        Field.apply[SangriaGraphQlContext, ParentContext, Any, Any](
          name = "total",
          fieldType = OptionType(LongType),
          resolve = totalResolver)))
  }

  private[schema] val limitArgument = Argument(
    name = "limit",
    argumentType = OptionInputType(IntType),
    defaultValue = PaginationConfiguration().defaultLimit,
    description = "Maximum number of results to include in response")

  private[schema] val startArgument = Argument(
    name = "start",
    argumentType = OptionInputType(StringType),
    description = "Cursor to start pagination at")

  val paginationArguments = List(limitArgument, startArgument)


  private[schema] def getResolver(
      resourceName: String,
      fieldName: String): Context[SangriaGraphQlContext, ParentContext] => Value[SangriaGraphQlContext, ResponsePagination] = {
    (context: Context[SangriaGraphQlContext, ParentContext]) => {
      val parsedResourceName = ResourceName.parse(resourceName).getOrElse {
        throw new SchemaExecutionException(s"Cannot parse resource name from $resourceName")
      }
      val responsePagination = context.ctx.response.data.get(parsedResourceName).map { objects =>
        Option(context.value.parentContext.value).map { parentElement =>
          // Nested Request
          val idsFromParent = Option(parentElement.getDataList(fieldName))
            .map(_.asScala)
            .getOrElse(List.empty)
          val startOption = context.value.parentContext.arg(startArgument)
          val limit = context.value.parentContext.arg(limitArgument)

          val idsAfterStart = startOption
            .map(start => idsFromParent.dropWhile(_.toString != start))
            .getOrElse(idsFromParent)
          val next = idsAfterStart.drop(limit).headOption.map(_.toString)
          val total = idsFromParent.size
          ResponsePagination(next, Some(total.toLong))

        }.getOrElse {
          // Top-Level Request
          context.ctx.response.topLevelResponses.find { case (topLevelRequest, _) =>
            topLevelRequest.resource.identifier == resourceName &&
              topLevelRequest.selection.alias == context.astFields.headOption.flatMap(_.alias)
          }.map(_._2.pagination).getOrElse(ResponsePagination.empty)
        }
      }.getOrElse(ResponsePagination.empty)

      Value[SangriaGraphQlContext, ResponsePagination](responsePagination)
    }
  }

}
