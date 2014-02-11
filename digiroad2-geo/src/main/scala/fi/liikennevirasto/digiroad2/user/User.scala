package fi.liikennevirasto.digiroad2.user

case class Configuration(
    zoom: Option[Long] = None,
    east: Option[Long] = None,
    north: Option[Long] = None,
    municipalityNumber: Option[Long]  = None,
    authorizedMunicipalities: Set[Long] = Set()
)
case class User(id: Long, username: String, configuration: Configuration)
