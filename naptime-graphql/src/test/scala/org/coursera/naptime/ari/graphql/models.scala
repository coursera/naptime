package org.coursera.naptime.ari.graphql

import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind

object Models {

  val courseResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "courses",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedCourse",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters = List(Parameter(name = "id", `type` = "Integer", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.MULTI_GET,
        name = "multiGet",
        parameters = List(Parameter(name = "ids", `type` = "List[Integer]", attributes = List.empty)),
        attributes = List.empty)),
    className = "",
    attributes = List.empty)

  val instructorResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "instructors",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedInstructor",
    handlers = List.empty,
    className = "",
    attributes = List.empty)

  val partnersResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "partners",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedPartner",
    handlers = List.empty,
    className = "",
    attributes = List.empty)

}
