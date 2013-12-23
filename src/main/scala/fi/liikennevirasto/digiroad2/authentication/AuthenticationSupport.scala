package fi.liikennevirasto.digiroad2.authentication

import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import fi.liikennevirasto.digiroad2.user.User
import org.scalatra.auth.strategy.BasicAuthSupport
import org.scalatra.ScalatraBase

trait AuthenticationSupport extends ScentrySupport[User] with BasicAuthSupport[User] {
  self: ScalatraBase =>

    val realm = "Digiroad 2"

    protected def fromSession = { case id: String => User(id)  }
    protected def toSession   = { case user: User => user.id }

    protected val scentryConfig = (new ScentryConfig {}).asInstanceOf[ScentryConfiguration]


    override protected def configureScentry = {
      scentry.unauthenticated {
        scentry.strategies("Basic").unauthenticated()
      }
    }

    override protected def registerAuthStrategies = {
      scentry.register("Basic", app => new Digiroad2BasicAuthStrategy(app, realm))
    }
}
