package fi.liikennevirasto.digiroad2.linearasset

import java.text.SimpleDateFormat

import fi.liikennevirasto.digiroad2.{Point, asset}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.{FunSuite, Matchers}


class DamagedByThawFillerSpec extends FunSuite with Matchers {

  object damagedByThawFiller extends DamagedByThawFiller

  private val dateFormat = "dd.MM.yyyy"
  private val formatter = DateTimeFormat.forPattern(dateFormat)

  def dateToString(date: DateTime): String = {
    date.toString(dateFormat)
  }

  def stringToDate(date: String): DateTime = {
    formatter.parseDateTime(date)
  }

  def buildDateValue(datePeriods: Seq[DatePeriodValue]): Seq[DynamicProperty] = {
    Seq(DynamicProperty("spring_thaw_period", "date_period", false, datePeriods.map(x => DynamicPropertyValue(DatePeriodValue.toMap(x)))))
  }

  def getChangeSetValue(changeSet: ChangeSet): Seq[Seq[DynamicPropertyValue]] = {
    changeSet.valueAdjustments.map(_.asset.value.map(_.asInstanceOf[DynamicValue].value).get.properties.find(x => x.publicId === "spring_thaw_period").get.values)
  }

  val checkedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("1"))))
  val unCheckedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("1"))))

  private val today = DateTime.now()
  val oneYear = 1
  val oneWeek = 1
  val oneMonth = 1
  val ongoingPeriod = DatePeriodValue(dateToString(today.minusYears(oneYear).minusWeeks(oneWeek)), dateToString(today.plusWeeks(oneYear)))
  val futurePeriod = DatePeriodValue(dateToString(today.plusWeeks(oneWeek)), dateToString(today.plusMonths(oneMonth)))
  val pastPeriod = DatePeriodValue(dateToString(today.minusMonths(oneMonth)), dateToString(today.minusWeeks(oneWeek)))
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
    adjustedValue.startDate should be (dateToString(stringToDate(pastPeriod.startDate).plusYears(1)))
    adjustedValue.endDate should be (dateToString(stringToDate(pastPeriod.endDate).plusYears(1)))
  }

  test("ongoing period is not updated") {
    val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(ongoingPeriod)) ++ checkedCheckbox))
    val linearAssets = Map(1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(assetValue), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))
    val (filledTopology, changeSet) = damagedByThawFiller.fillTopology(topology, linearAssets, DamagedByThaw.typeId)
    getChangeSetValue(changeSet) should be (empty)
  }



}