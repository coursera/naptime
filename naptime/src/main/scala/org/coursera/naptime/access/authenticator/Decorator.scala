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

package org.coursera.naptime.access.authenticator

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Augments parsed information [[I]] (using some asynchronous process) returning either
 * output [[O]] or an error.
 *
 * [[Decorator]]s should not throw exceptions. On error, they should return `Left` with an error
 * message suitable for returning to the Naptime client.
 */
trait Decorator[-I, +O] {

  def apply(input: I)(implicit ec: ExecutionContext): Future[Either[String, O]]

  def andThen[O2](other: Decorator[O, O2]): Decorator[I, O2] = {
    val self = this
    new Decorator[I, O2] {
      override def apply(input: I)(implicit ec: ExecutionContext): Future[Either[String, O2]] = {
        for {
          eitherO <- self.apply(input)
          eitherO2 <- eitherO.left
            .map(error => Future.successful(Left(error)))
            .right
            .map(other.apply)
            .merge
        } yield eitherO2
      }
    }
  }

  def map[O2](other: O => O2): Decorator[I, O2] = {
    val self = this
    new Decorator[I, O2] {
      override def apply(input: I)(implicit ec: ExecutionContext): Future[Either[String, O2]] = {
        self.apply(input).map { eitherO =>
          eitherO.right.map(other.apply)
        }
      }
    }
  }

  def flatMap[O2](other: O => Either[String, O2]): Decorator[I, O2] = {
    val self = this
    new Decorator[I, O2] {
      override def apply(input: I)(implicit ec: ExecutionContext): Future[Either[String, O2]] = {
        self.apply(input).map { eitherO =>
          eitherO.right.flatMap(other.apply)
        }
      }
    }
  }
}

object Decorator {

  def identity[I]: Decorator[I, I] = new Decorator[I, I] {
    override def apply(input: I)(implicit ec: ExecutionContext): Future[Either[String, I]] = {
      Future.successful(Right(input))
    }
  }

  /**
   * Build a decorator from a function `f` that doesn't require an [[ExecutionContext]].
   */
  def function[I, O](f: I => Future[Either[String, O]]): Decorator[I, O] =
    new Decorator[I, O] {
      override def apply(input: I)(implicit ec: ExecutionContext): Future[Either[String, O]] = {
        f(input)
      }
    }

}
