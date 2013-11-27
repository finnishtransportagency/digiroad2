package fi.liikennevirasto.digiroad2.user;

class DummyUserProvider extends UserProvider {
  def getUserConfiguration(): Map[String, String] = { Map("zoom" -> "8", "east" -> "373560", "north"-> "6677676") }
}
