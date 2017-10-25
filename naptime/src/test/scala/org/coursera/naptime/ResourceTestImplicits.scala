package org.coursera.naptime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import org.junit.After

import scala.concurrent.ExecutionContext

trait ResourceTestImplicits {
  private[this] val internalActorSystem: ActorSystem = ActorSystem("test")
  private[this] val internalExecutionContext: ExecutionContext = actorSystem.dispatcher
  private[this] val internalMaterializer: Materializer = ActorMaterializer()

  implicit protected def actorSystem: ActorSystem = internalActorSystem
  implicit protected def executionContext: ExecutionContext = internalExecutionContext
  implicit protected def materializer: Materializer = internalMaterializer

  @After
  def shutDownActorSystem(): Unit = {
    actorSystem.terminate()
  }
}
