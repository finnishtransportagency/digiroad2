package fi.liikennevirasto.digiroad2.user;

class DummyUserProvider extends UserProvider {
  def getUserConfiguration(): Map[String, String] = { Map("zoom" -> "5", "east" -> "123456") }
}
