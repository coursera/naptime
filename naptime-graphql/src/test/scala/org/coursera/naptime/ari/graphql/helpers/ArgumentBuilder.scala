package org.coursera.naptime.ari.graphql.helpers

import sangria.schema.Args

import scala.collection.concurrent.TrieMap

object ArgumentBuilder {
  def buildArgs(args: Map[String, Any] = Map.empty, limit: Int = 10, start: Option[Int] = None): Args = {
    Args(args, Set("limit"), Set("limit", "start"), Set.empty, TrieMap("limit" -> limit))
  }
}
