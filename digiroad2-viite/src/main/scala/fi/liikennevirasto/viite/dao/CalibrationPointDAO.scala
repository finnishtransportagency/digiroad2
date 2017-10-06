package fi.liikennevirasto.viite.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

/**
  * Created by pedrosag on 04-10-2017.
  */
object CalibrationPointDAO {

  def findExistingCalibrationPoint(projectLinkId: Long, segmentMValue: Long): Seq[CalibrationPoint] = {
    val query =
      s"""
         |Select * From CALIBRATION_POINT cp Where cp.PROJECT_LINK_ID = ${projectLinkId} And cp.LINK_M = ${segmentMValue}
       """
    Q.queryNA[(Long, Double, Int)](query).list.map{
      case (projectLinkId, linkM, addressM) => CalibrationPoint(projectLinkId, linkM, addressM)
    }
  }

  def findExistingCalibrationPoint(calibrationPoint: CalibrationPoint): Seq[CalibrationPoint] = {
    val query =
      s"""
         |Select * From CALIBRATION_POINT cp Where cp.PROJECT_LINK_ID = ${calibrationPoint.linkId} And cp.LINK_M = ${calibrationPoint.segmentMValue}
       """
    Q.queryNA[(Long, Double, Int)](query).list.map{
      case (projectLinkId, linkM, addressM) => CalibrationPoint(projectLinkId, linkM, addressM)
    }
  }

  def insertNewCalibrationPoint(projectLinkId: Long, segmentMValue: Long, addressMValue: Long) = {
    sqlu"""
           Insert Into CALIBRATION_POINT (PROJECT_LINK_ID, LINK_M, ADDR_M) Values
           (${projectLinkId}, ${segmentMValue}, ${addressMValue})
      """.execute
  }

  def insertNewCalibrationPoint(calibrationPoint: CalibrationPoint) = {
    sqlu"""
           Insert Into CALIBRATION_POINT (PROJECT_LINK_ID, LINK_M, ADDR_M) Values
           (${calibrationPoint.linkId}, ${calibrationPoint.segmentMValue}, ${calibrationPoint.addressMValue})
      """.execute
  }

  def updateCalibrationPointAddressM(projectLinkId: Long, segmentMValue: Long, addressMValue: Long) = {
    sqlu"""
           Update CALIBRATION_POINT Where PROJECT_LINK_ID = ${projectLinkId} And LINK_M = ${segmentMValue} SET ADDR_M = ${addressMValue}
      """.execute
  }

  def updateCalibrationPointAddressM(calibrationPoint: CalibrationPoint) = {
    sqlu"""
           Update CALIBRATION_POINT Where PROJECT_LINK_ID = ${calibrationPoint.linkId} And LINK_M = ${calibrationPoint.segmentMValue} SET ADDR_M = ${calibrationPoint.addressMValue}
      """.execute
  }

}
