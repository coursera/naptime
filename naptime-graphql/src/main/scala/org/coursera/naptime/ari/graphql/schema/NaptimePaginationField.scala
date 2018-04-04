package org.coursera.naptime.ari.graphql.schema

import com.typesafe.scalalogging.StrictLogging
import org.coursera.naptime.PaginationConfiguration
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ResponsePagination
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import sangria.schema.Argument
import sangria.schema.Field
import sangria.schema.IntType
import sangria.schema.LongType
import sangria.schema.ObjectType
import sangria.schema.OptionInputType
import sangria.schema.OptionType
import sangria.schema.StringType

object NaptimePaginationField extends StrictLogging {

  def getField(
      resourceName: ResourceName,
      fieldName: String): ObjectType[SangriaGraphQlContext, ResponsePagination] = {

    ObjectType[SangriaGraphQlContext, ResponsePagination](
      name = "ResponsePagination",
      fields = List(
        Field.apply[SangriaGraphQlContext, ResponsePagination, Any, Any](
          name = "next",
          fieldType = OptionType(StringType),
          resolve = _.value.next),
        Field.apply[SangriaGraphQlContext, ResponsePagination, Any, Any](
          name = "total",
          fieldType = OptionType(LongType),
          resolve = context => {
            context.value match {
              case responsePagination: ResponsePagination =>
                responsePagination.total
              case null =>
                logger.error("Expected ResponsePagination but got null")
                None
            }
          }
        )
      )
    )
  }

  private[graphql] val limitArgument = Argument(
    name = "limit",
    argumentType = OptionInputType(IntType),
    defaultValue = PaginationConfiguration().defaultLimit,
    description = "Maximum number of results to include in response"
  )

  private[graphql] val startArgument = Argument(
    name = "start",
    argumentType = OptionInputType(StringType),
    description = "Cursor to start pagination at")

  val paginationArguments = List(limitArgument, startArgument)

}
