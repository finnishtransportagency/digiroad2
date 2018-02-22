package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset
import fi.liikennevirasto.digiroad2.asset.SpeedLimitAsset
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession


class InaccurateAssetDaoSpec extends FunSuite with Matchers {
  val speedLimitAssetTypeID = SpeedLimitAsset.typeId
  val municipalityCode = 235
  val areaCode = 5
  val stateAdminClass = asset.State.value
  val municipalityAdminClass = asset.Municipality.value
  val inaccurateAssetDao = new InaccurateAssetDAO

  test("create and get new asset on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val speedLimitTestAsset = sql"""select id from asset where rownum = 1 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].first
      inaccurateAssetDao.createInaccurateAsset(speedLimitTestAsset, speedLimitAssetTypeID, municipalityCode, areaCode, municipalityAdminClass)

      val inaccurateAssetInfo = inaccurateAssetDao.getInaccurateAssetById(speedLimitTestAsset)

      inaccurateAssetInfo.size == 1 should be(true)
      inaccurateAssetInfo.head should be(speedLimitTestAsset)
      dynamicSession.rollback()
    }
  }

  test("delete and get new asset on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val speedLimitTestAsset = sql"""select id from asset where rownum = 1 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].first
      inaccurateAssetDao.createInaccurateAsset(speedLimitTestAsset, speedLimitAssetTypeID, municipalityCode, areaCode, municipalityAdminClass)

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetById(speedLimitTestAsset)
      inaccurateAssetCreated.size == 1 should be(true)
      inaccurateAssetCreated.head should be(speedLimitTestAsset)

      inaccurateAssetDao.deleteInaccurateAssetById(speedLimitTestAsset)
      val inaccurateAssetInfoDeleted = inaccurateAssetDao.getInaccurateAssetById(speedLimitTestAsset)
      inaccurateAssetInfoDeleted.size == 0 should be(true)
      dynamicSession.rollback()
    }
  }

  test("get inaccurate assets by Type Id") {
    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 5 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.map{speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, areaCode, municipalityAdminClass)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID)
      inaccurateAssetCreated.size == 5 should be(true)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.head)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.last)

      dynamicSession.rollback()
    }
  }

  test("get inaccurate assets by Type Id and with Authorized Municipalities List") {
    val authorizedMunicipalitiesList = Some(Set(235, 300))

    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 5 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.map{speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, areaCode, municipalityAdminClass)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID, authorizedMunicipalitiesList)
      inaccurateAssetCreated.size == 5 should be(true)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.head)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.last)

      dynamicSession.rollback()
    }
  }

  test("get inaccurate assets by Type Id and with Authorized Areas List") {
    val authorizedAreasList = Some(Set(1, 5))

    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 5 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.map{speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, areaCode, stateAdminClass)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID, areas = authorizedAreasList)
      inaccurateAssetCreated.size == 5 should be(true)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.head)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.last)

      dynamicSession.rollback()
    }
  }

  test("delete all asset related with some type id on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 10 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.map { speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, areaCode, municipalityAdminClass)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID)
      inaccurateAssetCreated.size == 10 should be(true)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.head)
      inaccurateAssetCreated.exists(_ == listSpeedLimit.last)


      inaccurateAssetDao.deleteAllInaccurateAssets(speedLimitAssetTypeID)
      val inaccurateAssetDeleted = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID)
      inaccurateAssetDeleted.size == 0 should be(true)
      dynamicSession.rollback()
    }
  }

//  test("delete all asset on InaccurateAsset table") {
//    OracleDatabase.withDynTransaction {
//      val listSpeedLimit = sql"""select id from asset where rownum <= 10 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
//      listSpeedLimit.map { speedLimitId =>
//        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, areaCode, municipalityAdminClass)
//      }
//
//      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID)
//      inaccurateAssetCreated.size == 10 should be(true)
//      inaccurateAssetCreated.exists(_ == listSpeedLimit.head)
//      inaccurateAssetCreated.exists(_ == listSpeedLimit.last)
//
//
//      inaccurateAssetDao.deleteAllInaccurateAssets()
//      val inaccurateAssetDeleted = inaccurateAssetDao.getInaccurateAssetByTypeId(speedLimitAssetTypeID)
//      inaccurateAssetDeleted.size == 0 should be(true)
//      dynamicSession.rollback()
//    }
//
//  }
}