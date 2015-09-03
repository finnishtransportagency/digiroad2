package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{StaticQuery => Q}
import Q.interpolation
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

class AssetService(roadLinkService: RoadLinkService) {
  def getMunicipalityCodes(assetId: Long): Set[Int] = {
    val roadLinkIds = OracleDatabase.withDynTransaction {
      sql"""
      select lrm.road_link_id
        from asset a
        join asset_link al on a.ID = al.ASSET_ID
        join lrm_position lrm on lrm.id = al.POSITION_ID
        where a.id = $assetId
    """.as[Long].list
    }
    roadLinkIds.flatMap(roadLinkService.getMunicipalityCode).toSet
  }
}
