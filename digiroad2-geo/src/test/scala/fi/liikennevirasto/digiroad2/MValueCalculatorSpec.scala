package fi.liikennevirasto.digiroad2
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import org.scalatest.{FunSuite, Matchers}

class MValueCalculatorSpec extends FunSuite with Matchers  {
  test("Test calculator: link lengthen, asset was half in old") {
    val newPosition4 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 5, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)

    newPosition4._1 should be(0)
    newPosition4._2 should be(10)
  }

  test("Test calculator: link lengthened , asset fill whole link") {
    val newPosition5 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 10, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)

    newPosition5._1 should be(0)
    newPosition5._2 should be(20)

  }

  test("Test calculator: link lengthened , asset is split from start") {
    val newPosition5 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 5, endMeasure = 10, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)
    newPosition5._1 should be(10)
    newPosition5._2 should be(20)
  }

  test("Test calculator: link lengthened , asset is split from end") {
    val newPosition5 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 5, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)

    newPosition5._1 should be(0)
    newPosition5._2 should be(10)
  }

  test("Test calculator: link shortened , asset is bigger than new location") {
    val newPosition5 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 0, endMeasure = 10, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 5), 5)

    newPosition5._1 should be(0)
    newPosition5._2 should be(5)
  }

  test("Test calculator: asset middle of link, keep relative size") {

    val newPosition2 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 2, endMeasure = 4, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)

    newPosition2._1 should be(4)
    newPosition2._2 should be(8)

  }

  test("Test calculator: link split from begin,  asset cover link only partially") {
    val newPosition3 = MValueCalculator.calculateNewMValues(
      AssetLinearReference(id = 1, startMeasure = 2, endMeasure = 10, sideCode = 2),
      Projection(
        oldStart = 0, oldEnd = 10, newStart = 0, newEnd = 20), 20)

    newPosition3._1 should be(4)
    newPosition3._2 should be(20)

  }
}
