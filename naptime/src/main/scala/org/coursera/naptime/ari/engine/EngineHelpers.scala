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

package org.coursera.naptime.ari.engine

import com.linkedin.data.DataMap
import com.linkedin.data.element.DataElement
import com.linkedin.data.it.Builder
import com.linkedin.data.it.IterationOrder
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ResourceName
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.collection.mutable


object EngineHelpers extends StrictLogging {

  /**
    * Defines a field and selection used in a forward relation
    * (where model A defines the ids on model B)
    * @param selection field selection defining arguments, field, and nested selections
    * @param resourceName resource name to fetch the forward relation from
    * @param ids ids of the elements on resourceName to fetch
    */
  case class ForwardRelatedField(
      selection: RequestField,
      resourceName: ResourceName,
      ids: List[String])

  /**
    * Defines a field and selection used in a reverse relation
    * (where resource B defines the matching models to be appended to model A)
    * @param selection field selection defining arguments, field, and nested selections
    * @param path path of the dynamic field to receive the ids from the reverse relation
    * @param element top level element, used for argument interpolation
    * @param annotation details about the reverse relation (including arguments, etc.)
    */
  case class ReverseRelatedField(
      selection: RequestField,
      path: Seq[String],
      element: DataMap,
      annotation: ReverseRelationAnnotation)

  /**
    * Get subselection fields from a selection. This filters out all "elements" level fields,
    * which are used only for pagination and do not describe the data needs of a request.
    * @param selection input RequestField selection
    * @return List[RequestField] of subselections, with "elements" levels filtered out
    */
  private[engine] def getSelections(selection: RequestField): List[RequestField] = {
    selection.selections.flatMap { subselection =>
      if (subselection.name == "elements") {
        subselection.selections
      } else {
        List(subselection)
      }
    }
  }

  /**
    * Mutates a data map in-place by inserting an element in a field at a specified path.
    * This allows relations to update a list of IDs anywhere on a map (nested, in unions, etc.)
    *
    * @param element top-level element to be updated
    * @param schema data schema defining the fields on the element
    * @param path list of strings defining the path to the target element
    * @param data data to be inserted at the specified path
    */
  private[engine] def insertAtPath(
      element: DataMap,
      schema: RecordDataSchema,
      path: Seq[String],
      data: AnyRef): Unit = {
    if (data != null) {
      val it = Builder.create(element, schema, IterationOrder.PRE_ORDER).dataIterator()
      Iterator
        .continually(it.next)
        .takeWhile(_ != null)
        .filter(_.path.toSeq.map(_.toString) == path.dropRight(1))
        .foreach(_.getValue.asInstanceOf[DataMap].put(path.last, data))
    }
  }

  /**
    * Iterates through a data map and returns the value stored at a particular path in the data map,
    * or None if the path isn't found (or the data is null)
    *
    * @param element top-level element to be updated
    * @param schema data schema defining the fields on the element
    * @param path list of strings defining the path to the target element
    */
  private[engine] def getValueAtPath(
      element: DataMap,
      schema: RecordDataSchema,
      path: Seq[String]): Option[Object] = {
      val it = Builder.create(element, schema, IterationOrder.PRE_ORDER).dataIterator()
      Iterator
        .continually(it.next)
        .takeWhile(_ != null)
        .find(_.path.toSeq.map(_.toString) == path.dropRight(1))
        .map(_.getValue.asInstanceOf[DataMap].get(path.last))
        .flatMap(Option(_))
  }

  /**
    * Parses an element, using an associated schema and a list of selections from the request,
    * and returns a list of all forward and reverse relations requested from the model
    *
    * @param selection field selection defining arguments, field, and nested selections
    * @param data list of top-level elements / models returned from a previous request.
    * @param schema associated RecordDataSchema for the elements returned
    * @return List of forward and reverse relations from all of the elements in the data
    */
  private[engine] def collectRelations(
      selection: RequestField,
      data: Iterable[DataMap],
      schema: RecordDataSchema): (List[ForwardRelatedField], List[ReverseRelatedField]) = {

    val forwardRelations = mutable.Buffer[ForwardRelatedField]()
    val reverseRelations = mutable.Buffer[ReverseRelatedField]()

    data.foreach { element =>
      val it = Builder.create(element, schema, IterationOrder.PRE_ORDER).dataIterator()
      Iterator
        .continually(it.next)
        .takeWhile(_ != null)
        .foreach { dataElement =>
          val path = dataElement.path.toSeq.map(_.toString)
          for {
            selection <- selectionAtPath(selection, path).toList
            if selection.selections.nonEmpty
            recordField <- dataElement.getSchema match {
              case record: RecordDataSchema => record.getFields.asScala
              case _ => List.empty
            }
            fieldSelection <- mergeSelections(getSelections(selection).filter(_.name == recordField.getName))
          } yield {
            forwardRelationForField(recordField).foreach { forwardRelation =>
              val forwardIds = getFieldValues(recordField, dataElement.getValue.asInstanceOf[DataMap])
              forwardRelations += ForwardRelatedField(fieldSelection, forwardRelation, forwardIds)
            }

            reverseRelationForField(recordField).foreach { reverseRelation =>
              reverseRelations += ReverseRelatedField(
                selection = fieldSelection,
                path = path :+ fieldSelection.alias.getOrElse(fieldSelection.name),
                element = element,
                annotation = reverseRelation)
            }
          }
        }
    }
    (forwardRelations.toList, reverseRelations.toList)
  }

  /**
    * Returns an optional ResourceName if the specified field has a forward relation specified
    * @param field field on the RecordDataSchema to check for forward relations
    * @return optional ResourceName if forward relation exists, None if no forward relation is set
    */
  private[engine] def forwardRelationForField(
      field: RecordDataSchema.Field): Option[ResourceName] = {
    Option(field.getProperties.get(Relations.PROPERTY_NAME)).map {
      case idString: String =>
        ResourceName.parse(idString).getOrElse {
          throw new IllegalStateException(s"Could not parse identifier '$idString' " +
            s"for field '$field'")
        }
      case identifier =>
        throw new IllegalStateException(s"Unexpected type for identifier '$identifier' " +
          s"for field '$field'")
    }
  }

  /**
    * Returns an ReverseRelationAnnotation if the specified field has a reverse relation specified
    * @param field field on the RecordDataSchema to check for reverse relations
    * @return optional ReverseRelationAnnotation if relation exists, None if no relation is set
    */
  private[engine] def reverseRelationForField(
      field: RecordDataSchema.Field): Option[ReverseRelationAnnotation] = {
    Option(field.getProperties.get(Relations.REVERSE_PROPERTY_NAME)).map {
      case dataMap: DataMap => ReverseRelationAnnotation.build(dataMap, DataConversion.SetReadOnly)
    }
  }

  private[engine] def getFieldValues(
      field: RecordDataSchema.Field,
      element: DataMap): List[String] = {

    if (field.getType.getDereferencedDataSchema.isPrimitive) {
      List(element.get(field.getName).toString)
    } else if (field.getType.getDereferencedDataSchema.isInstanceOf[ArrayDataSchema]) {
      Option(element.getDataList(field.getName)).map(_.asScala.map(_.toString).toList).getOrElse {
        logger.debug(s"Field ${field.getName} was not found / was not a data list " +
          s"in $element for query field $field")
        List.empty
      }
    } else {
      throw new IllegalStateException(
        s"Cannot join on an unknown field type '${field.getType.getDereferencedType}' " +
          s"for field '${field.getName}'")
    }
  }

  /**
    * Recursively parses a list of selections to find a selection given a path
    * @param selection top-level selection to begin parsing at
    * @param path list of child selection names to traverse down
    * @return optional RequestField selection if path is found, None if path is not found
    */
  private[engine] def selectionAtPath(
      selection: RequestField,
      path: Seq[String]): Option[RequestField] = {
    if (path.isEmpty) {
      // Selection matching path is found
      Some(selection)
    } else {
      getSelections(selection).find(_.name == path.head.replaceAll("\\.", "_"))
        .flatMap { childSelection =>
          selectionAtPath(childSelection, path.tail)
        }
    }
  }

  private[engine] def stringifyArg(value: JsValue): String = {
    value match {
      case JsArray(arrayElements) =>
        arrayElements.map(stringifyArg).filterNot(_.isEmpty).mkString(",")
      case stringValue: JsString =>
        stringValue.as[String]
      case number: JsNumber =>
        number.toString
      case boolean: JsBoolean =>
        boolean.toString
      case jsObject: JsObject =>
        Json.stringify(jsObject)
      case JsNull =>
        ""
    }
  }

  /**
    * Merges all sub-selections of a request into a single selection
    * @param fields list of selections to merge
    * @return list of merged selections
    */
  private[engine] def mergeSelections(fields: List[RequestField]): List[RequestField] = {
    fields.groupBy(f => (f.name, f.args, f.alias)).values.map { l =>
      l.head.copy(selections = l.flatMap(_.selections))
    }.toList
  }
}
