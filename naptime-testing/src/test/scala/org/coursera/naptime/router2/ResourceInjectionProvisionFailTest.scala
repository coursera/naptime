package org.coursera.naptime.router2

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provider
import com.google.inject.Stage
import org.coursera.naptime.ResourceTestImplicits
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Exercises the ProvisionException branch in ResourceInjectionTest.resourceInjection().
 */
class ResourceInjectionProvisionFailTest
    extends AssertionsForJUnit
    with ResourceInjectionTest
    with ResourceTestImplicits {

  import ResourceInjectionProvisionFailTest._

  object FailingModule extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[NaptimePlayRouter]).toProvider(classOf[FailingNaptimePlayRouterProvider])
    }
  }

  override val injector: Injector = Guice.createInjector(Stage.DEVELOPMENT, FailingModule)
}

object ResourceInjectionProvisionFailTest {
  // Must be a static (top-level or nested in an object) class for Guice injection
  class FailingNaptimePlayRouterProvider extends Provider[NaptimePlayRouter] {
    override def get(): NaptimePlayRouter =
      throw new RuntimeException("Intentional provisioning failure for coverage")
  }
}
