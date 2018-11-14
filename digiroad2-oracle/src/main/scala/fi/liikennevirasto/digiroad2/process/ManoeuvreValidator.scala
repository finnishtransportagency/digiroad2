package fi.liikennevirasto.digiroad2.process

import java.sql.SQLIntegrityConstraintViolationException

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreTurnRestrictionType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType

import scala.math.Pi

class ManoeuvreValidator extends AssetServiceValidatorOperations {
  override type AssetType = Manoeuvre
  override val radiusDistance: Int = 50

  override def assetTypeInfo: AssetTypeInfo = Manoeuvres

  lazy val manoeuvreDao: ManoeuvreDao = new ManoeuvreDao(vvhClient)

  val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.NoLeftTurn, TrafficSignType.NoRightTurn, TrafficSignType.NoUTurn)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double, trafficSign: Option[PersistedTrafficSign] = None): Seq[AssetType] = {
    assets.filter {
      _.elements.filter(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).contains(roadLink.linkId)
    }
  }

  override def verifyAsset(assets: Seq[AssetType], roadLink: RoadLink, trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    val manoeuvres = assets.asInstanceOf[Seq[Manoeuvre]]

    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(manoeuvres.flatMap(manoeuvre => manoeuvre.elements.map(_.sourceLinkId)).toSet, newTransaction = false)

    manoeuvres.flatMap {
      manoeuvre =>
        val roadLink = roadLinks.find(roadLink => manoeuvre.elements.map(_.sourceLinkId).contains(roadLink.linkId)).get
        val manoeuvreTurnRestrictionType = getManoeuvreTurnRestrictionType(manoeuvre, roadLinks)

        val manoeuvreLinkId = manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId)
        TrafficSignType.apply(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
          case TrafficSignType.NoLeftTurn =>
            if (manoeuvreTurnRestrictionType != ManoeuvreTurnRestrictionType.LeftTurn)
              Seq(Inaccurate(None, manoeuvreLinkId, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
          case TrafficSignType.NoRightTurn =>
            if (manoeuvreTurnRestrictionType != ManoeuvreTurnRestrictionType.RightTurn)
              Seq(Inaccurate(None, manoeuvreLinkId, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
          case TrafficSignType.NoUTurn =>
            if (manoeuvreTurnRestrictionType != ManoeuvreTurnRestrictionType.UTurn)
              Seq(Inaccurate(None, manoeuvreLinkId, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
          case _ => throw new NumberFormatException("Not supported trafficSign on Manoeuvres asset")
        }
    }.toSet
  }

  override def getAdjacents(previousInfo: (Point, RoadLink), roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Seq[(RoadLink, (Point, Point))] = {
    val adjacentInfo = getAdjacentInfo(roadLinks, previousInfo)
    if(adjacentInfo.size == 1) {
      adjacentInfo
    } else
    TrafficSignType.apply(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.NoLeftTurn | TrafficSignType.NoUTurn =>
        adjacentInfo.filter(_._1 == roadLinkService.pickLeftMost(previousInfo._2, adjacentInfo.map(_._1)))
      case TrafficSignType.NoRightTurn =>
        adjacentInfo.filter(_._1 == roadLinkService.pickRightMost(previousInfo._2, adjacentInfo.map(_._1)))
      case _ => Seq()
    }
  }

  def getAdjacentInfo(roadLinks: Seq[RoadLink], previousInfo: (Point, RoadLink)) : Seq[(RoadLink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousInfo._1)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo._1)) (first, last) else (last, first)

      (roadLink, points)
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
        (GeometryUtils.lastSegmentDirection(prevRoadLink.geometry), GeometryUtils.firstSegmentDirection(nextRoadLink.geometry))
      } else {
        (GeometryUtils.lastSegmentDirection(prevRoadLink.geometry), GeometryUtils.lastSegmentDirection(nextRoadLink.geometry))
      }
    } else {
      if (GeometryUtils.areAdjacent(prevLast, nextFirst)) {
        (GeometryUtils.firstSegmentDirection(prevRoadLink.geometry), GeometryUtils.firstSegmentDirection(nextRoadLink.geometry))
      } else {
        (GeometryUtils.firstSegmentDirection(prevRoadLink.geometry), GeometryUtils.lastSegmentDirection(nextRoadLink.geometry))
      }
    }
    seg1.angleXYWithNegativeValues(seg2)
  }

  override def getAsset(roadLinks: Seq[RoadLink]): Seq[AssetType] = {
    manoeuvreDao.getByRoadLinks(roadLinks.map(_.linkId))
  }


  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {

    if (assetInfo.ids.toSeq.nonEmpty) {
      withDynTransaction {

        val manoeuvre = manoeuvreDao.fetchManoeuvreById(assetInfo.ids.head)
        val sourceLinkId = manoeuvre.find(_.elementType == ElementTypes.FirstElement).get.linkId
        val nextLinkId = manoeuvre.find(_.elementType == ElementTypes.FirstElement).get.destLinkId

        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(sourceLinkId, nextLinkId), newTransaction = false)


        if(roadLinks.exists(road => road.linkId == sourceLinkId && road.administrativeClass != Private)){
          val (sourceFirst, sourceLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == sourceLinkId).head.geometry)
          val (secondFirst, secondLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == nextLinkId).head.geometry)

          val pointOfInterest : Point = if (GeometryUtils.areAdjacent(sourceFirst, secondFirst) || GeometryUtils.areAdjacent(sourceFirst, secondLast)) sourceLast else sourceFirst

          val trafficSigns =
            splitBothDirectionTrafficSignInTwo(trafficSignService.getTrafficSignByRadius(pointOfInterest, radiusDistance) ++ trafficSignService.getTrafficSign(Seq(sourceLinkId)))
              .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(trafficSignService.getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
              .filterNot(_.floating)

          inaccurateAssetDAO.deleteInaccurateAssetByLinkIds((manoeuvre.map(_.linkId) ++ trafficSigns.map(_.linkId) ++ assetInfo.newLinkIds).toSet  ,assetTypeInfo.typeId)

          trafficSigns.foreach(validateAndInsert)
        }
      }
    }
  }
}

