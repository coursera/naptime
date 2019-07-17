package org.coursera.naptime

import java.io.File

import org.junit.After
import play.api.Application
import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder

trait ResourceTestImplicits {
  implicit protected val application: Application = ResourceTestImplicits.application
  @After
  def shutDownActorSystem(): Unit = {
    application.stop()
  }
}

object ResourceTestImplicits {

  val application: Application = GuiceApplicationBuilder()
    .in(new File("."))
    .in(Mode.Test)
    .build()

}
