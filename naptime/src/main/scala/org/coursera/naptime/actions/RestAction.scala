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

import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import org.coursera.common.concurrent.Futures
import org.coursera.naptime.NaptimeActionException
import org.coursera.naptime.PaginationConfiguration
import org.coursera.naptime.QueryStringParser.NaptimeParseError
import org.coursera.naptime.RequestPagination
import org.coursera.naptime.ResourceFields
import org.coursera.naptime.ResourceName
import org.coursera.naptime.RestContext
import org.coursera.naptime.RestError
import org.coursera.naptime.RestResponse
import org.coursera.naptime.ari.Response
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.router2.RouteAction
import play.api.Application
import play.api.libs.json.OFormat
import play.api.libs.streams.Accumulator
import play.api.libs.typedmap.TypedKey
import play.api.mvc.BodyParser
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Result

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * A RestAction is a layer on top of Play! with additional type information
 *
 * This type information is used to help enforce conventions, DRY things out, and support some
 * additional features.
 *
 * Type parameters:
 * RACType - The rest action type. This is typically a subclass of RestActionCategory
 * AuthType - The authentication return type.
 * BodyType - The HTTP request body is parsed to this type for use in the handler.
 * KeyType - The key type of the model being processed.
 * ResourceType - This is the resource type this action is supposed to handle.
 * ResponseType - This is the response type this action is supposed to return (e.g. Seq of
 *   ResourceType)
 *
 * TODO(saeta): Enforce RACType extends from RestActionCategory.
 */
trait RestAction[RACType, AuthType, BodyType, KeyType, ResourceType, ResponseType]
    extends RouteAction
    with StrictLogging {

  protected[actions] def restAuthGenerator: AuthGenerator[BodyType, AuthType]
  protected def restBodyParser: BodyParser[BodyType]
  protected[naptime] def restEngine
    : RestActionCategoryEngine[RACType, KeyType, ResourceType, ResponseType]
  protected[naptime] def fieldsEngine: ResourceFields[ResourceType]
  protected def paginationConfiguration: PaginationConfiguration
  protected def errorHandler: PartialFunction[Throwable, RestError]
  protected implicit val keyFormat: KeyFormat[KeyType]
  protected implicit val resourceFormat: OFormat[ResourceType]
  protected val application: Application
  protected implicit def executionContext: ExecutionContext = application.actorSystem.dispatcher
  protected implicit def materializer: Materializer = application.materializer

  /**
   * High level API, also used for testing.
   */
  private[naptime] def apply(
      context: RestContext[AuthType, BodyType]): Future[RestResponse[ResponseType]]

  private[naptime] def safeApply(
      context: RestContext[AuthType, BodyType]): Future[RestResponse[ResponseType]] = {
    Futures
      .safelyCall(apply(context))
      .recover(errorHandler)
      .recover {
        case e: NaptimeActionException =>
          // Chain this recover because it is quite a common pattern to throw a NaptimeActionException in the
          // errorHandler instead of returning RestError. This recover should wrap those thrown
          // NaptimeActionExceptions into RestErrors. This consolidates the code paths for handling
          // NaptimeActionExceptions.
          RestError(e)
      }
  }

  private[naptime] def localRun(rh: RequestHeader, resourceName: ResourceName): Future[Response] = {
    restBodyParser(rh)
      .mapFuture[Response] {
        case Left(bodyError) =>
          val bodyAsBytesEventually = bodyError.body.consumeData
          val bodyAsStrEventually = bodyAsBytesEventually.map(byteStr => byteStr.utf8String)
          bodyAsStrEventually.map { bodyAsStr =>
            throw new IllegalArgumentException(s"${rh.headers} Encountered body error: $bodyAsStr")
          }
        case Right(a) =>
          restAuthGenerator(a).run(rh).flatMap[Response] {
            case Left(error) => Future.failed(error) // TODO: log?
            case Right(auth) => {
              val responseTry = for {
                fields <- fieldsEngine.computeFields(rh)
                includes <- fieldsEngine.computeIncludes(rh)
              } yield {
                val pagination = RequestPagination(rh, paginationConfiguration)
                val playRequest = Request(rh, a)
                val ctx = new RestContext(a, auth, playRequest, pagination, includes, fields)

                def run(): Future[Response] = {
                  val highLevelResponse = safeApply(ctx)
                  highLevelResponse.flatMap { resp =>
                    restEngine match {
                      case engine2: RestActionCategoryEngine2[
                            RACType,
                            KeyType,
                            ResourceType,
                            ResponseType] =>
                        engine2.mkResponse(
                          rh,
                          fieldsEngine,
                          fields,
                          includes,
                          pagination,
                          resp,
                          resourceName)
                      case _ =>
                        Future.failed(new IllegalArgumentException("Was not an engine2 resource.")) // TODO: better msg
                    }
                  } recoverWith {
                    case e: NaptimeActionException => Future.failed(e)
                  }
                }

                // Implementation below borrowed from Play's Action.scala
                play.utils.Threads.withContextClassLoader(application.classloader) {
                  run()
                }
              }
              responseTry.recover {
                case e: NaptimeParseError      => Future.failed(e)
                case e: NaptimeActionException => Future.failed(e)
              }.get
            }
          }
      }
      .run()
  }

  /**
   * Invoke the rest action in production.
   */
  final override def apply(rh: RequestHeader): Accumulator[ByteString, Result] = {
    restBodyParser(rh).mapFuture {
      case Left(bodyError) => Future.successful(bodyError)
      case Right(body) =>
        runAuthAndBody(rh, body)
    }
  }

  private[naptime] def runAuthAndBody(rh: RequestHeader, body: BodyType): Future[Result] = {
    restAuthGenerator(body).run(rh).flatMap {
      case Left(error) => Future.successful(error.result) // TODO: log?
      case Right(auth) =>
        val responseTry = for {
          fields <- fieldsEngine.computeFields(rh)
          includes <- fieldsEngine.computeIncludes(rh)
        } yield {
          val pagination = RequestPagination(rh, paginationConfiguration)
          val playRequest = Request(rh, body)
          val ctx = new RestContext(body, auth, playRequest, pagination, includes, fields)

          def run(): Future[Result] = {
            safeApply(ctx).map { resp =>
              // log error messages from resources that return a RestError(...)
              resp match {
                case RestError(e) if e.httpCode >= 500 =>
                  logger.error("Server 5xx error response", e)
                case _ =>
              }

              restEngine.mkResult(rh, fieldsEngine, fields, includes, pagination, resp)
            }
          }

          // Implementation below borrowed from Play's Action.scala
          play.utils.Threads.withContextClassLoader(application.classloader) {
            run()
          }
        }
        responseTry.recover {
          case e: NaptimeParseError      => Future.successful(e.result)
          case e: NaptimeActionException => Future.successful(e.result)
        }.get
    }
  }

  override def toString() =
    s"RestAction(engine=$restEngine, auth=$restAuthGenerator, body=$restBodyParser)"

  /**
   * A set of tags to add to all requests that are processed by this RestAction.
   *
   * Note: while this is theoretically thread unsafe, the only caller of `setTags` should be the
   * macro-based router. Further, it should only be called once, and the `tags` value should only be
   * read at most once. (As the action is re-generated on every request.)
   */
  @volatile private[this] var tags: Option[Map[TypedKey[String], String]] = None

  /**
   * Adds tags to the request.
   *
   * This will permit us to remove the trace modifications that the old router needed to annotate
   * the traces appropriately.
   *
   * @param request The request to tag.
   * @return The tagged request.
   */
  override def tagRequest(request: RequestHeader): RequestHeader = {
    tags
      .map { tags =>
        tags.foldLeft(request) {
          case (request, (key, value)) =>
            request.addAttr(key, value)
        }
      }
      .getOrElse(request)
  }

  /**
   * Retrieves the tags. Exposed for testing.
   */
  private[naptime] def copyTags(): Option[Map[TypedKey[String], String]] = tags

  /**
   * The naptime ResourceRouter should call this function before returning the action.
   */
  def setTags(tags: Map[TypedKey[String], String]): this.type = {
    this.tags = Some(tags)
    this
  }
}
