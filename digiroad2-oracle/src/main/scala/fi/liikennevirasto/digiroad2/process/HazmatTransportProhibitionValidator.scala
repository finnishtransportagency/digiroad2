package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType.NoVehiclesWithDangerGoods
import fi.liikennevirasto.digiroad2.util.AssetValidatorProcess.inaccurateAssetDAO

class HazmatTransportProhibitionValidator extends AssetServiceValidatorOperations {
  override type AssetType = PersistedLinearAsset
  override def assetName: String = "hazmatTransportProhibition"
  override def assetType: Int = HazmatTransportProhibition.typeId
  override val radiusDistance: Int = 50

  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)

  val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.HazmatProhibitionA, TrafficSignType.HazmatProhibitionB, NoVehiclesWithDangerGoods)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType]): Seq[AssetType] = {
    assets.filter(_.linkId == roadLink.linkId)
  }

  def comparingProhibitionValue(prohibition: PersistedLinearAsset, typeId: Int) : Boolean = {
    prohibition.value match {
      case Some(value) => value.asInstanceOf[Prohibitions].prohibitions.exists(_.typeId == typeId)
      case _ => false
    }
  }

  override def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Seq[Inaccurate] = {
    val prohibitions = assets.asInstanceOf[Seq[PersistedLinearAsset]]

    prohibitions.flatMap{ prohibition =>
      val roadLink = roadLinks.find(_.linkId == prohibition.linkId)
      TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {

        case TrafficSignType.HazmatProhibitionA => if(!comparingProhibitionValue(prohibition, 24))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.get.municipalityCode, roadLink.get.administrativeClass)) else Seq()
        case TrafficSignType.HazmatProhibitionB => if(!comparingProhibitionValue(prohibition, 25))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.get.municipalityCode, roadLink.get.administrativeClass)) else Seq()
        case NoVehiclesWithDangerGoods => Seq()
        case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
      }
    }
  }

//  override def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSigns: Seq[PersistedTrafficSign]): Boolean = {
//    /*    val prohibitions = Seq(asset.asInstanceOf[PersistedLinearAsset])
//        // fetch all asset in theses roadLinks
//        //verify if exist some place in adjacent without link
//        val linkIdWithAsset = GenericQueries.getLinkIdsWithMatchedAsset(asset.typeId, roadLinks.map(_.linkId))
//        val filterRoadLink = roadLinks.filterNot(roadLink => linkIdWithAsset.contains(roadLink.linkId))
//
//        if(filterRoadLink.nonEmpty) {
//
//          filterRoadLink.exists { roadLink =>
//            trafficSigns.filter(_.linkId == roadLink.linkId).exists{ trafficSign =>
//              TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {
//
//                case TrafficSignType.HazmatProhibitionA => comparingProhibitionValue(prohibitions.head, 24)
//                case TrafficSignType.HazmatProhibitionB => comparingProhibitionValue(prohibitions.head, 24)
//                case NoVehiclesWithDangerGoods => true
//                case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
//              }
//            }
//          }
//        }els*/ true
//  }

  override def getAsset(roadLinks: Seq[RoadLink]): Seq[AssetType] = {
    dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, roadLinks.map(_.linkId), false)
  }

  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    val trafficSignsRelevantToHazmatTransportProhibition: Set[TrafficSignType] = Set(TrafficSignType.HazmatProhibitionA, TrafficSignType.HazmatProhibitionB)
    trafficSignService.getTrafficSign(Seq(roadLink.linkId)).filter(trafficSign =>
      trafficSignsRelevantToHazmatTransportProhibition.contains(TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt)))
  }

//  def validateHazmatTransportProhibition(prohibition: PersistedLinearAsset) : Boolean  = {
//    val roadLink = roadLinkService.getRoadLinkByLinkIdFromVVH(prohibition.linkId).head
//    val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
//
//    val pointsOfInterest = getPointOfInterest(first, last, SideCode.apply(prohibition.sideCode))
//
//    if (!pointsOfInterest.exists { pointOfInterest =>
//      assetValidatorX(prohibition, pointOfInterest, roadLink)
//    }) false else true
//  }


  override def reprocessAllRelevantTrafficSigns(asset: AssetType, roadLink: RoadLink): Unit = {
    val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
    val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

    val trafficSingsByRadius: Seq[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap { case position =>
      trafficSignService.getTrafficSignByRadius(position, radiusDistance)
        .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
    }

    trafficSingsByRadius.foreach { trafficSign =>
      assetValidator(trafficSign).foreach{
        inaccurate =>
          (inaccurate.assetId, inaccurate.linkId) match {
            case (Some(assetId), _) => inaccurateAssetDAO.createInaccurateAsset(assetId, assetType, inaccurate.municipalityCode, inaccurate.administrativeClass)
            case (_, Some(linkId)) => inaccurateAssetDAO.createInaccurateLink(linkId, assetType, inaccurate.municipalityCode, roadLink.administrativeClass)
            case _ => None
          }
      }
    }
  }
}

