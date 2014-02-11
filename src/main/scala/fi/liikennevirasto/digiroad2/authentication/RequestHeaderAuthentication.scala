package fi.liikennevirasto.digiroad2.authentication

import javax.servlet.http.HttpServletRequest
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}

trait RequestHeaderAuthentication extends Authentication {
  val OamRemoteUserHeader = "OAM_REMOTE_USER"

  def authenticate(request: HttpServletRequest)(implicit userProvider: UserProvider): User = {
    val remoteUser = request.getHeader(OamRemoteUserHeader)
    userProvider.getUser(remoteUser).getOrElse(throw new IllegalStateException("Could not authenticate: " + remoteUser))
  }
}