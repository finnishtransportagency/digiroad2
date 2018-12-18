package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.{Municipality, SpeedLimitAsset, State}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession


class InaccurateAssetDaoSpec extends FunSuite with Matchers {
  val speedLimitAssetTypeID = SpeedLimitAsset.typeId
  val municipalityCode = 235
  val inaccurateAssetDao = new InaccurateAssetDAO

  test("create and get new asset on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val speedLimitTestAsset = sql"""select id from asset where rownum = 1 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].first
      inaccurateAssetDao.createInaccurateAsset(speedLimitTestAsset, speedLimitAssetTypeID, municipalityCode, Municipality)

      val inaccurateAssetInfo = inaccurateAssetDao.getInaccurateAssetById(speedLimitTestAsset)

      inaccurateAssetInfo.size == 1 should be(true)
      inaccurateAssetInfo.head should be(speedLimitTestAsset)
      dynamicSession.rollback()
    }
  }

  test("delete and get new asset on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val speedLimitTestAsset = sql"""select id from asset where rownum = 1 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].first
      inaccurateAssetDao.createInaccurateAsset(speedLimitTestAsset, speedLimitAssetTypeID, municipalityCode, Municipality)

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAssetById(speedLimitTestAsset)
      inaccurateAssetCreated.size == 1 should be(true)
      inaccurateAssetCreated.head should be(speedLimitTestAsset)

      inaccurateAssetDao.deleteInaccurateAssetById(speedLimitTestAsset)
      val inaccurateAssetInfoDeleted = inaccurateAssetDao.getInaccurateAssetById(speedLimitTestAsset)
      inaccurateAssetInfoDeleted.isEmpty should be(true)
      dynamicSession.rollback()
    }
  }

  test("get inaccurate assets by Type Id") {
    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 5 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.foreach{speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, Municipality)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAsset(speedLimitAssetTypeID)
      inaccurateAssetCreated.size == 5 should be(true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.head)) should be (true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.last)) should be (true)

      dynamicSession.rollback()
    }
  }

  test("get inaccurate assets by Type Id and with Authorized Municipalities List") {
    val authorizedMunicipalitiesList = Set(235, 300)

    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 5 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.foreach{speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, Municipality)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAsset(speedLimitAssetTypeID, authorizedMunicipalitiesList)
      inaccurateAssetCreated.size == 5 should be(true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.head)) should be (true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.last)) should be (true)

      dynamicSession.rollback()
    }
  }


  test("delete all asset related with some type id on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 10 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.foreach { speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, State)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAsset(speedLimitAssetTypeID)
      inaccurateAssetCreated.size == 10 should be(true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.head)) should be (true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.last)) should be (true)

      inaccurateAssetDao.deleteAllInaccurateAssets(speedLimitAssetTypeID)
      val inaccurateAssetDeleted = inaccurateAssetDao.getInaccurateAsset(speedLimitAssetTypeID)
      inaccurateAssetDeleted.isEmpty should be(true)
      dynamicSession.rollback()
    }
  }

  test("delete all asset on InaccurateAsset table") {
    OracleDatabase.withDynTransaction {
      val listSpeedLimit = sql"""select id from asset where rownum <= 10 and asset_type_id = $speedLimitAssetTypeID order by id""".as[Long].list
      listSpeedLimit.foreach { speedLimitId =>
        inaccurateAssetDao.createInaccurateAsset(speedLimitId, speedLimitAssetTypeID, municipalityCode, Municipality)
      }

      val inaccurateAssetCreated = inaccurateAssetDao.getInaccurateAsset(speedLimitAssetTypeID)
      inaccurateAssetCreated.size == 10 should be(true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.head)) should be (true)
      inaccurateAssetCreated.map(_.assetId).contains(Some(listSpeedLimit.last)) should be (true)


      inaccurateAssetDao.deleteAllInaccurateAssets(speedLimitAssetTypeID)
      val inaccurateAssetDeleted = inaccurateAssetDao.getInaccurateAsset(speedLimitAssetTypeID)
      inaccurateAssetDeleted.isEmpty should be (true)
      dynamicSession.rollback()
    }

  }
}