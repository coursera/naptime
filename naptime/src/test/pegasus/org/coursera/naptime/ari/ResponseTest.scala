package org.coursera.naptime.ari

import com.linkedin.data.DataList
import com.linkedin.data.DataMap
import org.coursera.naptime.ResourceName
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString

import scala.collection.JavaConverters._

class ResponseTest extends AssertionsForJUnit {

  @Test
  def emptyMerges(): Unit = {
    val topLevelRequest = TopLevelRequest(ResourceName("courses", 1), RequestField(
      name = "search",
      alias = None,
      args = Set("query" -> JsString("machine learning")),
      selections = List.empty))
    val topLevelDataList = new DataList(List("abc").asJava)
    val response = Response(
      topLevelIds = Map(topLevelRequest -> topLevelDataList),
      data = Map(topLevelRequest.resource -> Map(
        "abc" -> new DataMap(Map("id" -> "abc", "slug" -> "machine-learning").asJava))))

    val merged = Response.empty ++ response

    assert(1 === merged.topLevelIds.size)
    assert(merged.topLevelIds.contains(topLevelRequest))
    val topLevelResponse = merged.topLevelIds(topLevelRequest)
    assert(1 === topLevelResponse.size())
    assert("abc" === topLevelResponse.get(0))

    assert(1 === merged.data.size)
    assert(merged.data.contains(topLevelRequest.resource))
    val coursesData = merged.data(topLevelRequest.resource)
    assert(1 === coursesData.size)
    assert(coursesData.contains("abc"))
    val abcDataMap = coursesData("abc")
    assert(2 === abcDataMap.size())
    assert(abcDataMap.containsKey("id"))
    assert(abcDataMap.containsKey("slug"))

    val merged2 = response ++ Response.empty
    assert(merged === merged2)
  }

  @Test
  def mergeMultipleUnrelated(): Unit = {
    val topLevelRequestCourses = TopLevelRequest(ResourceName("courses", 1), RequestField(
      name = "search",
      alias = None,
      args = Set("query" -> JsString("machine learning")),
      selections = List.empty))
    val topLevelDataListCourses = new DataList(List("abc").asJava)
    val responseCourses = Response(
      topLevelIds = Map(topLevelRequestCourses -> topLevelDataListCourses),
      data = Map(topLevelRequestCourses.resource -> Map(
        "abc" -> new DataMap(Map("id" -> "abc", "slug" -> "machine-learning").asJava))))

    val topLevelRequestInstructors = TopLevelRequest(ResourceName("instructors", 3), RequestField(
      name = "get",
      alias = None,
      args = Set("id" -> JsNumber(123)),
      selections = List.empty))
    val topLevelDataListInstructors = new DataList(List(new Integer(123)).asJava)
    val responseInstructors = Response(
      topLevelIds = Map(topLevelRequestInstructors -> topLevelDataListInstructors),
      data = Map(topLevelRequestInstructors.resource -> Map(
        new Integer(123) -> new DataMap(Map("id" -> new Integer(123), "name" -> "Professor X").asJava))))

    val merged = Response.empty ++ responseCourses ++ responseInstructors

    assert(2 === merged.topLevelIds.size)
    assert(merged.topLevelIds.contains(topLevelRequestCourses))
    assert(merged.topLevelIds.contains(topLevelRequestInstructors))
    val topLevelResponseCourses = merged.topLevelIds(topLevelRequestCourses)
    assert(1 === topLevelResponseCourses.size())
    assert("abc" === topLevelResponseCourses.get(0))
    val topLevelResponseInstructors = merged.topLevelIds(topLevelRequestInstructors)
    assert(1 === topLevelResponseInstructors.size())
    assert(new Integer(123) === topLevelResponseInstructors.get(0))

    assert(2 === merged.data.size)
    assert(merged.data.contains(topLevelRequestCourses.resource))
    val coursesData = merged.data(topLevelRequestCourses.resource)
    assert(merged.data.contains(topLevelRequestInstructors.resource))
    assert(1 === coursesData.size)
    assert(coursesData.contains("abc"))
    val abcDataMap = coursesData("abc")
    assert(2 === abcDataMap.size())
    assert(abcDataMap.containsKey("id"))
    assert(abcDataMap.containsKey("slug"))

    val instructorsData = merged.data(topLevelRequestInstructors.resource)
    assert(1 === instructorsData.size)
    assert(instructorsData.contains(new Integer(123)))
    val instructor123DataMap = instructorsData(new Integer(123))
    assert(2 === instructor123DataMap.size())
    assert(instructor123DataMap.containsKey("id"))
    assert(instructor123DataMap.containsKey("name"))
  }

  @Test
  def mergedMultipleSingleResourceResponses(): Unit = {
    val topLevelRequest1 = TopLevelRequest(ResourceName("courses", 1), RequestField(
      name = "search",
      alias = None,
      args = Set("query" -> JsString("machine learning")),
      selections = List.empty))
    val topLevelDataList1 = new DataList(List("abc").asJava)
    val response1 = Response(
      topLevelIds = Map(topLevelRequest1 -> topLevelDataList1),
      data = Map(topLevelRequest1.resource -> Map(
        "abc" -> new DataMap(Map("id" -> "abc", "slug" -> "machine-learning").asJava))))

    val topLevelRequest2 = TopLevelRequest(ResourceName("courses", 1), RequestField(
      name = "get",
      alias = None,
      args = Set("id" -> JsString("xyz")),
      selections = List.empty))
    val topLevelDataList2 = new DataList(List("xyz").asJava)
    val response2 = Response(
      topLevelIds = Map(topLevelRequest2 -> topLevelDataList2),
      data = Map(topLevelRequest2.resource -> Map(
        "xyz" -> new DataMap(Map("id" -> "xyz", "slug" -> "pgm").asJava))))

    val merged = response1 ++ response2

    assert(2 === merged.topLevelIds.size)
    assert(merged.topLevelIds.contains(topLevelRequest1))
    val topLevelResponse1 = merged.topLevelIds(topLevelRequest1)
    assert(1 === topLevelResponse1.size())
    assert("abc" === topLevelResponse1.get(0))
    assert(merged.topLevelIds.contains(topLevelRequest2))
    val topLevelResponse2 = merged.topLevelIds(topLevelRequest2)
    assert(1 === topLevelResponse2.size())
    assert("xyz" === topLevelResponse2.get(0))

    assert(1 === merged.data.size)
    assert(merged.data.contains(topLevelRequest1.resource))
    assert(merged.data.contains(topLevelRequest2.resource))
    val coursesData = merged.data(topLevelRequest1.resource)
    assert(2 === coursesData.size)
    assert(coursesData.contains("abc"))
    val abcDataMap = coursesData("abc")
    assert(2 === abcDataMap.size())
    assert(abcDataMap.containsKey("id"))
    assert(abcDataMap.containsKey("slug"))
    assert(coursesData.contains("xyz"))
    val xyzDataMap = coursesData("xyz")
    assert(2 === xyzDataMap.size())
    assert(xyzDataMap.containsKey("id"))
    assert(xyzDataMap.containsKey("slug"))
  }

  @Test
  def mergeOverlappingResponses(): Unit = {
    val topLevelRequest1 = TopLevelRequest(ResourceName("courses", 1), RequestField(
      name = "search",
      alias = None,
      args = Set("query" -> JsString("machine learning")),
      selections = List.empty))
    val topLevelDataList1 = new DataList(List("abc").asJava)
    val response1 = Response(
      topLevelIds = Map(topLevelRequest1 -> topLevelDataList1),
      data = Map(topLevelRequest1.resource -> Map(
        "abc" -> new DataMap(Map("id" -> "abc", "slug" -> "machine-learning").asJava))))

    val topLevelRequest2 = TopLevelRequest(ResourceName("courses", 1), RequestField(
      name = "get",
      alias = None,
      args = Set("id" -> JsString("abc")),
      selections = List.empty))
    val topLevelDataList2 = new DataList(List("abc").asJava)
    val response2 = Response(
      topLevelIds = Map(topLevelRequest2 -> topLevelDataList2),
      data = Map(topLevelRequest2.resource -> Map(
        "abc" -> new DataMap(Map("id" -> "abc", "slug" -> "machine-learning").asJava))))

    val merged = response1 ++ response2

    assert(2 === merged.topLevelIds.size)
    assert(merged.topLevelIds.contains(topLevelRequest1))
    val topLevelResponse1 = merged.topLevelIds(topLevelRequest1)
    assert(1 === topLevelResponse1.size())
    assert("abc" === topLevelResponse1.get(0))
    assert(merged.topLevelIds.contains(topLevelRequest2))
    val topLevelResponse2 = merged.topLevelIds(topLevelRequest2)
    assert(1 === topLevelResponse2.size())
    assert("abc" === topLevelResponse2.get(0))

    assert(1 === merged.data.size)
    assert(topLevelRequest1.resource === topLevelRequest2.resource)
    assert(merged.data.contains(topLevelRequest1.resource))
    val coursesData = merged.data(topLevelRequest1.resource)
    assert(1 === coursesData.size)
    assert(coursesData.contains("abc"))
    val abcDataMap = coursesData("abc")
    assert(2 === abcDataMap.size())
    assert(abcDataMap.containsKey("id"))
    assert(abcDataMap.containsKey("slug"))
  }
}
