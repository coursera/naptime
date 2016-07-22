package org.coursera.naptime.ari.graphql

import org.coursera.naptime.ari.Request
import play.api.mvc.RequestHeader

trait GraphQlParser {

  def parse(request: String, requestHeader: RequestHeader): Option[Request]

}
