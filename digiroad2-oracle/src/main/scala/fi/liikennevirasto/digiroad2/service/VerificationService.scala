package fi.liikennevirasto.digiroad2.service

import java.util.Date

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.VerificationDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.util.LogUtils.time
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class VerificationInfo(municipalityCode: Int, municipalityName: String, assetTypeCode: Int, assetTypeName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime], verified: Boolean = false, counter: Option[Int] = None)
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

  def getAssetVerificationById(id: Long, assetTypeId: Int): Option[VerificationInfo] = {
    withDynSession {
      dao.getAssetVerificationById(id)
    }
  }

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

  def getAssetVerificationInfo(typeId: Int, municipality: Int): Option[VerificationInfo] = {
    getAssetVerification(municipality, typeId).headOption
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

  def removeAssetTypeVerification(municipalityCode: Int, assetTypeIds: Set[Int], userName: String) : Unit = {
    withDynTransaction{
      assetTypeIds.map { assetType =>
        dao.expireAssetTypeVerification(municipalityCode, assetType, userName)
      }
    }
  }

  def getCriticalAssetTypesByMunicipality(municipalityCode: Int): List[VerificationInfo] = {
    time(logger, "GetCriticalAssetTypesByMunicipality method to populate the DashBoard Functionality") {
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
  }

  def getAssetLatestModifications(municipalities: Set[Int]): List[LatestModificationInfo] = {
    val tinyRoadLink = municipalities.flatMap { municipality =>
      roadLinkService.getTinyRoadLinkFromVVH(municipality)
    }

    time(logger, "Query to getAssetLatestModifications on the DashBoard Functionality") {
      withDynTransaction {
        dao.getModifiedAssetTypes(tinyRoadLink.map(_.linkId))
      }
    }
  }
}
