package fi.liikennevirasto.digiroad2.authentication

import javax.servlet.http.HttpServletRequest
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.user.User
import scala.Some
import fi.liikennevirasto.digiroad2.Digiroad2Context

trait Authentication extends TestUserSupport {
  // NOTE: maybe cache user data if required for performance reasons
  def authenticateForApi(request: HttpServletRequest)(implicit userProvider: UserProvider) = {
    userProvider.clearThreadLocalUser()
    authenticate(request)(userProvider) match {
      case Some(user) => userProvider.setThreadLocalUser(user)
      case None => {
        if (authenticationTestModeEnabled) {
          userProvider.setThreadLocalUser(getTestUser(request)(userProvider).getOrElse(null))
        } else {
          throw new UnauthenticatedException()
        }
      }
    }
  }

  def authenticate(request: HttpServletRequest)(implicit userProvider: UserProvider): Option[User]
}