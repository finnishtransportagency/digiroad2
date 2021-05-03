package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.scalatra.ScalatraBase
import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.auth.strategy.{BasicAuthStrategy, BasicAuthSupport}

case class BasicAuthUser(username: String)

// IntergationAuthStrategy.scala will be deleted in the future (aws).
class IntegrationAuthStrategy(protected override val app: ScalatraBase, realm: String, baseAuth: String = "")
  extends BasicAuthStrategy[BasicAuthUser](app, realm) {

  def validate(username: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[BasicAuthUser] = {
      if (username == Digiroad2Properties.authenticationBasicUsername && password == Digiroad2Properties.authenticationBasicPassword) Some(BasicAuthUser(username))
    else None
  }

  def getUserId(user: BasicAuthUser)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.username
}

trait AuthenticationSupport extends ScentrySupport[BasicAuthUser] with BasicAuthSupport[BasicAuthUser] {
  self: ScalatraBase =>

  def baseAuth: String = ""
  val realm = "Digiroad 2 Integration API"

  protected def fromSession = { case id: String => BasicAuthUser(id)  }
  protected def toSession = { case user: BasicAuthUser => user.username }

  protected val scentryConfig = (new ScentryConfig {}).asInstanceOf[ScentryConfiguration]

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies("Basic").unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register("Basic", app => new IntegrationAuthStrategy(app, realm, baseAuth))
  }
}

