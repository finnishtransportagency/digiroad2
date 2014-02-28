package fi.liikennevirasto.digiroad2.municipality

class DummyMunicipalityProvider extends MunicipalityProvider {
  override def getMunicipalities(elyNumber: Int): Seq[Int] = List(5, 10)
}
