package fi.liikennevirasto.digiroad2.municipality.oracle

import fi.liikennevirasto.digiroad2.dao.OracleMunicipalityProvider
import org.scalatest.{FunSuite, Matchers, Tag}

class OracleMunicipalityProviderSpec extends FunSuite with Matchers {
  val provider = new OracleMunicipalityProvider
  val elyCodeForLappi = Set(1)
  val elyCodeForAhvenanmaa = Set(0)
  val elyCodesForLappiAndAhvenanmaa = elyCodeForLappi ++ elyCodeForAhvenanmaa
  val municipalitiesForLappiEly = Set(47, 148, 240, 241, 261, 273, 320, 498, 583, 614, 683, 698, 732, 742, 751, 758, 845, 851, 854, 890, 976)
  val municipalitiesForAahvenanmaaEly = Set(35, 43, 60, 62, 65, 76, 170, 295, 318, 417, 438, 478, 736, 766, 771, 941)

  test("load municipality numbers for one ELY code", Tag("db")) {
    val municipalities = provider.getMunicipalities(elyCodeForAhvenanmaa)
    municipalities shouldBe 'nonEmpty
    municipalities.toSet should equal (municipalitiesForAahvenanmaaEly)
  }

  test("load municipality numbers for several ELY codes", Tag("db")) {
    val municipalities = provider.getMunicipalities(elyCodesForLappiAndAhvenanmaa)
    municipalities shouldBe 'nonEmpty
    municipalities.toSet should equal (municipalitiesForLappiEly ++ municipalitiesForAahvenanmaaEly)
  }
}
