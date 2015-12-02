package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest.{MustMatchers, FunSuite}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.{PropertyRow, AssetRow}
import fi.liikennevirasto.digiroad2.asset.{State, AdministrativeClass, Position, Modification}
import slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import fi.liikennevirasto.digiroad2.Point
import Database.dynamicSession

class OracleSpatialAssetDaoSpec extends FunSuite with MustMatchers {

  test("bearing description is correct") {
    OracleSpatialAssetDao.getBearingDescription(2, Some(316)) must equal("Pohjoinen")
    OracleSpatialAssetDao.getBearingDescription(2, Some(45)) must equal("Pohjoinen")
    OracleSpatialAssetDao.getBearingDescription(2, Some(46)) must equal("Itä")
    OracleSpatialAssetDao.getBearingDescription(2, Some(135)) must equal("Itä")
    OracleSpatialAssetDao.getBearingDescription(2, Some(136)) must equal("Etelä")
    OracleSpatialAssetDao.getBearingDescription(2, Some(225)) must equal("Etelä")
    OracleSpatialAssetDao.getBearingDescription(2, Some(226)) must equal("Länsi")
    OracleSpatialAssetDao.getBearingDescription(2, Some(315)) must equal("Länsi")
  }

  test("bearing description is correct when validity direction is against") {
    OracleSpatialAssetDao.getBearingDescription(3, Some(316)) must equal("Etelä")
    OracleSpatialAssetDao.getBearingDescription(3, Some(45)) must equal("Etelä")
    OracleSpatialAssetDao.getBearingDescription(3, Some(46)) must equal("Länsi")
    OracleSpatialAssetDao.getBearingDescription(3, Some(135)) must equal("Länsi")
    OracleSpatialAssetDao.getBearingDescription(3, Some(136)) must equal("Pohjoinen")
    OracleSpatialAssetDao.getBearingDescription(3, Some(225)) must equal("Pohjoinen")
    OracleSpatialAssetDao.getBearingDescription(3, Some(226)) must equal("Itä")
    OracleSpatialAssetDao.getBearingDescription(3, Some(315)) must equal("Itä")
  }

  test("bearing property row generates bearing description") {
    val propertyRow = PropertyRow(1, "liikennointisuuntima", "", 1, false, "", "")
    val properties = OracleSpatialAssetDao.assetRowToProperty(List(createAssetRow(propertyRow)))
    properties.head.publicId must equal("liikennointisuuntima")
    properties.head.values.head.propertyDisplayValue must equal(Some("Etelä"))
  }

  test("asset row values are mapped correctly to property row") {
    val propertyRow = PropertyRow(1, "sometestproperty", "", 1, false, "123", "foo")
    val properties = OracleSpatialAssetDao.assetRowToProperty(List(createAssetRow(propertyRow)))
    properties.head.publicId must equal("sometestproperty")
    properties.head.values.head.propertyDisplayValue must equal(Some("foo"))
    properties.head.values.head.propertyValue must equal("123")
  }

  test("asset where lrm position and geometry match should not float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, Some(Point(489607.0, 6787032.0)), State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    val geometry = Some(Point(489607.0, 6787032.0))
    OracleSpatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 235), testRoadLink) must equal(false)
  }

  test("asset where lrm position and geometry don't match should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, Some(Point(489607.0, 6787032.0)), State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    val geometry = Some(Point(100.0, 100.0))
    OracleSpatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 235), testRoadLink) must equal(true)
  }

  test("asset where lrm position doesn't fall on road link should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, None, State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 100, endMeasure = 100, point = None)
    val geometry = Some(Point(489607.0, 6787032.0))
    OracleSpatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 235), testRoadLink) must equal(true)
  }

  test("asset on non-existing road link should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    OracleSpatialAssetDao.isFloating(TestAsset(roadLinkId = 9999999l, lrmPosition = lrmPosition, point = None, municipalityCode = 235), None) must equal(true)
  }

  test("asset where municipality code does not match road link municipality code should float") {
    case class TestAsset(roadLinkId: Long, lrmPosition: LRMPosition, point: Option[Point], municipalityCode: Int)
    val testRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)] = Some(762335l, 235, Some(Point(489607.0, 6787032.0)), State)
    val lrmPosition = LRMPosition(id = 0l, startMeasure = 50, endMeasure = 50, point = None)
    val geometry = Some(Point(489607.0, 6787032.0))
    OracleSpatialAssetDao.isFloating(TestAsset(roadLinkId = 762335l, lrmPosition = lrmPosition, point = geometry, municipalityCode = 999), testRoadLink) must equal(true)
  }

  private def createAssetRow(propertyRow: PropertyRow) = {
    AssetRow(1, 1, 1, Some(Point(1, 1)), Some(1), 1, Some(180), 2, None, None, propertyRow,
      Modification(None, None), Modification(None, None), Some(Point(1, 1)), lrmPosition = null, 235, false)
  }
}
