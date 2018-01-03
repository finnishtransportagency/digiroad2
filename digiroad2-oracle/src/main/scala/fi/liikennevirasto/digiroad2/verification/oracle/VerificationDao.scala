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
      sql"""select m.name_fi, at.name, mv.verified_at, mv.verified_by
         from municipality_verification mv
         join municipality m on mv.municipality_id = m.id
         join asset_type at on mv.asset_type_id = at.id
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
}
