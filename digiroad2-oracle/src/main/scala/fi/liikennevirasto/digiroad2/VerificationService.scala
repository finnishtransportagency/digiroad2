package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.verification.oracle.VerificationDao
import org.joda.time.DateTime

class VerificationService(eventbus: DigiroadEventBus) {

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

  def getAssetVerification(municipalityCode: Int, assetTypeId: Int): Option[DateTime] = {
    val verificationDate = withDynTransaction{
      dao.getAssetVerification(municipalityCode, assetTypeId)
    }
    verificationDate
  }
}
