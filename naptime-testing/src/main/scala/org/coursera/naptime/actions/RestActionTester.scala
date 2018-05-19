/*
 * Copyright 2016 Coursera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coursera.naptime.actions

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.QueryFields
import org.coursera.naptime.QueryIncludes
import org.coursera.naptime.RequestEvidence
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.RestContext
import org.coursera.naptime.RestError
import org.coursera.naptime.RestResponse
import org.junit.After
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 * Mix in to resource unit tests to invoke resource actions with `.testAction`.
 */
trait RestActionTester { this: ScalaFutures =>
  private[this] val internalActorSystem: ActorSystem = ActorSystem("test")
  private[this] val internalExecutionContext: ExecutionContext = actorSystem.dispatcher
  private[this] val internalMaterializer: Materializer = ActorMaterializer()

  implicit protected def actorSystem: ActorSystem = internalActorSystem
  implicit protected def executionContext: ExecutionContext = internalExecutionContext
  implicit protected def materializer: Materializer = internalMaterializer

  @After
  def shutDownActorSystem(): Unit = {
    internalActorSystem.terminate()
  }

  /**
   * Allow access to the request to facilitate testing.
   */
  protected[this] implicit def requestEvidence: RequestEvidence = RequestEvidence

  protected[this] def buildRestContext[AuthType, BodyType](
      auth: AuthType,
      body: BodyType,
      request: FakeRequest[BodyType],
      paging: RequestPagination,
      fields: String = "",
      includes: String = ""): RestContext[AuthType, BodyType] = {
    new RestContext(
      body,
      auth,
      request,
      paging,
      QueryIncludes(includes).get,
      QueryFields(fields).get)
  }

  /**
   * Add an extra `.testAction` method to [[org.coursera.naptime.actions.RestAction]] to make testing
   * easier.
   */
  protected[this] implicit class RestActionTestOps[AuthType, BodyType, ResponseType](
      action: RestAction[_, AuthType, BodyType, _, _, ResponseType]) {

    def testAction(ctx: RestContext[AuthType, BodyType]): RestResponse[ResponseType] = {
      val updatedAuthEither = action.restAuthGenerator.apply(ctx.body).check(ctx.auth)

      updatedAuthEither match {
        case Left(error) => RestError(error)
        case Right(updatedAuth) =>
          val responseFuture = action.safeApply(ctx.copyWithAuth(updatedAuth)).recover {
            case e: NaptimeActionException => RestError(e)
          }

          Try(responseFuture.futureValue).recover {
            case e: TestFailedException => e.cause.map(throw _).getOrElse(throw e)
          }.get
      }
    }

    def testActionPassAuth(ctx: RestContext[AuthType, BodyType]): RestResponse[ResponseType] = {
      val responseFuture = action.safeApply(ctx).recover {
        case e: NaptimeActionException => RestError(e)
      }

      Try(responseFuture.futureValue).recover {
        case e: TestFailedException => e.cause.map(throw _).getOrElse(throw e)
      }.get
    }
  }
}
