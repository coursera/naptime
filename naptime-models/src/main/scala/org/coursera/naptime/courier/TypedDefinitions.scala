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

package org.coursera.naptime.courier

import com.linkedin.data.DataMap
import com.linkedin.data.schema.TyperefDataSchema
import org.coursera.courier.companions.UnionCompanion
import org.coursera.courier.companions.UnionMemberCompanion
import org.coursera.courier.companions.UnionWithTyperefCompanion
import org.coursera.courier.templates.ScalaUnionTemplate

/**
 * Convenience methods for working with Typed Definitions.
 */
object TypedDefinitions {

  /**
   * Gets the typed definition (or flat typed definition) "typeName" of a Courier union member
   * instance.
   *
   * The provided type must be a properly structured typed definition (or flat typed definition).
   * Namely:
   *
   * - The union must be declared within a declaration of a typeref
   * - The typeref must have a 'typedDefinition' or 'flatTypedDefinition' schema property
   * - The schema property must contain a complete mapping of member keys (fully qualified Courier
   *   type name) to 'typeName' for all members of the union
   *
   * If any of these conditions are not met, a IllegalArgumentException is thrown.
   *
   * For example, given the schema:
   *
   * {{{
   * namespace org.example
   *
   * @@typedDefinition = {
   *   "org.example.Alpha": "alpha",
   *   "org.example.Beta": "beta"
   * }
   * typeref ExampleUnion = union[
   *   record Alpha {}
   *   record Beta {}
   * ]
   * }}}
   *
   * The Scala expression:
   *
   * {{{
   * val member = ExampleUnion.AlphaMember(Alpha())
   * TypedDefinitions.typeName(member)
   * }}}
   *
   * returns "alpha"
   */
  def typeName(member: ScalaUnionTemplate): String = {
    val schema = member.declaringTyperefSchema.getOrElse {
      throw new IllegalArgumentException(
        "Union must be declared within a typeref and the typeref must have a 'typedDefinition' " +
          s"(or 'flatTypedDefinition') schema property, for union member: ${member.getClass}")
    }
    val memberKey = member.memberType().getUnionMemberKey
    typeName(schema, memberKey)
  }

  /**
   * Gets the typed definition (or flat typed definition) "typeName" of a Courier union member's
   * companion object.
   *
   * Example:
   *
   * ```
   * TypedDefinitions.typeName(ExampleUnion.AlphaMember)
   * ```
   *
   * See `typeName(ScalaUnionTemplate)` for more details.
   *
   */
  def typeName[K <: ScalaUnionTemplate](memberCompanion: UnionMemberCompanion[K]): String = {
    val unionCompanion = memberCompanion.unionCompanion
    val schema = unionCompanion match {
      case withTyperef: UnionWithTyperefCompanion[K] =>
        withTyperef.TYPEREF_SCHEMA
      case withoutTyperef: UnionCompanion[K] =>
        throw new IllegalArgumentException(
          "Union must be declared within a typeref and the typeref must have a 'typedDefinition' " +
            s"(or 'flatTypedDefinition') schema property, for union: $withoutTyperef")
    }
    typeName(schema, memberCompanion.memberKey)
  }

  private[this] def typeName(schema: TyperefDataSchema, memberKey: String): String = {
    val mapping = getMemberKeyToTypeNameMapping(schema)
    Option(mapping.get(memberKey)) match {
      case Some(typeName: String) => typeName
      case unknown: AnyRef =>
        throw new IllegalArgumentException(
          s"Schema must contain a '$memberKey' entry in the 'typedDefinition' (or " +
            s"'flatTypedDefinition') schema property, but one was not found, for schema: $schema")
    }
  }

  private[this] def getMemberKeyToTypeNameMapping(schema: TyperefDataSchema): DataMap = {
    val properties = schema.getProperties
    val mapping = Option(properties.get("typedDefinition")).getOrElse {
      Option(properties.get("flatTypedDefinition")).getOrElse {
        throw new IllegalArgumentException(
          "Union's declaring typeref schema must have a 'typedDefinition' " +
            s"(or 'flatTypedDefinition') schema property, but neither found, for schema $schema")
      }
    }
    mapping match {
      case dataMap: DataMap => dataMap
      case unknown: AnyRef =>
        throw new IllegalArgumentException(
          "Union's declaring typeref schema must have a 'typedDefinition' (or " +
            s"'flatTypedDefinition') schema property of type map, but was: ${unknown.getClass}")
    }
  }
}
