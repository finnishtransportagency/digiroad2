package fi.liikennevirasto.digiroad2.verification.oracle
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

class VerificationDao {

  def getVerifiedAssetTypes(municipalityCode: Int) = {
    val verifiedAssetTypes =
      sql"""select m.name_fi, asst.name, mv.verified_at, mv.verified_by
         from municipality_verification mv
         join municipality m on mv.municipality_id = m.id
         join asset_type asst on mv.asset_type_id = asst.id
         where mv.municipality_id = $municipalityCode""".as[(String, String, DateTime, String)].list
    verifiedAssetTypes
  }

  def getAssetVerification(municipalityCode: Int, assetTypeId: Int) = {
    val verifiedAssetType =
      sql"""select mv.verified_at
         from municipality_verification mv
         where mv.municipality_id = $municipalityCode
         and mv.asset_type_id = $assetTypeId""".as[DateTime].firstOption
    verifiedAssetType
  }

  def verifyAssetType(municipalityCode: Int, assetTypeId: Int, username: String) = {
    sqlu"""insert into municipality_verification (municipality_id, asset_type_id, verified_at, verified_by)
           values ($municipalityCode, $assetTypeId, sysdate, $username)
      """.execute
  }

  def updateAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, username: String) = {
    sqlu"""update municipality_verification
           set verified_at = sysdate, verified_by = $username
           where municipality_id = $municipalityCode
           and asset_type_id = $assetTypeId
        """.execute
  }

  def removeAssetTypeVerification(municipalityCode: Int, assetTypeId: Int) = {
    sqlu"""update municipality_verification
           set verified_at = NULL, verified_by = NULL
           where municipality_id = $municipalityCode
           and asset_type_id = $assetTypeId
      """.execute
  }
}
