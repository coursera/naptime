package org.coursera.naptime.access.authenticator.combiner

import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.authenticator.Authenticator
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[authenticator] trait EitherOf {

  /**
   * Left-leaning combiner. That is, it tries to return each of these, in order:
   *   - Left authenticator's successful result.
   *   - Right authenticator's successful result.
   *   - Skip, if either authenticator skipped.
   *   - Left authenticator's error.
   *   - Right authenticator's error.
   */
  def eitherOf[A, B](
      left: Authenticator[A],
      right: Authenticator[B])
      (implicit ec: ExecutionContext): Authenticator[Either[A, B]] = {
    new Authenticator[Either[A, B]] {
      override def maybeAuthenticate(requestHeader: RequestHeader)(implicit ec: ExecutionContext):
        Future[Option[Either[NaptimeActionException, Either[A, B]]]] = {

        val leftFuture = Authenticator.safelyAuthenticate(left, requestHeader)
        val rightFuture = Authenticator.safelyAuthenticate(right, requestHeader)

        for {
          leftResponse <- leftFuture
          rightResponse <- rightFuture
        } yield {
          (leftResponse, rightResponse) match {
            case (Some(Right(authentication)), _) => Some(Right(Left(authentication)))
            case (_, Some(Right(authentication))) => Some(Right(Right(authentication)))
            case (None, _) | (_, None) => None
            case (Some(Left(error)), _) => Some(Left(error))
            case (_, Some(Left(error))) => Some(Left(error))
          }
        }
      }
    }
  }

}
