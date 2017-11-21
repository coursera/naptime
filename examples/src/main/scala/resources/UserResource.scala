package resources

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

import akka.stream.Materializer
import org.coursera.naptime.model.KeyFormat
import org.coursera.naptime.model.Keyed
import org.coursera.naptime.Ok
import org.coursera.example.User
import org.coursera.naptime.courier.CourierFormats
import org.coursera.naptime.resources.TopLevelCollectionResource
import play.api.libs.json.OFormat

import scala.concurrent.ExecutionContext

/**
 * This is a sample resource, illustrating the power of Naptime.
 *
 * Using `curl`, you can create and retrieve users:
 * {{{
 *   saeta@betacspro ~% curl -v -X 'POST' -H "Content-Type: application/json" -d '{"name":"a", "email":"a@b.com"}' localhost:9000/api/users.v1/
 *   Hostname was NOT found in DNS cache
 *     Trying ::1...
 *   Connected to localhost (::1) port 9000 (#0)
 *   > POST /api/users.v1/ HTTP/1.1
 *   > User-Agent: curl/7.37.1
 *   > Host: localhost:9000
 *   > Content-Type: application/json
 *   > Content-Length: 31
 *   >
 *   * upload completely sent off: 31 out of 31 bytes
 *   < HTTP/1.1 201 Created
 *   < ETag: "-347601404"
 *   < Location: /api/users.v1/1
 *   < Content-Type: application/json; charset=utf-8
 *   < X-Coursera-Id: 1
 *   < Date: Tue, 10 May 2016 22:27:23 GMT
 *   < Content-Length: 80
 *   <
 *   * Connection #0 to host localhost left intact
 *   {"elements":[{"id":1,"email":"a@b.com","name":"a"}],"paging":null,"linked":null}
 *   saeta@betacspro ~% curl localhost:9000/api/users.v1/1
 *   {"elements":[{"id":1,"email":"a@b.com","name":"a"}],"paging":null,"linked":null}
 *   saeta@betacspro ~% curl localhost:9000/api/users.v1/2
 *   {"errorCode":"notFound","message":"not found","details":null}%
 * }}}
 */
@Singleton
class UsersResource @Inject() (
    userStore: UserStore,
    banManager: UserBanManager)
    (implicit override val executionContext: ExecutionContext,
    override val materializer: Materializer)
  extends TopLevelCollectionResource[Int, User] {

  override def resourceName = "users"
  override def resourceVersion = 1  // optional; defaults to 1
  implicit val fields = Fields.withDefaultFields(  // default field projection
    "id", "name", "email")

  override def keyFormat: KeyFormat[KeyType] = KeyFormat.intKeyFormat
  override implicit def resourceFormat: OFormat[User] = CourierFormats.recordTemplateFormats[User]

  def get(id: Int) = Nap.get { context =>
    OkIfPresent(id, userStore.get(id))
  }

  def multiGet(ids: Set[Int]) = Nap.multiGet { context =>
    Ok(userStore.all()
      .filter(user => ids.contains(user._1))
      .map { case (id, user) => Keyed(id, user) }.toList)
  }

  def getAll() = Nap.getAll { context =>
    Ok(userStore.all().map { case (id, user) => Keyed(id, user) }.toList)
  }

  def create() = Nap
    .jsonBody[User]
    .create { context =>
      val user = context.body
      val id = userStore.create(user)

      // Could return Ok(Keyed(id, None)) if we want to return 201 Created,
      // with an empty body. Prefer returning the updated body, however.
      Ok(Keyed(id, Some(user)))
    }

  def email(email: String) = Nap.finder { context =>
    Ok(userStore.all()
      .filter(_._2.email == email)
      .map { case (id, user) => Keyed(id, user) }.toList)
  }

}


trait UserStore {
  def get(id: Int): Option[User]
  def create(user: User): Int
  def all(): Map[Int, User]
}

@Singleton
class UserStoreImpl extends UserStore {
  @volatile
  var userStore = Map.empty[Int, User]
  val nextId = new AtomicInteger(0)

  def get(id: Int) = userStore.get(id)

  def create(user: User): Int = {
    val id = nextId.incrementAndGet()
    userStore = userStore + (id -> user)
    id
  }

  def all() = userStore

}

class UserBanManager {
  @volatile
  var bannedUsers = Set.empty[Int]
}
