package fi.liikennevirasto.digiroad2.user;

class DummyUserProvider extends UserProvider {
  def getUserConfiguration(): Map[String, String] = {
    getThreadLocalUser() match {
      case Some(user) => user.id match {
        case "tamperetest" => Map("zoom" -> "7", "east" -> "328308", "north"-> "6822545", "municipalityNumber" -> "837")
        case _ => Map("zoom" -> "8", "east" -> "373560", "north"-> "6677676", "municipalityNumber" -> "235")
      }
      case None => Map()
    }
  }
}
