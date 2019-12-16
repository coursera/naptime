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

import org.coursera.naptime.ResourceTestImplicits
import org.junit.Test
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Future

class DecoratorTest extends AssertionsForJUnit with ScalaFutures with ResourceTestImplicits {

  @Test
  def identity(): Unit = {
    assert(Right("a") === Decorator.identity("a").futureValue)
  }

  @Test
  def simple(): Unit = {
    val decorator = Decorator.function[String, Int] { a =>
      Future.successful(Right(a.length))
    }
    assert(Right(3) === decorator("abc").futureValue)
  }

  @Test
  def composition(): Unit = {
    val decorator1 = Decorator.function[String, Int](a => Future.successful(Right(a.length)))
    val decorator2 = Decorator.function[Int, Int](b => Future.successful(Right(b + 2)))
    assert(Right(5) === decorator1.andThen(decorator2)("abc").futureValue)
  }

  @Test
  def compositionFailingFirst(): Unit = {
    val decorator1 =
      Decorator.function[String, Int](a => Future.failed(new RuntimeException("err")))
    val decorator2 = Decorator.function[Int, Int](i => Future.successful(Right(i + 3)))
    val result = decorator1.andThen(decorator2)("abc")
    try {
      result.futureValue
      fail("Should have thrown an exception!")
    } catch {
      case e: RuntimeException =>
      // pass
    }
  }

  @Test
  def compositionFailingFirstLeft(): Unit = {
    var failTest = false
    val decorator1 = Decorator.function[String, Int](a => Future.successful(Left("err")))
    val decorator2 = Decorator.function[Int, Int] { i =>
      failTest = true // We shouldn't be called.
      Future.successful(Right(i + 3))
    }
    val result = decorator1.andThen(decorator2)("abc")
    assert(Left("err") === result.futureValue)
    assert(!failTest)

  }

  @Test
  def compositionFailingSecond(): Unit = {
    val decorator1 = Decorator.function[String, Int](a => Future.successful(Right(a.length)))
    val decorator2 = Decorator.function[Int, Int](_ => Future.successful(Left("error")))
    val result = decorator1.andThen(decorator2)("abc")
    assert(Left("error") === result.futureValue)
  }

  @Test
  def map(): Unit = {
    val decorator = Decorator.identity[String].map(x => x.length)
    assert(Right(3) === decorator("abc").futureValue)
  }

  @Test
  def flatMap(): Unit = {
    val decorator = Decorator.identity[String].flatMap(x => Right(x.length))
    assert(Right(3) === decorator("abc").futureValue)
  }

  @Test
  def flatMapLeft(): Unit = {
    val decorator = Decorator.identity[String].flatMap(x => Left("error!"))
    assert(Left("error!") === decorator("abc").futureValue)
  }
}
