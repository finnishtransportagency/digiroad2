package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ValidityPeriod}
import org.scalatest.{Matchers, FunSuite}

class TimeDomainParserSpec extends FunSuite with Matchers {
  val parser = new TimeDomainParser

  test("simple") {
    parser.parse("[(t7h21){h10}]") should be(Right(Seq(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday))))
    parser.parse("[(t2){d5}]") should be(Right(Seq(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t1h21){h10}]") should be(Right(Seq(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Sunday))))
  }

  test("validity period without date specification spans over all dates") {
    parser.parse("[(h6){h4}]") should be(Right(Seq(
      ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday),
      ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Saturday),
      ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Sunday))))
    parser.parse("[(h23){h1}]") should be(Right(Seq(
      ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday),
      ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Saturday),
      ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Sunday))))
  }

  test("and") {
    parser.parse("[[(h8){h8}]*[(t2){d5}]]") should be(Right(Seq(ValidityPeriod(8, 16, ValidityPeriodDayOfWeek.Weekday))))
  }

  test("* distributes over its operands") {
    parser.parse("[(t2){d5}]*[(h6){h4}]") should be(Right(Seq(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t2){d5}]*[(h23){h1}]") should be(Right(Seq(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday))))
    parser.parse("[(t7){d1}]*[(h5){h4}]") should be(Right(Seq(ValidityPeriod(5, 9, ValidityPeriodDayOfWeek.Saturday))))
    parser.parse("[[(t2){d5}]+[(t1){d1}]]*[(h5){h4}]") should be(Right(Seq(
      ValidityPeriod(5, 9, ValidityPeriodDayOfWeek.Weekday),
      ValidityPeriod(5, 9, ValidityPeriodDayOfWeek.Sunday))))
  }

  test("operators evaluate left to right") {
    parser.parse("[[[(h18){h13}]+[(h9){h6}]*[(t2){d5}]]+[(t7){d2}]]") should be(Right(Seq(
      ValidityPeriod(18, 7, ValidityPeriodDayOfWeek.Weekday),
      ValidityPeriod(9, 15, ValidityPeriodDayOfWeek.Weekday),
      ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Saturday),
      ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Sunday))))
    parser.parse("[[(t2){d5}]*[[(h7){h2}]+[(h15){h3}]]]") should be(Right(Seq(
      ValidityPeriod(7, 9, ValidityPeriodDayOfWeek.Weekday),
      ValidityPeriod(15, 18, ValidityPeriodDayOfWeek.Weekday))))
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
    /*    println(s"Failed time domain strings: ")
        results.filter { x => x._2.isLeft }.foreach { case (input, r) =>
          println(s"\tParsing failed on input: $input due to ${r.left.get}")
        }*/
  }
}
