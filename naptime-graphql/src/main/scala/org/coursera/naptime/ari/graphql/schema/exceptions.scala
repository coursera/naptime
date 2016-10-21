package org.coursera.naptime.ari.graphql.schema

case class SchemaGenerationException(msg: String) extends Exception(msg)
case class SchemaExecutionException(msg: String) extends Exception(msg)

case class ResponseFormatException(msg: String) extends Exception(msg)
