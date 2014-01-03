package fi.liikennevirasto.digiroad2.user;

class DummyUserProvider extends UserProvider {
  def createUser(username: String, password: String, config: Map[String, String]) = {
    User(0, username, password, config)
  }
  def getUser(username: String): Option[User] = {
    Some(User(0, username, "", Map("zoom" -> "8", "east" -> "373560", "north"-> "6677676", "municipalityNumber" -> "235")))
  }

  def getUserConfiguration(): Map[String, String] = {
    getThreadLocalUser() match {
      case Some(user) => user.username match {
        case "tamperetest" => Map("zoom" -> "7", "east" -> "328308", "north"-> "6822545", "municipalityNumber" -> "837")
        case _ => Map("zoom" -> "8", "east" -> "373560", "north"-> "6677676", "municipalityNumber" -> "235")
      }
      case None => Map()
    }
  }
}
