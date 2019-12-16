package org.coursera.naptime

import java.util.Date
import javax.inject.Inject

import akka.stream.Materializer
import com.google.inject.Guice
import com.google.inject.Stage
import com.linkedin.data.schema.DataSchema
import com.linkedin.data.schema.DataSchemaUtil
import com.linkedin.data.schema.PrimitiveDataSchema
import com.linkedin.data.schema.RecordDataSchema
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.coursera.naptime.router2.NaptimeRoutes
import org.junit.Test
import org.mockito.Mockito.mock
import org.scalatest.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import scala.concurrent.ExecutionContext

object NaptimeModuleTest {
  case class User(name: String, createdAt: Date)
  object User {
    implicit val oFormat: OFormat[User] = Json.format[User]
  }
  class MyResource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
      extends TopLevelCollectionResource[String, User] {
    override implicit def resourceFormat: OFormat[User] = User.oFormat
    override def keyFormat: KeyFormat[KeyType] = KeyFormat.stringKeyFormat
    override def resourceName: String = "myResource"
    implicit val fields = Fields

    def get(id: String) = Nap.get(ctx => ???)
  }
  object MyFakeModule extends NaptimeModule {
    override def configure(): Unit = {
      bindResource[MyResource]
      bind[MyResource].toInstance(mock(classOf[MyResource]))
      bindSchemaType[Date](DataSchemaUtil.dataSchemaTypeToPrimitiveDataSchema(DataSchema.Type.LONG))
    }
  }

  class OverrideTypesHelper @Inject()(val schemaOverrideTypes: NaptimeModule.SchemaTypeOverrides)
}

class NaptimeModuleTest extends AssertionsForJUnit {
  import NaptimeModuleTest._

  /**
   * Check to ensure that configured type schema overrides are appropriately set.
   */
  @Test
  def checkInferredOverrides(): Unit = {
    val injector = Guice.createInjector(Stage.DEVELOPMENT, MyFakeModule, NaptimeModule)
    val overrides = injector.getInstance(classOf[OverrideTypesHelper])
    assert(overrides.schemaOverrideTypes.size === 1)
    assert(overrides.schemaOverrideTypes.contains("java.util.Date"))
  }

  @Test
  def checkComputedOverrides(): Unit = {
    val injector = Guice.createInjector(Stage.DEVELOPMENT, MyFakeModule, NaptimeModule)
    val overrides = injector.getInstance(classOf[OverrideTypesHelper])
    val routes = injector.getInstance(classOf[NaptimeRoutes])
    assert(1 === routes.routerBuilders.size)
    val routerBuilder = routes.routerBuilders.head
    val inferredSchemaKeyed =
      routerBuilder.types.find(_.key == "org.coursera.naptime.NaptimeModuleTest.User").get
    assert(inferredSchemaKeyed.value.isInstanceOf[RecordDataSchema])
    val userSchema = inferredSchemaKeyed.value.asInstanceOf[RecordDataSchema]
    assert(2 === userSchema.getFields.size())
    val initialCreatedAtSchema = userSchema.getField("createdAt").getType.getDereferencedDataSchema
    assert(initialCreatedAtSchema.isInstanceOf[RecordDataSchema])
    assert(
      initialCreatedAtSchema
        .asInstanceOf[RecordDataSchema]
        .getDoc
        .contains("Unable to infer schema"))
    SchemaUtils.fixupInferredSchemas(userSchema, overrides.schemaOverrideTypes)
    val fixedCreatedAtSchema = userSchema.getField("createdAt").getType.getDereferencedDataSchema
    assert(fixedCreatedAtSchema.isInstanceOf[PrimitiveDataSchema])
  }
}
