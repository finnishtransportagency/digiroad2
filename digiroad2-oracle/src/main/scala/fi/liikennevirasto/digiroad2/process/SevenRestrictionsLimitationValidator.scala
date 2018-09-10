package fi.liikennevirasto.digiroad2.process

import java.sql.SQLIntegrityConstraintViolationException
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, AssetTypeInfo, HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

trait SevenRestrictionsLimitationValidator extends AssetServiceValidatorOperations {

  override type AssetType = PersistedLinearAsset
  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)
  override def assetTypeInfo: AssetTypeInfo =  HazmatTransportProhibition
  override val radiusDistance: Int = 50

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def comparingAssetAndTrafficValue(asset: PersistedLinearAsset, trafficSign: PersistedTrafficSign): Boolean = {true}

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double): Seq[AssetType] = {
    def assetDistance(assets: Seq[AssetType]): (AssetType, Double) = {
      val (first, _) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      if (GeometryUtils.areAdjacent(pointOfInterest, first)) {
        val nearestAsset = assets.minBy(_.startMeasure)
        (nearestAsset, nearestAsset.startMeasure)
      } else {
        val nearestAsset = assets.maxBy(_.endMeasure)
        (nearestAsset, GeometryUtils.geometryLength(roadLink.geometry) - nearestAsset.endMeasure)
      }
    }

    val assetOnLink = assets.filter(_.linkId == roadLink.linkId)
    if (assetOnLink.nonEmpty && assetDistance(assetOnLink)._2 + distance <= radiusDistance) {
      Seq(assetDistance(assets)._1)
    } else
      Seq()
  }

  def getAssetValue(asset: PersistedLinearAsset): String = {
    asset.value match {
      case Some(NumericValue(intValue)) => intValue.toString
      case _ => ""
    }
  }

  override def getAsset(roadLink: Seq[RoadLink]): Seq[AssetType] = {
    dao.fetchLinearAssetsByLinkIds(assetTypeInfo.typeId, roadLink.map(_.linkId), LinearAssetTypes.numericValuePropertyId, false).filterNot(_.expired)
  }

  override def verifyAsset(assets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    assets.flatMap { asset =>
      val roadLink = roadLinks.find(_.linkId == asset.linkId).get

      if (!comparingAssetAndTrafficValue(asset, trafficSign))
        Seq(Inaccurate(Some(asset.id), None, roadLink.municipalityCode, roadLink.administrativeClass))
      else
        Seq()
    }.toSet
  }

  override def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo): Unit = {
    def insertInaccurate(insertInaccurate: (Long, Int, Int, AdministrativeClass) => Unit, id: Long, assetType: Int, municipalityCode: Int, adminClass: AdministrativeClass): Unit = {
      try {
        insertInaccurate(id, assetType, municipalityCode, adminClass)
      } catch {
        case ex: SQLIntegrityConstraintViolationException =>
          print("duplicate key inserted ")
        case e: Exception => print("duplicate key inserted ")
          throw new RuntimeException("SQL exception " + e.getMessage)
      }
    }

    if (assetInfo.oldIds.toSeq.nonEmpty) {
      withDynTransaction {
        inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.oldIds.toSeq)

        val assets = dao.fetchAssetsWithTextualValuesByIds(assetInfo.oldIds ++ assetInfo.newIds, LinearAssetTypes.getValuePropertyId(assetTypeInfo.typeId)).filterNot(_.expired)
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false)

        assets.foreach { asset =>
          val roadLink = roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)
          val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
          val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

          val trafficSingsByRadius: Seq[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap {
            trafficSignService.getTrafficSignByRadius(_, radiusDistance)
              .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
              .filterNot(_.floating)
          }

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
        }
      }
    }
  }
}

