package org.coursera.naptime.router2

import akka.stream.Materializer
import com.google.inject.Guice
import com.google.inject.Injector
import org.coursera.naptime.NaptimeModule
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.Ok
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.resources.TopLevelCollectionResource
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import scala.concurrent.ExecutionContext

/**
 * Exercises ResourceInjectionTest via a real Guice injector backed by a bound instance.
 *
 * ResourceInjectionTest provides routerInjection() and resourceInjection() @Test methods
 * that were previously at 0% coverage. Using bind(...).toInstance(...) avoids the need
 * for an @Inject constructor on the resource.
 */
class ResourceInjectionTestImplTest
    extends AssertionsForJUnit
    with ResourceInjectionTest
    with ResourceTestImplicits {

  import ResourceInjectionTestImplTest._

  private val widgetInstance = new WidgetsResource

  object TestModule extends NaptimeModule {
    override def configure(): Unit = {
      bindResource[WidgetsResource]
      bind(classOf[WidgetsResource]).toInstance(widgetInstance)
    }
  }

  override val injector: Injector = Guice.createInjector(TestModule)

  // routerInjection() and resourceInjection() are inherited @Test methods from ResourceInjectionTest.
}

object ResourceInjectionTestImplTest {

  case class Widget(name: String)

  object Widget {
    implicit val jsonFormat: OFormat[Widget] = Json.format[Widget]
  }

  class WidgetsResource(implicit val executionContext: ExecutionContext, val materializer: Materializer)
      extends TopLevelCollectionResource[Int, Widget] {

    override def keyFormat: KeyFormat[Int] = KeyFormat.intKeyFormat

    override implicit def resourceFormat: OFormat[Widget] = Widget.jsonFormat

    override def resourceName: String = "widgets"

    implicit val fields = Fields.withDefaultFields("name")

    def getAll = Nap.getAll { implicit ctx =>
      Ok(List(Keyed(1, Widget("wrench"))))
    }
  }
}
