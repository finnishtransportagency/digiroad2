package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.{CyclingAndWalking, Manoeuvres, Obstacles, ServicePoints}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest._
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import scala.language.implicitConversions

class HistoryServiceSpec extends FunSuite with Matchers {
  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  val testService = new HistoryService
  val testUser = "historyServiceSpecUser"
  val testSQLTimestamp = "TIMESTAMP '2010-01-01 10:00:00.000000'"
  val testLinkId = 100

  def generateAssets = {
    //Insert expired 2 service points, 1 obstacle, 1 cycling and walking and 1 manoeuvre
    sqlu"""
        INSERT ALL
          INTO ASSET (ID, ASSET_TYPE_ID, CREATED_DATE, CREATED_BY, VALID_TO)
            VALUES (1, ${ServicePoints.typeId}, #$testSQLTimestamp, $testUser, SYSDATE)
          INTO ASSET (ID, ASSET_TYPE_ID, CREATED_DATE, CREATED_BY, VALID_TO)
            VALUES (2, ${ServicePoints.typeId}, #$testSQLTimestamp, $testUser, #$testSQLTimestamp)

          INTO ASSET (ID, ASSET_TYPE_ID, CREATED_DATE, CREATED_BY, VALID_TO)
            VALUES (3, ${Obstacles.typeId}, #$testSQLTimestamp, $testUser, #$testSQLTimestamp)
          INTO LRM_POSITION (ID, LINK_ID, START_MEASURE, SIDE_CODE) VALUES (1, $testLinkId, 100.0, 1)
          INTO ASSET_LINK (ASSET_ID, POSITION_ID) VALUES (3, 1)

          INTO ASSET (ID, ASSET_TYPE_ID, CREATED_DATE, CREATED_BY, VALID_TO)
            VALUES (4, ${CyclingAndWalking.typeId}, #$testSQLTimestamp, $testUser, #$testSQLTimestamp)
          INTO LRM_POSITION (ID, LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) VALUES (2, $testLinkId, 0, 100.0, 1)
          INTO ASSET_LINK (ASSET_ID, POSITION_ID) VALUES (4, 2)

          INTO MANOEUVRE (ID, TYPE, ADDITIONAL_INFO, CREATED_DATE, CREATED_BY, VALID_TO)
            VALUES (5, 2, 'test', #$testSQLTimestamp, $testUser, #$testSQLTimestamp)

        SELECT * FROM DUAL
      """.execute
  }

  test("Get expired assets with a year gap of 9") {
    runWithRollback {
      generateAssets

      val servicePointExpiredIds = testService.getExpiredAssetsIdsByAssetTypeAndYearGap(ServicePoints, 9)
      val obstacleExpiredIds = testService.getExpiredAssetsIdsByAssetTypeAndYearGap(Obstacles, 9)
      val cyclingAndWalkingExpiredIds = testService.getExpiredAssetsIdsByAssetTypeAndYearGap(CyclingAndWalking, 9)
      val manoeuvreExpiredIds = testService.getExpiredAssetsIdsByAssetTypeAndYearGap(Manoeuvres, 9)

      (obstacleExpiredIds ++ servicePointExpiredIds ++ cyclingAndWalkingExpiredIds ++ manoeuvreExpiredIds).size should be (4)

      servicePointExpiredIds.head should be(2)
      obstacleExpiredIds.head should be(3)
      cyclingAndWalkingExpiredIds.head should be(4)
      manoeuvreExpiredIds.head should be(5)
    }
  }

  test("Transfer assets to history") {
    runWithRollback {
      generateAssets

      testService.transferExpiredAssetToHistoryById(2, ServicePoints)
      testService.transferExpiredAssetToHistoryById(3, Obstacles)
      testService.transferExpiredAssetToHistoryById(4, CyclingAndWalking)
      testService.transferExpiredAssetToHistoryById(5, Manoeuvres)

      val assetsInMainTable = sql"""SELECT ID FROM ASSET WHERE ID IN (2,3,4)""".as[Long].list
      val manoeuvresInMainTable = sql"""SELECT ID FROM MANOEUVRE WHERE ID = 5""".as[Long].list

      (assetsInMainTable ++ manoeuvresInMainTable).isEmpty should be (true)

      val assetsInHistoryTable = sql"""SELECT ID FROM ASSET_HISTORY WHERE ID IN (2,3,4)""".as[Long].list
      val manoeuvresInHistoryTable = sql"""SELECT ID FROM MANOEUVRE_HISTORY WHERE ID = 5""".as[Long].list

      (assetsInHistoryTable ++ manoeuvresInHistoryTable).size should be (4)
    }
  }
}
