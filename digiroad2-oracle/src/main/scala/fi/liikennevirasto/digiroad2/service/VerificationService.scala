package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.VerificationDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, TinyRoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.LogUtils
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class VerificationInfo(municipalityCode: Int, municipalityName: String, assetTypeCode: Int, assetTypeName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime],  geometryType: String, counter: Int, verified: Boolean = false,
                            modifiedBy: Option[String] = None, modifiedDate: Option[DateTime] = None, refreshDate: Option[DateTime] = None, suggestedAssetsCount: Option[Int] = None)
case class LatestModificationInfo(assetTypeCode: Int, modifiedBy: Option[String], modifiedDate: Option[DateTime])
case class SuggestedAssetsStructure(municipalityName: String, municipalityCode: Int, assetTypeName: String, assetTypeId: Int, suggestedIds: Set[Int])

class VerificationService(eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def dao: VerificationDao = new VerificationDao

  def getAssetVerification(municipalityCode: Int, assetTypeId: Int): Seq[VerificationInfo] = {
    withDynSession {
      dao.getAssetVerification(municipalityCode, assetTypeId)
    }
  }

  def getSuggestedAssets(municipalityCode: Int, assetTypeId: Int): SuggestedAssetsStructure = {
    withDynSession {
      dao.getSuggestedByTypeIdAndMunicipality(municipalityCode, assetTypeId)
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
        insertAssetTypeVerification(municipalityCode, typeId, Some(username), oldAssetInfo._3, oldAssetInfo._4, oldAssetInfo._5, oldAssetInfo._6, "")
      }
    }
  }

  def getVerificationInfo(municipalityCode: Int, assetTypeIds: Set[Int] = Set()) :  Seq[(Int, Int, Option[String], Option[DateTime], Int, Option[DateTime], String)] = {
    dao.getVerificationInfo(municipalityCode, assetTypeIds)
  }

  def insertAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, verifiedBy: Option[String], lastUserModification: Option[String], lastDateModification: Option[DateTime], numberOfAssets: Int, refreshDate: Option[DateTime], suggestedAssets: String) : Long = {
    dao.insertAssetTypeVerification(municipalityCode, assetTypeId, verifiedBy, lastUserModification, lastDateModification, numberOfAssets, refreshDate, suggestedAssets)
  }

  def getMunicipalityInfo(bounds: BoundingRectangle): Option[Int] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(bounds,asyncMode = false)
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
        insertAssetTypeVerification(municipalityCode, typeId, None, oldAssetInfo._3, oldAssetInfo._4, oldAssetInfo._5, oldAssetInfo._6, "")
      }
    }
  }

  def getCriticalAssetTypesByMunicipality(municipalityCode: Int): List[VerificationInfo] = {
    val criticalAssetTypes =
      Seq(
        Prohibition.typeId,
        Obstacles.typeId,
        Manoeuvres.typeId,
        TrafficSigns.typeId,
        SpeedLimitAsset.typeId,
        HeightLimit.typeId,
        TotalWeightLimit.typeId,
        HazmatTransportProhibition.typeId
      )

    getCriticalAssetVerification(municipalityCode, criticalAssetTypes).toList
  }

  def getAssetsLatestModifications(municipalityCodes: Set[Int]): List[LatestModificationInfo] = {
    withDynTransaction {
      dao.getDashboardInfo(municipalityCodes)
    }
  }

  def getLastModificationLinearAssets(linkIds: Seq[String]): Seq[LatestModificationInfo] = {
    withDynTransaction {
      dao.getLastModificationLinearAssets(linkIds.toSet)
    }
  }

  def getLastModificationPointAssets(municipality: Int): Seq[LatestModificationInfo] = {
    withDynSession {
      dao.getLastModificationPointAssets(municipality)
    }
  }

  def getSuggestedLinearAssets(linkIds: Seq[String]): Seq[(Long, Int)] = {
    withDynTransaction {
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

  def refreshVerificationInfo(municipality: Int, linkIds: Seq[String], refreshDate: Option[DateTime]) : Unit = {
    val assetsInfo = LogUtils.time(logger, "BATCH LOG get asset types by municipality f")(getAssetTypesByMunicipalityF(municipality, linkIds))
    val assetOnMunicipalityVerification = LogUtils.time(logger, "BATCH LOG get verification info")(getVerificationInfo(municipality))

    val (toUpdate, toInsert) =  assetsInfo.partition{ case(_, typeId, _, _, _, _) => assetOnMunicipalityVerification.map(_._2).contains(typeId)}

    LogUtils.time(logger, "BATCH LOG update asset type verification")(
      if (toUpdate.nonEmpty) dao.updateAssetTypeVerifications(toUpdate, refreshDate)
    )
    LogUtils.time(logger, "BATCH LOG insert asset type verification")(
      if (toInsert.nonEmpty) dao.insertAssetTypeVerifications(toInsert, refreshDate)
    )
  }

  def getRefreshedAssetTypesByMunicipality(municipalityCode : Int,  getRoadLink: Int => Seq[RoadLink]): Seq[VerificationInfo] = {
    val tinyRoadLink = roadLinkService.getTinyRoadLinksByMunicipality(municipalityCode)

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

  def getAssetTypesByMunicipalityF(municipality: Int, linkIds: Seq[String]): Seq[(Int, Int, Int, Option[String], Option[DateTime], String)] = {
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
    val suggestedAssets = (suggestedLinearAssets ++ suggestedPointAssets).groupBy(_._2)
    verifiedAssetTypes.map{ case (typeId, geometryType) =>
      val numberOfAssets = if(geometryType == "point")
        numberOfPointAssets.find(_._1 == typeId).map(_._2).getOrElse(0)
      else {
        if (linearLastModification.exists(_.assetTypeCode == typeId)) 1 else 0
      }

      val suggestedAssetsByType = if(suggestedAssets.nonEmpty && suggestedAssets.contains(typeId))
        suggestedAssets(typeId).map(_._1).mkString(",")
      else ""

      val assetModificationInfo = assetsLastModification.find(_.assetTypeCode == typeId)

      if(assetModificationInfo.nonEmpty)
        (municipality, typeId, numberOfAssets, assetModificationInfo.flatMap(_.modifiedBy), assetModificationInfo.flatMap(_.modifiedDate), suggestedAssetsByType)
      else
        (municipality, typeId, numberOfAssets, None, None, suggestedAssetsByType)
    }
  }

  def getNumberSuggestedAssetNumber(municipalityCode: Set[Int]) : Long = withDynTransaction { dao.getNumberSuggestedAssetNumber(municipalityCode)}
}
