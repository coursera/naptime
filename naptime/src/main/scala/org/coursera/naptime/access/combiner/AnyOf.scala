package org.coursera.naptime.access.combiner

import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.HeaderAccessControl
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Allows the request if at least one of access controls allow it.
 *
 * The authentication data are exposed as a tuple containing [[Option]]s where at least one
 * is defined.
 */
private[access] trait AnyOf {

  /** See [[AnyOf]]. */
  def anyOf[A, B](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B]):
    HeaderAccessControl[(Option[A], Option[B])] = {

    new HeaderAccessControl[(Option[A], Option[B])] {
      override def run(requestHeader: RequestHeader)(implicit ec: ExecutionContext):
        Future[Either[NaptimeActionException, (Option[A], Option[B])]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
        } yield {
          (resultA, resultB) match {
            case (Left(errorA), Left(_)) => Left(errorA)  // Ignore the other error.
            case _ =>
              Right((resultA.right.toOption, resultB.right.toOption))
          }
        }
      }
    }
  }

  /** See [[AnyOf]]. */
  def anyOf[A, B, C](
      controlA: HeaderAccessControl[A],
      controlB: HeaderAccessControl[B],
      controlC: HeaderAccessControl[C]):
    HeaderAccessControl[(Option[A], Option[B], Option[C])] = {

    new HeaderAccessControl[(Option[A], Option[B], Option[C])] {
      override def run(requestHeader: RequestHeader)(implicit ec: ExecutionContext):
        Future[Either[NaptimeActionException, (Option[A], Option[B], Option[C])]] = {

        val futureA = Futures.safelyCall(controlA.run(requestHeader))
        val futureB = Futures.safelyCall(controlB.run(requestHeader))
        val futureC = Futures.safelyCall(controlC.run(requestHeader))

        for {
          resultA <- futureA
          resultB <- futureB
          resultC <- futureC
        } yield {
          (resultA, resultB, resultC) match {
            case (Left(errorA), Left(_), Left(_)) => Left(errorA)  // Ignore the other errors.
            case _ =>
              Right((resultA.right.toOption, resultB.right.toOption, resultC.right.toOption))
          }
        }
      }
    }
  }

}
