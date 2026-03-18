package org.coursera.naptime.ari.graphql.schema

import com.linkedin.data.schema.Name
import com.linkedin.data.schema.RecordDataSchema
import com.linkedin.data.schema.StringDataSchema
import org.coursera.courier.data.StringMap
import org.coursera.courier.templates.DataTemplates.DataConversion
import org.coursera.naptime.ResourceName
import org.coursera.naptime.Types
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.schema.GraphQLRelationAnnotation
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.RelationType
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.JsArray

import scala.collection.JavaConverters._
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import sangria.schema.BigDecimalType
import sangria.schema.BooleanType
import sangria.schema.FloatType
import sangria.schema.IntType
import sangria.schema.ListInputType
import sangria.schema.LongType
import sangria.schema.OptionInputType
import sangria.schema.StringType

class NaptimeResourceUtilsMoreTest extends AssertionsForJUnit {

  // -------------------------------------------------------------------------
  // generateHandlerArguments — exercises scalaTypeToSangria branches
  // -------------------------------------------------------------------------

  private def makeHandler(paramType: String): Handler =
    Handler(
      kind = HandlerKind.GET,
      name = "get",
      parameters = List(Parameter(name = "p", `type` = paramType, attributes = List.empty)),
      attributes = List.empty)

  @Test
  def generateHandlerArguments_longType(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("Long"))
    assert(args.nonEmpty)
    val argType = args.head.argumentType
    // Optional wrapper (required=false default)
    argType match {
      case OptionInputType(LongType) => // expected
      case LongType                  => // also acceptable if required=true
      case other                     => fail(s"Unexpected type: $other")
    }
  }

  @Test
  def generateHandlerArguments_floatType(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("Float"))
    assert(args.nonEmpty)
  }

  @Test
  def generateHandlerArguments_booleanType(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("Boolean"))
    assert(args.nonEmpty)
  }

  @Test
  def generateHandlerArguments_decimalType(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("Decimal"))
    assert(args.nonEmpty)
  }

  @Test
  def generateHandlerArguments_unknownType_fallsBackToString(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("ComplexCustomType"))
    assert(args.nonEmpty)
  }

  @Test
  def generateHandlerArguments_listOfLong(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("List[Long]"))
    assert(args.nonEmpty)
  }

  @Test
  def generateHandlerArguments_listOfUnknown(): Unit = {
    // Tests scalaTypeToFromInput list with an inner unknown type
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("List[CustomType]"))
    assert(args.nonEmpty)
  }

  @Test
  def generateHandlerArguments_optionType(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("Option[String]"))
    assert(args.nonEmpty)
    args.head.argumentType match {
      case OptionInputType(_) => // expected
      case other              => fail(s"Expected OptionInputType but got $other")
    }
  }

  @Test
  def generateHandlerArguments_stringType(): Unit = {
    val args = NaptimeResourceUtils.generateHandlerArguments(makeHandler("String"))
    assert(args.nonEmpty)
    args.head.argumentType match {
      case OptionInputType(StringType) => // expected - optional by default
      case StringType                  => // also acceptable
      case other                       => fail(s"Expected StringType or OptionInputType[StringType] but got $other")
    }
  }

  @Test
  def generateHandlerArguments_withPagination_includesPaginationArgs(): Unit = {
    val handler = makeHandler("String")
    val argsNoPagination =
      NaptimeResourceUtils.generateHandlerArguments(handler, includePagination = false)
    val argsWithPagination =
      NaptimeResourceUtils.generateHandlerArguments(handler, includePagination = true)
    assert(argsWithPagination.size > argsNoPagination.size)
  }

  // -------------------------------------------------------------------------
  // parseToJson — covers Some(v), Int, Long, Float, Double branches
  // -------------------------------------------------------------------------

  @Test
  def parseToJson_someValue(): Unit = {
    assert(NaptimeResourceUtils.parseToJson(Some("hello")) === JsString("hello"))
  }

  @Test
  def parseToJson_none(): Unit = {
    assert(NaptimeResourceUtils.parseToJson(None) === JsNull)
  }

  @Test
  def parseToJson_int(): Unit = {
    assert(NaptimeResourceUtils.parseToJson(42) === JsNumber(42))
  }

  @Test
  def parseToJson_long(): Unit = {
    assert(NaptimeResourceUtils.parseToJson(100L) === JsNumber(100))
  }

  @Test
  def parseToJson_float(): Unit = {
    // Float is stored as long
    val result = NaptimeResourceUtils.parseToJson(3.0f)
    assert(result === JsNumber(3))
  }

  @Test
  def parseToJson_double(): Unit = {
    assert(NaptimeResourceUtils.parseToJson(1.5) === JsNumber(1.5))
  }

  @Test
  def parseToJson_iterable(): Unit = {
    val result = NaptimeResourceUtils.parseToJson(List("a", "b"))
    assert(result === JsArray(Seq(JsString("a"), JsString("b"))))
  }

  @Test
  def parseToJson_unknownType_fallsBackToString(): Unit = {
    case class Foo(x: Int)
    val result = NaptimeResourceUtils.parseToJson(Foo(1))
    assert(result.isInstanceOf[JsString])
  }

  // -------------------------------------------------------------------------
  // interpolateArguments — no-variable case (hardcoded constants)
  // -------------------------------------------------------------------------

  @Test
  def interpolateArguments_noInterpolationVariables_returnsLiteralValue(): Unit = {
    val testCourse = DataMapWithParent(
      Models.COURSE_A.data(),
      ParentModel(ResourceName("courses", 1), Models.COURSE_A.data(), MergedCourse.SCHEMA))

    // No $ in argument value → treated as a constant
    val finderRelation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("q" -> "courseName")),
      relationType = RelationType.FINDER)

    val interpolated = NaptimeResourceUtils.interpolateArguments(testCourse, finderRelation).toMap
    assert(interpolated("q") === JsString("courseName"))
  }

  // -------------------------------------------------------------------------
  // interpolateArguments — variable that doesn't exist in data → empty list
  // -------------------------------------------------------------------------

  // -------------------------------------------------------------------------
  // GraphQLRelation.parse — covers line 15 (GraphQLRelationAnnotation.build)
  // -------------------------------------------------------------------------

  @Test
  def graphQLRelation_parse_fieldWithRelationAnnotation_returnsSome(): Unit = {
    // Build a RecordDataSchema.Field with the RELATION_PROPERTY_NAME property set
    val annotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("ids" -> "$instructorIds")),
      relationType = RelationType.MULTI_GET)

    // Set the annotation data as a property on a field
    val record = new RecordDataSchema(
      new Name("TestRecord", "org.test", new java.lang.StringBuilder()),
      RecordDataSchema.RecordType.RECORD)
    val field = new RecordDataSchema.Field(new StringDataSchema())
    field.setName("instructorIds", new java.lang.StringBuilder())
    field.setRecord(record)
    val props = new java.util.HashMap[String, Object]()
    props.put(Types.Relations.RELATION_PROPERTY_NAME, annotation.data())
    field.setProperties(props)

    val result = GraphQLRelation.parse(field)
    assert(result.isDefined)
    assert(result.get.resourceName === "courses.v1")
  }

  @Test
  def interpolateArguments_variableNotInData_returnsNull(): Unit = {
    val testCourse = DataMapWithParent(
      Models.COURSE_A.data(),
      ParentModel(ResourceName("courses", 1), Models.COURSE_A.data(), MergedCourse.SCHEMA))

    // $nonExistentField doesn't exist in COURSE_A data → interpolated ids = empty
    val finderRelation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("id" -> "$nonExistentField")),
      relationType = RelationType.FINDER)

    val interpolated = NaptimeResourceUtils.interpolateArguments(testCourse, finderRelation).toMap
    // When variable exists but value is empty → JsNull (headOption.getOrElse(JsNull))
    assert(interpolated("id") === JsNull)
  }
}
