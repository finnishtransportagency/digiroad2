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

  test("or") {
  }

  test("nested") {
  }

  test("* distributes over its operands") {
    // [d2]*[h2] => [d2h2]
    // [[d2]+[d7]]*[h2] => [d2h2]+[d7h2]
  }

  test("doesn't crash") {
    val resource = getClass.getResource("time_domain_test_values.txt")
    val lines = scala.io.Source.fromURL(resource).getLines()
    lines.foreach { line =>
      parser.parse(line)
    }
  }

  test("parses all from conversion database into _something_") {
    // TODO: Doesn't pass yet, might not need to parse every line anyway.
    val resource = getClass.getResource("time_domain_test_values.txt")
    val lines = scala.io.Source.fromURL(resource).getLines()
    lines.foreach { line =>
      println(s"Parse: $line")
      parser.parse(line) should be('defined)
    }
  }
}
