package fi.liikennevirasto.digiroad2.user

import fi.liikennevirasto.digiroad2.Point
import java.sql.Date
import java.time.LocalDate

import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.asset._

case class Configuration(
                        zoom: Option[Int] = None,
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

  def isOperator(): Boolean = configuration.roles(Role.Operator)

  def isELYMaintainer(): Boolean = configuration.roles(Role.ElyMaintainer)

  def isMunicipalityMaintainer(): Boolean = configuration.roles.isEmpty && configuration.authorizedMunicipalities.nonEmpty

  def isAuthorizedToRead(municipalityCode: Int): Boolean = true

  def isAuthorizedToWrite(municipalityCode: Int): Boolean = isAuthorizedFor(municipalityCode)

  def isAuthorizedToWrite(municipalityCode: Int, administrativeClass: AdministrativeClass): Boolean = isAuthorizedFor(municipalityCode, administrativeClass)

  def isAuthorizedToWriteInArea(areaCode: Int, administrativeClass: AdministrativeClass): Boolean = isAuthorizedForArea(areaCode, administrativeClass)

  private def isAuthorizedFor(municipalityCode: Int): Boolean =
    isOperator() || configuration.authorizedMunicipalities.contains(municipalityCode)

  private def isAuthorizedFor(municipalityCode: Int, administrativeClass: AdministrativeClass): Boolean =
    (isMunicipalityMaintainer() && administrativeClass != State && configuration.authorizedMunicipalities.contains(municipalityCode)) || (isELYMaintainer() && configuration.authorizedMunicipalities.contains(municipalityCode)) || isOperator()

  private def isAuthorizedForArea(areaCode: Int, administrativeClass: AdministrativeClass): Boolean =
    isOperator() || (isServiceRoadMaintainer() && configuration.authorizedAreas.contains(areaCode))
}

object Role {
  val Operator = "operator"
  val Viewer = "viewer"
  val ElyMaintainer = "elyMaintainer"
  val ServiceRoadMaintainer = "serviceRoadMaintainer"
}