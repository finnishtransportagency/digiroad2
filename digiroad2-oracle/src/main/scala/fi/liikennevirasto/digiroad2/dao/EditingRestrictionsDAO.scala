package fi.liikennevirasto.digiroad2.dao

import org.joda.time.DateTime
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

case class EditingRestrictions(id: Int, municipalityId: Int, stateRoadRestrictedAssetTypes: Seq[Int], municipalityRoadRestrictedAssetTypes: Seq[Int], createdAt: Option[DateTime] = None, modifiedAt: Option[DateTime] = None)
class EditingRestrictionsDAO {

  implicit val getRestrictedAssetTypes: GetResult[EditingRestrictions] = new GetResult[EditingRestrictions] {
    def apply(r: PositionedResult): EditingRestrictions = {
      val id = r.nextInt()
      val municipalityId = r.nextInt()
      val stateRoadRestrictedAssetTypes = r.nextString().split(",").toSeq.map(_.toInt)
      val municipalityRoadRestrictedAssetTypes = r.nextString().split(",").toSeq.map(_.toInt)
      val createdAt = Option(r.nextTimestamp()).map(new DateTime(_))
      val modifiedAt = Option(r.nextTimestamp()).map(new DateTime(_))

      EditingRestrictions(id, municipalityId, stateRoadRestrictedAssetTypes, municipalityRoadRestrictedAssetTypes, createdAt, modifiedAt)
    }
  }

  def fetchAllRestrictions(): Seq[EditingRestrictions] = {
    sql"""
    SELECT id, municipality_id, state_road_restricted_asset_types, municipality_road_restricted_asset_types, created_at, modified_at
    FROM editing_restrictions
  """.as[EditingRestrictions].list
  }

  def fetchRestrictionsByMunicipality(municipality: Int): Option[EditingRestrictions] = {
    sql"""
    SELECT id, municipality_id, state_road_restricted_asset_types, municipality_road_restricted_asset_types, created_at, modified_at
    FROM editing_restrictions
    WHERE municipality_id = $municipality
  """.as[EditingRestrictions].firstOption
  }

}
