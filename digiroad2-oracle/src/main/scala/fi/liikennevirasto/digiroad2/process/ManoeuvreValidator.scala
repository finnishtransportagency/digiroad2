package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.asset.Manoeuvres
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreTurnRestrictionType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import scala.math.Pi

class ManoeuvreValidator extends AssetServiceValidatorOperations {
  override type AssetType = Manoeuvre
  override def assetName: String = "manoeuvre"
  override val radiusDistance: Int = 50
  override def assetType: Int = Manoeuvres.typeId
  lazy val manoeuvreDao: ManoeuvreDao = new ManoeuvreDao(vvhClient)

  val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.NoLeftTurn, TrafficSignType.NoRightTurn, TrafficSignType.NoUTurn)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double): Seq[AssetType] = {
    assets.filter{_.elements.filter(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).contains(roadLink.linkId)}
  }

  override def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    val manoeuvres = assets.asInstanceOf[Seq[Manoeuvre]]

    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(manoeuvres.flatMap(manoeuvre => manoeuvre.elements.map(_.sourceLinkId)).toSet)

    manoeuvres.flatMap {
      manoeuvre =>
        val roadLink = roadLinks.find(roadLink =>  manoeuvre.elements.map(_.sourceLinkId).contains(roadLink.linkId)).get
        val manoeuvreTurnRestrictionType = getManoeuvreTurnRestrictionType(manoeuvre, roadLinks)

        TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
          case TrafficSignType.NoLeftTurn =>
            if(manoeuvreTurnRestrictionType != ManoeuvreTurnRestrictionType.LeftTurn)
              Seq(Inaccurate(Some(manoeuvre.id), None, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
          case TrafficSignType.NoRightTurn =>
            if (manoeuvreTurnRestrictionType != ManoeuvreTurnRestrictionType.RightTurn)
              Seq(Inaccurate(Some(manoeuvre.id), None, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
          case TrafficSignType.NoUTurn =>
            if (manoeuvreTurnRestrictionType != ManoeuvreTurnRestrictionType.UTurn)
              Seq(Inaccurate(Some(manoeuvre.id), None, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
          case _ => throw new NumberFormatException("Not supported trafficSign on Manoeuvres asset")
        }
    }.toSet
  }

  override def getAdjacents(previousInfo: (Point, RoadLink), roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign) : Seq[(RoadLink, (Point, Point))] = {
    val adjacentInfo = roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousInfo._1)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo._1)) (first, last) else (last, first)

      (roadLink, points)
    }

    TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
      case TrafficSignType.NoLeftTurn | TrafficSignType.NoUTurn =>
        adjacentInfo.filter(_._1 == roadLinkService.pickLeftMost(previousInfo._2, adjacentInfo.map(_._1)))
      case TrafficSignType.NoRightTurn =>
        adjacentInfo.filter(_._1 == roadLinkService.pickRightMost(previousInfo._2, adjacentInfo.map(_._1)))
      case _ => Seq()
    }
  }

//  override def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSign: Seq[PersistedTrafficSign]): Boolean = {
//    val manoeuvres = Seq(asset.asInstanceOf[Manoeuvre])
//    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(manoeuvres.flatMap{_.elements.filterNot(_.elementType == ElementTypes.LastElement).flatMap{element => Seq(element.sourceLinkId) ++ Seq(element.destLinkId)}}.toSet)
//
//    val manoeuvreTurnRestrictionType = manoeuvres.map {
//      manoeuvre =>
//        getManoeuvreTurnRestrictionType(manoeuvre, roadLinks)
//    }
//    trafficSign.exists {
//      trafficSign =>
//        TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
//          case TrafficSignType.NoLeftTurn =>
//            manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.LeftTurn)
//          case TrafficSignType.NoRightTurn =>
//            manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.RightTurn)
//          case TrafficSignType.NoUTurn =>
//            manoeuvreTurnRestrictionType.contains(ManoeuvreTurnRestrictionType.UTurn)
//          case _ => throw new NumberFormatException("Not supported trafficSign on Manoeuvres asset")
//        }
//    }
//  }

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

  override def getAsset(roadLinks: Seq[RoadLink]): Seq[AssetType] = {
    manoeuvreDao.getByRoadLinks(roadLinks.map(_.linkId))
  }

//  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
//    trafficSignService.getTrafficSign(Seq(roadLink.linkId)).filter(trafficSign =>
//      allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)))
//  }

//  def validateManoeuvre(manoeuvre: Manoeuvre) : Boolean  = {
//
//    val sourceLinkId = manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).head
//    val nextLinkId = manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId).getOrElse(manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).head)
//
//    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(sourceLinkId, nextLinkId))
//
//    val (sourceFirst, sourceLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == sourceLinkId).head.geometry)
//    val (secondFirst, secondLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == nextLinkId).head.geometry)
//
//    val pointOfInterest : Point = if (GeometryUtils.areAdjacent(sourceFirst, secondFirst) || GeometryUtils.areAdjacent(sourceFirst, secondLast)) sourceLast else sourceFirst
//
//    assetValidatorX(manoeuvre, pointOfInterest ,roadLinks.filter(_.linkId == sourceLinkId).head)
//  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {

//    val asset: PersistedLinearAsset = validatorInfo.newAssetId match {
//      case Some(newAssetId) => dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, Set(newAssetId)).head
//      case _ => validatorInfo.oldAsset.asInstanceOf[Manoeuvre]
//    }
//
//    val sourceLinkId = asset.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).head
//    val nextLinkId = asset.elements.find(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId)
//      .getOrElse(asset.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).head)
//
//    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(sourceLinkId, nextLinkId), newTransaction = false)
//
//    val (sourceFirst, sourceLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == sourceLinkId).head.geometry)
//    val (secondFirst, secondLast) = GeometryUtils.geometryEndpoints(roadLinks.filter(_.linkId == nextLinkId).head.geometry)
//
//    val pointOfInterest : Point = if (GeometryUtils.areAdjacent(sourceFirst, secondFirst) || GeometryUtils.areAdjacent(sourceFirst, secondLast)) sourceLast else sourceFirst
//
//
//    val trafficSingsByRadius: Seq[PersistedTrafficSign] =
//      trafficSignService.getTrafficSignByRadius(pointOfInterest, radiusDistance)
//        .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
//
//    inaccurateAssetDAO.deleteInaccurateAssetById(asset.id)
//
//    trafficSingsByRadius.foreach { trafficSign =>
//      assetValidator(trafficSign).foreach{
//        inaccurate =>
//          (inaccurate.assetId, inaccurate.linkId) match {
//            case (Some(assetId), _) => inaccurateAssetDAO.createInaccurateAsset(assetId, assetType, inaccurate.municipalityCode, inaccurate.administrativeClass)
//            case (_, Some(linkId)) => inaccurateAssetDAO.createInaccurateLink(linkId, assetType, inaccurate.municipalityCode, roadLink.administrativeClass)
//            case _ => None
//          }
//      }
//    }
 }
}

