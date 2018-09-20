package fi.liikennevirasto.digiroad2.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PedestrianCrossings, Private, SideCode}
import fi.liikennevirasto.digiroad2.dao.pointasset.{OraclePedestrianCrossingDao, PedestrianCrossing, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType

class PedestrianCrossingValidator extends AssetServiceValidatorOperations {
  override type AssetType = PedestrianCrossing
  override def assetTypeInfo: AssetTypeInfo =  PedestrianCrossings
  override val radiusDistance: Int = 50

  lazy val dao: OraclePedestrianCrossingDao = new OraclePedestrianCrossingDao()
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.PedestrianCrossing)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], trafficSignPoint: Point, distance: Double): Seq[AssetType] = {
    def assetDistance(assets: Seq[AssetType]): (AssetType, Double) = {
      val (first, _) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      if (GeometryUtils.areAdjacent(trafficSignPoint, first)) {
        val nearestAsset = assets.minBy(_.mValue)
        (nearestAsset, nearestAsset.mValue)
      } else {
        val nearestAsset = assets.maxBy(_.mValue)
        (nearestAsset, GeometryUtils.geometryLength(roadLink.geometry) - nearestAsset.mValue)
      }
    }

    val assetOnLink = assets.filter(_.linkId == roadLink.linkId)
    if (assetOnLink.nonEmpty && assetDistance(assetOnLink)._2 + distance <= radiusDistance) {
      Seq(assetDistance(assets)._1)
    } else
      Seq()
  }

  override def getAsset(roadLink: Seq[RoadLink]): Seq[AssetType] = {
    dao.fetchPedestrianCrossingByLinkIds(roadLink.map(_.linkId)).filterNot(_.floating)
  }


  override def verifyAsset(assets: Seq[AssetType], roadLink: RoadLink, trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    //Since the Pedestrian Crossing asset doesn't have value or direction, if exist asset on this method return a empty Set
    Set()
  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {
    if (assetInfo.ids.toSeq.nonEmpty) {
      withDynTransaction {

        val idFilter = s"where a.id in (${assetInfo.ids.mkString(",")})"
        val assets = dao.fetchByFilter(query => query + idFilter).filterNot(_.floating)
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false).filterNot(_.administrativeClass == Private)

        assets.foreach { asset =>
          roadLinks.find(_.linkId == asset.linkId) match {
            case Some(roadLink) =>
              val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.lon, asset.lat)
              val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

              val trafficSingsByRadius: Set[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.BothDirections).flatMap { position =>
                splitBothDirectionTrafficSignInTwo(trafficSignService.getTrafficSignByRadius(position, radiusDistance) ++ trafficSignService.getTrafficSign(Seq(asset.linkId)))
                  .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
                  .filterNot(_.floating)
              }.toSet
              val allLinkIds = assetInfo.newLinkIds ++ trafficSingsByRadius.map(_.linkId)

              inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.ids)
              if(allLinkIds.nonEmpty)
                inaccurateAssetDAO.deleteInaccurateAssetByLinkIds(allLinkIds, assetTypeInfo.typeId)

              trafficSingsByRadius.foreach { trafficSign =>
                assetValidator(trafficSign).foreach {
                  inaccurate =>
                    (inaccurate.assetId, inaccurate.linkId) match {
                      case (Some(assetId), _) => insertInaccurate(inaccurateAssetDAO.createInaccurateAsset, assetId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
                      case (_, Some(linkId)) => insertInaccurate(inaccurateAssetDAO.createInaccurateLink, linkId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
                      case _ => None
                    }
                }
              }
            case _ =>
          }
        }
      }
    }
  }
}
