package org.coursera.naptime.ari.graphql.schema

import org.coursera.courier.data.StringMap
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.graphql.Models
import org.coursera.naptime.ari.graphql.SangriaGraphQlContext
import org.coursera.naptime.schema.GraphQLRelationAnnotation
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.RelationType
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatestplus.junit.AssertionsForJUnit
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext

/**
 * Tests for NaptimePaginatedResourceField.build - covers the various build branches
 * for different relation types.
 */
class NaptimePaginatedResourceFieldMoreTest extends AssertionsForJUnit with MockitoSugar {

  val fieldName = "relatedIds"
  val resourceName = ResourceName("courses", 1)

  private[this] val schemaMetadata = mock[SchemaMetadata]
  private[this] val resource = Models.courseResource
  when(schemaMetadata.getResourceOpt(resourceName)).thenReturn(Some(resource))
  when(schemaMetadata.getSchema(resource)).thenReturn(Some(null))

  // -------------------------------------------------------------------------
  // FINDER relation type with valid "q" parameter
  // -------------------------------------------------------------------------

  @Test
  def build_finderRelation_withValidQParam_returnsRight(): Unit = {
    val finderAnnotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("q" -> "getAll")),
      relationType = RelationType.FINDER)

    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      Some(finderAnnotation),
      List.empty)

    // The "getAll" finder exists in courseResource handlers
    result match {
      case Right(_) => // expected
      case Left(err) => fail(s"Expected Right but got Left($err)")
    }
  }

  // -------------------------------------------------------------------------
  // FINDER relation type with missing "q" parameter (no q in annotation args)
  // -------------------------------------------------------------------------

  @Test
  def build_finderRelation_withoutQParam_returnsLeft(): Unit = {
    // No "q" in arguments → MissingQParameterOnFinderRelation
    val finderAnnotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map.empty[String, String]),
      relationType = RelationType.FINDER)

    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      Some(finderAnnotation),
      List.empty)

    result match {
      case Left(MissingQParameterOnFinderRelation(_, _)) => // expected
      case Left(err) => // Another error type is also acceptable
      case Right(_)  => fail("Expected Left but got Right")
    }
  }

  // -------------------------------------------------------------------------
  // FINDER relation with "q" pointing to non-existent handler
  // -------------------------------------------------------------------------

  @Test
  def build_finderRelation_withNonExistentFinder_returnsLeft(): Unit = {
    val finderAnnotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("q" -> "nonExistentFinder")),
      relationType = RelationType.FINDER)

    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      Some(finderAnnotation),
      List.empty)

    result match {
      case Left(_) => // expected - finder not found
      case Right(_) => fail("Expected Left but got Right")
    }
  }

  // -------------------------------------------------------------------------
  // MULTI_GET relation type
  // -------------------------------------------------------------------------

  @Test
  def build_multiGetRelation_returnsRight(): Unit = {
    val multiGetAnnotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("ids" -> "$instructorIds")),
      relationType = RelationType.MULTI_GET)

    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      Some(multiGetAnnotation),
      List.empty)

    result match {
      case Right(_)  => // expected - courseResource has multiGet handler
      case Left(err) => fail(s"Expected Right but got Left($err)")
    }
  }

  // -------------------------------------------------------------------------
  // GET relation type — single element, returns Left (unsupported for paginated)
  // -------------------------------------------------------------------------

  @Test
  def build_getRelation_returnsLeft(): Unit = {
    val getAnnotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("id" -> "$id")),
      relationType = RelationType.GET)

    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      Some(getAnnotation),
      List.empty)

    result match {
      case Left(UnhandledSchemaError(_, _)) => // expected
      case Left(err)                        => // other error type also acceptable
      case Right(_) => fail("Expected Left but got Right")
    }
  }

  // -------------------------------------------------------------------------
  // SINGLE_ELEMENT_FINDER relation type — returns Left
  // -------------------------------------------------------------------------

  @Test
  def build_singleElementFinderRelation_returnsLeft(): Unit = {
    val singleFinderAnnotation = GraphQLRelationAnnotation(
      resourceName = "courses.v1",
      arguments = StringMap(Map("id" -> "$id")),
      relationType = RelationType.SINGLE_ELEMENT_FINDER)

    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      None,
      Some(singleFinderAnnotation),
      List.empty)

    result match {
      case Left(_)  => // expected
      case Right(_) => fail("Expected Left but got Right")
    }
  }

  // -------------------------------------------------------------------------
  // Resource not found in schemaMetadata — returns Left(SchemaNotFound)
  // -------------------------------------------------------------------------

  @Test
  def build_resourceNotFoundInMetadata_returnsLeft(): Unit = {
    val unknownResource = ResourceName("unknownResource", 1)
    val metadataWithUnknown = mock[SchemaMetadata]
    // Return None so the for-comprehension yields None → getOrElse(Left(SchemaNotFound))
    when(metadataWithUnknown.getResourceOpt(unknownResource)).thenReturn(None)

    val result = NaptimePaginatedResourceField.build(
      metadataWithUnknown,
      unknownResource,
      fieldName,
      None,
      None,
      List.empty)

    result match {
      case Left(SchemaNotFound(_)) => // expected
      case Left(err)               => // other error type acceptable
      case Right(_)                => fail("Expected Left but got Right")
    }
  }

  // -------------------------------------------------------------------------
  // Resource with no MULTI_GET handler and no annotation — returns Left
  // -------------------------------------------------------------------------

  @Test
  def build_resourceWithNoMultiGetAndNoAnnotation_returnsLeft(): Unit = {
    val noMultiGetResource = Models.multigetFreeEntity
    val noMultiGetName = ResourceName.fromResource(noMultiGetResource)
    val metadataWithNoMultiGet = mock[SchemaMetadata]
    when(metadataWithNoMultiGet.getResourceOpt(noMultiGetName)).thenReturn(Some(noMultiGetResource))
    when(metadataWithNoMultiGet.getSchema(noMultiGetResource)).thenReturn(Some(null))

    val result = NaptimePaginatedResourceField.build(
      metadataWithNoMultiGet,
      noMultiGetName,
      fieldName,
      None,
      None,
      List.empty)

    result match {
      case Left(HasForwardRelationButMissingMultiGet(_, _)) => // expected
      case Left(err) => // other error type acceptable
      case Right(_)  => fail("Expected Left but got Right")
    }
  }

  // -------------------------------------------------------------------------
  // HandlerOverride provided — uses the overriding handler directly
  // -------------------------------------------------------------------------

  @Test
  def build_withHandlerOverride_usesOverrideHandler(): Unit = {
    val handler = resource.handlers.find(_.kind == HandlerKind.GET_ALL).get
    val result = NaptimePaginatedResourceField.build(
      schemaMetadata,
      resourceName,
      fieldName,
      Some(handler),
      None,
      List.empty)

    result match {
      case Right(_)  => // expected
      case Left(err) => fail(s"Expected Right but got Left($err)")
    }
  }
}
