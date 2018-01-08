package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.verification.oracle.{VerificationDao}
import org.joda.time.DateTime

case class VerificationInfo(municipalityCode: Int, municipalityName: String, assetTypeCode: Int, assetTypeName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime], verified: Boolean)

class VerificationService(eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def dao: VerificationDao = new VerificationDao

  def getAssetTypesByMunicipality(municipalityCode: Int): List[VerificationInfo] = {
    withDynTransaction {
      dao.getVerifiedAssetTypes(municipalityCode)
    }
  }

  def getAssetVerification(municipalityCode: Int, assetTypeId: Int): Seq[VerificationInfo] = {
    withDynTransaction{
      dao.getAssetVerification(municipalityCode, assetTypeId)
    }
  }

  def getAssetVerificationById(id: Long, assetTypeId: Int): Option[VerificationInfo] = {
    withDynTransaction{
      dao.getAssetVerificationById(id)
    }
  }

  def verifyAssetType(municipalityCode: Int, assetTypeId: Set[Int], userName: String) = {
    assetTypeId.foreach { typeId =>
      dao.expireAssetTypeVerification(municipalityCode, typeId, userName)
      dao.insertAssetTypeVerification(municipalityCode, typeId, userName)
      }
    }

  def getMunicipalityInfo(typeId: Int, bounds: BoundingRectangle): Option[VerificationInfo] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    val midPoint = Point((bounds.rightTop.x + bounds.leftBottom.x) / 2, (bounds.rightTop.y + bounds.leftBottom.y) / 2)

    val nearestRoadLink = roadLinks.minBy(road => GeometryUtils.minimumDistance(midPoint, road.geometry))

    getAssetVerification(nearestRoadLink.municipalityCode, typeId).headOption
  }

  def setAssetTypeVerification(municipalityCode: Int, assetTypeIds: Set[Int], username: String): Seq[Long] = {
    withDynTransaction {
      assetTypeIds.map { assetTypeId =>
        dao.insertAssetTypeVerification(municipalityCode, assetTypeId, username)
      }
    }.toSeq
  }

  def removeAssetTypeVerification(municipalityCode: Int, assetType: Int, userName: String) = {
    withDynTransaction{
      dao.expireAssetTypeVerification(municipalityCode, assetType, userName)
    }
  }
}
