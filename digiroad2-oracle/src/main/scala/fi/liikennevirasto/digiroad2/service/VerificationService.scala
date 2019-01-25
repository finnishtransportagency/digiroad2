package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.VerificationDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class VerificationInfo(municipalityCode: Int, municipalityName: String, assetTypeCode: Int, assetTypeName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime], verified: Boolean = false, geometryType: String, counter: Option[Int] = None,
                            modifiedBy: Option[String] = None, modifiedDate: Option[DateTime] = None)
case class LatestModificationInfo(assetTypeCode: Int, modifiedBy: Option[String], modifiedDate: Option[DateTime])

class VerificationService(eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def dao: VerificationDao = new VerificationDao

  def getAssetTypesByMunicipality(municipalityCode: Int): List[VerificationInfo] = {
    withDynSession {
      dao.getVerifiedAssetTypes(municipalityCode)
    }
  }

  def getAssetVerification(municipalityCode: Int, assetTypeId: Int): Seq[VerificationInfo] = {
    withDynSession {
      dao.getAssetVerification(municipalityCode, assetTypeId)
    }
  }

  def getCriticalAssetVerification(municipalityCode: Int, assetTypeIds: Seq[Int]): Seq[VerificationInfo] = {
    withDynSession {
      dao.getCriticalAssetVerification(municipalityCode, assetTypeIds)
    }
  }
//
//  def getAssetVerificationById(id: Long, assetTypeId: Int): Option[VerificationInfo] = {
//    withDynSession {
//      dao.getAssetVerificationById(id)
//    }
//  }

  def verifyAssetType(municipalityCode: Int, assetTypeIds: Set[Int], userName: String) = {
    withDynSession {
      if (!assetTypeIds.forall(dao.getVerifiableAssetTypes.contains))
        throw new IllegalStateException("Asset type not allowed")

      assetTypeIds.map { typeId =>
        dao.expireAssetTypeVerification(municipalityCode, typeId, userName)
        dao.insertAssetTypeVerification(municipalityCode, typeId, userName)
      }
    }
  }

  def getMunicipalityInfo(bounds: BoundingRectangle): Option[Int] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    val midPoint = Point((bounds.rightTop.x + bounds.leftBottom.x) / 2, (bounds.rightTop.y + bounds.leftBottom.y) / 2)

    if(roadLinks.nonEmpty)
      Some(roadLinks.minBy(road => GeometryUtils.minimumDistance(midPoint, road.geometry)).municipalityCode)
    else
      None
  }

  def getAssetVerificationInfo(typeId: Int, municipality: Int): VerificationInfo = {
    getAssetVerification(municipality, typeId).headOption
      .getOrElse( throw new IllegalArgumentException("Asset type or municipality Code not found"))
  }

  def setAssetTypeVerification(municipalityCode: Int, assetTypeIds: Set[Int], username: String): Seq[Long] = {
    if (!assetTypeIds.forall(dao.getVerifiableAssetTypes.contains))
      throw new IllegalStateException("Asset type not allowed")

    withDynTransaction {
      assetTypeIds.map { assetTypeId =>
        dao.insertAssetTypeVerification(municipalityCode, assetTypeId, username)
      }
    }.toSeq
  }

  def removeAssetTypeVerification(municipalityCode: Int, assetTypeIds: Set[Int], userName: String) : Set[Int] = {
    withDynTransaction{
      assetTypeIds.map { assetType =>
        dao.expireAssetTypeVerification(municipalityCode, assetType, userName)
      }
    }
  }

  def getCriticalAssetTypesByMunicipality(municipalityCode: Int): List[VerificationInfo] = {
    val criticalAssetTypes =
      Seq(
        MassTransitStopAsset.typeId,
        SpeedLimitAsset.typeId,
        TotalWeightLimit.typeId,
        Prohibition.typeId,
        Manoeuvres.typeId
      )

    getCriticalAssetVerification(municipalityCode, criticalAssetTypes).toList
  }

  def getAssetLatestModifications(municipalities: Set[Int]): List[LatestModificationInfo] = {
    val tinyRoadLink = municipalities.flatMap { municipality =>
      roadLinkService.getTinyRoadLinkFromVVH(municipality)
    }

    withDynTransaction {
      dao.getModifiedAssetTypes(tinyRoadLink.map(_.linkId))
    }
  }

  def getLastModificationLinearAssets(municipality: Int): Seq[LatestModificationInfo] = {
    val tinyRoadLink = roadLinkService.getTinyRoadLinkFromVVH(municipality)

    withDynTransaction {
      dao.getLastModificationLinearAssets(tinyRoadLink.map(_.linkId).toSet)
    }
  }

  def getLastModificationPointAssets(municipality: Int): Seq[LatestModificationInfo] = {
    withDynSession {
      dao.getLastModificationPointAssets(municipality)
    }
  }

  def getNumberOfPointAssets(municipality: Int): Seq[(Int, Int)] = {
    withDynSession {
      dao.getNumberOfPointAssets(municipality)
    }
  }

  def getVerifiedInfoTypes(municipality: Int): Seq[VerificationInfo] = {
    withDynSession {
      dao.getVerifiedInfoTypes(municipality)
    }
  }

  def getAssetTypesByMunicipalityF(municipality: Int): Seq[VerificationInfo] = {
    val fut = for {
      linearLastModification <- Future(getLastModificationLinearAssets(municipality))
      pointLastModification <- Future(getLastModificationPointAssets(municipality))
      numberOfPointAssets <- Future(getNumberOfPointAssets(municipality))
      verifiedInfo <- Future(getVerifiedInfoTypes(municipality))
    } yield (linearLastModification, pointLastModification, numberOfPointAssets, verifiedInfo)

    val (linearLastModification, pointLastModification, numberOfPointAssets, verifiedInfo) = Await.result(fut, Duration.Inf)

    val assetsLastModification = linearLastModification ++ pointLastModification
    verifiedInfo.map{ info =>
      val numberOfAssets : Option[Int] = if(info.geometryType == "point")
        numberOfPointAssets.find(_._1 == info.assetTypeCode).map(_._2)
      else {
        Some(if (linearLastModification.exists(_.assetTypeCode == info.assetTypeCode)) 1 else 0)
      }

      val assetModificationInfo = assetsLastModification.find(_.assetTypeCode == info.assetTypeCode)

      if(assetModificationInfo.nonEmpty)
        VerificationInfo(info.municipalityCode, info.municipalityName, info.assetTypeCode, info.assetTypeName, info.verifiedBy, info.verifiedDate, info.verified, info.geometryType, numberOfAssets, assetModificationInfo.get.modifiedBy, assetModificationInfo.get.modifiedDate )
      else
        VerificationInfo(info.municipalityCode, info.municipalityName, info.assetTypeCode, info.assetTypeName, info.verifiedBy, info.verifiedDate, info.verified, info.geometryType, numberOfAssets)
    }
  }
}
