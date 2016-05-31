package org.coursera.naptime.access.combiner

import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.HeaderAccessControl
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[access] trait EitherOf {

  /**
   * Left-leaning combiner. That is, it tries to return each of these, in order:
   *   - Left access control's successful result.
   *   - Right access control's successful result.
   *   - Left access control's error.
   *   - Right access control's error.
   */
  def eitherOf[A, B](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B]):
    HeaderAccessControl[Either[A, B]] = {

    new HeaderAccessControl[Either[A, B]] {
      override def run(
          requestHeader: RequestHeader)
          (implicit ec: ExecutionContext):
        Future[Either[NaptimeActionException, Either[A, B]]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
        } yield {
          (resultA, resultB) match {
            case (Right(authentication), _) => Right(Left(authentication))
            case (_, Right(authentication)) => Right(Right(authentication))
            case (Left(error), _) => Left(error)
            case (_, Left(error)) => Left(error)
          }
        }
      }
    }
  }

}
