package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType.NoVehiclesWithDangerGoods

class HazmatTransportProhibitionValidator extends AssetServiceValidator {
  override type AssetType = PersistedLinearAsset
  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)

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
        case NoVehiclesWithDangerGoods => true
        case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
      }
    }
  }

  override def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSigns: Seq[PersistedTrafficSign]): Boolean = {
    /*    val prohibitions = Seq(asset.asInstanceOf[PersistedLinearAsset])
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
                case NoVehiclesWithDangerGoods => true
                case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
              }
            }
          }
        }els*/ true
  }

  override def getAsset(roadLink: RoadLink): Seq[AssetType] = {
    dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId ,Seq(roadLink.linkId), false)
  }

  override def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign] = {
    val trafficSignsRelevantToHazmatTransportProhibition: Set[TrafficSignType] = Set(TrafficSignType.HazmatProhibitionA, TrafficSignType.HazmatProhibitionB)
    trafficSignService.getTrafficSign(Seq(roadLink.linkId)).filter(trafficSign =>
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

  override def assetName: String = "hazmatTransportProhibition"
}

