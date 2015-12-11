package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.oracle.Queries.PropertyRow
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Modification}
import fi.liikennevirasto.digiroad2.{MassTransitStopRow, Point}
import org.scalatest.{FunSuite, MustMatchers}

class OracleSpatialAssetDaoSpec extends FunSuite with MustMatchers {
  val spatialAssetDao = new OracleSpatialAssetDao

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

  private def createAssetRow(propertyRow: PropertyRow) = {
    MassTransitStopRow(1, 1, 1, Some(Point(1, 1)), Some(1), 1, 123l, Some(180), 2, None, None, propertyRow,
      Modification(None, None), Modification(None, None), Some(Point(1, 1)), lrmPosition = null, AdministrativeClass.apply(99), 235, false)
  }
}
