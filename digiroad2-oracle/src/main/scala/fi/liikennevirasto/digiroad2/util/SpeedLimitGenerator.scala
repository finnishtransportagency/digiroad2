package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.nextPrimaryKeyId
import scala.slick.jdbc.{StaticQuery => Q, _}
import Q.interpolation

object SpeedLimitGenerator {
  def generateForCityAreas(municipalities: Int*): Unit = {
    println("Running speed limit generation...")
    var handledCount = 0l
    Database.forDataSource(ds).withDynSession {
      val baseQuery = """
        select id, SDO_LRS.GEOM_SEGMENT_LENGTH(rl.geom) as length FROM road_link rl
        where id not in (
          select lp.road_link_id from lrm_position lp
          join asset_link al on al.position_id = lp.id
        )
        and mod(functional_class, 10) IN (2, 3, 4, 5, 6)
      """
      val roadLinks = Q.queryNA[(Long, Double)](
        if (municipalities.isEmpty) baseQuery
        else baseQuery + " and rl.MUNICIPALITY_NUMBER in (" + municipalities.mkString(", ") + ")").list()

      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, 20, SYSDATE, 'automatic_speed_limit_generation')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
      val speedLimitPS = dynamicSession.prepareStatement("insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date, modified_by) values (?, (select id from enumerated_value where value = ? and property_id = (select id from property where public_id = 'rajoitus')), (select id from property where public_id = 'rajoitus'), sysdate, 'automatic_speed_limit_generation')")

      roadLinks.foreach { case (roadLinkId, length) =>
        val assetId = nextPrimaryKeyId.as[Long].first
        val lrmPositionId = nextPrimaryKeyId.as[Long].first
        val speedLimit = 50
        assetPS.setLong(1, assetId)
        assetPS.addBatch()

        lrmPositionPS.setLong(1, lrmPositionId)
        lrmPositionPS.setLong(2, roadLinkId)
        lrmPositionPS.setDouble(3, 0)
        lrmPositionPS.setDouble(4, length)
        lrmPositionPS.setInt(5, 1)
        lrmPositionPS.addBatch()

        assetLinkPS.setLong(1, assetId)
        assetLinkPS.setLong(2, lrmPositionId)
        assetLinkPS.addBatch()

        speedLimitPS.setLong(1, assetId)
        speedLimitPS.setInt(2, speedLimit)
        speedLimitPS.addBatch()
        handledCount = handledCount + 1
        if (handledCount % 1000 == 0) {
          println("generated " + handledCount + " speed limits")
        }
      }

      assetPS.executeBatch()
      lrmPositionPS.executeBatch()
      assetLinkPS.executeBatch()
      speedLimitPS.executeBatch()
      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
      speedLimitPS.close()
    }
    println("...done (generated " + handledCount + " speed limits).")
  }
}
