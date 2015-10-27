package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ProhibitionValidityPeriod}
import org.scalatest.{Matchers, FunSuite}

class TimeDomainParserSpec extends FunSuite with Matchers {
  val parser = new TimeDomainParser

  test("simple") {
    parser.parse("[(t7h21){h10}]") should be(Right(Seq(ProhibitionValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday))))
    parser.parse("[(t2){d5}]") should be(Right(Seq(ProhibitionValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t7h21){h10}]") should be(Right(Seq(ProhibitionValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday))))
  }

  test("validity period without date specification spans over all dates") {
    parser.parse("[(h6){h4}]") should be(Right(Seq(
      ProhibitionValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday),
      ProhibitionValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Saturday),
      ProhibitionValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Sunday))))
    parser.parse("[(h23){h1}]") should be(Right(Seq(
      ProhibitionValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday),
      ProhibitionValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Saturday),
      ProhibitionValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Sunday))))
  }

  test("and") {
    parser.parse("[[(h8){h8}]*[(t2){d5}]]") should be(Right(Seq(ProhibitionValidityPeriod(8, 16, ValidityPeriodDayOfWeek.Weekday))))
  }

  test("* distributes over its operands") {
    parser.parse("[(t2){d5}]*[(h6){h4}]") should be(Right(Seq(ProhibitionValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t2){d5}]*[(h23){h1}]") should be(Right(Seq(ProhibitionValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t7){d1}]*[(h5){h4}]") should be(Right(Seq(ProhibitionValidityPeriod(5, 9, ValidityPeriodDayOfWeek.Saturday))))
    parser.parse("[[(t2){d5}]+[(t1){d1}]]*[(h5){h4}]") should be(Right(Seq(
      ProhibitionValidityPeriod(5, 9, ValidityPeriodDayOfWeek.Weekday),
      ProhibitionValidityPeriod(5, 9, ValidityPeriodDayOfWeek.Sunday))))
    parser.parse("[[[(h18){h13}]+[(h9){h6}]*[(t2){d5}]]+[(t7){d2}]]") should be(Right(Seq(
      ProhibitionValidityPeriod(18, 7, ValidityPeriodDayOfWeek.Weekday),
      ProhibitionValidityPeriod(9, 15, ValidityPeriodDayOfWeek.Weekday),
      ProhibitionValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Saturday),
      ProhibitionValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Sunday))))
    parser.parse("[[(t2){d5}]*[[(h7){h2}]+[(h15){h3}]]]") should be(Right(Seq(
      ProhibitionValidityPeriod(7, 9, ValidityPeriodDayOfWeek.Weekday),
      ProhibitionValidityPeriod(15, 18, ValidityPeriodDayOfWeek.Weekday))))
  }

  test("doesn't crash") {
    val resource = getClass.getResource("time_domain_test_values.txt")
    val lines = scala.io.Source.fromURL(resource).getLines().toSeq
    println(s"Amount of lines: ${lines.length}")
    val results = lines.map { line =>
      (line, parser.parse(line))
    }
    println(s"Amount of successfully parsed time domain strings: ${results.count { x => x._2.isRight }}")
    println(s"Amount of failed parsed time domain strings: ${results.count { x => x._2.isLeft }}")
    println(s"Failed time domain strings: ")
    results.filter { x => x._2.isLeft }.foreach { case (input, r) =>
      println(s"\tParsing failed on input: $input due to ${r.left.get}")
    }
  }
}
