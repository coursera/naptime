package org.coursera.naptime.ari.engine

import org.coursera.naptime.actions.NaptimeSerializer
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedCourses
import org.coursera.naptime.courier.CourierFormats
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar

class UtilitiesTest extends AssertionsForJUnit with MockitoSugar {

  @Test
  def getValuesAtPathSimpleStringValue(): Unit = {
    val courseSchema = MergedCourse.SCHEMA
    val course = Models.COURSE_A.data()

    val path = List("name")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = List(Models.COURSE_A.name)
    assert(retrievedValue === expectedValue)
  }

  @Test
  def getValuesAtPathTypeRefValue(): Unit = {
    val courseSchema = MergedCourse.SCHEMA
    val course = Models.COURSE_A.data()

    val path = List("partnerId")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = List(Models.COURSE_A.partnerId.toString)
    assert(retrievedValue === expectedValue)
  }

  @Test
  def getValuesAtPathUnionValue(): Unit = {
    val courseSchema = MergedCourse.SCHEMA
    val course = Models.COURSE_A.data()

    val path = List("originalId")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = List(Models.originalId.value)
    assert(retrievedValue === expectedValue)
  }

  @Test
  def getValuesAtPathUnionWithTypedDefinitionValue(): Unit = {
    val courseSchema = MergedCourse.SCHEMA
    val course = Models.COURSE_A.data()

    val path = List("platformSpecificData", "old", "notAvailableMessage")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = List(Models.oldPlatformNotAvailableMessageA)
    assert(retrievedValue === expectedValue)
  }

  // This tests the case when the Courier model is serialized by Naptime's PlayJSON layer.
  // This is useful when we get untyped JsObjects.
  @Test
  def getValuesAtPathUnionWithNaptimePlayJsonSerializerAndTypedDefinitionValue(): Unit = {
    val courseSchema = MergedCourse.SCHEMA
    val courierFormat = CourierFormats.recordTemplateFormats[MergedCourse]
    val course = NaptimeSerializer.playJsonFormats(courierFormat).serialize(Models.COURSE_A)

    val path = List("platformSpecificData", "old", "notAvailableMessage")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = List(Models.oldPlatformNotAvailableMessageA)
    assert(retrievedValue === expectedValue)
  }

  @Test
  def getValuesAtPathArrayValue(): Unit = {
    val courseSchema = MergedCourse.SCHEMA
    val course = Models.COURSE_A.data()

    val path = List("instructorIds")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = Models.COURSE_A.instructorIds
    assert(retrievedValue === expectedValue)
  }

  @Test
  def getValuesAtPathArrayOfRecordsValue(): Unit = {
    val courseSchema = MergedCourses.SCHEMA
    val course = Models.COURSES.data()

    val path = List("courses", "instructorIds")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue = Models.COURSES.courses.flatMap(_.instructorIds).distinct
    assert(retrievedValue === expectedValue)
  }

  @Test
  def getValuesAtPathValueWithArrayOfTypedDefinitions(): Unit = {
    val courseSchema = MergedCourses.SCHEMA
    val course = Models.COURSES.data()

    val path = List("courses", "platformSpecificData", "old", "notAvailableMessage")
    val retrievedValue = Utilities.getValuesAtPath(course, courseSchema, path)
    val expectedValue =
      List(Models.oldPlatformNotAvailableMessageA, Models.oldPlatformNotAvailableMessageB)
    assert(retrievedValue === expectedValue)

  }
}
