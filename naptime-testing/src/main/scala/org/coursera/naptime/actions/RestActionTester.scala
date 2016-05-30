package org.coursera.naptime.actions

import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.RestContext
import org.coursera.naptime.RestError
import org.coursera.naptime.RestResponse
import org.coursera.naptime.actions.RestAction
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Future
import scala.util.Try

/**
 * Mix in to resource unit tests to invoke resource actions with `.testAction`.
 */
trait RestActionTester { this: ScalaFutures =>


  /**
   * Unfortunately, `.futureValue` wraps the resulting exception in a `TestFailedException`.
   * Here, we unwrap it, in case tests care about the original exception's type.
   */
  implicit class UnwrappedFutureValue[T](future: Future[T]) {
    def unwrappedFutureValue: T = {
      Try(future.futureValue).recover {
        case e: TestFailedException => e.cause.map(throw _).getOrElse(throw e)
      }.get
    }
  }

  implicit class RestActionTestOps[AuthType, BodyType, ResponseType](
    action: RestAction[_, AuthType, BodyType, _, _, ResponseType]) {

    def testAction(ctx: RestContext[AuthType, BodyType]): RestResponse[ResponseType] = {
      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      val responseFuture = for {
        _ <- Future.successful(())
        _ = action.restAuth.check(ctx.auth).foreach(throw _)
        response <- action.safeApply(ctx)
      } yield response

      responseFuture.recover {
        case e: NaptimeActionException => RestError(e)
      }.unwrappedFutureValue
    }

  }
}
