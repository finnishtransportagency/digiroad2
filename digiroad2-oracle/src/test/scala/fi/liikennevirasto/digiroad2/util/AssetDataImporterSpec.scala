package fi.liikennevirasto.digiroad2.util

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import org.scalatest.{Matchers, FunSuite}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

class AssetDataImporterSpec extends FunSuite with Matchers {
  private val assetDataImporter = new AssetDataImporter {
    override def withDynTransaction(f: => Unit): Unit = f
    override def withDynSession[T](f: => T): T = f
  }

  test("Batch drivers chunck size") {
    assetDataImporter.getBatchDrivers(1, 10000, 1000)
      .map( chunk => (chunk._2 - chunk._1) + 1)
      .foreach { chunkSize => chunkSize shouldBe 1000 }
  }

  test("Split multi-link speed limit assets") {
    TestTransactions.runWithRollback() {
      val originalId = createMultiLinkLinearAsset(20, Seq(LinearAssetSegment(Some(1), 0, 50), LinearAssetSegment(Some(2), 0, 50)))
      insertSpeedLimitValue(originalId, 60)

      assetDataImporter.splitMultiLinkSpeedLimitsToSingleLinkLimits()

      val splitSegments = fetchSpeedLimitSegments(s"split_speedlimit_$originalId")

      splitSegments.length shouldBe 2
      splitSegments(0)._1 shouldNot be(splitSegments(1)._1)
      splitSegments(0)._6 should be(60)
      splitSegments(1)._6 should be(60)
      splitSegments.map(_._3).toSet should be(Set(1, 2))
      splitSegments(0)._7 should be(false)
      splitSegments(1)._7 should be(false)

      val originalSpeedLimitSegments = fetchSpeedLimitSegments("asset_data_importer_spec")

      originalSpeedLimitSegments.length should be(2)
      originalSpeedLimitSegments(0)._7 should be(true)
      originalSpeedLimitSegments(1)._7 should be(true)
      originalSpeedLimitSegments(0)._1 should be(originalId)
      originalSpeedLimitSegments(1)._1 should be(originalId)
    }
  }

  test("Split multi-link total weight limit assets") {
    TestTransactions.runWithRollback() {
      val originalId1 = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(Some(1), 0, 50), LinearAssetSegment(Some(2), 0, 50)))
      val originalId2 = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(Some(3), 0, 50), LinearAssetSegment(Some(4), 0, 50)))
      insertNumericalLimitValue(originalId1, 40000)
      insertNumericalLimitValue(originalId2, 50000)

      assetDataImporter.splitMultiLinkAssetsToSingleLinkAssets(30)

      val splitSegments = (fetchNumericalLimitSegments(s"split_linearasset_$originalId1") ++
                           fetchNumericalLimitSegments(s"split_linearasset_$originalId2")).sortBy(_._3)

      splitSegments.map(_._3).toSet should be(Set(1, 2, 3, 4))
      splitSegments.length shouldBe 4
      splitSegments.map(_._1).toSet.size should be(4)
      splitSegments(0)._6 should be(Some(40000))
      splitSegments(1)._6 should be(Some(40000))
      splitSegments(2)._6 should be(Some(50000))
      splitSegments(3)._6 should be(Some(50000))
      splitSegments.foreach(segment => segment._7 should be (false))

      val originalSpeedLimitSegments = fetchNumericalLimitSegments("asset_data_importer_spec").sortBy(_._3)

      originalSpeedLimitSegments.length should be(4)
      originalSpeedLimitSegments.map(_._1).toSet should be(Set(originalId1, originalId2))
      originalSpeedLimitSegments.foreach { case (_, _, mmlId, _, _, _, floating, validTo, modifiedBy, _) =>
        val now = DateTime.now().plusSeconds(2) // add two seconds because of date time precision in db
        validTo.get.isBefore(now) should be(true)
        floating should be(false)
        modifiedBy should be("expired_splitted_linearasset")
      }
    }
  }

  test("Split multi-link lit road assets") {
    TestTransactions.runWithRollback() {
      val originalId = createMultiLinkLinearAsset(100, Seq(LinearAssetSegment(Some(1), 0, 50), LinearAssetSegment(Some(2), 0, 50)))

      assetDataImporter.splitMultiLinkAssetsToSingleLinkAssets(100)

      val splitSegments = fetchNumericalLimitSegments(s"split_linearasset_$originalId")

      splitSegments.length shouldBe 2
      splitSegments(0)._1 shouldNot be(splitSegments(1)._1)
      splitSegments(0)._6 should be(None)
      splitSegments(1)._6 should be(None)
      splitSegments.map(_._3).toSet should be(Set(1, 2))
      splitSegments(0)._7 should be(false)
      splitSegments(1)._7 should be(false)

      val originalSpeedLimitSegments = fetchNumericalLimitSegments("asset_data_importer_spec")

      originalSpeedLimitSegments.length should be(2)
      val now = DateTime.now().plusSeconds(2)
      originalSpeedLimitSegments(0)._8.get.isBefore(now) should be(true)
      originalSpeedLimitSegments(1)._8.get.isBefore(now) should be(true)
      originalSpeedLimitSegments(0)._1 should be(originalId)
      originalSpeedLimitSegments(1)._1 should be(originalId)
    }
  }

  test("Assign values to lit road properties") {
    TestTransactions.runWithRollback() {
      val litRoadId = createMultiLinkLinearAsset(100, Seq(LinearAssetSegment(Some(1), 0, 50)))
      val numericalLimitId = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(Some(1), 0, 50)))
      insertNumericalLimitValue(numericalLimitId, 40000)

      assetDataImporter.generateValuesForLitRoads()

      val numericalLimits = fetchNumericalLimitSegments("asset_data_importer_spec")

      numericalLimits.find(_._1 == litRoadId).map(_._6) should be(Some(Some(1)))
      numericalLimits.find(_._1 == numericalLimitId).map(_._6) should be(Some(Some(40000)))
    }
  }

  test("Expire split linear asset without mml id") {
    TestTransactions.runWithRollback() {
      val expireAssetId = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(None, 1, 10)), "split_linearasset_1")
      val assetWithMmlId = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(Some(1), 1, 10)), "split_linearasset_1")
      val expiredAssetId = createMultiLinkLinearAsset(30, Seq(LinearAssetSegment(None, 1, 10)), "split_linearasset_1", true)
      val differentAssetTypeId = createMultiLinkLinearAsset(40, Seq(LinearAssetSegment(None, 1, 10)), "split_linearasset_1")

      assetDataImporter.expireSplitAssetsWithoutMml(30)

      val assets = fetchNumericalLimitSegments("split_linearasset_1")
      assets.length shouldBe 4

      val expireAsset = assets.find(_._1 == expireAssetId)
      expireAsset.map(_._7) should be(Some(false))
      expireAsset.map(_._9) should be(Some("expired_asset_without_mml"))
      expireAsset.map(_._8.isDefined) should be(Some(true))
      expireAsset.map(_._10.isDefined) should be(Some(true))


      val assetWithMml = assets.find(_._1 == assetWithMmlId)
      assetWithMml.map(_._7) should be(Some(false))
      assetWithMml.map(_._9) should be(Some(null))
      assetWithMml.map(_._8.isDefined) should be(Some(false))
      assetWithMml.map(_._10.isDefined) should be(Some(false))

      val expiredAsset = assets.find(_._1 == expiredAssetId)
      expiredAsset.map(_._7) should be(Some(false))
      expiredAsset.map(_._9) should be(Some(null))
      expiredAsset.map(_._8.isDefined) should be(Some(true))
      expiredAsset.map(_._10.isDefined) should be(Some(false))

      val differentAssetType = assets.find(_._1 == differentAssetTypeId)
      differentAssetType.map(_._7) should be(Some(false))
      differentAssetType.map(_._9) should be(Some(null))
      differentAssetType.map(_._8.isDefined) should be(Some(false))
      differentAssetType.map(_._10.isDefined) should be(Some(false))
    }
  }

  private def prohibitionSegment(id: Long = 1l,
                                 mmlId: Long = 1l,
                                 startMeasure: Double = 0.0,
                                 endMeasure: Double = 1.0,
                                 municipality: Int = 235,
                                 value: Int = 2,
                                 sideCode: Int = 1,
                                 validityPeriod: Option[String] = None):
  (Long, Long, Double, Double, Int, Int, Int, Option[String]) = {
    (id, mmlId, startMeasure, endMeasure, municipality, value, sideCode, validityPeriod)
  }

  test("Two prohibition segments on the same link produces one asset with two prohibition values") {
    val segment1 = prohibitionSegment()
    val segment2 = prohibitionSegment(id = 2l, value = 4)
    val prohibitionSegments = Seq(segment1, segment2)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Seq[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil)

    val expectedValue = Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty), ProhibitionValue(4, Set.empty, Set.empty))))
    result should be(Seq(Right(PersistedLinearAsset(0l, 1l, 1, expectedValue, 0.0, 1.0, None, None, None, None, false, 190))))
  }

  test("Two prohibition segments on the same link with different side codes produces two assets with one prohibition value") {
    val segment1 = prohibitionSegment(sideCode = 2)
    val segment2 = prohibitionSegment(id = 2l, value = 4, sideCode = 3)
    val prohibitionSegments = Seq(segment1, segment2)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 2, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Right(PersistedLinearAsset(0l, 1l, 3, Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Two-sided prohibition segment and one-sided prohibition segment produces two assets with combined prohibitions on one side") {
    val segment1 = prohibitionSegment()
    val segment2 = prohibitionSegment(id = 2l, value = 4, sideCode = 3)
    val prohibitionSegments = Seq(segment1, segment2)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 2, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Right(PersistedLinearAsset(0l, 1l, 3, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty), ProhibitionValue(4, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Segment without associated road link from VVH is dropped") {
    val segment1 = prohibitionSegment()
    val prohibitionSegments = Seq(segment1)
    val roadLinks: Seq[VVHRoadlink] = Nil

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    result should be(Set(Left("No VVH road link found for mml id 1. 1 dropped.")))
  }

  test("Drop prohibition segments of type maintenance drive and drive to plot") {
    val segment1 = prohibitionSegment(value = 21)
    val segment2 = prohibitionSegment(id = 2l, value = 22)
    val prohibitionSegments = Seq(segment1, segment2)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    result should be(Set(Left("Invalid type for prohibition. 1 dropped."), Left("Invalid type for prohibition. 2 dropped.")))
  }

  test("Adjust segment measurements to road link") {
    val segment1 = prohibitionSegment(endMeasure = 0.5)
    val prohibitionSegments = Seq(segment1)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 1, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1))
  }

  test("Include exception in prohibition value") {
    val segment1 = prohibitionSegment()
    val prohibitionSegments = Seq(segment1)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 1l, 8, 1))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 1, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set(8))))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1))
  }

  test("Exceptions that do not relate to prohibition are not included") {
    val segment1 = prohibitionSegment()
    val prohibitionSegments = Seq(segment1)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 2l, 8, 1))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 1, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Left("No prohibition found on mml id 2. Dropped exception 1.")
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Filter out exceptions that allow all traffic") {
    val segment1 = prohibitionSegment()
    val prohibitionSegments = Seq(segment1)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 1l, 1, 1))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 1, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Left("Invalid exception. Dropped exception 1.")
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Filter out exceptions with exception codes not supported") {
    val segment1 = prohibitionSegment()
    val prohibitionSegments = Seq(segment1)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 1l, 20, 1))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 1, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Left("Invalid exception. Dropped exception 1.")
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Exception affects prohibition with same side code") {
    val segment1 = prohibitionSegment(sideCode = 2)
    val segment2 = prohibitionSegment(id = 2l, value = 4, sideCode = 3)
    val prohibitionSegments = Seq(segment1, segment2)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 1l, 8, 2))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 2, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set(8))))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Right(PersistedLinearAsset(0l, 1l, 3, Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("One sided exception splits two sided prohibition") {
    val segment1 = prohibitionSegment()
    val prohibitionSegments = Seq(segment1)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 1l, 8, 2), (1l, 1l, 9, 3))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 2, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set(8))))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Right(PersistedLinearAsset(0l, 1l, 3, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set(9))))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Two sided exceptions affect one sided prohibitions") {
    val segment1 = prohibitionSegment(sideCode = 2)
    val segment2 = prohibitionSegment(id = 2l, value = 4, sideCode = 3)
    val prohibitionSegments = Seq(segment1, segment2)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)
    val exceptions = Seq((1l, 1l, 8, 1))

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, exceptions).toSet

    val conversionResult1 = Right(PersistedLinearAsset(0l, 1l, 2, Some(Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set(8))))), 0.0, 1.0, None, None, None, None, false, 190))
    val conversionResult2 = Right(PersistedLinearAsset(0l, 1l, 3, Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set(8))))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(conversionResult1, conversionResult2))
  }

  test("Parse validity period into prohibition") {
    val segment = prohibitionSegment(validityPeriod = Some("[[(h8){h7}]*[(t2){d5}]]"))
    val prohibitionSegments = Seq(segment)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    val expectedValidityPeriods = Set(ValidityPeriod(8, 15, ValidityPeriodDayOfWeek.Weekday))
    val expectedConversionResult = Right(PersistedLinearAsset(0l, 1l, 1, Some(Prohibitions(Seq(ProhibitionValue(2, expectedValidityPeriods, Set.empty)))), 0.0, 1.0, None, None, None, None, false, 190))
    result should be(Set(expectedConversionResult))
  }

  test("Report parse error from time domain parsing") {
    val segment = prohibitionSegment(validityPeriod = Some("[[(h8){h7"))
    val prohibitionSegments = Seq(segment)
    val roadLink = VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    val roadLinks: Seq[VVHRoadlink] = Seq(roadLink)

    val result: Set[Either[String, PersistedLinearAsset]] = assetDataImporter.convertToProhibitions(prohibitionSegments, roadLinks, Nil).toSet

    val expectedConversionError = Left("Parsing time domain string [[(h8){h7 failed with message: end of input. Dropped prohibition 1.")
    result should be(Set(expectedConversionError))
  }

  case class LinearAssetSegment(mmlId: Option[Long], startMeasure: Double, endMeasure: Double)

  private def createMultiLinkLinearAsset(typeId: Int,
                                         segments: Seq[LinearAssetSegment],
                                         creator: String = "asset_data_importer_spec",
                                         expired: Boolean = false): Long = {
    val speedLimitId = Sequences.nextPrimaryKeySeqValue

    sqlu"""
      insert
        into asset(id, asset_type_id, created_by, created_date)
        values ($speedLimitId, $typeId, $creator, sysdate)
    """.execute

    if (expired) {
      sqlu"""
        update asset set valid_to = sysdate where id = $speedLimitId
      """.execute
    }

    segments.foreach { segment =>
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val startMeasure = segment.startMeasure
      val endMeasure = segment.endMeasure
      val mmlId = segment.mmlId
      sqlu"""
        insert all
          into lrm_position(id, start_measure, end_measure, mml_id, side_code)
          values ($lrmPositionId, $startMeasure, $endMeasure, $mmlId, 1)

          into asset_link(asset_id, position_id)
          values ($speedLimitId, $lrmPositionId)
        select * from dual
      """.execute
    }
    speedLimitId
  }

  private def insertSpeedLimitValue(assetId: Long, value: Int): Unit = {
    val propertyId = StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("rajoitus").first

    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
      """.execute
  }

  private def insertNumericalLimitValue(assetId: Long, value: Int): Unit = {
    val numberPropertyValueId = Sequences.nextPrimaryKeySeqValue
    val propertyId = StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("mittarajoitus").first

    sqlu"""
      insert into number_property_value(id, asset_id, property_id, value)
      values ($numberPropertyValueId, $assetId, $propertyId, $value)
      """.execute
  }

  private def fetchNumericalLimitSegments(creator: String): List[(Long, Long, Long, Double, Double, Option[Int], Boolean, Option[DateTime], String, Option[DateTime])] = {
    sql"""
        select a.id, lrm.id, lrm.mml_id, lrm.start_measure, lrm.end_measure,
               n.value, a.floating, a.valid_to, a.modified_by, a.modified_date
        from asset a
        join asset_link al on al.asset_id = a.id
        join lrm_position lrm on lrm.id = al.position_id
        left join number_property_value n on a.id = n.asset_id
        where a.created_by = $creator
      """.as[(Long, Long, Long, Double, Double, Option[Int], Boolean, Option[DateTime], String, Option[DateTime])].list
  }

  private def fetchSpeedLimitSegments(creator: String): List[(Long, Long, Long, Double, Double, Int, Boolean)] = {
    sql"""
        select a.id, lrm.id, lrm.mml_id, lrm.start_measure, lrm.end_measure, e.value, a.floating
        from asset a
        join asset_link al on al.asset_id = a.id
        join lrm_position lrm on lrm.id = al.position_id
        join single_choice_value s on a.id = s.asset_id
        join enumerated_value e on e.id = s.enumerated_value_id
        where a.created_by = $creator
      """.as[(Long, Long, Long, Double, Double, Int, Boolean)].list
  }
}
