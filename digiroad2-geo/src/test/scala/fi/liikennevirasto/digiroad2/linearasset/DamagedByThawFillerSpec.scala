package fi.liikennevirasto.digiroad2.linearasset


import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import org.scalatest.{FunSuite, Matchers}
import java.util.UUID
import scala.util.Random


class DamagedByThawFillerSpec extends FunSuite with Matchers {

  object damagedByThawFiller extends DamagedByThawFiller

  def buildDateValue(datePeriods: Seq[DatePeriodValue]): Seq[DynamicProperty] = {
    Seq(DynamicProperty("spring_thaw_period", "date_period", false, datePeriods.map(x => DynamicPropertyValue(DatePeriodValue.toMap(x)))))
  }

  def getChangeSetValue(changeSet: ChangeSet): Seq[Seq[DynamicPropertyValue]] = {
    changeSet.valueAdjustments.map(_.value.asInstanceOf[DynamicValue].value).map(_.properties.find(x => x.publicId === "spring_thaw_period").get.values)
  }

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"
  val linkId: String = generateRandomLinkId()

  val checkedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("1"))))
  val unCheckedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("0"))))

  private val today = damagedByThawFiller.today
  val oneYear = 1
  val oneWeek = 1
  val oneMonth = 1
  val ongoingPeriod =
    DatePeriodValue(
      DateParser.dateToString(today.minusYears(oneYear).minusWeeks(oneWeek), DateParser.DatePropertyFormat),
      DateParser.dateToString(today.plusWeeks(oneYear), DateParser.DatePropertyFormat)
    )
  val futurePeriod =
    DatePeriodValue(
      DateParser.dateToString(today.plusWeeks(oneWeek), DateParser.DatePropertyFormat),
      DateParser.dateToString(today.plusMonths(oneMonth), DateParser.DatePropertyFormat)
    )
  val pastPeriod =
    DatePeriodValue(
      DateParser.dateToString(today.minusMonths(oneMonth), DateParser.DatePropertyFormat),
      DateParser.dateToString(today.minusWeeks(oneWeek), DateParser.DatePropertyFormat)
    )
  val topology = Seq(RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None))

  test("ongoing period is not updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(ongoingPeriod)) ++ checkedCheckbox))
    val linearAssets = Map(linkId -> damagedByThawFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId, 2, Some(assetValue),
      0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(damagedByThawFiller.toRoadLinkForFillTopology).head))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology.map(damagedByThawFiller.toRoadLinkForFillTopology), linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }

  test("past period is updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod)) ++ checkedCheckbox))
    val linearAssets = Map(linkId -> damagedByThawFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId, 2, Some(assetValue),
      0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(damagedByThawFiller.toRoadLinkForFillTopology).head))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology.map(damagedByThawFiller.toRoadLinkForFillTopology), linearAssets, DamagedByThaw.typeId)
    val adjustedValue = DatePeriodValue.fromMap(getChangeSetValue(changeSet).head.head.value.asInstanceOf[Map[String, String]])
    adjustedValue.startDate should be(
      DateParser.dateToString(
        DateParser.stringToDate(pastPeriod.startDate, DateParser.DatePropertyFormat).plusYears(1),
        DateParser.DatePropertyFormat)
    )
    adjustedValue.endDate should be(
      DateParser.dateToString(
        DateParser.stringToDate(pastPeriod.endDate, DateParser.DatePropertyFormat).plusYears(1),
        DateParser.DatePropertyFormat)
    )
  }

  test("future period is not updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(futurePeriod)) ++ checkedCheckbox))
    val linearAssets = Map(linkId -> damagedByThawFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId, 2, Some(assetValue),
      0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(damagedByThawFiller.toRoadLinkForFillTopology).head))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology.map(damagedByThawFiller.toRoadLinkForFillTopology), linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }

  test("past period is not updated without checkbox") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod)) ++ unCheckedCheckbox))
    val linearAssets = Map(linkId -> damagedByThawFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId, 2, Some(assetValue),
      0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(damagedByThawFiller.toRoadLinkForFillTopology).head))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology.map(damagedByThawFiller.toRoadLinkForFillTopology), linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }

  test("in case of past period and future period, only one is updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod, futurePeriod)) ++ checkedCheckbox))
    val linearAssets = Map(linkId -> damagedByThawFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId, 2, Some(assetValue),
      0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(damagedByThawFiller.toRoadLinkForFillTopology).head))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology.map(damagedByThawFiller.toRoadLinkForFillTopology), linearAssets, DamagedByThaw.typeId)
    val adjustedValues = getChangeSetValue(changeSet).head.map(_.value.asInstanceOf[Map[String, String]]).map(DatePeriodValue.fromMap)
    adjustedValues.contains(futurePeriod) should be (true)
    adjustedValues.contains(pastPeriod) should be (false)
  }
}