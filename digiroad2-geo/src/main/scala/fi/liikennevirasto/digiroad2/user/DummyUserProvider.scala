package fi.liikennevirasto.digiroad2.user;

class DummyUserProvider extends UserProvider {
  def createUser(username: String, config: Configuration) = {
    User(0, username, Configuration())
  }
  def getUser(username: String): Option[User] = {
    Some(User(0, username, Configuration(zoom = Some(8), east = Some(373560), north = Some(6677676), municipalityNumber = Some(235))))
  }
  def getAuthenticatedUser(username: String, password: String): Option[User] = getUser(username)

  def getUserConfiguration(): Configuration = {
    getThreadLocalUser() match {
      case Some(user) => user.username match {
        case "tamperetest" => Configuration(zoom = Some(7), east = Some(328308), north = Some(6822545), municipalityNumber = Some(837))
        case _ => Configuration(zoom = Some(8), east = Some(373560), north = Some(6677676), municipalityNumber = Some(235))
      }
      case None => Configuration()
    }
  }
}
