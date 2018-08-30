package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, HazmatTransportProhibition, PropertyValue, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.GenericQueries
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreTurnRestrictionType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType, TrafficSignTypeGroup}

import scala.math.Pi

trait AssetServiceValidator {
  type AssetType

  def trafficSignService: TrafficSignService
  def roadLinkService: RoadLinkService

  val radiusDistance: Int = 50
  val TrafficSignsGroup: Option[TrafficSignTypeGroup] = None

  def getLinkIdsByRadius(point: Point): Seq[RoadLink] = {
    val topLeft = Point(point.x - radiusDistance, point.y - radiusDistance)
    val bottomRight = Point(point.x + radiusDistance, point.y + radiusDistance)

    roadLinkService.getRoadLinksWithComplementaryFromVVH(BoundingRectangle(topLeft, bottomRight))
  }

  protected def getPointOfInterest(first: Point, last: Point, sideCode: SideCode): Seq[Point] = {
    sideCode match {
      case SideCode.TowardsDigitizing => Seq(first)
      case SideCode.AgainstDigitizing => Seq(last)
      case _ => Seq(first, last) //Not expected a traffic sign with bothDirection
    }
  }

  def findNearestRoadLink(point: Point, roadLinks: Seq[RoadLink]): RoadLink = {
    roadLinks.map { roadLink =>
      (GeometryUtils.calculateLinearReferenceFromPoint(Point(point.x, point.y), roadLink.geometry), roadLink)
    }.minBy(_._1)._2
  }

  def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean

  def verifyAssetX(asset: AssetType, roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign]): Boolean

  def getAsset(roadLink: RoadLink): Seq[AssetType]

  def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign]

  def getAdjacentRoadLink(point: Point,  prevRoadLink: RoadLink, roadLinks: Seq[RoadLink]) : Seq[(RoadLink, (Point, Point))]

  def getAdjacents(previousPoint: Point, roadLinks: Seq[RoadLink]): Seq[(RoadLink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousPoint)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousPoint)) (first, last) else (last, first)

      (roadLink, points)
    }
  }

  //TODO needs to be refactor
  def assetValidator(trafficSign: PersistedTrafficSign): Boolean = {
    val point = Point(trafficSign.lon, trafficSign.lon)
    val roadLinks = getLinkIdsByRadius(point)
    val trafficSignRoadLink = findNearestRoadLink(point, roadLinks)

    val (first, last) = GeometryUtils.geometryEndpoints(trafficSignRoadLink.geometry)
    val pointOfInterest = getPointOfInterest(first, last, SideCode.apply(trafficSign.validityDirection)).head

    validator(pointOfInterest, trafficSignRoadLink, roadLinks: Seq[RoadLink], trafficSign)
  }


  def assetValidatorX(asset: AssetType, pointOfInterest: Point, defaultRoadLink: RoadLink): Boolean = {
    val roadLinks = getLinkIdsByRadius(pointOfInterest)
    validatorX(pointOfInterest, defaultRoadLink, roadLinks, asset)
  }


  protected def validator(point: Point, oldRoadLink: RoadLink, roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == oldRoadLink.linkId)
    val adjAcents = getAdjacents(point, filteredRoadLink)
    if(adjAcents.isEmpty)
      false
    else {
      adjAcents.forall { case (newRoadLink, (_, oppositePoint)) =>

        val asset = getAsset(newRoadLink)
        if (asset.isEmpty) {
          validator(oppositePoint, newRoadLink: RoadLink, filteredRoadLink, trafficSign)
        } else {
          verifyAsset(asset, roadLinks, trafficSign)
        }
      }
    }
  }

  protected def validatorX(point: Point, prevRoadLink: RoadLink, roadLinks: Seq[RoadLink], asset: AssetType): Boolean = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == prevRoadLink.linkId)
    getAdjacentRoadLink(point, prevRoadLink, filteredRoadLink).exists { case (newRoadLink, (_, oppositePoint)) =>
      val trafficSign = getAssetTrafficSign(newRoadLink)
      if (trafficSign.isEmpty) {
        validatorX(oppositePoint, newRoadLink, filteredRoadLink, asset)
      } else {
        verifyAssetX(asset, roadLinks, trafficSign)
      }
    }
  }

  //TODO move this method to a generic place
  def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }
}

class ManoeuvreServiceValidator(roadLinkServiceImpl: RoadLinkService, trafficSignServiceImpl: TrafficSignService ) extends AssetServiceValidator {
  override type AssetType = Manoeuvre

  override def trafficSignService: TrafficSignService = trafficSignServiceImpl
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  def manoeuvreDao: ManoeuvreDao = new ManoeuvreDao(roadLinkServiceImpl.vvhClient)

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

  override def verifyAssetX(asset: AssetType, roadLinks: Seq[RoadLink], trafficSign: Seq[PersistedTrafficSign]): Boolean = {
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
    trafficSignService.getTrafficSign(roadLink.linkId).filter(trafficSign =>
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
}


class HazmatTransportProhibitionValidator(roadLinkServiceImpl: RoadLinkService, trafficSignServiceImpl: TrafficSignService ) extends AssetServiceValidator {
  override type AssetType = PersistedLinearAsset

  override def trafficSignService: TrafficSignService = trafficSignServiceImpl
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)

  override def getAdjacentRoadLink(point: Point,  prevRoadLink: RoadLink, roadLinks: Seq[RoadLink]) : Seq[(RoadLink, (Point, Point))] = {
    getAdjacents(point, roadLinks)
  }

  def comparingProhibitionValue(prohibition: PersistedLinearAsset, typeId: Int) : Boolean = {
    prohibition.value match {
      case Some(value) => value.asInstanceOf[Prohibitions].prohibitions.exists(_.typeId == typeId)
      case _ => false
    }
  }

  override def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean = {
    val prohibitions = assets.asInstanceOf[Seq[PersistedLinearAsset]]

    prohibitions.forall { prohibition =>
      TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {

        case TrafficSignType.HazmatProhibitionA => comparingProhibitionValue(prohibition, 24)
        case TrafficSignType.HazmatProhibitionB => comparingProhibitionValue(prohibition, 25)
        case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
      }
    }
  }

  override def verifyAssetX(asset: AssetType, roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign]): Boolean = {
    val prohibitions = Seq(asset.asInstanceOf[PersistedLinearAsset])
    // fetch all asset in theses roadLinks
    //verify if exist some place in adjacent without link
    val linkIdWithAsset = GenericQueries.getLinkIdsWithMatchedAsset(asset.typeId, roadLinks.map(_.linkId))
    val filterRoadLink = roadLinks.filterNot(roadLink => linkIdWithAsset.contains(roadLink.linkId))

    if(filterRoadLink.nonEmpty) {

      filterRoadLink.exists { roadLink =>
        trafficSigns.filter(_.linkId == roadLink.linkId).exists{ trafficSign =>
          TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {

            case TrafficSignType.HazmatProhibitionA => comparingProhibitionValue(prohibitions.head, 24)

            case TrafficSignType.HazmatProhibitionB => comparingProhibitionValue(prohibitions.head, 24)
            case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
          }

        }
      }
    }else true
  }

  override def getAsset(roadLink: RoadLink): Seq[AssetType] = {
    dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId ,Seq(roadLink.linkId), false)
  }

  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    val trafficSignsRelevantToHazmatTransportProhibition: Set[TrafficSignType] = Set(TrafficSignType.HazmatProhibitionA, TrafficSignType.HazmatProhibitionB)
    trafficSignService.getTrafficSign(roadLink.linkId).filter(trafficSign =>
      trafficSignsRelevantToHazmatTransportProhibition.contains(TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)))
  }

  def validateHazmatTransportProhibition(prohibition: PersistedLinearAsset) : Boolean  = {
    val roadLink = roadLinkService.getRoadLinkByLinkIdFromVVH(prohibition.linkId).head
    val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)

    val pointsOfInterest = getPointOfInterest(first, last, SideCode.apply(prohibition.sideCode))

    if (!pointsOfInterest.exists { pointOfInterest =>
      assetValidatorX(prohibition, pointOfInterest, roadLink)
    }) false else true
  }
}
