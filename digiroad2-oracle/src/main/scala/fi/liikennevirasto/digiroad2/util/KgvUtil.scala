package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.FeatureClass
import org.joda.time.DateTime

object KgvUtil {

  def extractModifiedAt(createdDate:Option[Long],lastEdited:Option[Long]): Option[DateTime] = {
    lastEdited.orElse(createdDate).map(new DateTime(_))
  }

  def extractFeatureClass(code: Int): FeatureClass = {
    code match {
      case 12316 => FeatureClass.TractorRoad
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
}
