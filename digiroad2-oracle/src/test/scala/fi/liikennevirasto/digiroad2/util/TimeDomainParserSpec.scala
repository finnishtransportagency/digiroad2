package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ProhibitionValidityPeriod}
import org.scalatest.{Matchers, FunSuite}

class TimeDomainParserSpec extends FunSuite with Matchers {
  val parser = new TimeDomainParser

  test("simple") {
    parser.parse("[(h6){h4}]") should be(Some(Seq(ProhibitionValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(h23){h1}]") should be(Some(Seq(ProhibitionValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t7h21){h10}]") should be(Some(Seq(ProhibitionValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday))))
    parser.parse("[(t2){d5}]") should be(Some(Seq(ProhibitionValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t7h21){h10}]") should be(Some(Seq(ProhibitionValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday))))
  }

  test("and") {
    parser.parse("[[(h8){h8}]*[(t2){d5}]]") should be(Some(Seq(ProhibitionValidityPeriod(8, 16, ValidityPeriodDayOfWeek.Weekday))))
  }
}
