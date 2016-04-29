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

package org.coursera.naptime

package object actions {

  /**
   * Defines the allowed types of API endpoints.
   *
   * This should not be extended outside the framework, and is thus sealed.
   */
  sealed trait RestActionCategory {
    def name: String
  }

  /**
   * Marker type for the framework-defined APIs.
   */
  sealed trait BasicRestActionCategory extends RestActionCategory

  type GetRestActionCategory = GetRestActionCategory.type
  case object GetRestActionCategory extends BasicRestActionCategory {
    def name = "get"
  }

  type GetAllRestActionCategory = GetAllRestActionCategory.type
  case object GetAllRestActionCategory extends BasicRestActionCategory {
    def name = "getAll"
  }

  type MultiGetRestActionCategory = MultiGetRestActionCategory.type
  case object MultiGetRestActionCategory extends BasicRestActionCategory {
    def name = "multiGet"
  }

  type CreateRestActionCategory = CreateRestActionCategory.type
  case object CreateRestActionCategory extends BasicRestActionCategory {
    def name = "create"
  }

  type UpdateRestActionCategory = UpdateRestActionCategory.type
  case object UpdateRestActionCategory extends BasicRestActionCategory {
    def name = "update"
  }

  type DeleteRestActionCategory = DeleteRestActionCategory.type
  case object DeleteRestActionCategory extends BasicRestActionCategory {
    def name = "delete"
  }

  type PatchRestActionCategory = PatchRestActionCategory.type
  case object PatchRestActionCategory extends BasicRestActionCategory {
    def name = "patch"
  }

  /**
   * Allows search and other finder-type methods.
   */
  type FinderRestActionCategory = FinderRestActionCategory.type
  case object FinderRestActionCategory extends RestActionCategory {
    def name = "finder"
  }

  /**
   * Allows arbitrary context-specific actions.
   */
  type ActionRestActionCategory = ActionRestActionCategory.type
  case object ActionRestActionCategory extends RestActionCategory {
    def name = "action"
  }

}
