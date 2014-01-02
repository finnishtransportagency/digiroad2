package fi.liikennevirasto.digiroad2.authentication

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import fi.liikennevirasto.digiroad2.user.User
import org.scalatra._
import org.scalatra.ScalatraBase
import org.slf4j.LoggerFactory

trait AuthenticationSupport extends ScalatraBase with ScentrySupport[User] {
  self: ScalatraBase =>

  val realm = "Digiroad 2"
  val logger = LoggerFactory.getLogger(getClass)

  protected def fromSession = {
    case id: String => User(id)
  }

  protected def toSession = {
    case user: User => user.id
  }

  protected val scentryConfig = (new ScentryConfig { }).asInstanceOf[ScentryConfiguration]

  protected def requireLogin() = {
    if(!isAuthenticated) {
      Unauthorized
    }
  }

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies("UserPassword").unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
      scentry.register("UserPassword", app => new UserPasswordAuthStrategy(app))
  }
}
