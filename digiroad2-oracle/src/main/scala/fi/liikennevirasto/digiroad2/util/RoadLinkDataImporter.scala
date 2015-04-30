package fi.liikennevirasto.digiroad2.util

import java.sql.Connection

import fi.liikennevirasto.digiroad2.ConversionDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.joda.time.DateTime
import scala.slick.direct.AnnotationMapper.column
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import fi.liikennevirasto.digiroad2.asset.oracle.{LocalizationDao, OracleSpatialAssetDao, Queries}
import scala.slick.jdbc.StaticQuery
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

object RoadLinkDataImporter {
  def importFromConversionDB() {
    val existingRoadLinkData = Database.forDataSource(ConversionDatabase.dataSource).withDynSession {
      sql"""select mml_id, max(toiminnallinen_luokka), max(linkkityyppi), max(liikennevirran_suunta) from tielinkki_ctas group by mml_id"""
        .as[(Long, Int, Int, Int)]
        .list
    }

    Database.forDataSource(ds).withDynTransaction {
      println("insert functional classes")
      insertFunctionalClasses(existingRoadLinkData)
      println("insert link types")
      insertLinkTypes(existingRoadLinkData)
      println("insert traffic directions")
      insertTrafficDirections(existingRoadLinkData)
    }
  }

  private def insertFunctionalClasses(functionalClasses: List[(Long, Int, Int, Int)]) {
    val statement = dynamicSession.prepareStatement("insert into functional_class(mml_id, functional_class, modified_date, modified_by) values(?, ?, sysdate, 'dr1_conversion')")
    val existingFunctionalClasses = Q.queryNA[Long]("Select mml_id from functional_class").list
    functionalClasses
      .filterNot(x => existingFunctionalClasses.contains(x._1))
      .foreach { x =>
      statement.setLong(1, x._1)
      statement.setInt(2, x._2)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

  private def insertLinkTypes(data: List[(Long, Int, Int, Int)]) = {
    val statement = dynamicSession.prepareStatement("insert into link_type(mml_id, link_type, modified_date, modified_by) values(?, ?, sysdate, 'dr1_conversion')")
    val existingData = Q.queryNA[Long]("Select mml_id from link_type").list
    data
      .filterNot(x => existingData.contains(x._1))
      .foreach { x =>
      statement.setLong(1, x._1)
      statement.setInt(2, x._3)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

  private def insertTrafficDirections(data: List[(Long, Int, Int, Int)]) = {
    val statement = dynamicSession.prepareStatement("insert into traffic_direction(mml_id, traffic_direction, modified_date, modified_by) values(?, ?, sysdate, 'dr1_conversion')")
    val existingData = Q.queryNA[Long]("Select mml_id from traffic_direction").list
    data
      .filterNot(x => existingData.contains(x._1))
      .foreach { x =>
      statement.setLong(1, x._1)
      statement.setInt(2, x._4)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

}
