package fi.liikennevirasto.digiroad2.authentication

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import fi.liikennevirasto.digiroad2.user.User
import org.scalatra._
import org.scalatra.ScalatraBase
import org.json4s._
import org.json4s.jackson.Serialization.{read, write}

trait AuthenticationSupport extends ScalatraBase with ScentrySupport[User] {
  self: ScalatraBase =>

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected def fromSession = {
    case user: String => read[User](user)
  }

  protected def toSession = {
    case user: User => write(user)
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
