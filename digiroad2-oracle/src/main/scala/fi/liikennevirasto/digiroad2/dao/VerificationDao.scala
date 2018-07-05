package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.service.VerificationInfo
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation

class VerificationDao {
  val TwoYears: Int = 24

  def getVerifiedAssetTypes(municipalityId: Int) = {
    val verifiedAssetTypes =
      sql"""
       SELECT tableResult.id, tableResult.name_fi, tableResult.verified_by, tableResult.verified_date, tableResult.assetId, tableResult.assetName,
              tableResult.verified, tableResult.counting
         FROM
         (SELECT m.id, m.name_fi, mv.verified_by, mv.verified_date, atype.id AS assetId, atype.name AS assetName,
                (CASE
                    WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears
                      THEN 1
                      ELSE 0
                END) AS verified,
                atype.GEOMETRY_TYPE,
                (CASE
                    WHEN atype.GEOMETRY_TYPE = 'point'
                      THEN count(a.id)
                      ELSE NULL
                END) AS counting
                FROM municipality m
                JOIN asset_type atype ON atype.verifiable = 1
                LEFT JOIN municipality_verification mv ON mv.municipality_id = m.id AND mv.asset_type_id = atype.id AND mv.valid_to IS NULL OR mv.valid_to > sysdate
                LEFT JOIN asset a ON a.ASSET_TYPE_ID = atype.ID and a.municipality_code = m.id AND a.VALID_TO IS NULL
                WHERE m.id = $municipalityId
                GROUP BY m.id, m.name_fi, mv.verified_by, mv.verified_date, atype.id, atype.name,
                      (CASE WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears THEN 1 ELSE 0 END),
                      atype.GEOMETRY_TYPE ) tableResult""".as[(Int, String, Option[String], Option[DateTime], Int, String, Boolean, Option[Int])].list

    verifiedAssetTypes.map { case ( municipalityCode, municipalityName, verifiedBy, verifiedDate, assetTypeCode, assetTypeName, verified, counter) =>
      VerificationInfo(municipalityCode, municipalityName, assetTypeCode, assetTypeName, verifiedBy, verifiedDate, verified, counter)
    }
  }


  def getAssetVerificationById(Id: Long): Option[VerificationInfo] = {
    val verifiedAssetType =
      sql"""
       SELECT tableResult.id, tableResult.name_fi, tableResult.verified_by, tableResult.verified_date, tableResult.assetId, tableResult.assetName,
              tableResult.verified, tableResult.counting
         FROM
         (SELECT m.id, m.name_fi, mv.verified_by, mv.verified_date, atype.id AS assetId, atype.name AS assetName,
                (CASE
                    WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears
                      THEN 1
                      ELSE 0
                END) AS verified,
                atype.GEOMETRY_TYPE,
                (CASE
                    WHEN atype.GEOMETRY_TYPE = 'point'
                      THEN count(*)
                      ELSE NULL
                END) AS counting
                FROM municipality m
                JOIN asset_type atype ON atype.verifiable = 1
                LEFT JOIN municipality_verification mv ON mv.municipality_id = m.id AND mv.asset_type_id = atype.id
                LEFT JOIN asset a ON a.ASSET_TYPE_ID = atype.ID
                WHERE mv.id = $Id AND mv.valid_to IS NULL OR mv.valid_to > sysdate
                GROUP BY m.id, m.name_fi, mv.verified_by, mv.verified_date, atype.id, atype.name,
                      (CASE WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears THEN 1 ELSE 0 END),
                      atype.GEOMETRY_TYPE ) tableResult""".as[(Int, String, Option[String], Option[DateTime], Int, String, Boolean, Option[Int])].firstOption
    verifiedAssetType.map { case (municipalityCode, municipalityName, verifiedBy, verifiedDate, assetTypeCode, assetTypeName, verified, counter) =>
      VerificationInfo(municipalityCode, municipalityName, assetTypeCode, assetTypeName, verifiedBy, verifiedDate, verified, counter)
    }
  }

  def getAssetVerification(municipalityCode: Int, assetTypeCode: Int): Seq[VerificationInfo] = {
    val verifiedAssetType =
      sql"""
       SELECT tableResult.id, tableResult.name_fi, tableResult.verified_by, tableResult.verified_date, tableResult.assetId, tableResult.assetName,
              tableResult.verified, tableResult.counting
         FROM
         (SELECT m.id, m.name_fi, mv.verified_by, mv.verified_date, atype.id AS assetId, atype.name AS assetName,
                (CASE
                    WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears
                      THEN 1
                      ELSE 0
                END) AS verified,
                atype.GEOMETRY_TYPE,
                (CASE
                    WHEN atype.GEOMETRY_TYPE = 'point'
                      THEN count(*)
                      ELSE NULL
                END) AS counting
                FROM municipality m
                JOIN asset_type atype ON atype.verifiable = 1 and atype.id = $assetTypeCode
                LEFT JOIN municipality_verification mv ON mv.municipality_id = m.id
                LEFT JOIN asset a ON a.ASSET_TYPE_ID = atype.ID
                WHERE  m.id = $municipalityCode and (mv.valid_to is null or mv.valid_to > sysdate) AND mv.asset_type_id = atype.id
                GROUP BY m.id, m.name_fi, mv.verified_by, mv.verified_date, atype.id, atype.name,
                      (CASE WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears THEN 1 ELSE 0 END),
                      (CASE WHEN MONTHS_BETWEEN(sysdate, mv.verified_date) < $TwoYears THEN 1 ELSE 0 END),
                      atype.GEOMETRY_TYPE ) tableResult""".as[(Int, String, Option[String], Option[DateTime], Int, String, Boolean, Option[Int])].list

    verifiedAssetType.map { case (municipality, municipalityName, verifiedBy, verifiedDate, assetType, assetTypeName, verified, counter) =>
      VerificationInfo(municipality, municipalityName, assetType, assetTypeName, verifiedBy, verifiedDate, verified, counter)
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
    assetTypeCode
  }

  def getVerifiableAssetTypes: Seq[Int] = {
    sql"""select asst.id
           from asset_type asst
           where asst.verifiable = 1
      """.as[(Int)].list
  }
}
