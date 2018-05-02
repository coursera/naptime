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

package org.coursera.naptime.access

import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.ResourceTestImplicits
import org.coursera.naptime.access.authenticator.Authenticator
import org.coursera.naptime.access.authenticator.Decorator
import org.coursera.naptime.access.authenticator.HeaderAuthenticationParser
import org.coursera.naptime.access.authenticator.ParseResult
import org.coursera.naptime.access.authorizer.AuthorizeResult
import org.coursera.naptime.access.authorizer.Authorizer
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.http.Status
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

class HeaderAccessControlCombinersTest
    extends AssertionsForJUnit
    with ScalaFutures
    with ResourceTestImplicits {

  import HeaderAccessControlCombinersTest._

  override def spanScaleFactor: Double = 10

  def runEither(
      left: StructuredAccessControl[String] = leftSuccess,
      right: StructuredAccessControl[String] = rightSuccess)(
      checks: Either[NaptimeActionException, Either[String, String]] => Unit): Unit = {
    val either = HeaderAccessControl.eitherOf(left, right)
    val result = either.run(FakeRequest())
    checks(result.futureValue)
  }

  def runAnyOf(
      left: StructuredAccessControl[String] = leftSuccess,
      right: StructuredAccessControl[String] = rightSuccess)(
      checks: Either[NaptimeActionException, (Option[String], Option[String])] => Unit): Unit = {
    val anyOf = HeaderAccessControl.anyOf(left, right)
    val result = anyOf.run(FakeRequest())
    checks(result.futureValue)
  }

  def runAnd(
      left: StructuredAccessControl[String] = leftSuccess,
      right: StructuredAccessControl[String] = rightSuccess)(
      checks: Either[NaptimeActionException, (String, String)] => Unit): Unit = {
    val and = HeaderAccessControl.and(left, right)
    val result = and.run(FakeRequest())
    checks(result.futureValue)
  }

  def runSuccessfulOf(
      left: StructuredAccessControl[String] = leftSuccess,
      right: StructuredAccessControl[String] = rightSuccess)(
      checks: Either[NaptimeActionException, Set[String]] => Unit): Unit = {
    val successfulOf = HeaderAccessControl.successfulOf(List(left, right))
    val result = successfulOf.run(FakeRequest())
    checks(result.futureValue)
  }

  @Test
  def eitherSimple(): Unit = {
    runEither() { result =>
      assert(Right(Right("right")) === result)
    }
  }

  @Test
  def eitherFallbackToRightParseMissing(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.allowed)
    runEither(left) { result =>
      assert(Right(Right("right")) === result)
    }
  }

  @Test
  def eitherFallbackToRightParseError(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseError[String](), Authorizers.allowed)
    runEither(left) { result =>
      assert(Right(Right("right")) === result)
    }
  }

  @Test
  def eitherFallbackToRightUnauthorized(): Unit = {
    runEither(leftDeny) { result =>
      assert(Right(Right("right")) === result)
    }
  }

  @Test
  def eitherFallbackToRightError(): Unit = {
    runEither(leftFail) { result =>
      assert(Right(Right("right")) === result)
    }
  }

  @Test
  def eitherIgnoreRightParseMissing(): Unit = {
    val right = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.allowed)
    runEither(right = right) { result =>
      assert(Right(Left("left")) === result)
    }
  }

  @Test
  def eitherIgnoreRightParseError(): Unit = {
    val right = StructuredAccessControl(Authenticators.parseError[String](), Authorizers.allowed)
    runEither(right = right) { result =>
      assert(Right(Left("left")) === result)
    }
  }

  @Test
  def eitherIgnoreRightUnauthorized(): Unit = {
    runEither(right = rightDeny) { result =>
      assert(Right(Left("left")) === result)
    }
  }

  @Test
  def eitherIgnoreRightError(): Unit = {
    runEither(right = rightFail) { result =>
      assert(Right(Left("left")) === result)
    }
  }

  @Test
  def eitherBothMissing(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.allowed)
    val right = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.deny())
    runEither(left, right) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.UNAUTHORIZED)
    }
  }

  @Test
  def eitherBothDenied(): Unit = {
    runEither(leftDeny, rightDeny) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def anyOfSimple(): Unit = {
    runAnyOf() { result =>
      assert(Right((Some("left"), Some("right"))) === result)
    }
  }

  @Test
  def anyOfLeftMissing(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.allowed)
    runAnyOf(left) { result =>
      assert(Right((None, Some("right"))) === result)
    }
  }

  @Test
  def anyOfLeftParseError(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseError[String](), Authorizers.allowed)
    runAnyOf(left) { result =>
      assert(Right((None, Some("right"))) === result)
    }
  }

  @Test
  def anyOfLeftDeny(): Unit = {
    runAnyOf(leftDeny) { result =>
      assert(Right((None, Some("right"))) === result)
    }
  }

  @Test
  def anyOfRightDeny(): Unit = {
    runAnyOf(right = rightDeny) { result =>
      assert(Right((Some("left"), None)) === result)
    }
  }

  @Test
  def anyOfBothDeny(): Unit = {
    runAnyOf(leftDeny, rightDeny) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def anyOfBothSkip(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.allowed)
    val right = StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.allowed)
    runAnyOf(left, right) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.UNAUTHORIZED)
    }
  }

  @Test
  def anyOfFailAndSkip(): Unit = {
    runAnyOf(leftFail, rightDeny) { result =>
      assert(result.isLeft)
    // Behavior is unspecified as to the response code returned.
    }
  }

  @Test
  def anyOfParseErrorAndDeny(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseError[String](), Authorizers.allowed)
    runAnyOf(left, rightDeny) { result =>
      assert(result.isLeft)
    // Behavior is unspecified as to the response code.
    }
  }

  @Test
  def andSimple(): Unit = {
    runAnd() { result =>
      assert(Right("left", "right") === result)
    }
  }

  @Test
  def andLeftDeny(): Unit = {
    runAnd(leftDeny) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def andRightDeny(): Unit = {
    runAnd(right = rightDeny) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def andBothDeny(): Unit = {
    runAnd(leftDeny, rightDeny) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def andDenyAndFail(): Unit = {
    runAnd(leftFail, rightDeny) { result =>
      assert(result.isLeft)
      assert(
        result.left.get.httpCode === Status.FORBIDDEN ||
          result.left.get.httpCode === Status.INTERNAL_SERVER_ERROR)
    }
  }

  @Test
  def andBothFail(): Unit = {
    runAnd(leftFail, rightFail) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.INTERNAL_SERVER_ERROR)
    }
  }

  @Test
  def successfulOfBothSucceed(): Unit = {
    runSuccessfulOf() { result =>
      assertResult(Right(Set("left", "right")))(result)
    }
  }

  @Test
  def successfulOfBothDeny(): Unit = {
    runSuccessfulOf(leftDeny, rightDeny) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def successfulOfBothFail(): Unit = {
    runSuccessfulOf(leftFail, rightFail) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.INTERNAL_SERVER_ERROR)
    }
  }

  @Test
  def successfulOfOneDeny(): Unit = {
    runSuccessfulOf(leftDeny) { result =>
      assertResult(Right(Set("right")))(result)
    }
  }

  @Test
  def successfulOfOneFail(): Unit = {
    runSuccessfulOf(right = rightFail) { result =>
      assertResult(Right(Set("left")))(result)
    }
  }

  @Test
  def complexTest(): Unit = {
    val acceptingParser =
      StructuredAccessControl(Authenticators.constant(true), Authorizers.allowed)
    val notParsing =
      StructuredAccessControl(Authenticators.parseMissing[String], Authorizers.deny())
    val innerEither = HeaderAccessControl.eitherOf(acceptingParser, notParsing)
    val acceptingOuter =
      StructuredAccessControl(Authenticators.constant("foo"), Authorizers.allowed)
    val denyingOuter = StructuredAccessControl(Authenticators.constant("foo"), Authorizers.deny())
    val wrapped = HeaderAccessControl.anyOf(
      HeaderAccessControl.and(innerEither, acceptingOuter),
      HeaderAccessControl.and(innerEither, denyingOuter))
    val result = wrapped.run(FakeRequest()).futureValue
    assert(Right((Some((Left(true), "foo")), None)) === result)
  }
}

object HeaderAccessControlCombinersTest {
  val leftSuccess = StructuredAccessControl(Authenticators.constant("left"), Authorizers.allowed)
  val rightSuccess = StructuredAccessControl(Authenticators.constant("right"), Authorizers.allowed)

  val leftDeny = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
  val rightDeny = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())

  val leftFail = StructuredAccessControl(Authenticators.constant("left"), Authorizers.fail())
  val rightFail = StructuredAccessControl(Authenticators.constant("right"), Authorizers.fail())

  object Authenticators {
    def constant[T](constant: T): Authenticator[T] = {
      Authenticator(HeaderAuthenticationParser.constant(constant), Decorator.identity[T])
    }

    def parseError[T](
        errorMsg: String = "parse error!",
        code: Int = Status.UNAUTHORIZED): Authenticator[T] = {
      val headerParser = new HeaderAuthenticationParser[T] {
        override def parseHeader(requestHeader: RequestHeader): ParseResult[T] = {
          ParseResult.Error(errorMsg)
        }
      }
      Authenticator(headerParser, Decorator.identity[T])
    }

    def parseMissing[T]: Authenticator[T] = {
      val headerParser = new HeaderAuthenticationParser[T] {
        override def parseHeader(requestHeader: RequestHeader): ParseResult[T] = {
          ParseResult.Skip
        }
      }
      Authenticator(headerParser, Decorator.identity[T])
    }
  }

  object Authorizers {
    def allowed[T]: Authorizer[T] = {
      Authorizer(_ => AuthorizeResult.Authorized)
    }

    def deny[T](msg: String = "denied!"): Authorizer[T] = {
      Authorizer(_ => AuthorizeResult.Rejected(msg))
    }

    def fail[T](msg: String = "failed!"): Authorizer[T] = {
      Authorizer(_ => AuthorizeResult.Failed(msg))
    }
  }
}
