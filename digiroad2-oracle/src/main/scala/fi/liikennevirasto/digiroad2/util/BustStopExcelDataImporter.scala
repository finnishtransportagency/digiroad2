package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, GetResult}
import Database.dynamicSession
import Q.interpolation

class BustStopExcelDataImporter {
  val Updater = "excel_data_migration"
  lazy val convDs: DataSource = initConversionDataSource
  lazy val excelDs: DataSource = initExcelDataSource

  case class ExcelBusStopData(externalId: Long, stopNameFi: String, stopNameSv: String, direction: String, reachability: String, accessibility: String, internalId: String, equipments: String)

  implicit val getExcelBusStopData = GetResult[ExcelBusStopData](r => ExcelBusStopData(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

  private[this] def initExcelDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("jdbc:oracle:thin:@livispr01n1l-vip:1521/drkonv")
    ds.setUsername("dr2sample2")
    ds.setPassword("dr2sample2")
    ds
  }

  private[this] def initConversionDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("jdbc:oracle:thin:@livispr01n1l-vip:1521/drkeh")
    ds.setUsername("dr2data")
    ds.setPassword("dr2data")
    ds
  }

  def importDataFromExcel(): List[ExcelBusStopData] = {
    Database.forDataSource(excelDs).withDynTransaction {
      sql"""
        select valtak_tunnus, pysakin_nimi, pysakin_nimi_se, SUUNTA_KANSALAISEN_NAKOK, PYSAKIN_SAAVUTETTAVUUS, ESTEETTOMYYS_TIEDOT, YLLAPITAJAN_SISAINEN_ID, VARUSTEET_MUOKKAUSSARAKE from excel_unique
      """.as[ExcelBusStopData].list
    }
  }

  def insertExcelData(ed: List[ExcelBusStopData]) {
    Database.forDataSource(convDs).withDynTransaction {
      ed.foreach{ row =>
        val assetId = sql"""
          select id from asset where external_id = ${row.externalId}
        """.as[Long].firstOption

        assetId match {
          case Some(_) => {
            println("UPDATING ASSET: " + assetId.get + " WITH EXTERNAL ID: " + row.externalId)

            sqlu"""
              update asset set modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP where id = ${assetId.get}
            """.execute

            insertTextPropertyValue(row.externalId, "Pysäkin nimi", row.stopNameFi, row.stopNameSv)

            insertTextPropertyValue(row.externalId, "Pysäkin suunta", row.direction)

            insertTextPropertyValue(row.externalId, "Pysäkin saavutettavuus", row.reachability)

            insertTextPropertyValue(row.externalId, "Esteettömyystiedot", row.accessibility)

            insertTextPropertyValue(row.externalId, "Pysäkin tunnus", row.internalId)

            insertTextPropertyValue(row.externalId, "Kommentit", row.equipments)
          }
          case None => {
            println("NO ASSET FOUND FOR EXTERNAL ID: " + row.externalId)
          }
        }
      }
    }
  }

  def insertTextPropertyValue(externalId: Long, propertyName: String, value: String) {
    insertTextPropertyValue(externalId, propertyName, value, null)
  }

  def insertTextPropertyValue(externalId: Long, propertyName: String, valueFi: String, valueSv: String) {
    println("  UPDATING PROPERTY: " + propertyName + " WITH VALUES FI: '" + valueFi + "', SE: '" + valueSv + "'")
    val propertyId = sql"""
      select id from text_property_value
      where property_id = (select id from property where NAME_FI = ${propertyName})
      and asset_id = (select id from asset where external_id = ${externalId})
    """.as[Long].firstOption

    propertyId match {
      case None => {
        sqlu"""
          insert into text_property_value(id, property_id, asset_id, value_fi, value_sv, created_by)
          values (primary_key_seq.nextval, (select id from property where NAME_FI = ${propertyName}),
          (select id from asset where external_id = ${externalId}),
          ${valueFi}, ${valueSv}, ${Updater})
        """.execute
      }
      case _ => {
        sqlu"""
          update text_property_value set value_fi = ${valueFi}, value_sv = ${valueSv}, modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP
          where id = ${propertyId}
        """.execute
      }
    }
  }
}

object BustStopExcelDataImporter extends App {
  val importer = new BustStopExcelDataImporter()
  importer.insertExcelData(importer.importDataFromExcel())
}
