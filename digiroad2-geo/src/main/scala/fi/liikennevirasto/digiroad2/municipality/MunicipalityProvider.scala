package fi.liikennevirasto.digiroad2.municipality

trait MunicipalityProvider {

  def getMunicipalities(elyNumber: Int): Seq[Int]
}
