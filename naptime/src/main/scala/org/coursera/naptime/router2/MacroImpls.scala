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

package org.coursera.naptime.router2

import com.linkedin.data.template.DataTemplate
import org.coursera.common.stringkey.StringKey
import org.coursera.common.stringkey.StringKeyFormat
import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.actions._
import org.coursera.naptime.resources.CollectionResource
import org.coursera.naptime.resources.TopLevelCollectionResource
import play.api.mvc.RequestHeader

import scala.reflect.macros.blackbox

private[router2] object MacroImpls {
  private[router2] case class MacroBugException(msg: String) extends RuntimeException(msg)

  val _DEBUG = false // Switch to true for debugging output during macro invocation.
  def debug(msg: String) = {
    if (_DEBUG) {
      println(msg)
    }
  }
}

/**
 * A macro bundle that builds [[ResourceRouterBuilder]]s specialized to particular types.
 */
class MacroImpls(val c: blackbox.Context) {
  import MacroImpls._

  // TODO: remove all `org.coursera.naptime` prefixes in quasiquotes from here.
  import c.universe._

  /**
   * A type that indicates a function either generates a Right(tree, schemaTrees) or a Left(error).
   */
  type OptionalTree = Either[(c.Position, String), (c.Tree, Iterable[c.Tree])]

  val REST_ACTION = weakTypeOf[RestAction[_, _, _, _, _, _]].typeConstructor
  val ACTION_GET = typeOf[GetRestActionCategory.type]
  val ACTION_GET_ALL = typeOf[GetAllRestActionCategory.type]
  val ACTION_GET_MULTI = typeOf[MultiGetRestActionCategory.type]
  val ACTION_CREATE = typeOf[CreateRestActionCategory.type]
  val ACTION_UPDATE = typeOf[UpdateRestActionCategory.type]
  val ACTION_DELETE = typeOf[DeleteRestActionCategory.type]
  val ACTION_PATCH = typeOf[PatchRestActionCategory.type]
  val ACTION_FINDER = typeOf[FinderRestActionCategory.type]
  val ACTION_ACTION = typeOf[ActionRestActionCategory.type]
  val REQUEST_HEADER = typeOf[RequestHeader]
  val ROUTE_ACTION = typeOf[RouteAction]
  val STRING_KEY = typeOf[StringKey]
  val COLLECTION_RESOURCE_TYPE = typeOf[CollectionResource[_, _, _]]
  val TOP_LEVEL_COLLECTION = typeOf[TopLevelCollectionResource[_, _]]
  val STRING_KEY_FORMAT_TYPE_CONSTRUCTOR = weakTypeOf[StringKeyFormat[_]].typeConstructor

  val ANY_VAL = typeOf[AnyVal] // Primitive types.
  val STRING = typeOf[String]
  val DATA_TEMPLATE = typeOf[DataTemplate[_]] // Pegasus types
  val SCALA_RECORD_TEMPLATE = typeOf[ScalaRecordTemplate]

  /**
   * Code-generates a subclass of [[ResourceRouterBuilder]] specialized for the [[Resource]] type.
   *
   * Be sure to look over [[CollectionResourceRouter]] first, as that is crucial to understanding
   * the implementation of this macro. The bulk of the router is actually implemented in normal code
   * within the [[CollectionResourceRouter]] class. This macro simply generates a subclass
   * specialized to provide the glue code to bind to an instance of [[Resource]].
   *
   * @param wtt The weak type tag for the resource we are specializing.
   * @tparam Resource The resource type that we are specializing.
   * @return A [[c.Tree]] corresponding to a [[ResourceRouterBuilder]].
   */
  def build[Resource <: CollectionResource[_, _, _]](
      implicit wtt: WeakTypeTag[Resource]): c.Tree = {
    Nested.buildRouter[Resource]
  }

  object Nested {

    /**
     * Code-generates a subclass of [[ResourceRouterBuilder]] specialized for the [[Resource]] type.
     *
     * Be sure to look over [[NestingCollectionResourceRouter]] first, as that is crucial to understanding
     * the implementation of this macro. The bulk of the router is actually implemented in normal
     * code within the [[NestingCollectionResourceRouter]] class. This macro simply generates a subclass
     * specialized to provide the glue code to bind to an instance of [[Resource]].
     *
     * @param wtt The weak type tag for the resource we are specializing.
     * @tparam Resource The resource type that we are specializing.
     * @return A [[c.Tree]] corresponding to a [[ResourceRouterBuilder]].
     */
    def buildRouter[Resource <: CollectionResource[_, _, _]](
      implicit wtt: WeakTypeTag[Resource]): c.Tree = {
      val resourceType = weakTypeOf[Resource]
      val classMethods = resourceType.members.collect {
        case member: Symbol if member.isMethod => member.asMethod
      }.filter(_.isPublic)
      val naptimeMethods = classMethods.filter(
        _.typeSignature.resultType.typeConstructor == REST_ACTION)
      debug(s"Naptime methods: $naptimeMethods")
      val methodsByRestActionCategory = try {
        naptimeMethods.groupBy { method =>
          method.typeSignature.resultType.typeArgs.headOption.getOrElse {
            c.error(method.pos, "Method did not have type argument in result type?! Macro bug :'-(")
            throw MacroImpls.MacroBugException(s"Method: $method at pos: ${method.pos}")
          }
        }.toList
      } catch {
        case e: MacroImpls.MacroBugException =>
          debug(s"Macro error exception: ${e.toString}")
          List.empty
      }

      // Trees is a tuple of (treeOfRoutingBindingMethods, treesOfHandlerSchemas)
      val trees = methodsByRestActionCategory.map {
        case (tpe, methods) if ACTION_GET =:= tpe =>
          buildGetTree(methods)
        case (tpe, methods) if ACTION_GET_MULTI =:= tpe =>
          buildMultiGetTree(methods)
        case (tpe, methods) if ACTION_GET_ALL =:= tpe =>
          buildGetAllTree(methods)
        case (tpe, methods) if ACTION_UPDATE =:= tpe =>
          buildUpdateTree(methods)
        case (tpe, methods) if ACTION_DELETE =:= tpe =>
          buildDeleteTree(methods)
        case (tpe, methods) if ACTION_CREATE =:= tpe =>
          buildCreateTree(methods)
        case (tpe, methods) if ACTION_PATCH =:= tpe =>
          buildPatchTree(methods)
        case (tpe, methods) if ACTION_FINDER =:= tpe =>
          buildFinderTree(methods, tpe)
        case (tpe, methods) if ACTION_ACTION =:= tpe =>
          buildActionTree(methods, tpe)
      }.flatMap { treeEither =>
        treeEither.fold(
          err => {
            c.error(err._1, err._2)
            None
          },
          Some(_))
      }
      val resourceRouterBuilderType = weakTypeOf[ResourceRouterBuilder]
      debug(s"TREES ARE: $trees")

      val parentResourceName = if (resourceType <:< TOP_LEVEL_COLLECTION) {
        q"None"
      } else {
        val collectionTypeView = resourceType.baseType(COLLECTION_RESOURCE_TYPE.typeSymbol)
        q"Some(${collectionTypeView.typeArgs.head.toString})"
      }

      val finalResource = q"""
      new $resourceRouterBuilderType {
        type ResourceClass = $resourceType
        override lazy val resourceClass = classOf[$resourceType]
        override def build(resourceInstance: ResourceClass) =
          new org.coursera.naptime.router2.NestingCollectionResourceRouter[
            $resourceType](resourceInstance) {
            ..${trees.map(_._1)}
          }
        override lazy val schema = {
          org.coursera.naptime.schema.Resource(
            kind = org.coursera.naptime.schema.ResourceKind.COLLECTION,
            name = Option(stubInstance.resourceName).getOrElse(
              "??? (resourceName should be def not val)"),
            version = Some(stubInstance.resourceVersion),
            keyType = ${keyType(resourceType)},
            valueType = ${valueType(resourceType)},
            mergedType = ${mergedType(resourceType)},
            parentClass = $parentResourceName,
            handlers = List(..${trees.flatMap(_._2)}),
            className = ${resourceType.toString},
            attributes = org.coursera.naptime.router2.AttributesProvider
                .getResourceAttributes(resourceClass.getName))
        }
        override lazy val types = ${computeTypes(resourceType)}
      }
      """
      debug(s"NaptimeRouterBuilder macro code for $resourceType : ${showCode(finalResource)}")
      finalResource
    }

    private[this] def keyType(resourceType: c.Type): c.Tree = {
      val collectionTypeView = resourceType.baseType(COLLECTION_RESOURCE_TYPE.typeSymbol)
      val keyType = collectionTypeView.typeArgs(1)
      if (keyType <:< ANY_VAL || keyType =:= typeOf[String]) {
        q"""
          com.linkedin.data.schema.DataSchemaUtil.classToPrimitiveDataSchema(
            classOf[$keyType]).getUnionMemberKey()
        """
      } else {
        q"${keyType.toString}"
      }
    }

    private[this] def valueType(resourceType: c.Type): c.Tree = {
      val collectionTypeView = resourceType.baseType(COLLECTION_RESOURCE_TYPE.typeSymbol)
      val bodyType = collectionTypeView.typeArgs(2)
      q"${bodyType.toString}"
    }

    private[this] def mergedType(resourceType: c.Type): String = {
      resourceType.toString + ".Model"
    }


    private[this] def getRecordSchemaForType(targetType: c.Type): c.Tree = {
      if (targetType <:< SCALA_RECORD_TEMPLATE) {
        q"""Some(
          org.coursera.courier.templates.DataTemplates
            .getSchema[$targetType]
            .asInstanceOf[com.linkedin.data.schema.RecordDataSchema])"""
      } else {
        q"""
          scala.util.Try {
            import scala.collection.JavaConversions._
            val resolver = new com.linkedin.data.schema.resolver.DefaultDataSchemaResolver()
            val parser = new com.linkedin.data.schema.SchemaParser(resolver)
            val schemaJson = org.coursera.naptime.courier.SchemaInference.inferSchema(
              scala.reflect.runtime.universe.typeTag[$targetType])
            parser.parse(schemaJson.toString)
            parser.topLevelDataSchemas.head.asInstanceOf[com.linkedin.data.schema.RecordDataSchema]
          }.toOption"""
      }
    }

    private[this] def getDataSchemaDataMapForType(targetType: c.Type): c.Tree = {
      val schema = if (targetType <:< SCALA_RECORD_TEMPLATE) {
        q"""Some(
          org.coursera.courier.templates.DataTemplates
            .getSchema[$targetType]
            .asInstanceOf[com.linkedin.data.schema.DataSchema])"""
      } else {
        q"""
          scala.util.Try {
            import scala.collection.JavaConversions._
            val resolver = new com.linkedin.data.schema.resolver.DefaultDataSchemaResolver()
            val parser = new com.linkedin.data.schema.SchemaParser(resolver)
            val schemaJson = org.coursera.naptime.courier.SchemaInference.inferSchemaFromWeakTypeTag(
              scala.reflect.runtime.universe.weakTypeTag[$targetType])
            parser.parse(schemaJson.toString)
            parser.topLevelDataSchemas.head.asInstanceOf[com.linkedin.data.schema.DataSchema]
          }.toOption"""
      }
      q"""
        val schemaOpt = $schema
        schemaOpt.flatMap { schema =>
          val dataCodec = new com.linkedin.data.codec.JacksonDataCodec();
          scala.util.Try(dataCodec.stringToMap(schema.toString)).toOption
        }
      """
    }

    private[this] def computeKeyType(keyType: c.Type): c.Tree = {
      keyType match {
        case _ if keyType =:= typeOf[Int] =>
          q"Some(new com.linkedin.data.schema.IntegerDataSchema)"
        case _ if keyType =:= typeOf[String] =>
          q"Some(new com.linkedin.data.schema.StringDataSchema)"
        case _ if keyType =:= typeOf[Long] =>
          q"Some(new com.linkedin.data.schema.LongDataSchema)"
        case _ =>
          // Search for an apply method, and attempt to do something with that.
          val companion = keyType.companion
          // Note: if the apply method is overloaded, isMethod will be false (will be OverloadedTerm instead)
          val applyMethod = companion.member(TermName("apply"))
          if (!(keyType <:< SCALA_RECORD_TEMPLATE)
              && applyMethod.isMethod
              && applyMethod.asMethod.returnType =:= keyType
              && applyMethod.asMethod.paramLists.size == 1) {

            // If there is a single-argument apply method, infer the id type from the parameter type.
            if (applyMethod.asMethod.paramLists.head.size == 1) {
              val idTerm = applyMethod.asMethod.paramLists.head.head.asTerm
              assert(idTerm.isParameter, s"Id type $idTerm was not a parameter?!?!?")
              val idType = idTerm.infoIn(keyType)
              // TODO: preserve & serialize the "typeref" nature of the schema.
              // Note: doing so may break other parts of the system that assume the only schemas are
              // record data schemas.
              computeKeyType(idType)
            } else {
              val params = applyMethod.asMethod.paramLists.head
              val fields = params.map { param =>
                q"""
                  ${computeKeyType(param.infoIn(keyType))}.map { paramType =>
                    val paramField = new com.linkedin.data.schema.RecordDataSchema.Field(paramType)
                    paramField.setName(${param.name.encodedName.toString}, null)
                    paramField.setRecord(keyRecord)
                    paramField
                  }
                 """
              }
              val recordSchema =
                q"""
                  val keyRecord = new com.linkedin.data.schema.RecordDataSchema(
                    new com.linkedin.data.schema.Name(${keyType.toString}),
                    com.linkedin.data.schema.RecordDataSchema.RecordType.RECORD)
                  val fields = $fields.flatten
                  val fieldsJava = scala.collection.convert.WrapAsJava.seqAsJavaList(fields)
                  keyRecord.setFields(fieldsJava, null)
                  Some(keyRecord)
                 """
              recordSchema
            }
          } else {
            getRecordSchemaForType(keyType)
          }
      }
    }

    private[this] def computeTypes(resourceType: c.Type): c.Tree = {
      val collectionTypeView = resourceType.baseType(COLLECTION_RESOURCE_TYPE.typeSymbol)
      val keyType = collectionTypeView.typeArgs(1)
      val bodyType = collectionTypeView.typeArgs(2)

      // Add additional types here
      val keySchemaOption = computeKeyType(keyType)
      val bodySchemaOption = getRecordSchemaForType(bodyType)

      q"""{
        val mergedType: String = ${mergedType(resourceType)}
        val keySchemaOption: Option[com.linkedin.data.schema.DataSchema] = $keySchemaOption
        val keySchema: com.linkedin.data.schema.DataSchema = keySchemaOption
          .getOrElse(new com.linkedin.data.schema.StringDataSchema)
        val bodySchemaOption: Option[com.linkedin.data.schema.RecordDataSchema] = $bodySchemaOption
        (for {
          bodySchema <- bodySchemaOption
        } yield {
          org.coursera.naptime.model.Keyed(
            mergedType,
            org.coursera.naptime.Types.computeAsymType(
              mergedType,
              keySchema,
              bodySchema,
              stubInstance.Fields))
        }).toList ++ List(
          keySchemaOption.map(org.coursera.naptime.model.Keyed(${keyType.typeSymbol.fullName}, _)),
          bodySchemaOption.map(org.coursera.naptime.model.Keyed(${bodyType.typeSymbol.fullName}, _))).flatten
      }"""
    }

    private[this] def handlerKind(actionCategory: RestActionCategory) = {
      actionCategory match {
        case GetRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.GET"
        case MultiGetRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.MULTI_GET"
        case GetAllRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.GET_ALL"
        case PatchRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.PATCH"
        case CreateRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.CREATE"
        case UpdateRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.UPSERT"
        case DeleteRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.DELETE"
        case FinderRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.FINDER"
        case ActionRestActionCategory =>
          q"org.coursera.naptime.schema.HandlerKind.ACTION"
      }
    }

    private[this] def handlerSchemaForMethod(
        method: c.universe.MethodSymbol,
        category: RestActionCategory): c.Tree = {
      if (method.paramLists.length > 1) {
        c.error(method.pos, "Naptime does not support curried argument lists at this time.")
      }
      val parameterTrees = for {
        paramList <- method.paramLists.headOption.toList
        (param, i) <- paramList.zipWithIndex
      } yield {
        // TODO(saeta): handle path keys appropriately!
        val parameterModelName = TermName(c.freshName())
        // TODO(saeta): Handle attributes!
        val isOptionalParam = Types.OptionalParam.unapply(param)
        val parameterDefinition = if (param.asTerm.isParamWithDefault) {
          val defaultFnName = TermName(s"${method.name}$$default$$" + (i + 1))
          val defaultValue = if (param.typeSignature <:< DATA_TEMPLATE) {
            q"""org.coursera.naptime.schema.ArbitraryValue.ArbitraryRecordMember(
                  org.coursera.naptime.schema.ArbitraryRecord(
                    stubInstance.$defaultFnName.data(),
                    org.coursera.courier.templates.DataTemplates.DataConversion.SetReadOnly))"""
          } else if (param.typeSignature <:< ANY_VAL || param.typeSignature =:= STRING) {
            // TODO(saeta): Note: this does not handle case class Foo(val: Int) extends AnyVal!
            q"""stubInstance.$defaultFnName.asInstanceOf[Any] match {
                  case i: Int =>
                    org.coursera.naptime.schema.ArbitraryValue.IntMember(i.asInstanceOf[Int])
                  case s: String =>
                    org.coursera.naptime.schema.ArbitraryValue.StringMember(s.asInstanceOf[String])
                  case l: Long =>
                    org.coursera.naptime.schema.ArbitraryValue.LongMember(l.asInstanceOf[Long])
                  case f: Float =>
                    org.coursera.naptime.schema.ArbitraryValue.FloatMember(f.asInstanceOf[Float])
                  case d: Double =>
                    org.coursera.naptime.schema.ArbitraryValue.DoubleMember(d.asInstanceOf[Double])
                  case b: com.linkedin.data.ByteString =>
                    org.coursera.naptime.schema.ArbitraryValue.ByteStringMember(b.asInstanceOf[com.linkedin.data.ByteString])
                  case b: Boolean =>
                    org.coursera.naptime.schema.ArbitraryValue.BooleanMember(b.asInstanceOf[Boolean])
                  case _ =>
                    org.coursera.naptime.schema.ArbitraryValue.StringMember("unknown default")
               }"""
          } else {
            // TODO: handle extends scala.Map and scala.Traversable: Construct a data list / map
            // TODO: Try and infer an implicit json.OFormat, and convert to JsValue and then into
            //       a DataMap.
            q"""org.coursera.naptime.schema.ArbitraryValue.StringMember("unknown default")"""
          }
          q"""
            val defaultValue: org.coursera.naptime.schema.ArbitraryValue = $defaultValue
            org.coursera.naptime.schema.Parameter(
              name = ${param.name.toString},
              `type` = ${param.typeSignature.toString},
              attributes = List.empty,
              default = Some(defaultValue),
              required = ${!isOptionalParam}
            )
          """
        } else {
          q"""
            org.coursera.naptime.schema.Parameter(
              name = ${param.name.toString},
              `type` = ${param.typeSignature.toString},
              attributes = List.empty,
              default = None,
              required = ${!isOptionalParam}
            )
          """
        }
        q"""
          val parameterWithoutTypeSchema = $parameterDefinition
          val typeSchema: Option[com.linkedin.data.DataMap] = ${getDataSchemaDataMapForType(param.typeSignature)}
          val updatedDataMap = parameterWithoutTypeSchema.data().clone()
          typeSchema.foreach(t => updatedDataMap.put("typeSchema", t))
          org.coursera.naptime.schema.Parameter(
            updatedDataMap,
            org.coursera.courier.templates.DataTemplates.DataConversion.SetReadOnly)
        """
      }
      // TODO: handle input, custom output bodies, and attributes
      q"""
      org.coursera.naptime.schema.Handler(
        kind = ${handlerKind(category)},
        name = ${method.name.toString},
        parameters = List(..$parameterTrees),
        attributes = org.coursera.naptime.router2.AttributesProvider
            .getMethodAttributes(resourceClass.getName, ${method.name.toString}))
      """
    }

    private[this] def methodOverrideCodeGenerator(
      params: List[(c.TermName, c.Tree, c.Tree)],
      methodName: c.TermName,
      methodSymbol: c.universe.MethodSymbol,
      overrideMethodParameters: List[c.Tree],
      category: RestActionCategory): OptionalTree = {
      val body = q"""
        ..${params.map(_._2)}
        val allResults = scala.List(..${params.map(_._1)})
        allResults.find(_.isLeft).map(_.left.get).getOrElse {
          resourceInstance.$methodSymbol(..${params.map(_._3)})
            .setTags(mkRequestTags(${methodSymbol.name.toString}))
        }
      """
      Right(q"""
        override def $methodName(..$overrideMethodParameters): $ROUTE_ACTION = {
          $body
        }
      """ -> List(handlerSchemaForMethod(methodSymbol, category)))
    }

    private[this] def buildGetAllTree(methods: Iterable[c.universe.MethodSymbol]): OptionalTree =
      buildGetAllOrCreateActionTree(GetAllRestActionCategory, "executeGetAll", methods)

    private[this] def buildCreateTree(methods: Iterable[c.universe.MethodSymbol]): OptionalTree =
      buildGetAllOrCreateActionTree(CreateRestActionCategory, "executeCreate", methods)

    /**
     * Builds a calling tree for a GetAll or a Create action tree.
     *
     * @param actionCategory The type of rest action we are generating code for.
     *                       i.e. [[GetAllRestActionCategory]], or [[CreateRestActionCategory]].
     * @param overrideMethodName Provides the name in the [[CollectionResourceRouter]] to override
     * @param methods Naptime methods of the resource class of the naptime action type
     * @return an optional tree representing the override method code.
     */
    private[this] def buildGetAllOrCreateActionTree(
        actionCategory: RestActionCategory,
        overrideMethodName: String,
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree = {
      methods match {
        case methodSymbol :: Nil =>
          if (methodSymbol.paramLists.isEmpty ||
            (methodSymbol.paramLists.size == 1 && methodSymbol.paramLists.head.isEmpty)) {
            val methodName = TermName(overrideMethodName)
            val tree =
              q"""override def $methodName(
                      requestHeader: $REQUEST_HEADER,
                      optPathKey: resourceInstance.OptPathKey): $ROUTE_ACTION = {
                    resourceInstance.$methodSymbol
                      .setTags(mkRequestTags(${methodSymbol.name.toString}))
                  }"""

            Right(tree -> List(handlerSchemaForMethod(methodSymbol, actionCategory)))
          } else if (methodSymbol.paramLists.size == 1) {
            val methodName = TermName(overrideMethodName)
            val params = for {
              (param, i) <- methodSymbol.paramLists.head.zipWithIndex
            } yield {
              debug(s"PARAM: ${param.name}: ${param.typeSignature}")
              val parsedTerm = TermName(s"param_${param.name.toString}")
              val parser = param match {
                case Types.OptPathKey() =>
                  q"Right(optPathKey)"
                case Types.PathKey() =>
                  c.error(param.pos, "You cannot bind a PathKey in this context.")
                  q"Left(???)"
                case Types.AncestorKeys() =>
                  q"Right(optPathToAncestor(optPathKey))"
                case Types.OptionalParam() =>
                  buildQueryParamParserTree(param, i, methodSymbol)
                case Types.ArbitraryParam() =>
                  c.error(param.pos,
                    s"Parameter ${param.name}: ${param.typeSignature} not allowed here. " +
                      "Please see https://docs.dkandu.me/projects/naptime/advanced.html")
                  q"Left(???)" // Use this as a placeholder.
              }
              val parsingTree = q"val $parsedTerm = $parser"
              val extractedValue = q"$parsedTerm.right.get"
              (parsedTerm, parsingTree, extractedValue)
            }
            methodOverrideCodeGenerator(
              params,
              methodName,
              methodSymbol,
              List(q"requestHeader: $REQUEST_HEADER", q"optPathKey: resourceInstance.OptPathKey"),
              actionCategory)
          } else {
              Left(methodSymbol.pos, "Parameter list must be empty.")
          }
        case firstMethod :: _ =>
          // Note: we use firstMethod.pos as this list is reverse of source-order.
          Left(firstMethod.pos, s"Multiple ${actionCategory.name} actions found.")
        case Nil =>
          val msg = "COMPILER BUG: methods in BuildParameterlessActionTree is empty"
          c.error(c.enclosingPosition, msg)
          throw MacroBugException(msg)
      }
    }

    /**
     * Builds a calling tree for single element action trees (i.e. naptime methods that generally
     * do not have method parameters). These request types are: getAll, and create.
     *
     * @param actionCategory The type of rest action we are generating code for.
     *                       i.e. [[GetRestActionCategory]], or [[UpdateRestActionCategory]].
     * @param overrideMethodName Provides the name in the [[CollectionResourceRouter]] to override
     * @param methods Naptime methods of the resource class of the naptime action type
     * @return  an optional tree representing the override method code.
     */
    private[this] def buildSingleElementActionTree(
        actionCategory: RestActionCategory,
        overrideMethodName: String,
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree = {
      methods match {
        case methodSymbol :: Nil =>
          if (methodSymbol.paramLists.size != 1) {
            Left(methodSymbol.pos, "Method must have one and only one parameter list.")
          } else {
            val methodName = TermName(overrideMethodName)
            val params = for {
              paramList <- methodSymbol.paramLists
              (param, i) <- paramList.zipWithIndex
            } yield {
                debug(s"param is: $param ('${param.name}') and ${param.typeSignature}")
                val parsedTerm = TermName(s"param_${param.name.toString}")
                val parser = param match {
                  case Types.PathKey() =>
                    debug(s"FOUND A PATH KEY FOR ${param.name}")
                    q"Right(pathKey)" // Method passes it right in.
                  case Types.OptPathKey() =>
                    debug(s"Found an inappropriate OptPathKey for param ${param.name}")
                    c.error(param.pos, "You cannot bind an OptPathKey in this context.")
                    q"Left(???)"
                  case Types.Id() =>
                    debug(s"Found an ID parameter: ${param.name} with type ${param.typeSignature}")
                    q"Right(pathKey.head)"
                  case Types.AncestorKeys() =>
                    debug(s"FOUND AN ANCESTORKEY for ${param.name}")
                    q"Right(pathToAncestor(pathKey))"
                  case Types.KeyType() =>
                    debug(s"Found a KeyType key for ${param.name}")
                    q"Right(pathKey.head)"
                  case Types.OptionalParam() =>
                    debug(s"Building parser for '${param.name}' with type '${param.typeSignature}'")
                    buildQueryParamParserTree(param, i, methodSymbol)
                  case Types.ArbitraryParam() =>
                    c.error(param.pos,
                      s"Parameter ${param.name}: ${param.typeSignature} not allowed here. " +
                        "Please see https://docs.dkandu.me/projects/naptime/advanced.html")
                    q"Left(???)" // Use this as a placeholder.
                }
                val parsingTree = q"val $parsedTerm = $parser"
                val extractedValue = q"$parsedTerm.right.get"
                (parsedTerm, parsingTree, extractedValue)
              }
            methodOverrideCodeGenerator(
              params,
              methodName,
              methodSymbol,
              List(q"requestHeader: $REQUEST_HEADER", q"pathKey: resourceInstance.PathKey"),
              actionCategory)
          }
        case firstMethod :: _ =>
          Left(firstMethod.pos, s"Multiple ${actionCategory.name}'s found.")
        case Nil =>
          val msg = "COMPILER BUG: methods in buildSingleElementActionTree is empty"
          c.error(c.enclosingPosition, msg)
          throw MacroBugException(msg)
      }
    }

    private[this] def buildGetTree(
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree =
      buildSingleElementActionTree(GetRestActionCategory, "executeGet", methods)

    private[this] def buildUpdateTree(
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree =
      buildSingleElementActionTree(UpdateRestActionCategory, "executePut", methods)

    private[this] def buildDeleteTree(
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree =
      buildSingleElementActionTree(DeleteRestActionCategory, "executeDelete", methods)

    private[this] def buildPatchTree(
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree =
      buildSingleElementActionTree(PatchRestActionCategory, "executePatch", methods)

    private[this] def buildMultiGetTree(
        methods: Iterable[c.universe.MethodSymbol]): OptionalTree = {
      methods match {
        case methodSymbol :: Nil =>
          if (methodSymbol.paramLists.length != 1) {
            Left(methodSymbol.pos, "MultiGet requires a single parameter list, with at least 'ids'")
          } else {
            var hasSeenIds = false
            val params = for {
              (param, i) <- methodSymbol.paramLists.head.zipWithIndex
            } yield {
              debug(s"PARAM: ${param.name}: ${param.typeSignature}")
              val parsedTerm = TermName(s"param_${param.name.toString}")
              val parser = param match {
                case Types.Ids() =>
                  hasSeenIds = true
                  q"Right(ids)"
                case Types.OptPathKey() =>
                  q"Right(optPathKey)"
                case Types.AncestorKeys() =>
                  q"Right(optPathToAncestor(optPathKey))"
                case Types.OptionalParam() =>
                  buildQueryParamParserTree(param, i, methodSymbol)
                case Types.ArbitraryParam() =>
                  c.error(param.pos,
                    s"Parameter ${param.name}: ${param.typeSignature} not allowed here. " +
                      "Please see https://docs.dkandu.me/projects/naptime/advanced.html")
                  q"Left(???)" // Use this as a placeholder.
              }
              val parsingTree = q"val $parsedTerm = $parser"
              val extractedValue = q"$parsedTerm.right.get"
              (parsedTerm, parsingTree, extractedValue)
            }
            if (hasSeenIds) {
              methodOverrideCodeGenerator(
                params,
                TermName("executeMultiGet"),
                methodSymbol,
                List(q"requestHeader: $REQUEST_HEADER",
                  q"optPathKey: resourceInstance.OptPathKey",
                  q"ids: Set[resourceInstance.KeyType]"),
                MultiGetRestActionCategory)
            } else {
              Left(methodSymbol.pos, "Multi-Get requires an 'ids' parameter!")
            }
          }
        case firstMethod :: _ =>
          Left(firstMethod.pos, "Multiple MultiGet's found.")
        case Nil =>
          val msg = "COMPILER BUG: methods in buildMultiGetTree is empty"
          c.error(c.enclosingPosition, msg)
          throw MacroBugException(msg)
      }
    }

    private[this] def buildFinderTree(
        methods: Iterable[c.universe.MethodSymbol],
        keyType: c.universe.Type): OptionalTree = {
      val methodBranches = methods.map(buildSingleNamedActionTree(FinderRestActionCategory))
      val tree = q"""
      override def executeFinder(
          requestHeader: $REQUEST_HEADER,
          optPathKey: resourceInstance.OptPathKey,
          finderName: String): $ROUTE_ACTION = {
        finderName match {
          case ..${methodBranches.map(_._1)}
          case _ => super.executeFinder(requestHeader, optPathKey, finderName)
        }
      }
      """
      Right(tree -> methodBranches.map(_._2))
    }

    private[this] def buildActionTree(
        methods: Iterable[c.universe.MethodSymbol],
        keyType: c.universe.Type): OptionalTree = {
      val methodBranches = methods.map(buildSingleNamedActionTree(ActionRestActionCategory))
      val tree = q"""
      override def executeAction(
          requestHeader: $REQUEST_HEADER,
          optPathKey: resourceInstance.OptPathKey,
          actionName: String): $ROUTE_ACTION = {
        actionName match {
          case ..${methodBranches.map(_._1)}
          case _ => super.executeAction(requestHeader, optPathKey, actionName)
        }
      }
    """
      Right(tree -> methodBranches.map(_._2))
    }

    /**
     * Generates a case clause for a match based on the finder or action name.
     *
     * e.g. example code that would be generated looks like:
     *
     * {{{
     *   case "byEmail" =>
     *     val param1 = new StrictQueryParser("email", implicitInferredParser)
     *       .evaluate(requestHeader)
     *     val errors = List(param1).find(_.isLeft).map(_.left.get)
     *     errors.getOrElse {
     *       resourceInstance.$method(param1)
     *     }
     * }}}
     *
     * Required variables to be defined outside of this tree:
     * - `resourceInstance` is a resource instance (usually supplied by CollectionResourceRouter.
     * - `requestHeader` is the request we are parsing.
     *
     * @param method The method we are parsing.
     * @return A tree corresponding to the case branch to take, and a Handler schema tree
     */
    private[this] def buildSingleNamedActionTree(category: RestActionCategory)
        (method: c.universe.MethodSymbol): (c.Tree, c.Tree) = {
      debug(s"building single named action tree for method $method")
      val params = for {
        paramList <- method.paramLists
        (param, i) <- paramList.zipWithIndex
      } yield {
        debug(s"param is: $param and ${param.typeSignature}")
        val parsedTerm = TermName(s"param_${param.name.toString}")
        val parser = param match {
          case Types.OptPathKey() =>
            q"Right(optPathKey)"
          case Types.PathKey() =>
            c.error(param.pos, s"Cannot automatically bind parameter ${param.name}")
            q"Left(???)"
          case Types.AncestorKeys() =>
            q"Right(optPathToAncestor(optPathKey))"
          case Types.ArbitraryParam() =>
            buildQueryParamParserTree(param, i, method)
        }
        val parsingTree = q"val $parsedTerm = $parser"
        val extractedValue = q"$parsedTerm.right.get"
        (parsedTerm, parsingTree, extractedValue)
      }
      val body = if (params.isEmpty) {
        q"resourceInstance.$method.setTags(mkRequestTags(${method.name.toString}))"
      } else {
        q"""
        ..${params.map(_._2)}
        val allResults = scala.List(..${params.map(_._1)})
        allResults.find(_.isLeft).map(_.left.get).getOrElse {
          resourceInstance.$method(..${params.map(_._3)})
            .setTags(mkRequestTags(${method.name.toString}))
        }
        """
      }
      (cq"${method.name.toString} => $body", handlerSchemaForMethod(method, category))
    }

    /**
     * Builds a tree that evaluates to an Either[RouteAction, ParamType].
     *
     * @see StrictQueryParser and OptionalQueryParser.
     * @param param Provides the parameter to parse.
     * @return The tree that parses the parameter.
     */
    private[this] def buildQueryParamParserTree(
        param: c.universe.Symbol,
        index: Int,
        method: c.universe.MethodSymbol): c.Tree = {
      val paramName = param.name.toString
      debug(s"Building queryparam parser tree for $paramName : ${param.typeSignature.toString}")
      if (param.typeSignature <:< typeOf[ScalaRecordTemplate]) {
        q"""org.coursera.naptime.router2.CourierQueryParsers.strictParse(
           $paramName,
           ${param.typeSignature.typeSymbol.companion}.SCHEMA,
           resourceInstance.getClass,
           requestHeader).right.map { dataMap =>
             ${param.typeSignature.typeSymbol.companion}.apply(dataMap,
               org.coursera.courier.templates.DataTemplates.DataConversion.SetReadOnly)
           }"""
      } else if (param.typeSignature <:< typeOf[Option[ScalaRecordTemplate]]) {
        q"""org.coursera.naptime.router2.CourierQueryParsers.optParse(
           $paramName,
           ${param.typeSignature.typeSymbol.companion}.SCHEMA,
           resourceInstance.getClass,
           requestHeader).right.map { dataMapOpt =>
             dataMapOpt.map { dataMap =>
               ${param.typeSignature.typeSymbol.companion}.apply(dataMap,
                 org.coursera.courier.templates.DataTemplates.DataConversion.SetReadOnly)
             }
           }"""
      } else if (param.typeSignature =:= typeOf[Boolean]) {
        if (param.asTerm.isParamWithDefault) {
          val getterName = TermName(s"${method.name}$$default$$" + (index + 1))
          q"""org.coursera.naptime.router2.CollectionResourceRouter.OptionBooleanFlagParser(
              $paramName, resourceInstance.getClass).evaluate(requestHeader).right.map(
                _.getOrElse(resourceInstance.$getterName))"""
        } else {
          q"""org.coursera.naptime.router2.CollectionResourceRouter.BooleanFlagParser(
              $paramName, resourceInstance.getClass).evaluate(requestHeader)"""
        }
      } else if (param.typeSignature =:= typeOf[Option[Boolean]]) {
        q"""org.coursera.naptime.router2.CollectionResourceRouter.OptionBooleanFlagParser(
              $paramName, resourceInstance.getClass).evaluate(requestHeader)"""
      } else if (param.typeSignature <:< weakTypeOf[Option[Any]]) {
        // Use OptionalQueryParser.
        val internalType = param.typeSignature.typeArgs.head // Option's type parameter.
        val stringKeyFormatType = appliedType(STRING_KEY_FORMAT_TYPE_CONSTRUCTOR,
            List(internalType))
        val inferredFormatter = c.inferImplicitValue(stringKeyFormatType)
        q"""org.coursera.naptime.router2.CollectionResourceRouter.OptionalQueryParser(
          $paramName, $inferredFormatter, resourceInstance.getClass).evaluate(requestHeader)"""
      } else {
        // Use strict query parser.
        val stringKeyFormatType = appliedType(STRING_KEY_FORMAT_TYPE_CONSTRUCTOR,
          List(param.typeSignature))
        val inferredFormatter = c.inferImplicitValue(stringKeyFormatType)
        if (param.asTerm.isParamWithDefault) {
          val getterName = TermName(s"${method.name}$$default$$" + (index + 1))
          q"""org.coursera.naptime.router2.CollectionResourceRouter.OptionalQueryParser(
                $paramName, $inferredFormatter, resourceInstance.getClass).evaluate(requestHeader)
                .right.map(_.getOrElse(resourceInstance.$getterName))"""
        } else {
          q"""org.coursera.naptime.router2.CollectionResourceRouter.StrictQueryParser(
            $paramName, $inferredFormatter, resourceInstance.getClass).evaluate(requestHeader)"""
        }
      }
    }

    object Types {
      object Id {
        def unapply(a: Symbol): Boolean = {
          a.name.toString == "id"
        }
      }

      object Ids {
        def unapply(a: Symbol): Boolean = {
          a.name.toString == "ids"
        }
      }

      object PathKey {
        def unapply(a: Symbol): Boolean = {
          a.typeSignature.toString.endsWith(".this.PathKey")
        }
      }

      object OptPathKey {
        def unapply(a: Symbol): Boolean = {
          a.typeSignature.toString.endsWith(".this.OptPathKey")
        }
      }

      object AncestorKeys {
        def unapply(a: Symbol): Boolean = {
          a.typeSignature.toString.endsWith(".this.AncestorKeys") ||
            a.typeSignature.toString == "AncestorKeys"
        }
      }

      object KeyType {
        def unapply(a: Symbol): Boolean = {
          a.typeSignature.toString.endsWith(".this.KeyType")
        }
      }

      /**
       * Matches function parameters that do not have to be present in the requests.
       * In particular, this matches both parameters that are Option's, as well as parameters that
       * have defaults.
       */
      object OptionalParam {
        def unapply(a: Symbol): Boolean = {
          a.typeSignature <:< weakTypeOf[Option[Any]] || a.asTerm.isParamWithDefault
        }
      }

      object ArbitraryParam {
        def unapply(a: Symbol): Boolean = {
          true // Always match!
        }
      }
    }
  }
}
