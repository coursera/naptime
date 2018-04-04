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

package org.coursera.naptime.router2

import java.io.IOException

import com.linkedin.data.DataMap
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.validation.CoercionMode
import com.linkedin.data.schema.validation.RequiredMode
import com.linkedin.data.schema.validation.ValidateDataAgainstSchema
import com.linkedin.data.schema.validation.ValidationOptions
import com.typesafe.scalalogging.StrictLogging
import org.coursera.courier.codecs.InlineStringCodec
import org.coursera.naptime.courier.StringKeyCodec
import play.api.mvc.RequestHeader

object CourierQueryParsers extends StrictLogging {

  import CollectionResourceRouter.errorRoute

  private[this] val validationOptions =
    new ValidationOptions(RequiredMode.FIXUP_ABSENT_WITH_DEFAULT, CoercionMode.STRING_TO_PRIMITIVE)

  private[this] def parseStringToDataMap(
      paramName: String,
      schema: DataSchema,
      resourceClass: Class[_])(value: String): Either[RouteAction, DataMap] = {
    try {
      val parsed = if (value.startsWith("(") && value.endsWith(")")) {
        InlineStringCodec.instance.bytesToMap(value.getBytes("UTF-8"))
      } else {
        val codec = new StringKeyCodec(schema)
        codec.bytesToMap(value.getBytes("UTF-8"))
      }
      val validated =
        ValidateDataAgainstSchema.validate(parsed, schema, validationOptions)
      if (validated.isValid) {
        Right(validated.getFixed.asInstanceOf[DataMap])
      } else {
        logger.warn(
          s"${resourceClass.getName}: Bad query parameter for parameter " +
            s"'$paramName': $value. Errors: ${validated.getMessages}")
        Left(errorRoute(s"Improperly formatted value for parameter '$paramName'", resourceClass))
      }
    } catch {
      case ioException: IOException =>
        logger.warn(
          s"${resourceClass.getName}: Bad query parameter for parameter " +
            s"'$paramName': $value. Errors: ${ioException.getMessage}")
        Left(errorRoute(s"Improperly formatted value for parameter '$paramName'", resourceClass))
    }
  }

  def strictParse(
      paramName: String,
      schema: DataSchema,
      resourceClass: Class[_],
      rh: RequestHeader): Either[RouteAction, DataMap] = {
    val queryStringResults = rh.queryString.get(paramName)
    if (queryStringResults.isEmpty || queryStringResults.get.isEmpty) {
      Left(errorRoute(s"Missing required parameter '$paramName'", resourceClass))
    } else if (queryStringResults.get.tail.isEmpty) {
      val stringValue = queryStringResults.get.head
      parseStringToDataMap(paramName, schema, resourceClass)(stringValue)
    } else {
      Left(errorRoute(s"Too many query parameters for '$paramName", resourceClass))
    }
  }

  def optParse(
      paramName: String,
      schema: DataSchema,
      resourceClass: Class[_],
      rh: RequestHeader): Either[RouteAction, Option[DataMap]] = {
    val queryStringResults = rh.queryString.get(paramName)
    if (queryStringResults.isEmpty || queryStringResults.get.isEmpty) {
      Right(None)
    } else if (queryStringResults.get.tail.isEmpty) {
      val stringValue = queryStringResults.get.head
      parseStringToDataMap(paramName, schema, resourceClass)(stringValue).right
        .map(Some(_))
    } else {
      Left(errorRoute(s"Too many query parameters for '$paramName", resourceClass))
    }
  }

  // TODO: Add a 'QTry' query parameter type that will attempt to parse the query parameter but
  // instead of failing, will provide the valiation errors to the resource handler to do with what
  // they want.
}
