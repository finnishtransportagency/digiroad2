package fi.liikennevirasto.digiroad2.client

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, ConstructionType, LinkGeomSource, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.kgv.RoadLinkHistoryClient
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import org.joda.time.DateTime

sealed trait FeatureClass
object FeatureClass {
  case object TractorRoad extends FeatureClass
  case object HardShoulder extends FeatureClass
  case object DrivePath extends FeatureClass
  case object CycleOrPedestrianPath extends FeatureClass
  case object WinterRoads extends FeatureClass
  case object SpecialTransportWithoutGate extends FeatureClass
  case object SpecialTransportWithGate extends FeatureClass
  case object CarRoad_IIIa extends FeatureClass
  case object CarRoad_IIIb extends FeatureClass
  case object AllOthers extends FeatureClass

  // RoadLinks with these feature classes are generally not handled by Digiroad
  val featureClassesToIgnore = Seq(WinterRoads, HardShoulder)
}


sealed trait IRoadLinkFetched extends RoadLinkLike {
  def featureClass: FeatureClass
  def modifiedAt: Option[DateTime]
}

case class RoadLinkFetched(linkId: String, municipalityCode: Int, geometry: Seq[Point],
                           administrativeClass: AdministrativeClass, trafficDirection: TrafficDirection,
                           featureClass: FeatureClass, modifiedAt: Option[DateTime] = None, attributes: Map[String, Any] = Map(),
                           constructionType: ConstructionType = ConstructionType.InUse, linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, length: Double = 0.0) extends IRoadLinkFetched {
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)
  def verticalLevel: Option[String] = attributes.get("VERTICALLEVEL").map(_.toString)
  val timeStamp = attributes.getOrElse("LAST_EDITED_DATE", attributes.getOrElse("CREATED_DATE", BigInt(0))).asInstanceOf[BigInt].longValue()
}

case class LinkIdAndExpiredDate(linkId: String, expiredDate: DateTime)


case class HistoryRoadLink(linkId: String, municipalityCode: Int, geometry: Seq[Point], administrativeClass: AdministrativeClass,
                           trafficDirection: TrafficDirection, featureClass: FeatureClass, modifiedAt: Option[DateTime] = None, createdDate:BigInt = 0, endDate: BigInt = 0, attributes: Map[String, Any] = Map(),kmtkid:String="",version:Int=0,
                           constructionType: ConstructionType = ConstructionType.InUse, linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, length: Double = 0.0) extends IRoadLinkFetched {
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)
  val timeStamp: Long = attributes.getOrElse("LAST_EDITED_DATE", createdDate).asInstanceOf[BigInt].longValue()
}

case class LinkOperationError(content: String, statusCode:String, url:String ="") extends Exception(s"Content: ${content}, Status code: ${statusCode}, ${url} ")
class ClientException(response: String) extends RuntimeException(response)

trait Filter {
  def withMmlIdFilter(mmlIds: Set[Long]): String = ???

  def withFilter[T](attributeName: String, ids: Set[T]): String = ???

  def withMunicipalityFilter(municipalities: Set[Int]): String = ???

  def withRoadNameFilter[T](attributeName: String, names: Set[T]): String = ???

  def combineFiltersWithAnd(filter1: String, filter2: String): String = ???

  def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String = ???

  def withOldkmtkidFilter(linkIds: Set[String]): String = ???
  /**
    *
    * @param polygon to be converted to string
    * @return string compatible with VVH polygon query
    */
  def stringifyPolygonGeometry(polygon: Polygon): String = ???

  // Query filters methods
  def withLinkIdFilter[T](linkIds: Set[T]): String = ???

  def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = ???

  def withMtkClassFilter(ids: Set[Long]): String = ???

  def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = ???

  def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = ???

}

class RoadLinkClient(vvhRestApiEndPoint: String) {
  lazy val historyData: RoadLinkHistoryClient = new RoadLinkHistoryClient()
}
