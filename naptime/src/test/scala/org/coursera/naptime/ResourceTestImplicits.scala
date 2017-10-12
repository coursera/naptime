package org.coursera.naptime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import org.junit.After

import scala.concurrent.ExecutionContext

trait ResourceTestImplicits {
  implicit protected val actorSystem: ActorSystem = ActorSystem()
  implicit protected val executionContext: ExecutionContext = actorSystem.dispatcher
  implicit protected val materializer: Materializer = ActorMaterializer()

  @After
  def shutDownActorSystem(): Unit = {
    actorSystem.terminate()
  }
}
