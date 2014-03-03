package fi.liikennevirasto.digiroad2.municipality

trait MunicipalityProvider {

  def getMunicipalities(elyNumbers: Set[Int]): Seq[Int]
}
