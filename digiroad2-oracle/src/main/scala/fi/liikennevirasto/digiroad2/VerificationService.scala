package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.verification.oracle.VerificationDao
import org.joda.time.DateTime

case class VerificationInfo(municipalityCode: Int, municipalityName: String, verifiedBy: Option[String], verifiedDate: Option[DateTime])

class VerificationService(eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def dao: VerificationDao = new VerificationDao

  def getAssetTypesByMunicipality(municipalityCode: Int): Option[Map[String, List[Map[String, Any]]]] = {

    val verifiedAssetTypes = withDynTransaction {
      dao.getVerifiedAssetTypes(municipalityCode)
    }

    verifiedAssetTypes.map {x => Map(verifiedAssetTypes.head._1 -> verifiedAssetTypes.map {
      assetType => Map("asset name"  -> assetType._2,
                       "verified at" -> assetType._3,
                       "verified by" -> assetType._4)
    })}.headOption
  }

  def getAssetVerification(municipalityCode: Int, assetTypeId: Int): Option[VerificationInfo] = {
    withDynTransaction{
      dao.getAssetVerification(municipalityCode, assetTypeId)
    }
  }

  def verifyAssetType(municipalityCode: Int, assetTypeId: Int, username: String) = {
    withDynTransaction{
        dao.verifyAssetType(municipalityCode, assetTypeId, username)
      }
    }

  def getMunicipalityInfo(typeId: Int, bounds: BoundingRectangle): VerificationInfo = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    val midPoint = Point((bounds.rightTop.x + bounds.leftBottom.x) / 2, (bounds.rightTop.y + bounds.leftBottom.y) / 2)

    val nearestRoadLink = roadLinks.minBy(road => GeometryUtils.minimumDistance(midPoint, road.geometry))

    getAssetVerification(nearestRoadLink.municipalityCode, typeId).get
  }


  def updateAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, username: String) = {
    withDynTransaction{
        dao.updateAssetTypeVerification(municipalityCode, assetTypeId, username)
      }
    }
}
