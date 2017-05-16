package fi.liikennevirasto.digiroad2

import java.util.Properties
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.scalatra.ScalatraBase
import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.auth.strategy.{BasicAuthStrategy, BasicAuthSupport}

case class BasicAuthUser(username: String)

class IntegrationAuthStrategy(protected override val app: ScalatraBase, realm: String, baseAuth: String = "")
  extends BasicAuthStrategy[BasicAuthUser](app, realm) {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/authentication.properties"))
    props
  }

  private def getProperty(name: String): String = {
    val property = properties.getProperty(name)
    if (property != null) {
      property
    } else {
      throw new RuntimeException(s"cannot find property $name")
    }
  }

  def validate(username: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[BasicAuthUser] = {
      if (username == getProperty("authentication."+baseAuth+"basic.username") && password == getProperty("authentication."+baseAuth+"basic.password")) Some(BasicAuthUser(username))
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

