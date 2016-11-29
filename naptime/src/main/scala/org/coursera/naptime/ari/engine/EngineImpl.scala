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

import javax.inject.Inject

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import com.linkedin.data.element.DataElement
import com.linkedin.data.it.Builder
import com.linkedin.data.it.IterationOrder
import com.linkedin.data.it.Predicate
import com.linkedin.data.schema.ArrayDataSchema
import com.linkedin.data.schema.RecordDataSchema
import com.typesafe.scalalogging.StrictLogging
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ResourceName
import org.coursera.naptime.Types.Relations
import org.coursera.naptime.ari.EngineApi
import org.coursera.naptime.ari.FetcherApi
import org.coursera.naptime.ari.Request
import org.coursera.naptime.ari.RequestField
import org.coursera.naptime.ari.Response
import org.coursera.naptime.ari.ResponseMetrics
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.TopLevelRequest
import org.coursera.naptime.schema.ReverseRelationAnnotation
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

class EngineImpl @Inject() (
    schemaProvider: SchemaProvider,
    fetcher: FetcherApi)
    (implicit executionContext: ExecutionContext) extends EngineApi with StrictLogging {

  private[this] def mergedTypeForResource(resourceName: ResourceName): Option[RecordDataSchema] = {
    schemaProvider.mergedType(resourceName)
  }

  override def execute(request: Request): Future[Response] = {
    val responseFutures = request.topLevelRequests.map { topLevelRequest =>
      executeTopLevelRequest(request.requestHeader, topLevelRequest).flatMap { response =>
        executeRelatedRequest(request.requestHeader, response, topLevelRequest)
      }

    }
    val futureResponses = Future.sequence(responseFutures)
    futureResponses.map { responses =>
      responses.foldLeft(Response.empty)(_ ++ _)
    }
  }

  case class ForwardRelatedField(selection: RequestField, resourceName: ResourceName, ids: List[String])
  case class ReverseRelatedField(selection: RequestField, path: Seq[String], element: DataMap, annotation: ReverseRelationAnnotation)

  private[this] def getSelections(selection: RequestField): List[RequestField] = {
    val elementLookups = (for {
      elements <- selection.selections.find(_.name == "elements")
    } yield {
      elements.selections.filter(_.selections.nonEmpty)
    }).getOrElse {
      List.empty
    }

    val singularLookups = selection.selections.filter { selection =>
      selection.selections.nonEmpty && selection.name != "elements"
    }

    elementLookups ++ singularLookups
  }

  private[this] def replaceAtPath(element: DataMap, schema: RecordDataSchema, path: Seq[String], updatedData: AnyRef): Unit = {
    def pathEqualsPredicate = new Predicate {
      def evaluate(element: DataElement): Boolean = {
        element.path.toSeq.map(_.toString) == path.dropRight(1)
      }
    }
    val it = Builder
      .create(element, schema, IterationOrder.PRE_ORDER)
      .filterBy(pathEqualsPredicate)
      .dataIterator()
    var dataElement: DataElement = null
    while ( {
      dataElement = it.next(); dataElement
    } != null) {
      dataElement.getValue.asInstanceOf[DataMap].put(path.last, updatedData)
    }
  }

  private[this] def collectRelations(
      selection: RequestField,
      data: Iterable[DataMap],
      schema: RecordDataSchema): (List[ForwardRelatedField], List[ReverseRelatedField]) = {
    val forwardRelations = mutable.Buffer[ForwardRelatedField]()
    val reverseRelations = mutable.Buffer[ReverseRelatedField]()

    val dataElements = data.flatMap { element =>
      val dataElements = mutable.Map[Seq[String], (DataMap, DataElement)]()
      val it = Builder.create(element, schema, IterationOrder.PRE_ORDER).dataIterator()
      var dataElement: DataElement = null
      while ( {
        dataElement = it.next(); dataElement
      } != null) {
        val path = dataElement.path.toSeq.map(_.toString)
        dataElements += (path -> (element, dataElement))
      }
      dataElements.toList
    }

    for {
      (path, (element, dataElement)) <- dataElements
      nestedSelection <- getSelections(selection).headOption.map(sel => RequestField("", None, Set.empty, List(sel), None)).toList // TODO(bryan): Fix this
      selection <- selectionAtPath(nestedSelection, path).toList
        if selection.selections.nonEmpty
      recordField <- dataElement.getSchema match {
        case record: RecordDataSchema => record.getFields.asScala
        case _ => List.empty
      }
      fieldSelection <- selection.selections.find(_.name == recordField.getName)
    } yield {
      forwardRelationForField(recordField).foreach { forwardRelation =>
        val forwardIds = getFieldValues(recordField, dataElement.getValue.asInstanceOf[DataMap])
        forwardRelations += ForwardRelatedField(fieldSelection, forwardRelation, forwardIds)
      }

      reverseRelationForField(recordField).foreach { reverseRelation =>
        reverseRelations += ReverseRelatedField(fieldSelection, path :+ recordField.getName, element, reverseRelation)
      }
    }
    (forwardRelations.toList, reverseRelations.toList)
  }

  def getFieldValues(field: RecordDataSchema.Field, element: DataMap): List[String] = {
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

  private[this] def selectionAtPath(selection: RequestField, path: Seq[String]): Option[RequestField] = {
    if (path.isEmpty) {
      Some(selection)
    } else {
      selection.selections.find(_.name == path.head.replaceAll("\\.", "_")).flatMap { childSelection =>
        selectionAtPath(childSelection, path.tail)
      }
    }
  }

  private[this] def executeTopLevelRequest(
      requestHeader: RequestHeader,
      topLevelRequest: TopLevelRequest): Future[Response] = {
    val startTime = System.nanoTime()
    val topLevelResponse = fetcher.data(Request(requestHeader, List(topLevelRequest)))

    topLevelResponse.map { topLevelResponse =>
      topLevelResponse.copy(
        metrics = ResponseMetrics(
          numRequests = 1,
          duration = FiniteDuration(System.nanoTime() - startTime, "nanos")))
    }
  }
  private[this] def executeRelatedRequest(
      requestHeader: RequestHeader,
      topLevelResponse: Response,
      topLevelRequest: TopLevelRequest): Future[Response] = {
    val topLevelData = extractDataMaps(topLevelResponse, topLevelRequest.resource)
    mergedTypeForResource(topLevelRequest.resource).map { mergedType =>
      val (forwardRelations, reverseRelations) = collectRelations(
        topLevelRequest.selection,
        topLevelData,
        mergedType)

      val forwardRelationResponses = forwardRelations
        .groupBy(relation => (relation.resourceName, relation.selection))
        .mapValues(_.foldLeft(List[String]())(_ ++ _.ids))
        .map { case ((resourceName, selection), ids) =>
          fetchForwardRelation(requestHeader, selection, resourceName, ids)
        }

      val reverseRelationResponses = reverseRelations
        .groupBy(relation => (relation.selection, relation.path, relation.annotation))
        .mapValues(_.map(_.element))
        .map { case ((selection, path, annotation), elements) =>
          fetchReverseRelation(requestHeader, selection, elements, path, annotation)
        }

      val topLevelResponses = forwardRelationResponses ++ reverseRelationResponses

      Future.sequence(topLevelResponses).flatMap { fieldResponses =>
        val mutableTopLevelData = topLevelData
          .map(_.clone())
          .map(data => data.get("id") -> data)
          .toMap
        for {
          fieldRelationResponse <- fieldResponses
          (id, data) <- mutableTopLevelData
          idMap <- fieldRelationResponse.idsToAnnotate
        } yield {
          val ids = idMap.getOrElse(id, List.empty)
          replaceAtPath(data, mergedType, fieldRelationResponse.path, new DataList(ids.asJava))
        }
        val updatedData = topLevelResponse.data +
          (topLevelRequest.resource -> mutableTopLevelData)
        val responseWithUpdatedData = topLevelResponse.copy(data = updatedData)

        val finalResponse = fieldResponses.foldLeft(responseWithUpdatedData)(_ ++ _.response)
        val relatedResponsesFut = fieldResponses.flatMap { fieldResponse =>
          fieldResponse.response.data.headOption.map { case (resourceName, data) =>
            val newTopLevelRequest = TopLevelRequest(resourceName, fieldResponse.requestField)
            executeRelatedRequest(
              requestHeader,
              finalResponse.copy(metrics = ResponseMetrics()),
              newTopLevelRequest)
          }
        }
        Future.sequence(relatedResponsesFut).map { relatedResponses =>
          relatedResponses.foldLeft(finalResponse)(_ ++ _)
        }
      }
    }.getOrElse {
      logger.error(s"No merged type found for resource ${topLevelRequest.resource}. " +
        s"Skipping automatic inclusions.")
      Future.successful(topLevelResponse)
    }
  }

  private[this] def extractDataMaps(
      response: Response,
      resourceName: ResourceName): Iterable[DataMap] = {
    response.data.get(resourceName).toIterable.flatMap { dataMap =>
      dataMap.values
    }
  }

  private[this] def forwardRelationForField(
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

  private[this] def reverseRelationForField(
      field: RecordDataSchema.Field): Option[ReverseRelationAnnotation] = {
    Option(field.getProperties.get(Relations.REVERSE_PROPERTY_NAME)).map {
      case dataMap: DataMap => ReverseRelationAnnotation(dataMap, DataConversion.SetReadOnly)
    }
  }

  private[this] def fetchForwardRelation(
      requestHeader: RequestHeader,
      requestField: RequestField,
      resourceName: ResourceName,
      ids: List[String]): Future[FieldRelationResponse] = {
    val multiGetIds = ids.toSet.mkString(",")

    if (multiGetIds.nonEmpty) {
      val relatedTopLevelRequest = TopLevelRequest(
        resource = resourceName,
        selection = RequestField(
          name = "multiGet",
          alias = None,
          args = Set("ids" -> JsString(multiGetIds)) ++ requestField.args,
          selections = requestField.selections))
      executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
        // Exclude the top level ids in the response.
        val res = Response(
          topLevelResponses = Map.empty,
          data = response.data,
          metrics = response.metrics)
        FieldRelationResponse(Seq.empty, requestField, res)
      }
    } else {
      Future.successful(FieldRelationResponse(Seq.empty, requestField))
    }
  }

  private[this] def fetchReverseRelation(
      requestHeader: RequestHeader,
      requestField: RequestField,
      data: Iterable[DataMap],
      path: Seq[String],
      reverse: ReverseRelationAnnotation) = {
    val resourceName = ResourceName.parse(reverse.resourceName).getOrElse {
      throw new IllegalStateException(s"Could not parse identifier " +
        s"'${reverse.resourceName}''")
    }
    val responses = data.map { topLevelElement =>
      val InterpolationRegex = new Regex("""\$([a-zA-Z0-9_]+)""", "variableName")
      val interpolatedArguments: Set[(String, JsValue)] = reverse.arguments
        .mapValues { value =>
          InterpolationRegex.replaceAllIn(value, { regexMatch =>
            val variableName = regexMatch.group("variableName")
            Option(topLevelElement.get(variableName))
              .map(_.toString)
              .getOrElse(variableName)
          })
        }
        .mapValues(value => JsString(value))
        .toSet
      val relatedTopLevelRequest = TopLevelRequest(
        resource = resourceName,
        selection = RequestField(
          name = "reverseRelation",
          alias = None,
          args = interpolatedArguments ++ requestField.args,
          selections = requestField.selections))
      executeTopLevelRequest(requestHeader, relatedTopLevelRequest).map { response =>
        topLevelElement.get("id") -> Response(
          topLevelResponses = Map.empty,
          data = response.data,
          metrics = response.metrics)
      }
    }
    Future.fold(responses)(FieldRelationResponse(path, requestField)) { case (frr, (id, response)) =>
      val idMap = response.data.headOption.map { case (_, data) =>
        id -> data.keys.toList
      }
      frr.copy(
        response = frr.response ++ response,
        idsToAnnotate = Some(frr.idsToAnnotate.getOrElse(Map.empty) ++ idMap))
    }
  }
}

case class FieldRelationResponse(
    path: Seq[String],
    requestField: RequestField,
    response: Response = Response.empty,
    idsToAnnotate: Option[Map[AnyRef, List[AnyRef]]] = None)

