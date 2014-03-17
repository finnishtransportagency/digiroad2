package fi.liikennevirasto.digiroad2.authentication

import javax.servlet.http.{Cookie, HttpServletRequest}
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}

trait TestUserSupport {
  val TestUsernameCookie = "testusername"
  var testUser: Option[User] = None

  def getTestUser(request: HttpServletRequest)(implicit userProvider: UserProvider): Option[User] = {
    request.getCookies.find(_.getName == TestUsernameCookie) match {
      case Some(c: Cookie) => userProvider.getUser(c.getValue)
      case _ => throw new UnauthenticatedException()
    }
  }
}