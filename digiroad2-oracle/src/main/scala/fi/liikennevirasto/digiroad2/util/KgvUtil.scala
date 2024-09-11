package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.TrafficDirection
import fi.liikennevirasto.digiroad2.client.FeatureClass
import net.postgis.jdbc.geometry.GeometryBuilder
import org.joda.time.DateTime
import org.postgresql.util.PGobject

import scala.collection.mutable.ListBuffer

object KgvUtil {

  def extractModifiedAt(createdDate:Option[Long],lastEdited:Option[Long]): Option[DateTime] = {
    lastEdited.orElse(createdDate).map(new DateTime(_))
  }

  def extractFeatureClass(code: Int): FeatureClass = {
    code match {
      case 12316 => FeatureClass.TractorRoad
      case 12317 => FeatureClass.TractorRoad
      case 12318 => FeatureClass.HardShoulder
      case 12141 => FeatureClass.DrivePath
      case 12314 => FeatureClass.CycleOrPedestrianPath
      case 12312 => FeatureClass.WinterRoads
      case 12153 => FeatureClass.SpecialTransportWithoutGate
      case 12154 => FeatureClass.SpecialTransportWithGate
      case 12131 => FeatureClass.CarRoad_IIIa
      case 12132 => FeatureClass.CarRoad_IIIb
      case _ => FeatureClass.AllOthers
    }
  }

  def extractTrafficDirection(code: Option[Int]): TrafficDirection = {
    code match {
      case Some(0) => TrafficDirection.BothDirections
      case Some(1) => TrafficDirection.TowardsDigitizing
      case Some(2) => TrafficDirection.AgainstDigitizing
      case _ => TrafficDirection.UnknownDirection
    }
  }

  def extractGeometry(data: Object): List[List[Double]] = {
    val geometry = data.asInstanceOf[PGobject]
    if (geometry == null) Nil
    else {
      val geomValue = geometry.getValue
      val geom = GeometryBuilder.geomFromString(geomValue)
      val listOfPoint = ListBuffer[List[Double]]()
      for (i <- 0 until geom.numPoints()) {
        val point = geom.getPoint(i)
        listOfPoint += List(point.x, point.y, point.z, point.m)
      }
      listOfPoint.toList
    }
  }

  def processGeometry(path: List[List[Double]]): (Seq[Map[String, Double]], String) = {
    val geometryForApi = path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3)))
    val geometryWKT = s"LINESTRING ZM (${path.map(point => s"${point(0)} ${point(1)} ${point(2)} ${point(3)}").mkString(", ")})"
    (geometryForApi, geometryWKT)
  }

  def getLatestModification(modifications: Seq[(Option[DateTime], Option[String])]): (BigInt, String) = {
    val validModifications = modifications.collect { case (Some(date), Some(by)) => (date, by) }
    validModifications match {
      case Nil => (BigInt(0), "")
      case mods =>
        val (date, by) = mods.maxBy { case (date, _) => date.getMillis }
        (BigInt(date.getMillis), by)
    }
  }
}
