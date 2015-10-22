package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.ProhibitionValidityPeriod
import org.scalatest.{Matchers, FunSuite}

class TimeDomainParserSpec extends FunSuite with Matchers {
  val parser = new TimeDomainParser

  test("hours") {
    parser.parse("[(h6){h4}]") should be(Some(Seq(ProhibitionValidityPeriod(6, 10))))
  }
}
