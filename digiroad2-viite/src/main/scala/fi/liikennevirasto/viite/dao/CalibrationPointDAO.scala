package fi.liikennevirasto.viite.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

/**
  * Created by pedrosag on 04-10-2017.
  */
object CalibrationPointDAO {

  trait CalibrationPointMValues {
    def segmentMValue: Double
    def addressMValue: Long
  }

  case class UserDefineCalibrationPoint(id: Long, projectLinkId: Long, projectId: Long, segmentMValue: Double, addressMValue: Long) extends CalibrationPointMValues

  def findCalibrationPointById(id: Long): UserDefineCalibrationPoint = {
    val baseQuery =
      s"""
         Select * From CALIBRATION_POINT Where ID = ${id}
       """

    Q.queryNA[(Long, Long, Long, Double, Long)](baseQuery).list.map{
      case (id,projectLinkId, projectId, linkM, addressM) => UserDefineCalibrationPoint(id,projectLinkId, projectId, linkM, addressM)
    }.head
  }

  def findCalibrationPointByRemainingValues(projectLinkId: Long, projectId: Long, segmentMValue: Double, addressMValue: Long): Seq[UserDefineCalibrationPoint] = {
    val baseQuery =
      s"""
         Select * From CALIBRATION_POINT Where PROJECT_LINK_ID = ${projectLinkId} And PROJECT_ID = ${projectId} And LINK_M = ${segmentMValue} And ADDRESS_M = ${addressMValue}
       """

    Q.queryNA[(Long, Long, Long, Double, Long)](baseQuery).list.map{
      case (id,projectLinkId, projectId, linkM, addressM) => UserDefineCalibrationPoint(id,projectLinkId, projectId, linkM, addressM)
    }
  }

  def findCalibrationPointsOfRoad(projectId: Long, linkId: Long): UserDefineCalibrationPoint = {
    val baseQuery =
      s"""
         Select * From CALIBRATION_POINT Where PROJECT_LINK_ID = ${linkId} And PROJECT_ID = ${projectId}
       """

    Q.queryNA[(Long, Long, Long, Double, Long)](baseQuery).list.map{
      case (id,projectLinkId, projectId, linkM, addressM) => UserDefineCalibrationPoint(id,projectLinkId, projectId, linkM, addressM)
    }.head
  }

  def createCalibrationPoint(calibrationPoint: UserDefineCalibrationPoint) = {
    val nextCalibrationPointId = sql"""select CALIBRATION_POINT_ID_SEQ.nextval from dual""".as[Long].first
    sqlu"""
      Insert Into CALIBRATION_POINT (ID, PROJECT_LINK_ID, PROJECT_ID, LINK_M, ADDRESS_M) Values
      (${nextCalibrationPointId}, ${calibrationPoint.projectLinkId}, ${calibrationPoint.projectId}, ${calibrationPoint.segmentMValue}, ${calibrationPoint.addressMValue})
      """.execute
  }

  def createCalibrationPoint(projectLinkId: Long, projectId: Long, segmentMValue: Double, addressMValue: Long) = {
    val nextCalibrationPointId = sql"""select CALIBRATION_POINT_ID_SEQ.nextval from dual""".as[Long].first
    sqlu"""
      Insert Into CALIBRATION_POINT (ID, PROJECT_LINK_ID, PROJECT_ID, LINK_M, ADDRESS_M) Values
      (${nextCalibrationPointId}, ${projectLinkId}, ${projectId}, ${segmentMValue}, ${addressMValue})
      """.execute
  }

  def updateSpecificCalibrationPointMeasures(id: Long, segmentMValue: Double, addressMValue: Long) = {
    sqlu"""
        Update CALIBRATION_POINT Where ID = ${id} SET LINK_M = ${segmentMValue}, ADDRESS_M = ${addressMValue}
      """
  }

  def removeSpecificCalibrationPoint(id: Long) = {
    sqlu"""
        Delete From CALIBRATION_POINT Where ID = ${id}
      """.execute
  }

  def removeAllCalibrationPointsFromRoad(projectLinkId: Long, projectId: Long) = {
    sqlu"""
        Delete From CALIBRATION_POINT Where PROJECT_LINK_ID = ${projectLinkId} And PROJECT_ID  = ${projectId}
      """.execute
  }

  def removeAllCalibrationPointsFromProject(projectId: Long) = {
    sqlu"""
        Delete From CALIBRATION_POINT Where PROJECT_ID  = ${projectId}
      """.execute
  }

}
