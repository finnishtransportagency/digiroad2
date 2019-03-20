package fi.liikennevirasto.digiroad2.linearasset


import fi.liikennevirasto.digiroad2.{Point}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.{FunSuite, Matchers}


class DamagedByThawFillerSpec extends FunSuite with Matchers {

  object damagedByThawFiller extends DamagedByThawFiller

  def buildDateValue(datePeriods: Seq[DatePeriodValue]): Seq[DynamicProperty] = {
    Seq(DynamicProperty("spring_thaw_period", "date_period", false, datePeriods.map(x => DynamicPropertyValue(DatePeriodValue.toMap(x)))))
  }

  def getChangeSetValue(changeSet: ChangeSet): Seq[Seq[DynamicPropertyValue]] = {
    changeSet.valueAdjustments.map(_.asset.value.map(_.asInstanceOf[DynamicValue].value).get.properties.find(x => x.publicId === "spring_thaw_period").get.values)
  }

  val checkedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("1"))))
  val unCheckedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("0"))))

  private val today = damagedByThawFiller.today
  val oneYear = 1
  val oneWeek = 1
  val oneMonth = 1
  val ongoingPeriod = DatePeriodValue(damagedByThawFiller.dateToString(today.minusYears(oneYear).minusWeeks(oneWeek)), damagedByThawFiller.dateToString(today.plusWeeks(oneYear)))
  val futurePeriod = DatePeriodValue(damagedByThawFiller.dateToString(today.plusWeeks(oneWeek)), damagedByThawFiller.dateToString(today.plusMonths(oneMonth)))
  val pastPeriod = DatePeriodValue(damagedByThawFiller.dateToString(today.minusMonths(oneMonth)), damagedByThawFiller.dateToString(today.minusWeeks(oneWeek)))
  val topology = Seq(RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None))

  test("ongoing period is not updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(ongoingPeriod)) ++ checkedCheckbox))
    val linearAssets = Map(1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(assetValue), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology, linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }

  test("past period is updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod)) ++ checkedCheckbox))
    val linearAssets = Map(1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(assetValue), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology, linearAssets, DamagedByThaw.typeId)
    val adjustedValue = DatePeriodValue.fromMap(getChangeSetValue(changeSet).head.head.value.asInstanceOf[Map[String, String]])
    adjustedValue.startDate should be (damagedByThawFiller.dateToString(damagedByThawFiller.stringToDate(pastPeriod.startDate).plusYears(1)))
    adjustedValue.endDate should be (damagedByThawFiller.dateToString(damagedByThawFiller.stringToDate(pastPeriod.endDate).plusYears(1)))
  }

  test("future period is not updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(futurePeriod)) ++ checkedCheckbox))
    val linearAssets = Map(1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(assetValue), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology, linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }

  test("past period is not updated without checkbox") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod)) ++ unCheckedCheckbox))
    val linearAssets = Map(1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(assetValue), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology, linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }

  test("in case of past period and future period, only one is updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod, futurePeriod)) ++ checkedCheckbox))
    val linearAssets = Map(1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(assetValue), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology, linearAssets, DamagedByThaw.typeId)
    val adjustedValues = getChangeSetValue(changeSet).head.map(_.value.asInstanceOf[Map[String, String]]).map(DatePeriodValue.fromMap)
    adjustedValues.contains(futurePeriod) should be (true)
    adjustedValues.contains(pastPeriod) should be (false)
  }
}