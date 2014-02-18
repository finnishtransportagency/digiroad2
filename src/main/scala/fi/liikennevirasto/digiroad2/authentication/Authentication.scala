package fi.liikennevirasto.digiroad2.authentication

import javax.servlet.http.HttpServletRequest
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.user.User
import scala.Some
import fi.liikennevirasto.digiroad2.Digiroad2Context
import org.slf4j.LoggerFactory

trait Authentication extends TestUserSupport {

  val authLogger = LoggerFactory.getLogger(getClass)
  // NOTE: maybe cache user data if required for performance reasons
  def authenticateForApi(request: HttpServletRequest)(implicit userProvider: UserProvider) = {
    userProvider.clearCurrentUser()
    try {
      userProvider.setCurrentUser(authenticate(request)(userProvider))
    } catch {
      case ise: IllegalStateException => {
        if (authenticationTestModeEnabled) {
          authLogger.info("Remote user not found, falling back to test mode authentication")
          userProvider.setCurrentUser(getTestUser(request)(userProvider).getOrElse(throw ise))
        } else {
          throw ise
        }
      }
    }
  }

  def authenticate(request: HttpServletRequest)(implicit userProvider: UserProvider): User
}