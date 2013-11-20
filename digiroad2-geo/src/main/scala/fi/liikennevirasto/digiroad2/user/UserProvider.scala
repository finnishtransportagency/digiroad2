package fi.liikennevirasto.digiroad2.user

trait UserProvider {
  def getUserConfiguration(): Map[String, String]
}
