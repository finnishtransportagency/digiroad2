package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.VerificationDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, TinyRoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class VerificationInfo(municipalityCode: Int, municipalityName: String, assetTypeCode: Int, assetTypeName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime],  geometryType: String, counter: Int, verified: Boolean = false,
                            modifiedBy: Option[String] = None, modifiedDate: Option[DateTime] = None, refreshDate: Option[DateTime] = None)
case class LatestModificationInfo(assetTypeCode: Int, modifiedBy: Option[String], modifiedDate: Option[DateTime])

class VerificationService(eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def dao: VerificationDao = new VerificationDao

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

  def verifyAssetType(municipalityCode: Int, assetTypeIds: Set[Int], username: String) : Set[Long] = {
    withDynSession {
      if (!assetTypeIds.forall(dao.getVerifiableAssetTypes.contains))
        throw new IllegalStateException("Asset type not allowed")

      val oldInfo = getVerificationInfo(municipalityCode, assetTypeIds)
      assetTypeIds.map { typeId =>
        val oldAssetInfo = oldInfo.find(_._2 == typeId).head
        dao.expireAssetTypeVerification(municipalityCode, typeId, username)
        insertAssetTypeVerification(municipalityCode, typeId, Some(username), oldAssetInfo._3, oldAssetInfo._4, oldAssetInfo._5, oldAssetInfo._6)
      }
    }
  }

  def getVerificationInfo(municipalityCode: Int, assetTypeIds: Set[Int] = Set()) :  Seq[(Int, Int, Option[String], Option[DateTime], Int, Option[DateTime])] = {
    dao.getVerificationInfo(municipalityCode, assetTypeIds)
  }

  def insertAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, verifiedBy: Option[String], lastUserModification: Option[String], lastDateModification: Option[DateTime], numberOfAssets: Int, refreshDate: Option[DateTime]) : Long = {
    dao.insertAssetTypeVerification(municipalityCode, assetTypeId, verifiedBy, lastUserModification, lastDateModification, numberOfAssets, refreshDate)
  }

  def updateAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, lastUserModification: Option[String], lastDateModification: Option[DateTime], numberOfAssets: Int,  refreshDate: Option[DateTime]) : Unit = {
    dao.updateAssetTypeVerification(municipalityCode, assetTypeId, lastUserModification, lastDateModification, numberOfAssets, refreshDate )
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

  def removeAssetTypeVerification(municipalityCode: Int, assetTypeIds: Set[Int], username: String) : Set[Long] = {
    withDynTransaction{
      val oldInfo = getVerificationInfo(municipalityCode, assetTypeIds)
      assetTypeIds.map { typeId =>
        val oldAssetInfo = oldInfo.find(_._2 == typeId).head
        dao.expireAssetTypeVerification(municipalityCode, typeId, username)
        insertAssetTypeVerification(municipalityCode, typeId, None, oldAssetInfo._3, oldAssetInfo._4, oldAssetInfo._5, oldAssetInfo._6)
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

  def getLastModificationLinearAssets(linkIds: Seq[Long]): Seq[LatestModificationInfo] = {
    withDynTransaction {
      dao.getLastModificationLinearAssets(linkIds.toSet)
    }
  }

  def getLastModificationPointAssets(municipality: Int): Seq[LatestModificationInfo] = {
    withDynSession {
      dao.getLastModificationPointAssets(municipality)
    }
  }

  def getSuggestedLinearAssets(linkIds: Seq[Long]): Seq[(Long, Int)] = {
    withDynSession {
      dao.getSuggestedLinearAssets(linkIds.toSet)
    }
  }

  def getSuggestedPointAssets(municipality: Int): Seq[(Long, Int)] = {
    withDynSession {
      dao.getSuggestedPointAssets(municipality)
    }
  }

  def getNumberOfPointAssets(municipality: Int): Seq[(Int, Int)] = {
    withDynSession {
      dao.getNumberOfPointAssets(municipality)
    }
  }

  def getVerifiedAssetTypes: Seq[(Int, String)] = { withDynSession { dao.getVerifiedAssetTypes }}

  def refreshVerificationInfo(municipality: Int, linkIds: Seq[Long], refreshDate: Option[DateTime]) : Unit = {
    val assetsInfo = getAssetTypesByMunicipalityF(municipality, linkIds)
    val assetOnMunicipalityVerification = getVerificationInfo(municipality)

    val (toUpdate, toInsert) =  assetsInfo.partition{ case(_, typeId, _, _, _) => assetOnMunicipalityVerification.map(_._2).contains(typeId)}

    toUpdate.foreach { case (municipalityCode, typeId, counter, modifiedBy, modifiedDate) =>
      updateAssetTypeVerification(municipalityCode, typeId, modifiedBy, modifiedDate, counter, refreshDate)
    }

    toInsert.foreach {case (municipalityCode, typeId, counter, modifiedBy, modifiedDate) =>
      insertAssetTypeVerification(municipalityCode, typeId, None, modifiedBy, modifiedDate, counter, refreshDate)
    }
  }

  def getRefreshedAssetTypesByMunicipality(municipalityCode : Int,  getRoadLink: Int => Seq[RoadLink]): Seq[VerificationInfo] = {
    val tinyRoadLink = roadLinkService.getTinyRoadLinkFromVVH(municipalityCode)

    withDynTransaction {
      refreshVerificationInfo(municipalityCode, tinyRoadLink.map(_.linkId), Some(DateTime.now()))
    }
    getAssetTypesByMunicipality(municipalityCode)
  }

  def getAssetTypesByMunicipality(municipalityCode : Int): Seq[VerificationInfo] = {
    withDynSession {
      dao.getVerifiedInfoTypes(municipalityCode)
    }
  }

  def getAssetTypesByMunicipalityF(municipality: Int, linkIds: Seq[Long]): Seq[(Int, Int, Int, Option[String], Option[DateTime])] = {
    val fut = for {
      linearLastModification <- Future(getLastModificationLinearAssets(linkIds))
      pointLastModification <- Future(getLastModificationPointAssets(municipality))
      numberOfPointAssets <- Future(getNumberOfPointAssets(municipality))
      verifiedAssetTypes <- Future(getVerifiedAssetTypes)
      suggestedLinearAssets <- Future(getSuggestedLinearAssets(linkIds))
      suggestedPointAssets <- Future(getSuggestedPointAssets(municipality))
    } yield (linearLastModification, pointLastModification, numberOfPointAssets, verifiedAssetTypes, suggestedLinearAssets, suggestedPointAssets)

    val (linearLastModification, pointLastModification, numberOfPointAssets, verifiedAssetTypes, suggestedLinearAssets, suggestedPointAssets) = Await.result(fut, Duration.Inf)

    val assetsLastModification = linearLastModification ++ pointLastModification
    val suggestedAssets = suggestedLinearAssets ++ suggestedPointAssets
    verifiedAssetTypes.map{ case (typeId, geometryType) =>
      val numberOfAssets = if(geometryType == "point")
        numberOfPointAssets.find(_._1 == typeId).map(_._2).getOrElse(0)
      else {
        if (linearLastModification.exists(_.assetTypeCode == typeId)) 1 else 0
      }

      val assetModificationInfo = assetsLastModification.find(_.assetTypeCode == typeId)

      if(assetModificationInfo.nonEmpty)
        (municipality, typeId, numberOfAssets, assetModificationInfo.flatMap(_.modifiedBy), assetModificationInfo.flatMap(_.modifiedDate))
      else
        (municipality, typeId, numberOfAssets, None, None)
    }
  }

  def getNumberSuggestedAssetNumber(municipalityCode: Set[Int]) : Long = withDynTransaction { dao.getNumberSuggestedAssetNumber(municipalityCode)}
}
