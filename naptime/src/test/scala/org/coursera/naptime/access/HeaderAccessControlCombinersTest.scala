package org.coursera.naptime.access

import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.authenticator.Authenticator
import org.coursera.naptime.access.authenticator.Decorator
import org.coursera.naptime.access.authenticator.HeaderAuthenticationParser
import org.coursera.naptime.access.authenticator.ParseResult
import org.coursera.naptime.access.authorizer.AuthorizeResult
import org.coursera.naptime.access.authorizer.Authorizer
import org.junit.Ignore
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit
import play.api.http.Status
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global

class HeaderAccessControlCombinersTest extends AssertionsForJUnit with ScalaFutures {

  import HeaderAccessControlCombinersTest._

  def runEither(
      left: StructuredAccessControl[String] = LEFT,
      right: StructuredAccessControl[String] = RIGHT)
      (checks: Either[NaptimeActionException, Either[String, String]] => Unit): Unit = {
    val either = HeaderAccessControl.eitherOf(left, right)
    val result = either.run(FakeRequest())
    checks(result.futureValue)
  }

  def runAnyOf(
      left: StructuredAccessControl[String] = LEFT,
      right: StructuredAccessControl[String] = RIGHT)
      (checks: Either[NaptimeActionException, (Option[String], Option[String])] => Unit): Unit = {
    val anyOf = HeaderAccessControl.anyOf(left, right)
    val result = anyOf.run(FakeRequest())
    checks(result.futureValue)
  }

  def runAnd(
    left: StructuredAccessControl[String] = LEFT,
    right: StructuredAccessControl[String] = RIGHT)
    (checks: Either[NaptimeActionException, (String, String)] => Unit): Unit = {
    val and = HeaderAccessControl.and(left, right)
    val result = and.run(FakeRequest())
    checks(result.futureValue)
  }

  @Test
  def eitherSimple(): Unit = {
    runEither() { result =>
      assert(Right(Left("left")) === result)
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
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
    runEither(left) { result =>
      assert(Right(Right("right")) === result)
    }
  }

  @Test
  def eitherFallbackToRightError(): Unit = {
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.fail())
    runEither(left) { result =>
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
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runEither(right = right) { result =>
      assert(Right(Left("left")) === result)
    }
  }

  @Test
  def eitherIgnoreRightError(): Unit = {
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.fail())
    runEither(right = right) { result =>
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
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runEither(left, right) { result =>
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
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
    runAnyOf(left) { result =>
      assert(Right((None, Some("right"))) === result)
    }
  }

  @Test
  def anyOfRightDeny(): Unit = {
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnyOf(right = right) { result =>
      assert(Right((Some("left"), None)) === result)
    }
  }

  @Test
  def anyOfBothDeny(): Unit = {
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnyOf(left, right) { result =>
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
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.fail())
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnyOf(left, right) { result =>
      assert(result.isLeft)
      // Behavior is unspecified as to the response code returned.
    }
  }

  @Test
  def anyOfParseErrorAndDeny(): Unit = {
    val left = StructuredAccessControl(Authenticators.parseError[String](), Authorizers.allowed)
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnyOf(left, right) { result =>
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
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
    runAnd(left) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def andRightDeny(): Unit = {
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnd(right = right) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def andBothDeny(): Unit = {
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.deny())
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnd(left, right) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.FORBIDDEN)
    }
  }

  @Test
  def andDenyAndFail(): Unit = {
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.fail())
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.deny())
    runAnd(left, right) { result =>
      assert(result.isLeft)
      assert(
        result.left.get.httpCode === Status.FORBIDDEN ||
          result.left.get.httpCode === Status.INTERNAL_SERVER_ERROR)
    }
  }

  @Test
  def andBothFail(): Unit = {
    val left = StructuredAccessControl(Authenticators.constant("left"), Authorizers.fail())
    val right = StructuredAccessControl(Authenticators.constant("right"), Authorizers.fail())
    runAnd(left, right) { result =>
      assert(result.isLeft)
      assert(result.left.get.httpCode === Status.INTERNAL_SERVER_ERROR)
    }
  }
}

object HeaderAccessControlCombinersTest {
  val LEFT = StructuredAccessControl(Authenticators.constant("left"), Authorizers.allowed)
  val RIGHT = StructuredAccessControl(Authenticators.constant("right"), Authorizers.allowed)

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
