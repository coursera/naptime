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

package org.coursera.naptime.access.authenticator.combiner

import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.access.authenticator.Authenticator
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[access] trait NoRestrictions {

  def noRestrictions: Authenticator[Unit] = {
    new Authenticator[Unit] {

      override def maybeAuthenticate(
          requestHeader: RequestHeader)
          (implicit executionContext: ExecutionContext): Future[Option[Either[NaptimeActionException, Unit]]] = {
        Future.successful(Some(Right(Unit)))
      }
    }
  }
}
