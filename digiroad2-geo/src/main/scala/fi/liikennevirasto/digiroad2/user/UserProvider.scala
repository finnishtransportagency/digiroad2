package fi.liikennevirasto.digiroad2.user


trait UserProvider {
  val threadLocalUser: ThreadLocal[User] = new ThreadLocal[User]

  def clearCurrentUser() {
    threadLocalUser.remove()
  }

  def setCurrentUser(user: User) {
    threadLocalUser.set(user)
  }

  def getCurrentUser(): User = {
    threadLocalUser.get() match {
      case u: User => u
      case _ => throw new IllegalStateException("Current user not available")
    }
  }

  def createUser(username: String, config: Configuration, name: Option[String] = None, newTransaction: Boolean = true)

  def getUser(username: String, newTransaction: Boolean = true): Option[User]

  def saveUser(user: User): User
  def updateUserConfiguration(user: User, newTransaction: Boolean = true): User
}

