package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest.mock.MockitoSugar
import org.scalatest.{MustMatchers, FunSuite}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.{PropertyRow, AssetRow}
import fi.liikennevirasto.digiroad2.asset.{State, AdministrativeClass, Position, Modification}
import slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import fi.liikennevirasto.digiroad2.{RoadLinkService, Point}
import Database.dynamicSession

class OracleSpatialAssetDaoSpec extends FunSuite with MustMatchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val spatialAssetDao = new OracleSpatialAssetDao(mockRoadLinkService)

  test("bearing description is correct") {
    spatialAssetDao.getBearingDescription(2, Some(316)) must equal("Pohjoinen")
    spatialAssetDao.getBearingDescription(2, Some(45)) must equal("Pohjoinen")
    spatialAssetDao.getBearingDescription(2, Some(46)) must equal("Itä")
    spatialAssetDao.getBearingDescription(2, Some(135)) must equal("Itä")
    spatialAssetDao.getBearingDescription(2, Some(136)) must equal("Etelä")
    spatialAssetDao.getBearingDescription(2, Some(225)) must equal("Etelä")
    spatialAssetDao.getBearingDescription(2, Some(226)) must equal("Länsi")
    spatialAssetDao.getBearingDescription(2, Some(315)) must equal("Länsi")
  }

  test("bearing description is correct when validity direction is against") {
    spatialAssetDao.getBearingDescription(3, Some(316)) must equal("Etelä")
    spatialAssetDao.getBearingDescription(3, Some(45)) must equal("Etelä")
    spatialAssetDao.getBearingDescription(3, Some(46)) must equal("Länsi")
    spatialAssetDao.getBearingDescription(3, Some(135)) must equal("Länsi")
    spatialAssetDao.getBearingDescription(3, Some(136)) must equal("Pohjoinen")
    spatialAssetDao.getBearingDescription(3, Some(225)) must equal("Pohjoinen")
    spatialAssetDao.getBearingDescription(3, Some(226)) must equal("Itä")
    spatialAssetDao.getBearingDescription(3, Some(315)) must equal("Itä")
  }

  test("bearing property row generates bearing description") {
    val propertyRow = PropertyRow(1, "liikennointisuuntima", "", 1, false, "", "")
    val properties = spatialAssetDao.assetRowToProperty(List(createAssetRow(propertyRow)))
    properties.head.publicId must equal("liikennointisuuntima")
    properties.head.values.head.propertyDisplayValue must equal(Some("Etelä"))
  }

  test("asset row values are mapped correctly to property row") {
    val propertyRow = PropertyRow(1, "sometestproperty", "", 1, false, "123", "foo")
    val properties = spatialAssetDao.assetRowToProperty(List(createAssetRow(propertyRow)))
    properties.head.publicId must equal("sometestproperty")
    properties.head.values.head.propertyDisplayValue must equal(Some("foo"))
    properties.head.values.head.propertyValue must equal("123")
  }

  ignore("asset geometry matches lrm position") {
    Database.forDataSource(ds).withDynTransaction {
      val id = 300000
      val asset = spatialAssetDao.getAssetById(id).get
      val assetGeometry = spatialAssetDao.getAssetGeometryById(id)
      asset.lon must equal(assetGeometry.x)
      asset.lat must equal(assetGeometry.y)
    }
  }

  test("asset where lrm position and geometry match should not float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, Some(Point(489607.0, 6787032.0)), State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    val geometry = Some(Point(489607.0, 6787032.0))
    spatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 235), testRoadLink) must equal(false)
  }

  test("asset where lrm position and geometry don't match should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, Some(Point(489607.0, 6787032.0)), State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    val geometry = Some(Point(100.0, 100.0))
    spatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 235), testRoadLink) must equal(true)
  }

  test("asset where lrm position doesn't fall on road link should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, None, State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 100, endMeasure = 100, point = None)
    val geometry = Some(Point(489607.0, 6787032.0))
    spatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 235), testRoadLink) must equal(true)
  }

  test("asset on non-existing road link should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    spatialAssetDao.isFloating(TestAsset(roadLinkId = 9999999l, lrmPosition = lrmPosition, point = None, municipalityCode = 235), None) must equal(true)
  }

  test("asset where municipality code does not match road link municipality code should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, Some(Point(489607.0, 6787032.0)), State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    val geometry = Some(Point(489607.0, 6787032.0))
    spatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 999), testRoadLink) must equal(true)
  }

  ignore("update the position of an asset to a new road link") {
    Database.forDataSource(ds).withDynTransaction {
      val assetId = 300000l
      val newPosition = Some(Position(lon = 374675.043988335, lat = 6677274.14596169, roadLinkId = 8620946, bearing = None))
      val asset = spatialAssetDao.updateAsset(assetId, newPosition, "OracleSpatialAssetDaoSpec", Nil)
      Math.abs(asset.lon - 374675.043988335) must (be < 0.05)
      Math.abs(asset.lat - 6677274.14596169) must (be < 0.05)
      dynamicSession.rollback()
    }
  }

  private def createAssetRow(propertyRow: PropertyRow) = {
    AssetRow(1, 1, 1, Some(Point(1, 1)), Some(1), 1, Some(180), 2, None, None, propertyRow,
      Modification(None, None), Modification(None, None), Some(Point(1, 1)), lrmPosition = null, 235, false)
  }
}
