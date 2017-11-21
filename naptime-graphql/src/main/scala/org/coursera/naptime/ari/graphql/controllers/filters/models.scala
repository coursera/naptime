package org.coursera.naptime.ari.graphql.controllers.filters

import org.coursera.naptime.ari.Response
import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader
import sangria.ast.Document

import scala.collection.immutable

case class IncomingQuery(
    document: Document,
    requestHeader: RequestHeader,
    variables: JsObject,
    operation: Option[String],
    debugMode: Boolean)

case class OutgoingQuery(response: JsObject, ariResponse: Option[Response])

case class FilterList(filters: immutable.Seq[Filter])
