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

package org.coursera.naptime.actions

import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Tests for all RestActionCategory singleton objects (each was at 0% coverage).
 */
class RestActionCategoryTest extends AssertionsForJUnit {

  @Test
  def getRestActionCategory_name(): Unit = {
    assert(GetRestActionCategory.name === "get")
  }

  @Test
  def getAllRestActionCategory_name(): Unit = {
    assert(GetAllRestActionCategory.name === "getAll")
  }

  @Test
  def multiGetRestActionCategory_name(): Unit = {
    assert(MultiGetRestActionCategory.name === "multiGet")
  }

  @Test
  def createRestActionCategory_name(): Unit = {
    assert(CreateRestActionCategory.name === "create")
  }

  @Test
  def updateRestActionCategory_name(): Unit = {
    assert(UpdateRestActionCategory.name === "update")
  }

  @Test
  def deleteRestActionCategory_name(): Unit = {
    assert(DeleteRestActionCategory.name === "delete")
  }

  @Test
  def patchRestActionCategory_name(): Unit = {
    assert(PatchRestActionCategory.name === "patch")
  }

  @Test
  def finderRestActionCategory_name(): Unit = {
    assert(FinderRestActionCategory.name === "finder")
  }

  @Test
  def actionRestActionCategory_name(): Unit = {
    assert(ActionRestActionCategory.name === "action")
  }

  @Test
  def restActionCategories_areDistinct(): Unit = {
    val categories: Seq[RestActionCategory] = Seq(
      GetRestActionCategory,
      GetAllRestActionCategory,
      MultiGetRestActionCategory,
      CreateRestActionCategory,
      UpdateRestActionCategory,
      DeleteRestActionCategory,
      PatchRestActionCategory,
      FinderRestActionCategory,
      ActionRestActionCategory)
    assert(categories.map(_.name).distinct.size === categories.size)
  }
}
