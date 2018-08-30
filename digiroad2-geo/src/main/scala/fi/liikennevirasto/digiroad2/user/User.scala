package fi.liikennevirasto.digiroad2.user

import java.sql.Date
import java.time.LocalDate

import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.asset._

case class Configuration(
                        zoom: Option[Long] = None,
                        east: Option[Long] = None,
                        north: Option[Long] = None,
                        municipalityNumber: Option[Int]  = None,
                        authorizedMunicipalities: Set[Int] = Set(),
                        authorizedAreas: Set[Int] = Set(),
                        roles: Set[String] = Set(),
                        lastNotificationDate: Option[String] = None,
                        lastLoginDate: Option[String] = None
                        )
case class User(id: Long, username: String, configuration: Configuration, name: Option[String] = None) {
  def hasWriteAccess() = !isViewer()

  def isViewer() = configuration.roles(Role.Viewer)

  def isServiceRoadMaintainer(): Boolean = configuration.roles(Role.ServiceRoadMaintainer)

  def isViiteUser(): Boolean = configuration.roles(Role.ViiteUser)

  def hasViiteWriteAccess(): Boolean = configuration.roles(Role.ViiteUser)

  def isOperator(): Boolean = {
    configuration.roles(Role.Operator)
  }

  //Todo change to ELY Maintainer
  def isBusStopMaintainer(): Boolean = {
    configuration.roles(Role.BusStopMaintainer)
  }

  def isMunicipalityMaintainer(): Boolean = configuration.roles.isEmpty || (configuration.roles(Role.Premium) && configuration.roles.size == 1)

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
  // TODO note this role should be change in newuser.html too
  val Operator = "operator"
  // TODO Could be deleted
  val Administrator = "administrator"
  // TODO Rename to municipality Maintainer
  val Premium = "premium"
  val Viewer = "viewer"
  val ViiteUser = "viite"
  //TODO change to ELY Maintainer and replace DBase
  val BusStopMaintainer = "busStopMaintainer"
  val ServiceRoadMaintainer = "serviceRoadMaintainer"
}