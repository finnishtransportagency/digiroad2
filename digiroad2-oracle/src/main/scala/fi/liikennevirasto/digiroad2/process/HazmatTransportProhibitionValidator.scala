package fi.liikennevirasto.digiroad2.process

import java.sql.{SQLException, SQLIntegrityConstraintViolationException}

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

class HazmatTransportProhibitionValidator extends AssetServiceValidatorOperations {
  override type AssetType = PersistedLinearAsset
  override def assetTypeInfo: AssetTypeInfo =  HazmatTransportProhibition
  override val radiusDistance: Int = 50

  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val allowedTrafficSign: Set[TrafficSignType] = Set(HazmatProhibitionA, HazmatProhibitionB, NoVehiclesWithDangerGoods)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double, trafficSign: Option[PersistedTrafficSign] = None): Seq[AssetType] = {
    def assetDistance(assets: Seq[AssetType]): (AssetType, Double) =  {
      val (first, _) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      if(GeometryUtils.areAdjacent(pointOfInterest, first)) {
        val nearestAsset = assets.filter(a => a.linkId == roadLink.linkId).minBy(_.startMeasure)
        (nearestAsset, nearestAsset.startMeasure)
      }else{
        val nearestAsset = assets.filter(a => a.linkId == roadLink.linkId).maxBy(_.endMeasure)
        (nearestAsset, GeometryUtils.geometryLength(roadLink.geometry) - nearestAsset.endMeasure)
      }
    }

    val assetOnLink = assets.filter(_.linkId == roadLink.linkId)
    if (assetOnLink.nonEmpty && assetDistance(assetOnLink)._2 + distance <= radiusDistance) {
      Seq(assetDistance(assets)._1)
    } else
      Seq()
  }

  def comparingProhibitionValue(prohibition: PersistedLinearAsset, typeId: Int) : Boolean = {
    prohibition.value match {
      case Some(value) => value.asInstanceOf[Prohibitions].prohibitions.exists(_.typeId == typeId)
      case _ => false
    }
  }

  override def verifyAsset(prohibitions: Seq[PersistedLinearAsset], roadLink: RoadLink, trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    prohibitions.flatMap{ prohibition =>
      TrafficSignType.applyOTHValue(trafficSignService.getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.asInstanceOf[TextPropertyValue].propertyValue.toInt) match {

        case HazmatProhibitionA => if(!comparingProhibitionValue(prohibition, 24))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
        case HazmatProhibitionB => if(!comparingProhibitionValue(prohibition, 25))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
        case NoVehiclesWithDangerGoods => Seq()
        case _ => throw new NumberFormatException("Not supported trafficSign on Prohibition asset")
      }
    }.toSet
  }

  override def getAsset(roadLinks: Seq[RoadLink]): Seq[AssetType] = {
    dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, roadLinks.map(_.linkId), false)
  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {
    if (assetInfo.ids.toSeq.nonEmpty) {
      withDynTransaction {

        val assets = dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, assetInfo.ids)
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false).filterNot(_.administrativeClass == Private)

        assets.foreach { asset =>
          roadLinks.find(_.linkId == asset.linkId) match {
            case Some(roadLink) =>
              val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
              val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

              val trafficSigns: Set[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap { position =>
                splitBothDirectionTrafficSignInTwo(trafficSignService.getTrafficSignByRadius(position, radiusDistance) ++ trafficSignService.getTrafficSign(Seq(asset.linkId)))
                  .filter(sign => allowedTrafficSign.contains(TrafficSignType.applyOTHValue(trafficSignService.getTrafficSignsProperties(sign, "trafficSigns_type").get.asInstanceOf[TextPropertyValue].propertyValue.toInt)))
                  .filterNot(_.floating)
              }.toSet

              val allLinkIds = assetInfo.newLinkIds ++ trafficSigns.map(_.linkId)

              inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.ids)
              if(allLinkIds.nonEmpty)
                inaccurateAssetDAO.deleteInaccurateAssetByLinkIds(assetInfo.newLinkIds ++ trafficSigns.map(_.linkId) ,assetTypeInfo.typeId)

              trafficSigns.foreach(validateAndInsert)

            case _ =>
          }
        }
      }
    }
  }
}

