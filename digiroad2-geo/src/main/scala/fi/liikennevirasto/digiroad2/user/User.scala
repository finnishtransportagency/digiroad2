package fi.liikennevirasto.digiroad2.user

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.asset._

case class Configuration(
                        zoom: Option[Long] = None,
                        east: Option[Long] = None,
                        north: Option[Long] = None,
                        municipalityNumber: Option[Int]  = None,
                        authorizedMunicipalities: Set[Int] = Set(),
                        authorizedAreas: Set[Int] = Set(),
                        roles: Set[String] = Set()
                        )
case class MapViewZoom(geometry : Point, zoom: Int)

case class User(id: Long, username: String, configuration: Configuration, name: Option[String] = None) {
  def hasWriteAccess() = !isViewer()

  def isViewer() = configuration.roles(Role.Viewer)

  def isServiceRoadMaintainer(): Boolean = configuration.roles(Role.ServiceRoadMaintainer)

  def isViiteUser(): Boolean = configuration.roles(Role.ViiteUser)

  def hasViiteWriteAccess(): Boolean = configuration.roles(Role.ViiteUser)

  def isOperator(): Boolean = {
    configuration.roles(Role.Operator)
  }
  //ely
  def isBusStopMaintainer(): Boolean = {
    configuration.roles(Role.BusStopMaintainer)
  }

  def isMunicipalityMaintainer(): Boolean = configuration.roles.isEmpty

  def hasEarlyAccess(): Boolean = {
    configuration.roles(Role.Premium) || configuration.roles(Role.Operator) || configuration.roles(Role.BusStopMaintainer)
  }

  def isAuthorizedToRead(municipalityCode: Int): Boolean = true

  def isAuthorizedToWrite(municipalityCode: Int): Boolean = isAuthorizedFor(municipalityCode)

  def isAuthorizedToWrite(municipalityCode: Int, administrativeClass: AdministrativeClass): Boolean = isAuthorizedFor(municipalityCode, administrativeClass)

  def isAuthorizedToWriteInArea(areaCode: Int, administrativeClass: AdministrativeClass): Boolean = isAuthorizedForArea(areaCode, administrativeClass)

  private def isAuthorizedFor(municipalityCode: Int): Boolean =
    isOperator() || configuration.authorizedMunicipalities.contains(municipalityCode)

  private def isAuthorizedFor(municipalityCode: Int, administrativeClass: AdministrativeClass): Boolean =
    (isMunicipalityMaintainer() && administrativeClass != State && configuration.authorizedMunicipalities.contains(municipalityCode)) || (isBusStopMaintainer() && configuration.authorizedMunicipalities.contains(municipalityCode)) || isOperator()

  private def isAuthorizedForArea(areaCode: Int, administrativeClass: AdministrativeClass): Boolean =
    isOperator() || (isServiceRoadMaintainer() && configuration.authorizedAreas.contains(areaCode))
}

object Role {
  val Operator = "operator"
  val Administrator = "administrator"
  val Premium = "premium"
  val Viewer = "viewer"
  val ViiteUser = "viite"
  val BusStopMaintainer = "busStopMaintainer"
  val ServiceRoadMaintainer = "serviceRoadMaintainer"
}