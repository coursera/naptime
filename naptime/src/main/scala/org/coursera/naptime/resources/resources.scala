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

package org.coursera.naptime.resources

import akka.stream.Materializer
import org.coursera.courier.templates.ScalaRecordTemplate
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime._
import org.coursera.naptime.access.HeaderAccessControl
import org.coursera.naptime.actions.RestActionBuilder
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.path.CollectionResourcePathParser
import org.coursera.naptime.path.NestedPathKeyParser
import org.coursera.naptime.path.ParsedPathKey
import org.coursera.naptime.path.PathKeyParser
import org.coursera.naptime.path.RootParsedPathKey
import org.coursera.naptime.path.RootPathParser
import org.coursera.naptime.path.:::
import org.coursera.naptime.path.UrlParseResult
import play.api.libs.json.OFormat
import play.api.mvc.AnyContent
import play.api.mvc.BodyParsers

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

/**
 * Base Resource trait: mostly a marker trait wrapper methods for model serialization
 */
trait Resource[M] {
  implicit def resourceFormat: OFormat[M]

  def resourceName: String
  def resourceVersion: Int = 1

  def Fields: Fields[M] = org.coursera.naptime.Fields[M](resourceFormat)

  type PathKey <: ParsedPathKey
  type PathParser <: PathKeyParser[PathKey]
  private[naptime] def pathParser: PathParser
}

/**
 * The root resource for nesting purposes.
 */
sealed trait RootResource extends Resource[Unit] {
  override type PathKey = RootParsedPathKey
  override type PathParser = RootPathParser
  private[naptime] def pathParser = RootPathParser
  def resourceFormat: OFormat[Unit] =
    ??? // TODO(saeta): remove this from here for courier-engines
  override def resourceName: String = ""
}
object RootResource extends RootResource

/**
 * Defines methods that collection resources can implement
 */
trait CollectionResource[ParentResource <: Resource[_], K, M] extends Resource[M] {
  type KeyType = K

  /**
   * Provide a default pagination configuration for the resource that users can override and
   * configure as needed.
   */
  implicit val paginationConfiguration: PaginationConfiguration =
    PaginationConfiguration()
  // TODO how to fetch associations?
  // TODO Efficient field projection at model level (not just filtered on the way out)

  def keyFormat: KeyFormat[KeyType]

  implicit protected val executionContext: ExecutionContext
  implicit protected val materializer: Materializer

  /**
   * The (Hlist-like) collection of ancestor keys has this type.
   */
  type AncestorKeys = parentResource.PathKey

  /**
   * The (HList-like) collection of all keys that identifies a single element of this collection.
   */
  override type PathKey <: KeyType ::: parentResource.PathKey
  type OptPathKey <: Option[KeyType] ::: parentResource.PathKey

  /**
   * A references to the parent resource (used to construct the path key parser).
   *
   * Must be implemented in the resources that this trait is mixed into, typically with a val param.
   *
   * {{{
   *   class MyNestedResource @Inject() (
   *       val parentResource: MyParentResource)
   *     extends CollectionResource[KeyType, ElemType]
   *     with NestedCollection[MyParentResource, KeyType] {
   *     // ...
   *   }
   * }}}
   */
  protected[this] val parentResource: ParentResource

  /**
   * Helper to easily construct Naptime actions.
   *
   * Typically, all actions in a naptime resource will all use the same auth parser and policy, or
   * will want to use the same error handling function for all requests. These resources should do
   * something similar to the following:
   *
   * {{{
   *   class MyResource extends TopLevelCollectionResource[MyKeyType, MyValueType] {
   *     def Rest[RACType, ResponseType] =
   *       Nap[RACType, ResponseType].auth(myAuthPolicy).catching(errorFn)
   *
   *   ...
   *   }
   * }}}
   */
  def Nap[RACType, ResponseType] =
    new RestActionBuilder[RACType, Unit, AnyContent, K, M, ResponseType](
      HeaderAccessControl.allowAll,
      BodyParsers.parse.default,
      PartialFunction.empty)(keyFormat, resourceFormat, executionContext, materializer)

  def OkIfPresent[T](a: Option[T]): RestResponse[T] = {
    a.map(Ok(_))
      .getOrElse(RestError(NaptimeActionException(404, Some("notFound"), Some("not found"), None)))
  }

  def OkIfPresent(key: K, maybeElement: Option[M]): RestResponse[Keyed[K, M]] = {
    maybeElement
      .map { element =>
        Ok(Keyed(key, element))
      }
      .getOrElse(RestError(NaptimeActionException(404, Some("notFound"), Some("not found"), None)))
  }

  /**
   * Obtain an instance of the pathParser.
   */
  private[naptime] def pathParser: PathParser = {
    // Ugly casts to work around the compiler's inability to prove that NestedPathKeyParser[K,T] is
    // a subtype of PathKeyParser[K ::: T]
    (CollectionResourcePathParser(resourceName, resourceVersion)(keyFormat.stringKeyFormat) ::
      parentResource.pathParser).asInstanceOf[PathParser]
  }
  private[naptime] def optParse(path: String): UrlParseResult[OptPathKey] = {
    // Cast to work around scala compiler's inability to prove certain things. :-(
    pathParser
      .asInstanceOf[NestedPathKeyParser[KeyType, parentResource.PathKey]]
      .parseFinalLevel(path)
      .asInstanceOf[UrlParseResult[OptPathKey]]
  }
}

/**
 * All collection resource that are not a sub-resource of any other resource should extend this.
 *
 * Nested resources should extend the standard [[CollectionResource]] trait, supplying the parent
 * resource as the first type parameter.
 */
trait TopLevelCollectionResource[K, M] extends CollectionResource[RootResource, K, M] {
  override val parentResource: RootResource = RootResource
}

/**
 * If the resource is a courier model, extending from [[NestedCourierCollectionResource]] removes some boilerplate.
 *
 * Aside from automating some of the boilerplate away, it functions the same as a standard
 * [[CollectionResource]]. If the resource is a top level resource, check out the
 * [[CourierCollectionResource]] abstract class.
 *
 * To configure customized fields, use the following snippet:
 *
 * {{{
 *   override implicit lazy val Fields = super.FIelds.withRelated(...)
 * }}}
 *
 * @param kf The [[KeyFormat]] for the key type in the resource.
 * @param classTag The concrete class tag for the value type in the resource.
 * @tparam ParentResource The parent resource (for nesting purposes). If it is a top level, check out:
 *                        [[CourierCollectionResource]]
 * @tparam K The key type of the resource.
 * @tparam M The "value" type of the resource.
 */
abstract class NestedCourierCollectionResource[
    ParentResource <: Resource[_],
    K,
    M <: ScalaRecordTemplate]()(
    implicit kf: KeyFormat[K],
    classTag: ClassTag[M],
    ec: ExecutionContext,
    mat: Materializer)
    extends CollectionResource[ParentResource, K, M] {

  final override implicit val keyFormat = kf

  override implicit protected val executionContext: ExecutionContext = ec
  override implicit protected val materializer: Materializer = mat

  // When we use the serializer constructor, the classTag parameter is never initialized, and
  // thus, we get NPEs when working with schemas. Because the OFormat isn't actually needed
  final override implicit lazy val resourceFormat: OFormat[M] =
    Option(classTag).map { ct =>
      CourierFormats.recordTemplateFormats[M](ct)
    }.orNull // TODO: consider making a fake formatter.

  protected[this] final lazy val BaseFields: Fields[M] =
    org.coursera.naptime.Fields(resourceFormat)

  implicit override def Fields: Fields[M] = BaseFields
}

/**
 * If the resource is a courier model, extending from [[CourierCollectionResource]] removes
 * some boilerplate.
 *
 * If you have a nested resource, check out [[NestedCourierCollectionResource]].
 *
 * @param kf The [[KeyFormat]] for the key type in the resource.
 * @param classTag The concrete class tag for the value type in the resource.
 * @tparam K The key type of the resource.
 * @tparam M The "value" type of the resource.
 */
abstract class CourierCollectionResource[K, M <: ScalaRecordTemplate](
    implicit kf: KeyFormat[K],
    classTag: ClassTag[M],
    ec: ExecutionContext,
    mat: Materializer)
    extends NestedCourierCollectionResource[RootResource, K, M]()(kf, classTag, ec, mat) {

  final override val parentResource: RootResource = RootResource
}
