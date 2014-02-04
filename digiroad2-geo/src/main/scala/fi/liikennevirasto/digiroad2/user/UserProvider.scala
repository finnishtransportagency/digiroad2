package fi.liikennevirasto.digiroad2.user

trait UserProvider {
  val threadLocalUser: ThreadLocal[User] = new ThreadLocal[User]

  def setThreadLocalUser(user: User) {
    threadLocalUser.set(user)
  }

  def getThreadLocalUser(): Option[User] = {
    threadLocalUser.get() match {
      case u: User => Some(u)
      case _ => None
    }
  }

  def createUser(username: String, password: String, email: String, config: Configuration)
  def getUser(username: String): Option[User]
  def getAuthenticatedUser(username: String, password: String): Option[User]
  def getUserConfiguration(): Configuration
}
