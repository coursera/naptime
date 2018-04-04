package org.coursera.naptime.ari.graphql

import com.linkedin.data.DataMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ari.graphql.models.AnyData
import org.coursera.naptime.ari.graphql.models.Coordinates
import org.coursera.naptime.ari.graphql.models.CoursePlatform
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedCourse.OriginalId.StringMember
import org.coursera.naptime.ari.graphql.models.PlatformSpecificData.OldPlatformDataMember
import org.coursera.naptime.ari.graphql.models.MergedCourses
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.models.OldPlatformData
import org.coursera.naptime.schema.Attribute
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.JsValue
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind

import scala.collection.JavaConverters._

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
        parameters = List(Parameter(name = "id", `type` = "String", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.MULTI_GET,
        name = "multiGet",
        parameters = List(Parameter(name = "ids", `type` = "List[String]", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.GET_ALL,
        name = "getAll",
        parameters = List.empty,
        attributes = List.empty)),
    className = "",
    attributes = List(Attribute("doc", Some(JsValue.build(
      new DataMap(Map("service" -> "myService").asJava),
      DataConversion.SetReadOnly)))))

  val instructorResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "instructors",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedInstructor",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters = List(Parameter(name = "id", `type` = "String", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.MULTI_GET,
        name = "multiGet",
        parameters = List(Parameter(name = "ids", `type` = "List[String]", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.GET_ALL,
        name = "getAll",
        parameters = List.empty,
        attributes = List.empty)),
    className = "",
    attributes = List.empty)

  val partnersResource = Resource(
    kind = ResourceKind.COLLECTION,
    name = "partners",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedPartner",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters = List(Parameter(name = "id", `type` = "Int", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.MULTI_GET,
        name = "multiGet",
        parameters = List(Parameter(name = "ids", `type` = "List[Int]", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.GET_ALL,
        name = "getAll",
        parameters = List.empty,
        attributes = List.empty)),
    className = "",
    attributes = List.empty)

  val multigetFreeEntity = Resource(
    kind = ResourceKind.COLLECTION,
    name = "multigetFreeEntity",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedMultigetFreeEntity",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters = List(Parameter(name = "id", `type` = "String", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.GET_ALL,
        name = "getAll",
        parameters = List.empty,
        attributes = List.empty),
      Handler(
        kind = HandlerKind.FINDER,
        name = "finder",
        parameters = List(Parameter(name = "id", `type` = "String", attributes = List.empty)),
        attributes = List.empty)),
    className = "",
    attributes = List.empty)


  val pointerEntity = Resource(
    kind = ResourceKind.COLLECTION,
    name = "pointerEntity",
    version = Some(1),
    keyType = "",
    valueType = "",
    mergedType = "org.coursera.naptime.ari.graphql.models.MergedPointerEntity",
    handlers = List(
      Handler(
        kind = HandlerKind.GET,
        name = "get",
        parameters = List(Parameter(name = "id", `type` = "String", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.MULTI_GET,
        name = "multiGet",
        parameters = List(Parameter(name = "ids", `type` = "List[Int]", attributes = List.empty)),
        attributes = List.empty),
      Handler(
        kind = HandlerKind.GET_ALL,
        name = "getAll",
        parameters = List.empty,
        attributes = List.empty)),
    className = "",
    attributes = List.empty)

  val oldPlatformNotAvailableMessageA = "Not Available."
  val oldPlatformNotAvailableMessageB = "Still Not Available."

  val oldCourseIdA = "oldCourseIdA"
  val oldCourseIdB = "oldCourseIdB"

  val originalId = StringMember("originalIdValue")
  val COURSE_A = MergedCourse(
    id = "courseAId",
    name = "Machine Learning",
    slug = "machine-learning",
    description = Some("An awesome course on machine learning."),
    instructorIds = List("instructor1Id", "instructor2Id"),
    partnerId = 123,
    originalId = originalId,
    platformSpecificData = OldPlatformDataMember(OldPlatformData(oldPlatformNotAvailableMessageA, oldCourseIdA)),
    coursePlatform = List(CoursePlatform.NewPlatform),
    arbitraryData = AnyData.build(new DataMap(), DataConversion.SetReadOnly))
  val COURSE_B = MergedCourse(
    id = "courseBId",
    name = "Probabalistic Graphical Models",
    slug = "pgm",
    description = Some("An awesome course on pgm's."),
    instructorIds = List("instructor2Id"),
    partnerId = 123,
    originalId = "",
    platformSpecificData = OldPlatformDataMember(OldPlatformData(oldPlatformNotAvailableMessageB, oldCourseIdB)),
    coursePlatform = List(CoursePlatform.NewPlatform),
    arbitraryData = AnyData.build(new DataMap(), DataConversion.SetReadOnly))
  val COURSES = MergedCourses(courses = List(COURSE_A, COURSE_B))

  val INSTRUCTOR_1 = MergedInstructor(
    id = "instructor1Id",
    name = "Professor X",
    title = "Chair",
    bio = "Professor X's bio",
    courseIds = List(COURSE_A.id),
    partnerId = 123)

  val INSTRUCTOR_2 = MergedInstructor(
    id = "instructor2Id",
    name = "Professor Y",
    title = "Table",
    bio = "Professor Y's bio",
    courseIds = List(COURSE_B.id),
    partnerId = 123)

  val PARTNER_123 = MergedPartner(
    id = 123,
    name = "University X",
    slug = "x-university",
    geolocation = Coordinates(37.386824, -122.061005))

}
