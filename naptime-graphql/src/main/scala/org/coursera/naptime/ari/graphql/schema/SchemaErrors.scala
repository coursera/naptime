package org.coursera.naptime.ari.graphql.schema

import org.coursera.naptime.ResourceName

sealed trait SchemaError {
  def resourceName: ResourceName
  def key: String
  def message: String
}

case class HasGetButMissingMultiGet(resourceName: ResourceName) extends SchemaError {
  val key = "HAS_GET_BUT_MISSING_MULTIGET"
  val message = "Resource has GET handler, but no MULTI_GET is available."
}

case class NoHandlersAvailable(resourceName: ResourceName) extends SchemaError {
  val key = "NO_HANDLERS_AVAILABLE"
  val message = "No handlers were detected on this resource."
}

case class MissingMergedType(resourceName: ResourceName) extends SchemaError {
  val key = "MISSING_MERGED_TYPE"
  val message = "No mergedType was available from the schemas.v1 endpoint."
}

case class HasForwardRelationButMissingMultiGet(resourceName: ResourceName, fieldName: String)
    extends SchemaError {

  val key = "HAS_FORWARD_RELATION_BUT_MISSING_MULTIGET"
  val message =
    s"There is a forward relation on $fieldName, but no MULTI_GET is available."
}

case class UnknownHandlerType(resourceName: ResourceName, handlerType: String) extends SchemaError {
  val key = "UNKNOWN_HANDLER_TYPE"
  val message = s"A handler type of $handlerType was not expected."
}

case class SchemaNotFound(resourceName: ResourceName) extends SchemaError {
  val key = "SCHEMA_NOT_FOUND"
  val message = "Could not find schema to build resource field."
}

case class MissingQParameterOnFinderRelation(resourceName: ResourceName, fieldName: String)
    extends SchemaError {
  val key = "MISSING_Q_PARAMETER"
  val message =
    s"Cannot have a finder relation on field $fieldName without having a `q` parameter"
}

case class UnhandledSchemaError(resourceName: ResourceName, error: String) extends SchemaError {
  val key = "UNHANDLED_SCHEMA_ERROR"
  val message = s"Unhandled error: $error"
}

case class SchemaErrors(errors: List[SchemaError]) {
  def ++(that: List[SchemaError]): SchemaErrors = {
    copy(errors = errors ++ that)
  }

  def ++(that: SchemaErrors): SchemaErrors = {
    copy(errors = errors ++ that.errors)
  }

  def +(error: SchemaError): SchemaErrors = {
    copy(errors = errors :+ error)
  }
}

object SchemaErrors {
  val empty = SchemaErrors(List.empty)
}

case class WithSchemaErrors[T](data: T, errors: SchemaErrors = SchemaErrors.empty)
