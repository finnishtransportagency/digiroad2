package fi.liikennevirasto.digiroad2.verification.oracle
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.VerificationInfo
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

class VerificationDao {

  def getVerifiedAssetTypes(municipalityCode: Int) = {
    val verifiedAssetTypes =
      sql"""select  m.id, m.name_fi, mv.verified_by, mv.verified_date, asst.name
         from municipality m
		     join asset_type asst on asst.verifiable = 1
         left join municipality_verification mv on mv.municipality_id = m.id and mv.asset_type_id = asst.id
         where m.id = $municipalityCode""".as[(Int, String, Option[String], Option[DateTime], Option[String])].list

    verifiedAssetTypes.map { case (municipalityCode, municipalityName, verifiedBy, verifiedDate, assetTypeName) =>
      VerificationInfo(municipalityCode, municipalityName, verifiedBy, verifiedDate, assetTypeName)
    }
  }

  def getAssetVerification(municipality: Int, typeId: Int) = {
    val verifiedAssetType =
      sql"""select m.id, m.name_fi, mv.verified_by, mv.verified_date
         from municipality m
         left join municipality_verification mv on mv.municipality_id = m.id and mv.asset_type_id = $typeId
         where m.id = $municipality""".as[(Int, String, Option[String], Option[DateTime])].firstOption

    verifiedAssetType.map { case (municipalityCode, municipalityName, verifiedBy, verifiedDate) =>
      VerificationInfo(municipalityCode, municipalityName, verifiedBy, verifiedDate)
    }
  }

  def verifyAssetType(municipalityCode: Int, assetTypeId: Int, username: String) = {
    sqlu"""insert into municipality_verification (municipality_id, asset_type_id, verified_date, verified_by)
           values ($municipalityCode, $assetTypeId, sysdate, $username)
      """.execute
  }

  def updateAssetTypeVerification(municipalityCode: Int, assetTypeId: Int, username: String) = {
    sqlu"""update municipality_verification
           set verified_date = sysdate, verified_by = $username
           where municipality_id = $municipalityCode
           and asset_type_id = $assetTypeId
        """.execute
  }

  def removeAssetTypeVerification(municipalityCode: Int, assetTypeId: Int) = {
    sqlu"""update municipality_verification
           set verified_date = NULL, verified_by = NULL
           where municipality_id = $municipalityCode
           and asset_type_id = $assetTypeId
      """.execute
  }
}
