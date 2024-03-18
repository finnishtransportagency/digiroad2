package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{DummyEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{DamagedByThawService, Measures}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import java.util.UUID
import scala.util.Random

class DamagedByThawRepeaterSpec extends FunSuite with Matchers {
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  val damagedByThawRepeaterMockServices: DamagedByThawRepeater = new DamagedByThawRepeater() {
    override val roadLinkService: RoadLinkService = mockRoadLinkService
    override val damagedByThawService: DamagedByThawService = new DamagedByThawService(mockRoadLinkService, new DummyEventBus)
  }
  val testMunicipalityCode = 99
  val testUsername = "test_username"

  def buildDateValue(datePeriods: Seq[DatePeriodValue]): Seq[DynamicProperty] = {
    Seq(DynamicProperty("spring_thaw_period", "date_period", false, datePeriods.map(x => DynamicPropertyValue(DatePeriodValue.toMap(x)))))
  }

  def getChangeSetValue(changeSet: ChangeSet): Seq[Seq[DynamicPropertyValue]] = {
    changeSet.valueAdjustments.map(_.asset.value.map(_.asInstanceOf[DynamicValue].value).get.properties.find(x => x.publicId === "spring_thaw_period").get.values)
  }

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  private def createTestAssets(newLinearAssets: Seq[NewLinearAsset]) = {
    newLinearAssets.map { newAsset =>
      damagedByThawRepeaterMockServices.damagedByThawService.createWithoutTransaction(DamagedByThaw.typeId,
        newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure),
        testUsername, 0L, None, verifiedBy = None)
    }
  }

  val linkId: String = generateRandomLinkId()
  val testRoadLink: RoadLink = RoadLink(linkId, Seq(), 100.0, State, 3, TrafficDirection.BothDirections, Motorway, None, None)

  val checkedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("1"))))
  val unCheckedCheckbox = Seq(DynamicProperty("annual_repetition", "checkbox", false, Seq(DynamicPropertyValue("0"))))

  private val today = damagedByThawRepeaterMockServices.today
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

  when(damagedByThawRepeaterMockServices.damagedByThawService.roadLinkService.getRoadLinksByMunicipality(testMunicipalityCode, newTransaction = false))
    .thenReturn(Seq(testRoadLink))

  test("ongoing period is not updated") {
    runWithRollback {
      val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(ongoingPeriod)) ++ checkedCheckbox))
      val newLinearAssets = Seq(NewLinearAsset(linkId, 0, 100.0, assetValue, SideCode.TowardsDigitizing.value, 0L, None))
      val ids = createTestAssets(newLinearAssets)

      // Check if created asset needs to be updated
      damagedByThawRepeaterMockServices.updateActivityPeriods(testMunicipalityCode)

      val adjustedAsset = damagedByThawRepeaterMockServices.damagedByThawService.getPersistedAssetsByIds(DamagedByThaw.typeId, ids.toSet, newTransaction = false).head
      val properties = adjustedAsset.value.map(_.asInstanceOf[DynamicValue].value.properties).get.find(_.publicId == damagedByThawRepeaterMockServices.ActivePeriod).get.values
      val period = DatePeriodValue.fromMap(properties.head.value.asInstanceOf[Map[String, String]])
      val startDate = DateParser.stringToDate(period.startDate, DateParser.DatePropertyFormat)
      val endDate = DateParser.stringToDate(period.endDate, DateParser.DatePropertyFormat)

      // Dates should have stayed the same
      startDate should equal(DateParser.stringToDate(ongoingPeriod.startDate, DateParser.DatePropertyFormat))
      endDate should equal(DateParser.stringToDate(ongoingPeriod.endDate, DateParser.DatePropertyFormat))
    }
  }

  test("past period is updated") {
    runWithRollback {
      val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod)) ++ checkedCheckbox))
      val newLinearAssets = Seq(NewLinearAsset(linkId, 0, 100.0, assetValue, SideCode.TowardsDigitizing.value, 0L, None))
      val ids = createTestAssets(newLinearAssets)

      // Check if created asset needs to be updated
      damagedByThawRepeaterMockServices.updateActivityPeriods(testMunicipalityCode)

      val adjustedAsset = damagedByThawRepeaterMockServices.damagedByThawService.getPersistedAssetsByIds(DamagedByThaw.typeId, ids.toSet, newTransaction = false).head
      val properties = adjustedAsset.value.map(_.asInstanceOf[DynamicValue].value.properties).get.find(_.publicId == damagedByThawRepeaterMockServices.ActivePeriod).get.values
      val period = DatePeriodValue.fromMap(properties.head.value.asInstanceOf[Map[String, String]])
      val startDate = DateParser.stringToDate(period.startDate, DateParser.DatePropertyFormat)
      val endDate = DateParser.stringToDate(period.endDate, DateParser.DatePropertyFormat)

      // Dates should have been updated for next year
      startDate should equal(DateParser.stringToDate(pastPeriod.startDate, DateParser.DatePropertyFormat).plusYears(1))
      endDate should equal(DateParser.stringToDate(pastPeriod.endDate, DateParser.DatePropertyFormat).plusYears(1))
    }
  }

  test("future period is not updated") {
    runWithRollback {
      val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(futurePeriod)) ++ checkedCheckbox))
      val newLinearAssets = Seq(NewLinearAsset(linkId, 0, 100.0, assetValue, SideCode.TowardsDigitizing.value, 0L, None))
      val ids = createTestAssets(newLinearAssets)

      // Check if created asset needs to be updated
      damagedByThawRepeaterMockServices.updateActivityPeriods(testMunicipalityCode)

      val adjustedAsset = damagedByThawRepeaterMockServices.damagedByThawService.getPersistedAssetsByIds(DamagedByThaw.typeId, ids.toSet, newTransaction = false).head
      val properties = adjustedAsset.value.map(_.asInstanceOf[DynamicValue].value.properties).get.find(_.publicId == damagedByThawRepeaterMockServices.ActivePeriod).get.values
      val period = DatePeriodValue.fromMap(properties.head.value.asInstanceOf[Map[String, String]])
      val startDate = DateParser.stringToDate(period.startDate, DateParser.DatePropertyFormat)
      val endDate = DateParser.stringToDate(period.endDate, DateParser.DatePropertyFormat)

      // Dates should have stayed the same
      startDate should equal(DateParser.stringToDate(futurePeriod.startDate, DateParser.DatePropertyFormat))
      endDate should equal(DateParser.stringToDate(futurePeriod.endDate, DateParser.DatePropertyFormat))
    }
  }

  test("past period is not updated without checkbox") {
    runWithRollback {
      val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod)) ++ unCheckedCheckbox))
      val newLinearAssets = Seq(NewLinearAsset(linkId, 0, 100.0, assetValue, SideCode.TowardsDigitizing.value, 0L, None))
      val ids = createTestAssets(newLinearAssets)

      // Check if created asset needs to be updated
      damagedByThawRepeaterMockServices.updateActivityPeriods(testMunicipalityCode)

      val adjustedAsset = damagedByThawRepeaterMockServices.damagedByThawService.getPersistedAssetsByIds(DamagedByThaw.typeId, ids.toSet, newTransaction = false).head
      val properties = adjustedAsset.value.map(_.asInstanceOf[DynamicValue].value.properties).get.find(_.publicId == damagedByThawRepeaterMockServices.ActivePeriod).get.values
      val period = DatePeriodValue.fromMap(properties.head.value.asInstanceOf[Map[String, String]])
      val startDate = DateParser.stringToDate(period.startDate, DateParser.DatePropertyFormat)
      val endDate = DateParser.stringToDate(period.endDate, DateParser.DatePropertyFormat)

      // Dates should have stayed the same
      startDate should equal(DateParser.stringToDate(pastPeriod.startDate, DateParser.DatePropertyFormat))
      endDate should equal(DateParser.stringToDate(pastPeriod.endDate, DateParser.DatePropertyFormat))
    }
  }

  test("in case of past period and future period, only one is updated") {
    runWithRollback {
      val assetValue = DynamicValue(DynamicAssetValue(buildDateValue(Seq(pastPeriod, futurePeriod)) ++ checkedCheckbox))
      val newLinearAssets = Seq(NewLinearAsset(linkId, 0, 100.0, assetValue, SideCode.TowardsDigitizing.value, 0L, None))
      val ids = createTestAssets(newLinearAssets)

      // Check if created asset needs to be updated
      damagedByThawRepeaterMockServices.updateActivityPeriods(testMunicipalityCode)

      val adjustedAsset = damagedByThawRepeaterMockServices.damagedByThawService.getPersistedAssetsByIds(DamagedByThaw.typeId, ids.toSet, newTransaction = false).head
      val properties = adjustedAsset.value.map(_.asInstanceOf[DynamicValue].value.properties).get.find(_.publicId == damagedByThawRepeaterMockServices.ActivePeriod).get.values

      // Future date period should not be updated
      val futureDateProps = properties.find(props => DatePeriodValue.fromMap(props.value.asInstanceOf[Map[String, String]]) == futurePeriod).get
      // Past date period should have been updated to next year
      val pastDateProps = properties.find(props => props != futureDateProps).get
      val period = DatePeriodValue.fromMap(pastDateProps.value.asInstanceOf[Map[String, String]])
      val startDate = DateParser.stringToDate(period.startDate, DateParser.DatePropertyFormat)
      val endDate = DateParser.stringToDate(period.endDate, DateParser.DatePropertyFormat)
      // Dates should have been updated
      startDate should equal(DateParser.stringToDate(pastPeriod.startDate, DateParser.DatePropertyFormat).plusYears(1))
      endDate should equal(DateParser.stringToDate(pastPeriod.endDate, DateParser.DatePropertyFormat).plusYears(1))

    }
  }
}