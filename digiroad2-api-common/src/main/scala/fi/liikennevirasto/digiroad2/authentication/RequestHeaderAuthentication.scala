package fi.liikennevirasto.digiroad2.authentication

import javax.servlet.http.HttpServletRequest
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import org.slf4j.LoggerFactory

trait RequestHeaderAuthentication extends Authentication {
  val raLogger = LoggerFactory.getLogger(getClass)
  val OamRemoteUserHeader = "OAM_REMOTE_USER"

  def authenticate(request: HttpServletRequest)(implicit userProvider: UserProvider): User = {
    val remoteUser = request.getHeader(OamRemoteUserHeader)
    raLogger.info("Authenticate request, remote user = " + remoteUser)
    if (remoteUser == null || remoteUser.isEmpty) {
      throw new UnauthenticatedException()
    }

    userProvider.getUser(remoteUser).getOrElse(viewerUser)
  }
}