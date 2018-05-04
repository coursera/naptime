/*
 * Copyright 2017 Coursera Inc.
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
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.schema.UnionDataSchema
import org.coursera.courier.templates.ScalaUnionTemplate
import org.coursera.naptime.courier.Exceptions._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue

import scala.collection.JavaConverters._

object CourierUtils {

  def getUnionMemberTypeName[T <: ScalaUnionTemplate](unionMember: T): String = {
    val memberSchema = unionMember.memberType
    val memberKey = memberSchema.getUnionMemberKey
    unionMember.declaringTyperefSchema match {
      case None =>
        memberKey
      case Some(typerefSchema) =>
        getTypedDefinition(typerefSchema) match {
          case None =>
            memberKey
          case Some(TypedDef(nameMap)) =>
            memberKeyToTypeName(typerefSchema, nameMap, memberKey)
          case Some(FlatTypedDef(nameMap)) =>
            memberKeyToTypeName(typerefSchema, nameMap, memberKey)
        }
    }
  }

  /* typedDefinition helpers */

  sealed trait TypedDefKind
  case class TypedDef(nameMap: DataMap) extends TypedDefKind
  case class FlatTypedDef(nameMap: DataMap) extends TypedDefKind

  val typedDefinitionField = "typedDefinition"
  val flatTypedDefinitionField = "flatTypedDefinition"
  val typeNameField = "typeName"
  val definitionField = "definition"

  def getTypedDefinition(typerefSchema: TyperefDataSchema): Option[TypedDefKind] = {
    val properties = typerefSchema.getProperties

    val flatDefinition = properties.get(flatTypedDefinitionField)
    val definition = properties.get(typedDefinitionField)

    (flatDefinition, definition) match {
      case (nameMap: DataMap, null) =>
        Some(FlatTypedDef(nameMap))
      case (null, nameMap: DataMap) =>
        Some(TypedDef(nameMap))
      case (null, null) =>
        None
      case _ =>
        throw new SerializationException(
          "'flatTypedDefinition' or 'typedDefinition' " +
            s"may be declared on ${typerefSchema.getFullName}, not both.")
    }
  }

  def destructureUnionMemberDataMap(
      dataMap: DataMap,
      unionSchema: UnionDataSchema): (String, AnyRef, DataSchema) = {
    if (dataMap.size != 1) {
      throw new WriteException(
        "Union DataMap must contain exactly one memberKey field, " +
          s"but found fields: ${dataMap.keySet}")
    }
    val memberKey = dataMap.keySet.iterator.next
    val memberData = dataMap.get(memberKey)
    val memberSchema = unionSchema.getType(memberKey)
    if (memberSchema != null) {
      (memberKey, memberData, memberSchema)
    } else {
      throw new WriteException(s"Union member '$memberKey' not found for schema $unionSchema")
    }
  }

  def destructureTypedDefinitionJsObject(jsObject: JsObject): (String, JsValue) = {
    val JsObject(entries) = jsObject
    (entries.get(typeNameField), entries.get(definitionField)) match {
      case (Some(JsString(typeName)), Some(memberJson)) =>
        (typeName, memberJson)
      case (None, _) =>
        throw new ReadException(s"No '$typeNameField' found on typedDefinition $entries")
      case (_, None) =>
        throw new ReadException(s"No '$definitionField' found on typedDefinition $entries")
      case (_, _) =>
        throw new ReadException(s"Unmatched on typedDefinition $entries")
    }
  }

  def destructureFlatTypedDefinitionJsObject(jsObject: JsObject): (String, JsObject) = {
    val JsObject(entries) = jsObject
    entries.get(typeNameField) match {
      case Some(JsString(typeName)) =>
        (typeName, JsObject(entries - typeNameField))
      case None =>
        throw new ReadException(s"No '$typeNameField' found on typedDefinition $entries")
      case _ =>
        throw new ReadException(
          s"Type doesn't match for field '$typeNameField' on typedDefinition $entries")
    }
  }

  def memberKeyToTypeName(
      typerefSchema: TyperefDataSchema,
      nameMap: DataMap,
      memberKey: String): String = {
    // When serializing to typedDefinition, we fail fast if the mapping is missing, as a
    // writer should only attempt to write with memberKeys that have a correct mapping.
    var typeName = nameMap.get(memberKey)
    if (typeName == null) {
      val nsPrefix = typerefSchema.getNamespace + "."
      if (memberKey.startsWith(nsPrefix)) {
        val simpleName = memberKey.substring(nsPrefix.length)
        typeName = nameMap.get(simpleName)
      }
    }
    typeName match {
      case str: String =>
        str
      case _ =>
        throw new WriteException(
          "No mapping in 'typedDefinition' or 'flatTypedDefinition' of " +
            s"'${typerefSchema.getFullName}' found for memberKey " +
            s"'$memberKey'. Mapping value must be a 'typeName' string.")
    }
  }

  def typeNameToMemberSchema(
      typerefSchema: TyperefDataSchema,
      unionSchema: UnionDataSchema,
      nameMap: DataMap,
      typeName: String): DataSchema = {
    nameMap.entrySet.asScala.find(entry => entry.getValue == typeName) match {
      case Some(entry) =>
        val memberKey = entry.getKey
        unionSchema.getType(memberKey) match {
          case memberSchema: DataSchema =>
            memberSchema
          case null =>
            if (!memberKey.contains(".")) {
              val qualifiedName = typerefSchema.getNamespace + "." + memberKey
              unionSchema.getType(qualifiedName) match {
                case memberSchema: DataSchema =>
                  memberSchema
                case null =>
                  throw new ReadException(
                    s"Union member '$qualifiedName' not found for type name '$typeName' " +
                      "in schema $unionSchema")
              }
            } else {
              throw new ReadException(
                s"Union member '$memberKey' not found for type name '$typeName' " +
                  "in schema $unionSchema")
            }
        }
      case None =>
        throw new ReadException(
          "No mapping in 'typedDefinition' or 'flatTypedDefinition' of " +
            s"'${typerefSchema.getFullName}' found for typeName '$typeName'. Mapping: $nameMap")
    }
  }

}
