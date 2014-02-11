package fi.liikennevirasto.digiroad2.authentication

import javax.servlet.http.HttpServletRequest
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}

trait Authentication {
  def authenticate(request: HttpServletRequest)(implicit userProvider: UserProvider): Option[User]
}