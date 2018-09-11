package fi.liikennevirasto.digiroad2.process

import java.sql.{SQLException, SQLIntegrityConstraintViolationException}

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, AssetTypeInfo, HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignType.NoVehiclesWithDangerGoods

class HazmatTransportProhibitionValidator extends AssetServiceValidatorOperations {
  override type AssetType = PersistedLinearAsset
  override def assetTypeInfo: AssetTypeInfo =  HazmatTransportProhibition
  override val radiusDistance: Int = 50

  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val allowedTrafficSign: Set[TrafficSignType] = Set(TrafficSignType.HazmatProhibitionA, TrafficSignType.HazmatProhibitionB, NoVehiclesWithDangerGoods)

  override def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], pointOfInterest: Point, distance: Double): Seq[AssetType] = {
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
      TrafficSignType.apply(getTrafficSignsProperties(trafficSign, "trafficSigns_type").get.propertyValue.toInt) match {

        case TrafficSignType.HazmatProhibitionA => if(!comparingProhibitionValue(prohibition, 24))
          Seq(Inaccurate(Some(prohibition.id), None, roadLink.municipalityCode, roadLink.administrativeClass)) else Seq()
        case TrafficSignType.HazmatProhibitionB => if(!comparingProhibitionValue(prohibition, 25))
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

        val assets = dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, assetInfo.newIds)
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet, newTransaction = false)

        assets.foreach { asset =>
          val roadLink = roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)
          val assetGeometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
          val (first, last) = GeometryUtils.geometryEndpoints(assetGeometry)

          val trafficSings: Seq[PersistedTrafficSign] = getPointOfInterest(first, last, SideCode.apply(asset.sideCode)).flatMap { position =>
            splitBothDirectionTrafficSignInTwo(trafficSignService.getTrafficSignByRadius(position, radiusDistance))
              .filter(sign => allowedTrafficSign.contains(TrafficSignType.apply(getTrafficSignsProperties(sign, "trafficSigns_type").get.propertyValue.toInt)))
              .filterNot(_.floating)
          }

          inaccurateAssetDAO.deleteInaccurateAssetByIds(assetInfo.oldIds)
          inaccurateAssetDAO.deleteInaccurateAssetByLinkIds(assetInfo.newLinkIds ++ trafficSings.map(_.linkId) ,assetTypeInfo.typeId)

          trafficSings.foreach { trafficSign =>
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

