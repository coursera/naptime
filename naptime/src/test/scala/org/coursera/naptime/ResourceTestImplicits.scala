package org.coursera.naptime

import java.io.File

import org.junit.After
import play.api.Application
import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder

trait ResourceTestImplicits {

  implicit val application: Application = GuiceApplicationBuilder()
    .in(new File("."))
    .in(Mode.Test)
    .build()

  implicit val ec = application.actorSystem.dispatcher
  implicit val materializer = application.materializer

  @After
  def shutDownActorSystem(): Unit = {
    application.stop()
  }
}
