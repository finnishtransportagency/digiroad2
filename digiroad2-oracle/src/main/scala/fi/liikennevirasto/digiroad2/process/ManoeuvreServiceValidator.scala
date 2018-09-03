package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreTurnRestrictionType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import scala.math.Pi

class ManoeuvreServiceValidator extends AssetServiceValidator {
  override type AssetType = Manoeuvre

  lazy val manoeuvreDao: ManoeuvreDao = new ManoeuvreDao(vvhClient)

  override def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean = {
    val manoeuvres = assets.asInstanceOf[Seq[Manoeuvre]]

    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(manoeuvres.flatMap(manoeuvre => manoeuvre.elements.map(_.sourceLinkId)).toSet)

    val manoeuvreTurnRestrictionType = manoeuvres.map {
      manoeuvre =>
        getManoeuvreTurnRestrictionType(manoeuvre, roadLinks)
    }

    TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.NoLeftTurn =>
        manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.LeftTurn)
      case TrafficSignType.NoRightTurn =>
        manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.RightTurn)
      case TrafficSignType.NoUTurn =>
        manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.UTurn)
      case _ => throw new NumberFormatException("Not supported trafficSign on Manoeuvres asset")
    }
  }

  override def getAdjacentRoadLink(point: Point,  prevRoadLink: RoadLink, roadLinks: Seq[RoadLink]) : Seq[(RoadLink, (Point, Point))] = {
    val adjacentInfo = getAdjacents(point, roadLinks)
    val mostForwardRoadLink = roadLinkService.pickForwardMost(prevRoadLink, adjacentInfo.map(_._1))
    adjacentInfo.filter(_._1 == mostForwardRoadLink)
  }

  override def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSign: Seq[PersistedTrafficSign]): Boolean = {
    val manoeuvres = Seq(asset.asInstanceOf[Manoeuvre])
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(manoeuvres.flatMap{_.elements.filterNot(_.elementType == ElementTypes.LastElement).flatMap{element => Seq(element.sourceLinkId) ++ Seq(element.destLinkId)}}.toSet)

    val manoeuvreTurnRestrictionType = manoeuvres.map {
      manoeuvre =>
        getManoeuvreTurnRestrictionType(manoeuvre, roadLinks)
    }
    trafficSign.exists {
      trafficSign =>
        TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
          case TrafficSignType.NoLeftTurn =>
            manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.LeftTurn)
          case TrafficSignType.NoRightTurn =>
            manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.RightTurn)
          case TrafficSignType.NoUTurn =>
            manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.UTurn)
          case _ => throw new NumberFormatException("Not supported trafficSign on Manoeuvres asset")
        }
    }
  }

  def getManoeuvreTurnRestrictionType(manoeuvre: Manoeuvre, roadLinks: Seq[RoadLink]) : ManoeuvreTurnRestrictionType = {
    val epsilon: Double = (5 * Pi)/180

    val angles = manoeuvre.elements.filterNot(_.elementType == ElementTypes.LastElement).map {
      manoeuvreElement =>
        getAngle(roadLinks.filter(_.linkId == manoeuvreElement.sourceLinkId).head, roadLinks.filter(_.linkId == manoeuvreElement.destLinkId).head)
    }

    val angleStandardization =  angles.map { angle => if(Math.abs(angle) - epsilon <= 0) 0 else angle }

    val turnLeft = angleStandardization.count(angle => angle < 0 )
    val righLeft = angleStandardization.count(angle => angle > 0 )

    if(turnLeft == 1 && righLeft == 0) {
      ManoeuvreTurnRestrictionType.LeftTurn
    } else if(turnLeft == 0 && righLeft == 1 ) {
      ManoeuvreTurnRestrictionType.RightTurn
    } else if(turnLeft == 2 && righLeft == 0 ) {
      ManoeuvreTurnRestrictionType.UTurn
    } else
      ManoeuvreTurnRestrictionType.Unknown
  }

  def getAngle(prevRoadLink: RoadLink, nextRoadLink: RoadLink) : Double = {
    val (prevFirst, prevLast) = GeometryUtils.geometryEndpoints(prevRoadLink.geometry)
    val (nextFirst, _) = GeometryUtils.geometryEndpoints(nextRoadLink.geometry)

    val (seg1, seg2) = if (GeometryUtils.areAdjacent(nextRoadLink.geometry, prevFirst)) {
      if (GeometryUtils.areAdjacent(prevFirst, nextFirst)) {
        (GeometryUtils.lastSegmentDirection(prevRoadLink.geometry.reverse), GeometryUtils.firstSegmentDirection(nextRoadLink.geometry))
      } else {
        (GeometryUtils.lastSegmentDirection(prevRoadLink.geometry.reverse), GeometryUtils.firstSegmentDirection(nextRoadLink.geometry.reverse))
      }
    } else {
      if (GeometryUtils.areAdjacent(prevLast, nextFirst)) {
        (GeometryUtils.lastSegmentDirection(prevRoadLink.geometry), GeometryUtils.firstSegmentDirection(nextRoadLink.geometry))
      } else {
        (GeometryUtils.lastSegmentDirection(prevRoadLink.geometry), GeometryUtils.firstSegmentDirection(nextRoadLink.geometry.reverse))
      }
    }
    seg1.angleXYWithNegativeValues(seg2)
  }

  override def getAsset(roadLink: RoadLink): Seq[AssetType] = {
    manoeuvreDao.getByRoadLinks(Seq(roadLink.linkId))
  }

  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    val trafficSignsRelevantToManoeuvre: Set[TrafficSignType] = Set(TrafficSignType.NoLeftTurn, TrafficSignType.NoRightTurn, TrafficSignType.NoUTurn)
    trafficSignService.getTrafficSign(Seq(roadLink.linkId)).filter(trafficSign =>
      trafficSignsRelevantToManoeuvre.contains(TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)))
  }

  def validateManoeuvre(manoeuvre: Manoeuvre) : Boolean  = {

    val sourceLinkId = manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).head
    val nextLinkId = manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId).getOrElse(manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).head)

    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(sourceLinkId, nextLinkId))

    val (sourceFirst, sourceLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == sourceLinkId).head.geometry)
    val (secondFirst, secondLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == nextLinkId).head.geometry)

    val pointOfInterest : Point = if (GeometryUtils.areAdjacent(sourceFirst, secondFirst) || GeometryUtils.areAdjacent(sourceFirst, secondLast)) sourceLast else sourceFirst

    assetValidatorX(manoeuvre, pointOfInterest ,roadLinks.filter(_.linkId == sourceLinkId).head)
  }

  override def assetName: String = "manoeuvre"

  def validate(radiousDistance: Option[Int]) = {

  }
}

