package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.ConversionDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

object RoadLinkDataImporter {
  def importFromConversionDB() {
    val existingRoadLinkData = Database.forDataSource(ConversionDatabase.dataSource).withDynSession {
      sql"""select link_id, max(toiminnallinen_luokka), max(linkkityyppi), max(liikennevirran_suunta) from tielinkki_ctas group by link_id """
        .as[(Long, Int, Int, Int)]
        .list
    }

    OracleDatabase.withDynTransaction {
      println("insert functional classes")
      insertFunctionalClasses(existingRoadLinkData)
      println("insert link types")
      insertLinkTypes(existingRoadLinkData)
      println("insert traffic directions")
      insertTrafficDirections(existingRoadLinkData)
    }
  }

  private def insertFunctionalClasses(functionalClasses: List[(Long, Int, Int, Int)]) {
    val statement = dynamicSession.prepareStatement("""
        insert into functional_class(id, link_id, functional_class, modified_date, modified_by)
        select primary_key_seq.nextval, ?, ?, to_timestamp('08-JAN-15','DD-MON-RR'), 'dr1_conversion'
        from dual
        where not exists (select * from functional_class where link_id = ?)
      """)
    functionalClasses
      .foreach { x =>
      statement.setLong(1, x._1)
      statement.setInt(2, x._2)
      statement.setLong(3, x._1)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

  private def insertLinkTypes(data: List[(Long, Int, Int, Int)]) = {
    val statement = dynamicSession.prepareStatement("""
        insert into link_type(link_id, link_type, modified_date, modified_by)
        select ?, ?, to_timestamp('08-JAN-15','DD-MON-RR'), 'dr1_conversion'
        from dual
        where not exists (select * from link_type where link_id = ?)
      """)
    data
      .foreach { x =>
      statement.setLong(1, x._1)
      statement.setInt(2, x._3)
      statement.setLong(3, x._1)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

  private def insertTrafficDirections(data: List[(Long, Int, Int, Int)]) = {
    val statement = dynamicSession.prepareStatement("""
        insert into traffic_direction(link_id, traffic_direction, modified_date, modified_by)
        select ?, ?, to_timestamp('08-JAN-15','DD-MON-RR'), 'dr1_conversion'
        from dual
        where not exists (select * from traffic_direction where link_id = ?)
      """)
    data
      .foreach { x =>
      statement.setLong(1, x._1)
      statement.setInt(2, x._4)
      statement.setLong(3, x._1)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

}
