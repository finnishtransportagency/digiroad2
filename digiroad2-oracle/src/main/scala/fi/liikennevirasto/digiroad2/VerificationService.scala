package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.verification.oracle.{VerificationDao}
import org.joda.time.DateTime

case class VerificationInfo(municipalityCode: Int, municipalityName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime] ,assetTypeName: Option[String] = None)

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

  def verifyAssetType(municipalityCode: Int, assetTypeId: Int, username: String) = {
    getAssetVerification(municipalityCode, assetTypeId).headOption match {
      case Some(info) => updateAssetTypeVerification(municipalityCode, assetTypeId, username)
      case None => createAssetTypeVerification(municipalityCode, assetTypeId, username)
    }
  }

  def getMunicipalityInfo(typeId: Int, bounds: BoundingRectangle): Seq[VerificationInfo] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    val midPoint = Point((bounds.rightTop.x + bounds.leftBottom.x) / 2, (bounds.rightTop.y + bounds.leftBottom.y) / 2)

    val nearestRoadLink = roadLinks.minBy(road => GeometryUtils.minimumDistance(midPoint, road.geometry))

    getAssetVerification(nearestRoadLink.municipalityCode, typeId)
  }

  def updateAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, username: String) = {
    withDynTransaction{
        dao.updateAssetTypeVerification(municipalityCode, assetTypeId, username)
      }
    }

  def createAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, username: String) = {
    withDynTransaction{
      dao.verifyAssetType(municipalityCode, assetTypeId, username)
    }
  }

  def removeAssetTypeVerification(municipalityCode: Int, assetTypeId: Int) = {
    withDynTransaction{
      dao.removeAssetTypeVerification(municipalityCode, assetTypeId)
    }
  }
}
