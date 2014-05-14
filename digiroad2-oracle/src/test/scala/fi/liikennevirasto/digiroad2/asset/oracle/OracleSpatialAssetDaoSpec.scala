package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest.{MustMatchers, FunSuite}

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
}
