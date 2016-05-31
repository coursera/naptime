package org.coursera.naptime.access.combiner

import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.HeaderAccessControl
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Allows the request if all access controls allow it.
 */
private[access] trait And {

  /** See [[And]]. */
  def and[A, B](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B]):
    HeaderAccessControl[(A, B)] = {

    new HeaderAccessControl[(A, B)] {
      override def run(requestHeader: RequestHeader)(implicit ec: ExecutionContext):
        Future[Either[NaptimeActionException, (A, B)]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
        } yield {
          (resultA, resultB) match {
            case (Right(authenticationA), Right(authenticationB)) =>
              Right((authenticationA, authenticationB))
            case _ =>
              List(resultA, resultB)
                .collectFirst { case Left(error) => Left(error) }
                .head
          }
        }
      }
    }
  }

  /** See [[And]]. */
  def and[A, B, C](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B],
      controlC: HeaderAccessControl[C]):
    HeaderAccessControl[(A, B, C)] = {

    new HeaderAccessControl[(A, B, C)] {
      override def run(requestHeader: RequestHeader)(implicit ec: ExecutionContext):
        Future[Either[NaptimeActionException, (A, B, C)]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))
        val futureC = Futures.safelyCall(controlC.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
          resultC <- futureC
        } yield {
          (resultA, resultB, resultC) match {
            case (Right(authenticationA), Right(authenticationB), Right(authenticationC)) =>
              Right((authenticationA, authenticationB, authenticationC))
            case _ =>
              List(resultA, resultB)
                .collectFirst { case Left(error) => Left(error) }
                .head
          }
        }
      }
    }
  }

}
