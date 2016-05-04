---
layout: page
---

# Getting Started #

This getting started / quickstart guide will help get your first Naptime resource up and running.

## An Example Resource: Users

Let's walk through an example of exposing a simple User resource. The full example resource can
be found at: <https://github.com/coursera/naptime/examples/TODO>.

A user has an id, a name, an email, and an optional description of self (long
blob of text).

<small>Note: For clarity, imports / sections of code may be snipped.</small>

### Setting Up

#### Installing Naptime

Simply add:

```
libraryDependencies += "org.coursera.naptime" %% "naptime" % "0.0.1"
```

to your `build.sbt` file.

If you would like to use Courier models (recommended), please also follow the
[Courier setup instructions for sbt](http://coursera.github.io/courier/gettingstarted/#scala-sbt).

#### Defining the model

Models for Naptime are declared in Courier.  See the
[Courier documentation](http://coursera.github.io/courier/) for details.

Example:

```
namespace org.coursera.user

record User {
  name: string,
  email: string,
  selfDescription: string?
}
```

All models live in the `models/src/main/pegasus` directory.

#### Legacy: Defining models with OFormats

Before Courier, we defined all models using Play! JSON and "OFormats".

Example:

```scala
package org.coursera.user

import play.api.libs.json.Json

case class User(
    name: String,
    email: String,
    selfDescription: Option[String])

object User {
  implicit val format: OFormat[User] = Json.format[User]
}
```

> <small>Note: IntelliJ will flag the type annotation `OFormat[User]` as unable to compile.
> This is (unfortunately) normal, and `sbt` should be able to compile correctly.[^1]</small>

#### Resource Collection Class

Next we define the CollectionResource for User. A CollectionResource exposes
a collection of resources each identified by a key (`Int` in our case) and of a
particular type (the case class `User`).

Naptime supports all kinds of key types natively, including `UUID`, `Int`, `Long`, `String` and
arbitrary user-defined types.

Note that resource names for collections (like users) should be plural.

```scala
import javax.inject.Inject
import javax.inject.Singleton
import org.coursera.model.Keyed
import org.coursera.playcour.naptime.router2.Router
import org.coursera.playcour.naptime.resources.ResourceCaches
import org.coursera.playcour.naptime.resources.DefaultCollectionResource

@Singleton
class UsersResource @Inject() (
    userStore: UserStore,
    banManager: UserBanManager)
  extends TopLevelCollectionResource[Int, User] {

  override def resourceName = "users"
  override def resourceVersion = 1  // optional; defaults to 1
  implicit val fields = Fields.withDefaultFields(  // default field projection
    "id", "name", "email")

  /* Rest Actions go here. See below */

}
```

Here we extend `TopLevelCollectionResource[Key, Model]` which has several helper
fields to make it easier to implement a resource.

We also define the default fields we want to return for our resource. See
[Fields]({{ BASE_PATH }}/projects/naptime/fields.html) for more info.

The last thing for us to do is to bind our `UserCollectionResource` into Guice.
To do so, we use a special Guice-like method `bindResource`.

```scala
class MyServiceModule extends NaptimeModule {
  override def configure(): Unit = {
    bindResource[UserCollectionResource]
  }
}
```

Be sure that your service's `conf/routes` file has the following line included
towards the top of the file:

```
# Hook in Naptime
->       /api                        org.coursera.playcour.naptime.router.NaptimePlayRouter
```

#### Interlude: Routing to Resources + Versioning

In Play!, you have to define routes for your actions in the `routes` file.
Because naptime enforces a specific URL structure, it's able to automatically do
the routing without having additional (resource-specific) entries in `routes`.

Since `UserCollectionResource` is named `users` and has version `1`, the base URL
is `/api/users.v1`.

### Rest Action Time!

The heart of a resource is the `RestActions` it exposes. Available actions of a
resource are picked up by the router, and dispatched as appropriate.

You can think of a Rest Action as being a function `RestContext =>
RestResponse[T]`.

`RestContext` provides context about the request, such as the (parsed) request
body, authentication information, fields of the object requested, paging and the
raw `play.api.mvc.Request`.[^2]

`RestResponse[T]` is similar to an `Either`, and has two concrete
implementations:

 * `Ok[T]` which indicates processing was successful.
 * `RestError` which indicates an error response, and contains an `NaptimeActionException`
   instance containing details such as the http error code.

Most `Ok` responses require some form of `Keyed(key, value)` return type.
`Keyed` is just a handy wrapper class to pair a model with its key.

#### Getting started with GET

Typically, rest actions are built using the included `Rest` builder object
included with `RestActionHelpers`.

Assuming `UserStore` has a method `def get(id: Int): Option[User]`, then we can
write our get Rest Action as such:

```scala
def get(id: Int) = Rest.get { context =>
  OkIfPresent(Keyed(id, userStore.get(id)))
}
```

API route: `GET /api/users.v1/:id`

A `GET` RestAction expects a return type of `RestResponse[Keyed[Int, User]]`, since we're
trying to obtain a User model.

Naptime expects that the Get method argument is named `id`. The resource ID
extracted from the last part of the URL will be passed into the method as the
`id` parameter.

`OkIfPresent` is a helper that transforms the result of `userStore.get(id)`:

* `Some(Keyed(id, user))` becomes `Ok(Keyed(id, user))`, indicating we found the user requested.
* `None` becomes `RestError` with a `404` http code, indicating requested resource was not found.

#### Create

Let's take a look a how we would enable creation of a user.

```scala
def create() = Rest
  .jsonBody[User]  // parse request body as User
  .create { context =>

    val user = context.body
    val id = userStore.create(user)

    // Could return Ok(Keyed(id, Some(user)))
    // if we want the updated entity to be in the response
    Ok(Keyed(id, None))
  }
```

API route: `POST /api/users.v1`

A Create RestAction expects a return type of `RestResponse[Keyed[Int, Option[User]]]`,
where the `Int` is the id of the newly created object, and `Option[User]` is to
optionally return the created user model in the response.

Here we've also used `Rest.jsonBody` to parse the request body so that
`context.body` is of type `User` rather than `AnyContent`.
In case you want to specify a maximum body length, you can also use
the syntax `Rest.jsonBody[User](BODY_MAX_LENGTH)`.

#### Update

Here's how you would do an update of a user. An update is considered to be a
wholesale replacement of the entity.

```scala
import org.coursera.playcour.naptime.Errors

/*             SNIP                 */

def update(id: Int) = Rest
  .jsonBody[User]  // parse request body as User
  .update { context =>

    val user = context.body

    try {
      userStore.update(id, user)
    } catch {
      case e: NotFound =>
        Errors.NotFound(msg = s"Could not find user with id $id")
    }

    // Could return Ok(Some(Keyed(id, user)))
    // if we want the updated entity to be in the response
    Ok(None)
  }
```

API route: `PUT /api/users.v1/:id`

An Update RestAction expects a return type of `RestResponse[Option[Keyed[Int, User]]]`,
where we optionally return the `User` entity if we wish for it to be included
in the response

Naptime expects to find an argument named `id` for the Update method. The
resource ID extracted from the last part of the URL will be passed into the
method as the `id` parameter.

Here we've used `Errors.NotFound` to throw a predefined exception to break out
of control flow to indicate common error conditions. The `Errors` trait
predefines commonly met error conditions (`NotFound`, `Unauthorized`, ...), and
you can always fall back to
`Errors.error(httpCode: Int, errorCode: String, msg: String, details: Option[JsValue])`

These `Errors.*` functions should only be used in Resource code, and should
*not* be used in lower level abstractions like stores. Dealing with HTTP
abstractions should only be done at the Resource level.

#### Delete

Delete is pretty straight forward:

```scala
def delete(id: Int) = Rest.delete { context =>

  try {
    userStore.delete(user)
  } catch {
    case e: NotFound =>
      Errors.NotFound(msg = s"Could not find user with id $id")
  }

  Ok()
}
```

API route: `DELETE /api/users.v1/:id`

A Delete RestAction expects a return type of `RestResponse[Unit]`.

Naptime expects that the Delete method argument is named `id`. The resource ID
extracted from the last part of the URL will be passed into the method as the
`id` parameter.

#### Multi Get

It's useful for client to be able to batch together requests for multiple items
for efficiency reasons. Naptime supports this via the multiGet API.

```scala
def multiGet(ids: Set[Int]) = Rest.multiGet { context =>

  val keyedUsers = userStore.get(ids)

  Ok(keyedUsers.toList)
}
```

API route: `GET /api/users.v1?ids=1,3,5`

A MultiGet RestAction expects a return type of `RestResponse[Seq[Keyed[Int, User]]]`.

#### Get All / Pagination

Naptime supports listing all entities in a collection. When the number of items
is potentially large, pagination should definitely be used.

```scala
def getAll() = Rest.getAll { context =>

  val paging = context.paging
  val (nextKey, keyedUsers) = userStore.getAll(
    paging.startAsInt.getOrElse(0),
    paging.limit)

  Ok(keyedUsers, pagination = Some(ResponsePagination(nextKey.map(_.toString))))
}
```

API route: `GET /api/users.v1`

A GetAll RestAction expects a return type of `RestResponse[Seq[Keyed[Int, User]]]`.

Let's take a closer look at pagination. The request pagination parameters are
availabe in `RestContext.paging`, and which contains the fields `start` and
`limit`. The `ResponsePagination` returns an optional `start` which indicates
whether more data can be obtained by calling with the same API with updated
pagination.

Pagination is intentionally designed with `start` being an opaque `String` type,
as opposed to a numeric page number, so as to support datastores / collections
that do not easily support numeric paging. However, there is a convenience
function `startAsInt` that will attempt to parse `start` as an Int, and throw a
BadRequest exception on failure.

#### Finders - Finding things

Sometimes we need a way of finding entities other than by key. Finders to the
rescue!

Finders are purely to find things – they should not have side effects.

Finders take the form of `GET /api/users.v1?q=finderName&param1=value1&param2=value2`,
and you would write a different finder for each type of query you're aiming to
support.

Let's write a finder to lookup users by email:

```scala
def email(email: String) = Rest.finder { context =>

  val keyedUsers = userStore.findByEmail(email)

  Ok(keyedUsers.toList)
}
```

API route: `GET /api/users.v1?q=email&email=a@123.com`

A Finder RestAction expects a return type of `RestResponse[Seq[Keyed[Int, User]]]`.

Finders are named after the function defining them (in this case, our finder is
called `email`). Query parameters are dynamically bound to the finder arguments,
and are parsed to the right type. Custom key types (using `KeyFormat`) are
supported in finders. Further, scala argument defaults are also supported for a
natural way to handle query parameters not sent by clients.

```scala
def email(
    email: String,
    organizationId: OrganizationId = OrganizationId.COURSERA) = Rest.finder { ctx =>
  ???
}
```

#### Actions - Do non-RESTful stuff!

Actions should be used with care. Most of the time, there is a way to model
things in a more resource-oriented manner.

That said, there are use cases for generic "actions" on resources, which are
(potentially) side effecting.

Actions are of the form `POST /api/users.v1?action=actionName` with most of the
parameters sent in the HTTP body. (Note that extra query parameters can be
passed in the URL.)

Let's say we want an action to put a temporary ban on a user. We delegate the
actual 'banning' to a `UserBanManager`, as banning a user is potentially
complicated and might require coordinating different actions.

```scala
def ban(id: Int, durationSeconds: Long) = Rest.action { context =>

  banManager.banUser(id, durationSeconds)

  Ok(Json.obj())
}
```

API route: `POST /api/users.v1?action=ban&id=123&durationSeconds=3600`
(invokes ban on user 123 for 3600 seconds)

Because actions are meant to be powerful and flexible, it's response type is
`RestResponse[A]` where  `A` is Json writeable – an implicit `Json.Writes[A]`
must be in scope.

## Nested Resources

In most cases, we advocate for just creating top-level resources. However, some
resources naturally live as a sub-resource of a parent resource. For such cases,
Naptime has built-in support.

For example, suppose that posts have a sub-resource called comments. Then, the
API to access comments would take the form:

```
/api/posts.v1/:postId/comments/:commentId
```

In all RestActions of the comments resource, an additional parameter `postId` is
available if required.

To denote that a resource is a sub-resource, simply extend from the `NestedCollectionResource`
trait, specifying the parent resource as the first type parameter. The subsequent 2 type parameters
are the Key type, and the Body type respectively.

```scala
class CommentsResource @Inject() (
    resourceCaches: ResourceCaches)
  extends NestedCollectionResource[PostsResource, Int, Comment](resourceCaches) {

  override def resourceName = "comments"
  override def resourceVersion = 0

  def get(postsId: Id, id: Int) = Rest.get { context =>
    /* do stuff */
  }
}
```

Sub-resources are only addressable via their parent resource. They can have an additional version
included in the path. However, because most nested resources are so inherently tied to the
implementations of their parent resource, it doesn't make sense to include an additional version.
Therefore, you can elide the additional version component from the resource name by setting the
`resourceVersion` to `0`.

------------------

[^1]: The reason for this is because IntelliJ does not expand Macros, while `sbt` (the real
      scala compiler) does.

[^2]: In order to future-proof your resource, please avoid accessing the underlying Play! request.
