package org.coursera.naptime.ari.graphql.schema

import sangria.execution.UserFacingError

case class SchemaGenerationException(msg: String) extends Exception(msg)
case class SchemaExecutionException(msg: String) extends Exception(msg)

case class ResponseFormatException(msg: String) extends Exception(msg) with UserFacingError
case class NotFoundException(msg: String) extends Exception(msg) with UserFacingError
case class HttpException(msg: String) extends Exception(msg) with UserFacingError
