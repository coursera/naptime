package org.coursera.naptime.actions

import org.coursera.naptime.model.Keyed

/**
 * Defines terminal-position builder methods for [[RestActionBuilder]]
 */
trait RestActionBuilderTerminators[
    RACType, AuthType, BodyType, ResourceKeyType, ResourceType, ResponseType] {

  type BodyBuilder[Category, Response] =
    RestActionBodyBuilder[Category, AuthType, BodyType, ResourceKeyType, ResourceType, Response]

  protected def bodyBuilder[Category, Response](): BodyBuilder[Category, Response]

  // Define all the available REST action types and categories.
  /**
   * Gets a resource by ID
   *
   * Example:
   * {{{
   * def get(id: Int) = Rest.get { ctx =>
   *   Ok(Keyed(id, MyResource(name=s"Resource-$id")))
   * }
   * }}}
   *
   * Underlying HTTP request (id = "1"):
   * {{{ GET /api/myResource/1 }}}
   */
  type GetBuilder = BodyBuilder[GetRestActionCategory, Keyed[ResourceKeyType, ResourceType]]
  def get: GetBuilder = bodyBuilder()

  /**
   * Gets a batch of resources from this collection.
   *
   * Example:
   * {{{
   * def multiGet(ids: Seq[Int]) = Rest.multiGet { ctx =>
   *   Ok(ids.map(id => Keyed(id, MyResource(name=s"Resource-$id"))))
   * }
   * }}}
   *
   * Underlying HTTP request (ids = "1,2,3,4")
   * {{{ GET /api/myResource?ids=1,2,3,4 }}}
   */
  type MultiGetBuilder =
    BodyBuilder[MultiGetRestActionCategory, Seq[Keyed[ResourceKeyType, ResourceType]]]
  def multiGet: MultiGetBuilder = bodyBuilder()

  /**
   * Gets all elements in a collection. Note: please use paging to avoid OOM-ing.
   *
   * Example:
   * {{{
   * def getAll = Rest.getAll { ctx =>
   *   val results = store.getAll(start=ctx.paging.start, limit=ctx.paging.limit)
   *   Ok(results)
   * }
   * }}}
   *
   * Underlying HTTP request (pagination: start=10, limit=5)
   * {{{ GET /api/myResource?start=10&limit=5 }}}
   */
  type GetAllBuilder =
    BodyBuilder[GetAllRestActionCategory, Seq[Keyed[ResourceKeyType, ResourceType]]]
  def getAll: GetAllBuilder = bodyBuilder()

  /**
   * Creates a new resource given an input.
   *
   * Example:
   * {{{
   * def create = Rest.create { ctx =>
   *   val newElement = createResourceFromBody(ctx.body)
   *   val newId = store.save(newOne)
   *   Ok(Keyed(newId, Some(newElement)))
   * }
   * }}}
   *
   * Underlying HTTP request (body elided):
   * {{{ POST /api/myResource }}}
   */
  type CreateBuilder =
    BodyBuilder[CreateRestActionCategory, Keyed[ResourceKeyType, Option[ResourceType]]]
  def create: CreateBuilder = bodyBuilder()

  /**
   * Updates a resource with a new copy of the resource.
   *
   * Example:
   * {{{
   * def update(id: Int) = Rest.update { ctx =>
   *   val newVersion = validateBody(ctx.body)
   *   store.save(id, newVersion)
   *   Ok(Keyed(id, newVersion))
   * }
   * }}}
   *
   * Underlying HTTP request (body elided, id=2):
   * {{{ PUT /api/myResource/2 }}}
   */
  type UpdateBuilder =
    BodyBuilder[UpdateRestActionCategory, Option[Keyed[ResourceKeyType, ResourceType]]]
  def update: UpdateBuilder = bodyBuilder()

  /**
   * Deletes an element from a collection.
   *
   * Example:
   * {{{
   * def delete(id: Int) = Rest.delete { ctx =>
   *   store.delete(id)
   *   Ok()
   * }
   * }}}
   *
   * Underlying HTTP request (id=4):
   * {{{ DELETE /api/myResource/4 }}}
   */
  type DeleteBuilder = BodyBuilder[DeleteRestActionCategory, Unit]
  def delete: DeleteBuilder = bodyBuilder()

  /**
   * Patch (or partial update) a resource.
   *
   * Example:
   * {{{
   * def patch(id: Int) = Rest.patch { ctx =>
   *   val oldObj = store.get(id)
   *   val newObj = PatchEngine.patch(oldObj, ctx.body)
   *   store.save(id, newObj)
   *   Ok(Keyed(id, newObj))
   * }
   * }}}
   *
   * Underlying HTTP request (id=10, body elided):
   * {{{ PATCH /api/myResource/10 }}}
   */
  type PatchBuilder = BodyBuilder[PatchRestActionCategory, Keyed[ResourceKeyType, ResourceType]]
  def patch: PatchBuilder = bodyBuilder()

  /**
   * Retrieval by any method other than retrival by Id [primary key]. (For example, alternate key
   * lookup, or full text search. This is intentionally a flexible API and can be used appropriately
   * in many contexts. A finder MUST NOT have side effects, and must be idempotent.
   *
   * Example:
   * {{{
   * def alternateKey(name: String) = Rest.finder { ctx =>
   *   val results = store.lookupByName(name=name)
   *   Ok(results.toSeq)
   * }
   * }}}
   *
   * Underlying HTTP request (finder name='alternateKey', name='hogwarts'):
   * {{{ GET /api/myResource?q=alternateKey&name=hogwarts }}}
   */
  type FinderResponseType = Seq[Keyed[ResourceKeyType, ResourceType]]
  type FinderBuilder = BodyBuilder[FinderRestActionCategory, FinderResponseType]
  def finder: FinderBuilder = bodyBuilder()

  /**
   * Arbitrary actions on objects that are not standard CRUD operations. This is very flexible, and
   * must be carefully used only for good (and not evil). Actions may have side effects and may not
   * be idempotent.
   *
   * Example:
   * {{{
   * def convertToHtml(ids: Seq[Int]) = Rest.action { ctx =>
   *   val pages = store.multiGet(ids)
   *   val htmlIfied = pages.map { page =>
   *     MarkdownEngine.toHtml(page)
   *   }
   *   store.multiSave(htmlIfied)
   *   Ok(htmlIfied)
   * }
   * }}}
   *
   * Underlying HTTP request (ids=1,2,3,5,8,13):
   * {{{ POST /api/myResource?action=convertToHtml&ids=1,2,3,5,8,13 }}}
   */
  type ActionBuilder[A] = BodyBuilder[ActionRestActionCategory, A]
  def action[A]: ActionBuilder[A] = bodyBuilder()

}
