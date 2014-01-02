package fi.liikennevirasto.digiroad2.authentication

import fi.liikennevirasto.digiroad2.user.User
import org.scalatra.auth.strategy.BasicAuthStrategy
import org.scalatra.ScalatraBase
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class HttpBasicAuthStrategy(protected override val app: ScalatraBase, realm: String)
  extends BasicAuthStrategy[User](app, realm) {

  protected def validate(userName: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    if (userName == password) Some(User(userName)) else None
  }

  protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.id
}