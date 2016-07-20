package org.coursera.naptime.ari.graphql

import javax.inject.Inject

import org.coursera.naptime.ari.EngineApi
import play.api.mvc.Action

/**
 * An (stub) implementation of a GraphQL Play endpoint based on the Naptime automatic resource inclusion engine.
 * @param engine The automatic resource inclusion engine that fetches data.
 */
class GraphQL @Inject() (engine: EngineApi) {

  def handleRequest = Action.async { request =>
    ???
  }
}
