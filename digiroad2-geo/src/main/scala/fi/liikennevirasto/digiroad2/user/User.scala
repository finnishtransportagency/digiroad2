package fi.liikennevirasto.digiroad2.user

import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.asset._


case class Configuration(
                        zoom: Option[Int] = None,
                        east: Option[Long] = None,
                        north: Option[Long] = None,
                        assetType: Option[Int] = None,
                        municipalityNumber: Option[Int]  = None,
                        authorizedMunicipalities: Set[Int] = Set(),
                        authorizedAreas: Set[Int] = Set(),
                        roles: Set[String] = Set(),
                        lastNotificationDate: Option[String] = None,
                        lastLoginDate: Option[String] = None
                        )

object ElyExceptionsForState {
  val municipalitiesForAhvenanmaaEly = Set(35, 43, 60, 62, 65, 76, 170, 295, 318, 417, 438, 478, 736, 766, 771, 941)

}


case class User(id: Long, username: String, configuration: Configuration, name: Option[String] = None) {
  def isNotInDigiroad(): Boolean = {
    id == 0
  }

  def hasWriteAccess() = !isViewer()

  def isViewer() = configuration.roles(Role.Viewer)

  def isServiceRoadMaintainer(): Boolean = configuration.roles(Role.ServiceRoadMaintainer)

  def isOperator(): Boolean = configuration.roles(Role.Operator)

  def isELYMaintainer(): Boolean = configuration.roles(Role.ElyMaintainer)
  
  def isLaneMaintainer(): Boolean = configuration.roles(Role.LaneMaintainer)

  def isMunicipalityMaintainer(): Boolean = configuration.roles.isEmpty && configuration.authorizedMunicipalities.nonEmpty

  def isAuthorizedToRead(municipalityCode: Int): Boolean = true

  def isAuthorizedToWrite(municipalityCode: Int): Boolean = isAuthorizedFor(municipalityCode)

  def isAuthorizedToWrite(municipalityCode: Int, administrativeClass: AdministrativeClass): Boolean = isAuthorizedFor(municipalityCode, administrativeClass)

  def isAuthorizedToWriteInArea(areaCode: Int, administrativeClass: AdministrativeClass): Boolean = isAuthorizedForArea(areaCode, administrativeClass)

  private def isAuthorizedFor(municipalityCode: Int): Boolean =
    isOperator() || configuration.authorizedMunicipalities.contains(municipalityCode)

  def isAnElyException( municipalityCode: Int): Boolean = {

    /* Users in Ahvenanmaa need to edit state information */
    if ( ElyExceptionsForState.municipalitiesForAhvenanmaaEly.contains(municipalityCode) )
      isAuthorizedFor(municipalityCode)
    else
      false
  }

  private def isAuthorizedFor(municipalityCode: Int, administrativeClass: AdministrativeClass): Boolean = {
    val isElyException = isAnElyException(municipalityCode)
    val isMunicipalityMaintainerAndIsAuthorized = isMunicipalityMaintainer() && administrativeClass != State && configuration.authorizedMunicipalities.contains(municipalityCode)
    val isElyMaintainerAndIsAuthorized = isELYMaintainer() && administrativeClass != Municipality && configuration.authorizedMunicipalities.contains(municipalityCode)

    isElyException || isMunicipalityMaintainerAndIsAuthorized  || isElyMaintainerAndIsAuthorized  || isLaneMaintainer() || isOperator()
  }

  private def isAuthorizedForArea(areaCode: Int, administrativeClass: AdministrativeClass): Boolean =
    isOperator() || (isServiceRoadMaintainer() && configuration.authorizedAreas.contains(areaCode))
}

object Role {
  val Operator = "operator"
  val Viewer = "viewer"
  val ElyMaintainer = "elyMaintainer"
  val ServiceRoadMaintainer = "serviceRoadMaintainer"
  val LaneMaintainer = "laneMaintainer"
}