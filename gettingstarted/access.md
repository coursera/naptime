---
layout: page
---

# Access Control #

Determining who has access to what data is a subtle and complex topic. In order to enable code
reuse across resources, we have broken up basic access control decisions into a set of re-usable
components. These components are designed to be general and flexible. At Coursera, we use the
Naptime access control abstractions to support sophisticated web and mobile applications with
different authentication mechanisms (Cookies & OAuth2 headers) within multiple applications each
with their own authorization paradigms (1st and 2nd generation course platforms, role-based internal
tools, etc.) Although many access control decisions can be entirely handled by these abstractions,
for those that don't fall into this model (e.g. a particular user can view the course details for
one course, and not another) the resource itself can perform additional filtering with the full
power and expressiveness of a general purpose programming language.

## Request Processing ##

When a request arrives at a server, after routing, the `auth` for the resource takes over and
begins determining if the request should be allowed. This process is broken up into the following
steps:

 1. The request header (the request path, the request method, and the HTTP headers) is parsed by a
    `HeaderAuthenticationParser`. The output of the header parse is a `ParseResult`. This operation
    should be synchronous and depend only upon the information present in a request header.
 2. Often, in order to make a decision to allow or deny access, additional information is required.
    The `Decorator` is the place to add additional lookups.
 3. The final step is to determine if the request should be allowed. An `Authorizer` can allow the
    request to proceed, or deny it with a message.

This pattern is wrapped up in the `StructuredAccessControl` class.

> Note: if you are familiar with the Authentication & Authorization (Auth-n / Auth-z) break down,
> the `HeaderAuthenticationParser` is most similar to Auth-n, and the `Authorizer` maps to Auth-z.
> We have added in the `Decorator` step, as we found that reduces code duplication.

The results of the Decorator are made available to the application code as part of the Naptime
context at: `ctx.auth`.

### Header Parsing Details ###

The `HeaderAuthenticationParser` computes a `ParseResult` from the `RequestHeader`. There are 3
possible `ParseResult`s:
 - **`Success`**: The details required were present in the request and were successfully parsed.
 - **`Skip`**: The authentication information was missing from the request. (i.e. the login cookie
   was missing, the basic auth header was not present, etc.) This `ParseResult` is converted into an
   HTTP Unauthorized (401) code.
 - **`Error`**: There was a problem with the request in some fashion. (i.e. a malformed cookie /
   token.) By default, an Unauthorized (401) response is returned, but this is customizable.

> Note: the `HeaderAuthenticationParser` _must not_ throw exceptions! It should not perform any
> blocking operations, and it should not make any network requests.

### Authorizer Details ###

The `Authorizer` returns one of three possible options:
 - **`Authorized`**: The request should be allowed to proceed.
 - **`Rejected`**: The request should be denied with a 403 Forbidden. An additional message is
   available to help facilitate debugging permissions problems.
 - **`Failed`**: Some part of the request processing chain failed. In this case, an Internal Server
   Error (500) is returned to the client.

> Note: the `Authorizer` _must not_ throw exceptions! Additionally, it should not perform any
> network requests or other blocking operations.

## Example Access Control ##

In our hypothetical application, we have a number of different kinds of users, roles, and permission
levels. We authenticate users via a secure, signed cookie. In this case, we would write an
`HeaderAuthenticationParser` that looks at the request header for the secure cookie, verifies the
signature, and returns the extracted user id. If the cookie is missing, a `ParseResult.Skip` should
be returned. If the cookie signature fails validation, a `ParseResult.Error` should be returned.

At this point, the decorator takes over. A hypothetical decorator could, given a user id, look up
the roles in a permissions cache or fall back to a database. Now, the auth information associated
with the request is not just a `UserId`, but rather now includes role information.

Finally, the Authorizer can determine if the request is allowed. Although a typical application may
have only one or two `HeaderAuthenticationParser`s and `Decorator`s, there are often many
`Authorizer`s. In our hypothetical application, we may have authorizers for (1) administrator access
only, (2) any logged in user, (3) users that have assumed a particular role.

Putting this all together, it might look as follows:

```scala
object Auths {
  private[this] object CompletelyInsecure extends HeaderAuthenticationParser[String] {
    override def parseHeader(requestHeader: RequestHeader): ParseResult[String] = {
      requestHeader.headers.get("EMAIL").map { email =>
        ParseResult.Success(email)
      }.getOrElse(ParseResult.Skip)
    }
  }
  private[this] val decorator = Decorator.function[String, User] { email =>
    val userDetailsFuture = DB.lookup(email)
    userDetailsFuture.map { userDetails =>
      Right(User(email, userDetails.roles, userDetails.isAdministrator))
    }
  }
  private[this] val authenticator = Authenticator(CompletelyInsecure, decorator)

  /**
   * Only allows administrators to perform this naptime request type.
   */
  val administrator = StructuredAccessControl(authenticator, Authorizer(_.isAdministrator))
  /**
   * Only allows users with the given role to perform this naptime request type.
   */
  def hasRole(role: Role) = StructuredAccessControl(
    authenticator, Authorizer(_.roles.contains(role)))
}
```

To use this sample `Auths` in a naptime resource:

```scala
def expensiveFinder() = Nap.auth(Auths.hasRole(Role.POWER_USER)).finder(ctx => ???)
def deleteAllTheThings() = Nap.auth(Auths.administrator).action(ctx => ???)
```

## Advanced Access Control FAQs ##

_What if I want to allow access to a resource whether a user is authenticated or not?_

If you would like to allow access irrespective of a particular user agent's authentication status,
simply use the `HeaderAccessControl.allowAll` access control implementation.

_What if I want to let a request through if it satisfies one of multiple access control policies?
What about if I want to only let a request through if it satisfies all of a set of access controls?_

You can flexibly combine decorators, authenticators, authorizers and even `StructuredAccessControl`
policies using provided helpers. For example, if you have already defined a couple
`StructuredAccessControl`s (e.g. `administrator` and `superuser`), you can allow access if a request
satisfies at least one of those policies with the following code snippet:

```scala
def myFunction(...) = Nap.auth(StructuredAccessControl.anyOf(
    Auths.administrator, Auths.superuser)).action { ctx =>
  // ctx.auth is a tuple with Option's containing the child access control policy outputs.
  val auth: (Option[Administrator], Option[Superuser]) = ctx.auth
  ??? // Your implementation here.
}
```

_What if I want to have the resource behave differently if the user is authenticated, while still
allowing access if they are unauthenticated?_

To do this, you should design your `HeaderAuthenticationParser` to return an `Option[Something]`,
and return either `ParseResult.Success(Some(...))` or `ParseResult.Success(None)` if the user agent
is authenticated or not (respectively).

Alternatively, you can use combinators to mix the access policies. For example, you can use the
StructuredAccessControl.anyOf(...) combinator to allow access whether they are authenticated or not
while still capturing the authentication information.

_What if I want to do something different for a mobile client vs a web client?_

In general, this is not recommended because this subverts the principle of re-using the same APIs
across all client platforms. If this is required, the `HeaderAuthenticationParser` should also parse
the `User-Agent` string as appropriate. Beware, however, that this can easily be spoofed.

_What if an access control decision must be made based on the output of the request body?_

At this time, it is not possible to make an authorization decision based on the content of the
request body as part of this framework. Instead, you should make this authorization decision as
part of your resource implementation based on the `ctx.auth` and `ctx.body`.

_What if I want to return an `WWW-Authenticate` header in my 401 response?_

Unfortunately, this is not supported at this time. Given the complex nature of authentication today,
we don't often see this header used except by HTTP Basic and Digest authentication mechanisms, which
are notoriously insecure. That said, if you're really interested, please open an issue (or better
yet, a PR!) on github.
