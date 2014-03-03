package fi.liikennevirasto.digiroad2.municipality.oracle

import org.scalatest.{Tag, Matchers, FunSuite}

class OracleMunicipalityProviderSpec extends FunSuite with Matchers {
  val provider = new OracleMunicipalityProvider
  val elyCodeForLapland = 1
  val municipalitiesForLaplandEly = List(47, 148, 240, 241, 261, 273, 320, 498, 583, 614, 683, 698, 732, 742, 751, 758, 845, 851, 854, 890, 976)

  test("load municipality numbers for given ELY code", Tag("db")) {
    val municipalities = provider.getMunicipalities(elyCodeForLapland)
    municipalities shouldBe 'nonEmpty
    municipalities.toSet should equal (municipalitiesForLaplandEly.toSet)
  }
}
