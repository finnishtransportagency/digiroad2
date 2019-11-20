package fi.liikennevirasto.digiroad2.process

import java.sql.SQLIntegrityConstraintViolationException
import fi.liikennevirasto.digiroad2.dao.pointasset.{OraclePedestrianCrossingDao, PedestrianCrossing, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.{GeometryUtils, PedestrianCrossingSign, Point, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, PedestrianCrossings, Private, SideCode}

class PedestrianCrossingValidator extends AssetServiceValidatorOperations {
  override type AssetType = PedestrianCrossing
  override def assetTypeInfo: AssetTypeInfo =  PedestrianCrossings
  override val radiusDistance: Int = 50

  lazy val dao: OraclePedestrianCrossingDao = new OraclePedestrianCrossingDao()
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  val allowedTrafficSign: Set[TrafficSignType] = Set(PedestrianCrossingSign)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double, trafficSign: Option[PersistedTrafficSign] = None): Seq[AssetType] = {
    def assetDistance(assets: Seq[AssetType]): Option[(AssetType, Double)] = {
      val (first, _) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val isAdjacente = GeometryUtils.areAdjacent(pointOfInterest, first)
        trafficSign match {
        case Some(traffic) =>
          val filteredAsset = if(SideCode.apply(traffic.validityDirection) == TowardsDigitizing)
            assets.filter(a => a.linkId == roadLink.linkId && a.mValue >= traffic.mValue)
          else
            assets.filter(a => a.linkId == roadLink.linkId && a.mValue <= traffic.mValue)

          if (filteredAsset.nonEmpty) {
            val nearestAsset = filteredAsset.minBy(asset => Math.abs(asset.mValue - traffic.mValue))
            Some(nearestAsset, Math.abs(traffic.mValue - nearestAsset.mValue))
          } else
            None
        case _ =>
          val filteredAsset =  assets.filter(a => a.linkId == roadLink.linkId)
          if (filteredAsset.nonEmpty) {
            if(isAdjacente) {
              val nearestAsset = filteredAsset.minBy(_.mValue)
              Some(nearestAsset, nearestAsset.mValue)
            }
            else {
              val nearestAsset = assets.filter(a => a.linkId == roadLink.linkId).maxBy(_.mValue)
              Some(nearestAsset, GeometryUtils.geometryLength(roadLink.geometry) - nearestAsset.mValue)
            }
          } else
            None
      }
    }

    val resultAssets = assetDistance(assets)
    if (resultAssets.nonEmpty && resultAssets.get._2 + distance <= radiusDistance) {
      Seq(resultAssets.get._1)
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
                val trafficSingsByRadius: Set[PersistedTrafficSign] =
                splitBothDirectionTrafficSignInTwo(trafficSignService.getTrafficSignByRadius(Point(asset.lon, asset.lat), radiusDistance) ++ trafficSignService.getTrafficSign(Seq(roadLink.linkId)))
                  .filter(sign => allowedTrafficSign.contains(TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, "trafficSigns_type").get.propertyValue.toInt)))
                  .filterNot(_.floating)

              val allLinkIds = assetInfo.newLinkIds ++ trafficSingsByRadius.map(_.linkId)

              inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.ids)
              if(allLinkIds.nonEmpty)
                inaccurateAssetDAO.deleteInaccurateAssetByLinkIds(allLinkIds, assetTypeInfo.typeId)

              trafficSingsByRadius.foreach { trafficSign =>
                assetValidator(trafficSign).foreach {
                  inaccurate =>
                    (inaccurate.assetId, inaccurate.linkId) match {
                      case (Some(assetId), _) => insertInaccurate(inaccurateAssetDAO.createInaccurateAsset, assetId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
                      case (_, Some(linkId)) =>
                        try {
                          insertInaccurate(inaccurateAssetDAO.createInaccurateLink, linkId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
                        } catch {
                          case e: SQLIntegrityConstraintViolationException =>
                            println(s"more than one for the same linkId: $linkId -> $e" )
                        }
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
