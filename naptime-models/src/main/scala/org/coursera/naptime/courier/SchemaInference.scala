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
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DataSchemaUtil
import com.linkedin.data.schema.JsonBuilder
import com.linkedin.data.schema.Name
import com.linkedin.data.schema.SchemaToJsonEncoder
import com.linkedin.data.schema.TyperefDataSchema
import com.linkedin.data.template.DataTemplate
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.runtime.{universe => ru}

/**
 * Infers or extracts Pegasus schemas for Scala types.
 */
object SchemaInference {

  /**
   * Extracts schemas from Courier generated bindings.
   * Infers schemas from Play! JSON style classes that use `Format` and `OFormat`.
   *
   * @tparam T provides type to infer a schema for
   * @return a JSON Schema for the type as a JsObject
   */
  def inferSchema[T: ru.TypeTag]: JsObject = inferSchema(ru.typeOf[T])

  def inferSchemaFromWeakTypeTag[T: ru.WeakTypeTag]: JsObject = inferSchema(ru.weakTypeOf[T])


  /**
   * Extracts schemas from Courier generated bindings.
   * Infers schemas from Play! JSON style classes that use `Format` and `OFormat`.
   *
   * @param typ provides the reflection type to be converted into JSON Schema
   * @return a JSON Schema for the type as a JsObject
   */
  def inferSchema(typ: ru.Type): JsObject = {
    val traverser = new ScalaClassTraverser(typ)
    traverser.extractPegasusSchemaIfPresent().flatMap(_.asOpt[JsObject]).getOrElse {
      traverser.inferSchema().schema match {
        case obj: JsObject => obj
        case value: JsValue => Json.obj("type" -> value)
      }
    }
  }
}

private[courier] case class InferredSchema(
    schema: JsValue,
    isOptional: Boolean = false,
    name: Option[String] = None)

private[courier] class ScalaClassTraverser(rootType: ru.Type) {
  private[this] val visited = mutable.Set[String]()

  private[this] val runtimeMirror = ru.runtimeMirror(getClass.getClassLoader)
  private[this] type CourierCompanionObject = { def SCHEMA: DataSchema }

  def inferSchema(): InferredSchema = {
    inferSchema(rootType)
  }

  def extractPegasusSchemaIfPresent(): Option[JsValue] = {
    extractPegasusSchemaIfPresent(rootType)
  }

  // If the type is a Courier generated data binding, extract the pegasus schema from the class.
  def extractPegasusSchemaIfPresent(typ: ru.Type): Option[JsValue] = {
    if (typ <:< runtimeMirror.typeOf[DataTemplate[_]]) {
      val maybeSchema = runtimeMirror.runtimeClass(typ) match {
        case clazz: Class[DataTemplate[Object] @unchecked] => Some(CourierSerializer.getSchema(clazz))
      }
      maybeSchema.map(schemaToJson)
    } else if (typ <:< ru.typeOf[scala.Enumeration#Value]) {
      inferEnumObjectFromValue(typ) collect {
        case EnumerationInfo(enum, enumSymbol)
          // Is the enum a Courier generated enum?
          if enumSymbol.isType &&
             enumSymbol.asType.toType <:< runtimeMirror.typeOf[CourierCompanionObject] =>
            import scala.language.reflectiveCalls
            schemaToJson(enum.asInstanceOf[CourierCompanionObject].SCHEMA)
      }
    } else {
      None
    }
  }

  private[this] def schemaToJson(schema: DataSchema): JsValue = {
    val schemaJson = SchemaToJsonEncoder.schemaToJson(schema, JsonBuilder.Pretty.COMPACT)
    Json.parse(schemaJson)
  }

  private[this] def inferSchema(typ: ru.Type): InferredSchema = {
    val typeName = typ.typeSymbol.fullName
    val maybePegasusSchema = extractPegasusSchemaIfPresent(typ)
    if (maybePegasusSchema.isDefined) {
      InferredSchema(maybePegasusSchema.get)
    } else if (isPredef(typ)) {
      inferPredef(typ)
    } else if (typ <:< ru.typeOf[scala.Enumeration#Value]) {
      inferEnum(typ)
    } else if (typ <:< ru.typeOf[Option[_]]) {
      if (typ <:< ru.typeOf[None.type]) {
        InferredSchema(JsString("null"))
      } else {
        val optionalType = typ.asInstanceOf[ru.TypeRefApi].args.head
        val inferred = inferSchema(optionalType)
        InferredSchema(inferred.schema, isOptional = !(typ <:< ru.typeOf[Some[_]]))
      }
    } else if (typ <:< ru.typeOf[Map[_, _]]) {
      inferMap(typ)
    } else if (typ <:< ru.typeOf[Traversable[_]] || typ <:< ru.typeOf[Array[_]]) {
      inferArray(typ)
    } else if (isUnion(typ)) {
      inferUnion(typ)
    } else if (typ <:< ru.typeOf[Product]) {
      inferRecordSchema(typ)
    } else {
      // We don't know how to infer a schema for this type
      visitNamedSchema(typeName) {
        val message = s"Unable to infer schema. See the $typeName Scala type for data model structure."
        InferredSchema(Json.obj(
          "name" -> typeName,
          "type" -> "record",
          "fields" -> Json.arr(),
          "deprecated" -> message,
          "doc" -> message))
      }
    }
  }

  // Scala types that have "predefined" mappings to Pegasus types.
  private[this] val schemaTypeForScala = Map(
    "play.api.libs.json.JsValue" -> InferredSchema(
      Json.obj(
        "name" -> "AnyData",
        "namespace" -> "org.coursera.common",
        "type" -> "record",
        "fields" -> Json.arr(),
        "deprecated" -> ("Provided for compatibility with existing data models only. Please " +
          "define schemas for all data. If enveloping is required, please ask infrastructure " +
          "team about 'AnyRecord'."),
        "doc" -> "Marker type for arbitrary JSON data."),
      name = Some("org.coursera.common.AnyData")),
    "org.coursera.common.EmptyModel" -> InferredSchema(
      Json.obj(
        "name" -> "EmptyRecord",
        "namespace" -> "org.coursera.common",
        "type" -> "record",
        "fields" -> Json.arr()), name = Some("org.coursera.common.EmptyRecord")),
    "java.util.UUID" -> InferredSchema(
      coercedType(
        "org.coursera.common.UUID",
        "java.util.UUID",
        "org.coursera.coercers.common.UUIDCoercer",
        DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.STRING)),
      name = Some("org.coursera.common.UUID")),
    "org.joda.time.DateTime" -> InferredSchema(
      coercedType(
        "org.coursera.common.DateTime",
        "org.joda.time.DateTime",
        "org.coursera.coercers.common.DateTime",
        DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.LONG)),
      name = Some("org.coursera.common.DateTime")),
    "java.lang.String" -> InferredSchema(JsString("string")),
    "scala.Boolean" -> InferredSchema(JsString("boolean")),
    "scala.Short" -> InferredSchema(JsString("int")),
    "scala.Int" -> InferredSchema(JsString("int")),
    "scala.Long" -> InferredSchema(JsString("long")),
    "scala.Float" -> InferredSchema(JsString("float")),
    "scala.Double" -> InferredSchema(JsString("double")))

  private[this] def isPredef(typ: ru.Type): Boolean = {
    val typeName = typ.typeSymbol.fullName
    (typ <:< ru.typeOf[JsValue]) || schemaTypeForScala.contains(typeName)
  }

  private[this] def inferPredef(typ: ru.Type): InferredSchema = {
    val typeName = typ.typeSymbol.fullName

    val predef = if (typ <:< ru.typeOf[JsValue]) {
      schemaTypeForScala("play.api.libs.json.JsValue")
    } else {
      schemaTypeForScala(typeName)
    }

    predef.name match {
      case Some(fullName) =>
        visitNamedSchema(fullName) {
          predef
        }
      case None => predef
    }
  }

  //
  private[this] def visitNamedSchema(fullName: String)(body: => InferredSchema): InferredSchema = {
    if (visited.contains(fullName)) {
      InferredSchema(JsString(fullName))
    } else {
      visited += fullName
      body
    }
  }

  private[this] def coercedType(
      pegasusName: String,
      scalaClassName: String,
      coercerClass: String,
      pegasusRefType: DataSchema): JsValue = {
    val schema = new TyperefDataSchema(new Name(pegasusName))
    schema.setReferencedType(pegasusRefType)
    schema.setProperties(Map[String, AnyRef](
      "scala" -> new DataMap(Map(
        "class" -> scalaClassName,
        "coercerClass" -> coercerClass
      ).asJava)
    ).asJava)
    schemaToJson(schema)
  }

  private[this] def inferRecordSchema(typ: ru.Type): InferredSchema = {
    // Check if this schema is recursive
    val name = typ.typeSymbol.name.decodedName.toString
    val namespace = packageName(typ.typeSymbol)
    val fullName = namespace.map(_ + ".").getOrElse("") + name
    visitNamedSchema(fullName) {
      val fieldList = typ.members.toList
          .filter(_.isTerm)
          // Order used here impacts where a type is first declared and where it is referenced
          // by-name.  Since we lack a better approach, we alpha sort the keys so that we at
          // least have a consistent order.
          .sortBy(_.asTerm.name.decodedName.toString.trim)
          .flatMap { member =>
        fieldToJson(member)
      }

      // Return the value and add it to the cache (since we're using getOrElseUpdate
      val namespaceField = namespace.map(ns =>Json.obj("namespace" -> ns)).getOrElse(Json.obj())

      InferredSchema(Json.obj(
        "name" -> name,
        "type" -> "record",
        "fields" -> fieldList)
        ++ namespaceField, name = Some(typ.typeSymbol.fullName))
    }
  }

  private[this] def fieldToJson(member: ru.Symbol): Option[JsObject] = {
    if (member.isTerm) {
      val term = member.asTerm
      try {
        // TODO(jbetz): How do we check isPublic correctly here? term.isPublic returns false for
        // public case class val fields and isPrivate returns true.
        // TODO(jbetz): Or, should I just tighten this down to `isCaseAccessor`?
        if (term.isVal || term.isVal) {
          val fieldSchema = inferSchema(term.typeSignature)
          val optionalField =
            if (fieldSchema.isOptional) Json.obj("optional" -> true) else Json.obj()

          if (term.isParamWithDefault) {
            // TODO(jbetz): Is it possible to extract the default value?
          }

          fieldSchema.schema match {
            // Ignore OFormat fields.
            case JsString("play.api.libs.json.OFormat") => None
            case _: Any =>
              Some(Json.obj(
                "name" -> term.name.decodedName.toString.trim,
                "type" -> fieldSchema.schema)
                ++ optionalField)
          }
        } else {
          None
        }
      } catch {
        case assertionError: AssertionError =>
          // Guard against "unsafe symbol <symbolname> in runtime reflection universe" errors.
          None
      }
    } else {
      None
    }
  }

  private[this] case class EnumerationInfo(enum: Enumeration, symbol: ru.Symbol)
  /**
   * Attempts to infer the Enumeration from a Enumeration.Value type using the following heuristics:
   *
   * Heuristic 1: The Enumeration.Value is defined with the same symbol as the Enumeration object.
   * E.g.:
   *
   *   object Format extends Enumeration {
   *     val BINARY = Value("BINARY")
   *     val TEXT = Value("TEXT")
   *   }
   *   type Format = Format.Value
   *
   * Heuristic 2: The Enumeration.Value is a child of the Enumeration object. E.g.:
   *
   *   object Format extends Enumeration {
   *     val BINARY = Value("BINARY")
   *     val TEXT = Value("TEXT")
   *
   *     type Format = Format.Value
   *   }
   *
   * Use with care. This is not a robust way to attempt to infer schema information from scala
   * types.
   *
   * @param typ An Enumeration.Value type.
   * @return The Enumeration object symbol, if found, otherwise None.
   */
  private[this] def inferEnumObjectFromValue(typ: ru.Type): Option[EnumerationInfo] = {
    // Attempts to lookup a symbol as a "module" (modules are defined using the "object" keyword).
    // If the symbol is a module and it is an Enumeration, return it.
    def reflectEnum(symbol: String): Option[EnumerationInfo] = {
      try {
        val module = runtimeMirror.staticModule(symbol)
        Option(runtimeMirror.reflectModule(module).instance).collect {
          case enum: Enumeration => EnumerationInfo(enum, module)
        }
      } catch {
        case ex: ScalaReflectionException => None
      }
    }

    val sameSymbol = typ.toString
    val parentSymbol = typ.toString.split('.').init.mkString(".")
    Seq(sameSymbol, parentSymbol).flatMap(reflectEnum).headOption
  }

  private[this] def inferEnum(typ: ru.Type): InferredSchema = {
    inferEnumObjectFromValue(typ).map { enumInfo =>
      val symbols = enumInfo.enum.values.map { v =>
        Json.toJson(v.toString)
      }.toList
      val enumSymbol = runtimeMirror.classSymbol(enumInfo.enum.getClass)
      val name = enumSymbol.asType.name.decodedName.toString
      val namespace = packageName(enumSymbol)
      val fullName = namespace.map(_ + ".").getOrElse("") + name
      visitNamedSchema(fullName) {
        val namespaceField = namespace.map(ns => Json.obj("namespace" -> ns)).getOrElse(Json.obj())
        InferredSchema(Json.obj(
          "name" -> name,
          "type" -> "enum",
          "symbols" -> JsArray(symbols))
          ++ namespaceField, name = Some(fullName))
      }
    }.getOrElse(InferredSchema(JsString("UnableToInferEnum"), isOptional = false))
  }

  private[this] def inferArray(typ: ru.Type): InferredSchema = {
    InferredSchema(Json.obj(
      "type" -> "array",
      "items" -> inferSchema(typ.asInstanceOf[ru.TypeRefApi].args.head).schema))
  }

  private[this] def inferMap(typ: ru.Type): InferredSchema = {
    val args = typ.asInstanceOf[ru.TypeRefApi].args
    require(args.length == 2)
    val keysType = inferSchema(args.head).schema
    val keyField = if (keysType == JsString("string")) Json.obj() else Json.obj("keys" -> keysType)
    InferredSchema(Json.obj(
      "type" -> "map",
      "values" -> inferSchema(args(1)).schema)
      ++ keyField)
  }

  private[this] def isUnion(typ: ru.Type): Boolean = {
    val symbol = typ.typeSymbol
    if (symbol.isClass) {
      val clazz = symbol.asClass
      clazz.isSealed && (clazz.isAbstract || clazz.isTrait)
    } else {
      false
    }
  }

  private[this] def inferUnion(typ: ru.Type): InferredSchema = {
    val unionMemberTypes = findAllUnionMemberTypes(typ.typeSymbol)
    val inferredMembers = unionMemberTypes.map { subtype =>
      val inferredSubtype =
        inferSchema(subtype.asType.toType)
      inferredSubtype.schema
    }

    val name = typ.typeSymbol.name.decodedName.toString
    val namespace = packageName(typ.typeSymbol)

    // TODO(jbetz): is it possible to gather the actual typedDefinition/flatTypedDefinition
    // information here? This code assumes all sealed classes are represented as "typedDefinition"
    // (which is not always incorrect) and also assumes that typeNames are just the camelCase form
    // of the type name (also not always correct).
    val typedDefinitionMapping = JsObject(unionMemberTypes.map { subtype =>
      val memberName = subtype.name.decodedName.toString
      val memberNamespace = packageName(typ.typeSymbol)
      val typeName = camelCase(memberName)
      val memberKey = s"${memberNamespace.map(_ + ".").getOrElse("")}$memberName"
      memberKey -> JsString(typeName)
    }.toSeq)

    InferredSchema(Json.obj(
        "name" -> name,
        "namespace" -> namespace,
        "type" -> "typeref",
        "ref" -> inferredMembers,
        "typedDefinition" -> typedDefinitionMapping))
  }

  private[this] def findAllUnionMemberTypes(symbol: ru.Symbol): Set[ru.Symbol] = {
    // the type is sealed, so knownDirectSubclasses contains the exhaustive subclass listing
    val directSubclasses = symbol.asClass.knownDirectSubclasses
    if (directSubclasses.nonEmpty) {
      directSubclasses
    } else {
      directSubclasses ++ directSubclasses.flatMap(findAllUnionMemberTypes)
    }
  }

  private[this] def packageName(sym: ru.Symbol): Option[String] = {
    if (sym == ru.NoSymbol) {
      None
      // Treat modules as part of package name
    } else if (!sym.isPackage && !sym.owner.isModule) {
      packageName(sym.owner)
    } else {
      Some(sym.fullName)
    }
  }

  private[this] def camelCase(name: String): String = {
    if (name.length > 0) {
      name.charAt(0).toLower + name.substring(1)
    } else {
      name
    }
  }
}
