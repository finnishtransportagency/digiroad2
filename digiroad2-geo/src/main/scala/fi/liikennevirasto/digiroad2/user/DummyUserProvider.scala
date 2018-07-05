package fi.liikennevirasto.digiroad2.user

import fi.liikennevirasto.digiroad2.Point


class DummyUserProvider extends UserProvider {
  def createUser(username: String, config: Configuration, name: Option[String]) = {
    User(0, username, Configuration(), name)
  }
  def getUser(username: String): Option[User] = {
    Some(User(0, username, Configuration(zoom = Some(8), east = Some(373560), north = Some(6677676), municipalityNumber = Some(235), authorizedMunicipalities = Set(235)), Some("John Tester")))
  }
  def getAuthenticatedUser(username: String, password: String): Option[User] = getUser(username)

  def getUserConfiguration(): Configuration = {
    getCurrentUser.username match {
      case "tamperetest" => Configuration(zoom = Some(7), east = Some(328308), north = Some(6822545), municipalityNumber = Some(837), authorizedMunicipalities = Set(837))
      case _ => Configuration(zoom = Some(8), east = Some(373560), north = Some(6677676), municipalityNumber = Some(235))
    }
  }
  def saveUser(user: User): User = user

  def getCenterViewMunicipality(municipalityId: Int): Option[MapViewZoom] = {
    Some(MapViewZoom(Point(390000, 6900000), 5))
  }

  def getCenterViewArea(area: Int): Option[MapViewZoom] = {
    Some(MapViewZoom(Point(390000, 6900000), 5))
  }

  def getCenterViewEly(ely: Int): Option[MapViewZoom] = {
    Some(MapViewZoom(Point(390000, 6900000), 5))
  }

  def updateUserConfiguration(user: User): User = {
    User(0, "username", Configuration())
  }
}
