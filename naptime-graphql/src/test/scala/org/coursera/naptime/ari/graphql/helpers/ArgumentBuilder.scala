package org.coursera.naptime.ari.graphql.helpers

import org.coursera.naptime.ari.graphql.schema.NaptimePaginationField
import sangria.schema.Args
import sangria.schema.Argument

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
    val defaultInfo = argumentDefinitions.collectFirst {
      case definition if argsWithDefault.contains(definition.name) =>
        definition.name -> definition.defaultValue.get._1
    }
    val defaultMap = TrieMap() ++= defaultInfo

    Args(argumentInputs, argsWithDefault, optionalArgs, undefinedArgs, defaultMap)
  }

  def getPaginationArgs(): List[Argument[_]] = {
    List(NaptimePaginationField.limitArgument, NaptimePaginationField.startArgument)
  }
}
