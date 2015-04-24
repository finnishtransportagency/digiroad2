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
  def importFunctionalClasses() {
    val functionalClasses = Database.forDataSource(ConversionDatabase.dataSource).withDynSession {
      sql"""select mml_id, toiminnallinen_luokka from tielinkki_ctas"""
        .as[(Long, Int)]
        .list
    }
    Database.forDataSource(ds).withDynTransaction {
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
  }
}
