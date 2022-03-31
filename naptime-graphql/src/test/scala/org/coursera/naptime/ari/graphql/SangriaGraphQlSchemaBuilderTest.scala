package org.coursera.naptime.ari.graphql

import com.google.inject.Injector
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.ResourceName
import org.coursera.naptime.ari.FullSchema
import org.coursera.naptime.ari.LocalSchemaProvider
import org.coursera.naptime.ari.SchemaProvider
import org.coursera.naptime.ari.graphql.models.MergedCourse
import org.coursera.naptime.ari.graphql.models.MergedInstructor
import org.coursera.naptime.ari.graphql.models.MergedPartner
import org.coursera.naptime.ari.graphql.models.RecordWithUnionTypes
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.router2.NaptimeRoutes
import org.coursera.naptime.router2.ResourceRouterBuilder
import org.coursera.naptime.schema.Handler
import org.coursera.naptime.schema.HandlerKind
import org.coursera.naptime.schema.Parameter
import org.coursera.naptime.schema.Resource
import org.coursera.naptime.schema.ResourceKind
import org.junit.Test
import org.mockito.Mockito._
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mockito.MockitoSugar

class SangriaGraphQlSchemaBuilderTest extends AssertionsForJUnit {
  @Test
  def checkEmptySchema(): Unit = {
    val schemaTypes = Map(
      "org.coursera.naptime.ari.graphql.models.MergedCourse" -> MergedCourse.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.FakeModel" -> RecordWithUnionTypes.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.MergedPartner" -> MergedPartner.SCHEMA,
      "org.coursera.naptime.ari.graphql.models.MergedInstructor" -> MergedInstructor.SCHEMA
    )
    val resourcesWithInvalidResource =
      Set(
        Models.courseResource.copy(version = Some(-1)),
        Models.instructorResource,
        Models.partnersResource,
        Models.fakeModelResource
      )
    val builder = new SangriaGraphQlSchemaBuilder(resourcesWithInvalidResource, schemaTypes)
    val schema = builder.generateSchema().data

    val allResources2 =
      Set(
        Models.instructorResource,
        Models.partnersResource,
        Models.fakeModelResource
      )
    val builder2 = new SangriaGraphQlSchemaBuilder(allResources2, schemaTypes)
    val schema2 = builder2.generateSchema().data

    assertResult(schema2.renderPretty)(schema.renderPretty)
  }
}
