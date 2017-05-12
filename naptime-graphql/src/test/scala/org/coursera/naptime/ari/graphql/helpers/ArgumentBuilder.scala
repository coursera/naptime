package org.coursera.naptime.ari.graphql.helpers

import org.coursera.naptime.PaginationConfiguration
import sangria.schema.Args
import sangria.schema.Argument
import sangria.schema.IntType
import sangria.schema.OptionInputType
import sangria.schema.StringType

import scala.collection.concurrent.TrieMap

object ArgumentBuilder {
  def buildArgs(argumentDefinitions: List[Argument[_]], argumentInputs: Map[String, Any]): Args = {
    val argsWithDefault = argumentDefinitions.filter(_.defaultValue.isDefined).map(_.name).toSet
    val optionalArgs = argumentDefinitions.filter(_.inputValueType.isOptional).map(_.name).toSet
    val undefinedArgs = argumentDefinitions.map(_.name)
      .filterNot(argsWithDefault.contains)
      .filterNot(optionalArgs.contains)
      .filterNot(argumentInputs.contains)
      .toSet
    val defaultInfo = argumentDefinitions
      .filter(x => argsWithDefault.contains(x.name)).map(x => x.name -> x.defaultValue.get._1)
    val builder = TrieMap.newBuilder[String, Any]
    defaultInfo.foreach(builder += _)

    Args(argumentInputs, argsWithDefault, optionalArgs, undefinedArgs, builder.result())
  }

  private[this] val limitArgument = Argument(
    name = "limit",
    argumentType = OptionInputType(IntType),
    defaultValue = PaginationConfiguration().defaultLimit,
    description = "Maximum number of results to include in response")

  private[this] val startArgument = Argument(
    name = "start",
    argumentType = OptionInputType(StringType),
    description = "Cursor to start pagination at")

  def getPaginationArgs(): List[Argument[_]] = {
    List(limitArgument, startArgument)
  }
}
