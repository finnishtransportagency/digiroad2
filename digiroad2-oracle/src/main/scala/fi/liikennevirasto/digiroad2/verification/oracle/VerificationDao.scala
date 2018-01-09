package fi.liikennevirasto.digiroad2.verification.oracle
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.VerificationInfo
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation

class VerificationDao {
  val TwoYears: Int = 24

  def getVerifiedAssetTypes(municipalityId: Int) = {
    val verifiedAssetTypes =
      sql"""select m.id, m.name_fi, mv.verified_by, mv.verified_date, asst.id, asst.name,
         case when MONTHS_BETWEEN(sysdate,mv.verified_date) < $TwoYears then 1 else 0 end as verified
         from municipality m
		     join asset_type asst on asst.verifiable = 1
         left join municipality_verification mv on mv.municipality_id = m.id and mv.asset_type_id = asst.id and mv.valid_to is null or mv.valid_to > sysdate
         where m.id = $municipalityId""".as[(Int, String, Option[String], Option[DateTime], Int, String, Boolean)].list

    verifiedAssetTypes.map { case ( municipalityCode, municipalityName, verifiedBy, verifiedDate, assetTypeCode, assetTypeName, verified) =>
      VerificationInfo(municipalityCode, municipalityName, assetTypeCode, assetTypeName, verifiedBy, verifiedDate, verified)
    }
  }

  def getAssetVerificationById(Id: Long): Option[VerificationInfo] = {
    val verifiedAssetType =
      sql"""select m.id, m.name_fi, mv.verified_by, mv.verified_date, asst.id, asst.name,
         case when MONTHS_BETWEEN(sysdate,mv.verified_date) < $TwoYears then 1 else 0 end as verified
         from  municipality_verification mv
         join asset_type asst on  asst.id = mv.asset_type_id and asst.verifiable = 1
         join municipality m on m.id = mv.municipality_id
         where mv.id = $Id""".as[(Int, String, Option[String], Option[DateTime], Int, String, Boolean)].firstOption

    verifiedAssetType.map { case (municipalityCode, municipalityName, verifiedBy, verifiedDate, assetTypeCode, assetTypeName, verified) =>
      VerificationInfo(municipalityCode, municipalityName, assetTypeCode, assetTypeName, verifiedBy, verifiedDate, verified)
    }
  }

  def getAssetVerification(municipalityCode: Int, assetTypeCode: Int): Seq[VerificationInfo] = {
    val verifiedAssetType =
      sql"""select m.id, m.name_fi, mv.verified_by, mv.verified_date, asst.id, asst.name,
         case when MONTHS_BETWEEN(sysdate,mv.verified_date) < $TwoYears then 1 else 0 end as verified
         from municipality m
         join asset_type asst on asst.verifiable = 1 and asst.id = $assetTypeCode
         join municipality_verification mv on mv.municipality_id = m.id and mv.asset_type_id = asst.id
         where m.id = $municipalityCode
         and mv.valid_to is null or mv.valid_to > sysdate """.as[(Int, String, Option[String], Option[DateTime], Int, String, Boolean)].list

    verifiedAssetType.map { case (municipality, municipalityName, verifiedBy, verifiedDate, assetType, assetTypeName, verified) =>
      VerificationInfo(municipality, municipalityName, assetType, assetTypeName, verifiedBy, verifiedDate, verified)
    }
  }

  def insertAssetTypeVerification(municipalityId: Int, assetTypeId: Int, username: String): Long = {
    val id = sql"""select primary_key_seq.nextval from dual""".as[Long].first
    sqlu"""insert into municipality_verification (id, municipality_id, asset_type_id, verified_date, verified_by)
           values ($id, $municipalityId, $assetTypeId, sysdate, $username)
      """.execute
    id
  }

  def expireAssetTypeVerification(municipalityCode: Int, assetTypeCode: Int, userName: String) = {
    sqlu"""update municipality_verification mv
           set valid_to = sysdate, modified_by = $userName
           where mv.municipality_id = $municipalityCode
           and mv.asset_type_id = $assetTypeCode
           and valid_to is null
      """.execute
  }

  def getVerifiableAssetTypes: Seq[Int] = {
    sql"""select asst.id
           from asset_type asst
           where asst.verifiable = 1
      """.as[(Int)].list
  }
}
