package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, GetResult}
import Database.dynamicSession
import Q.interpolation

class BustStopExcelDataImporter {
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
    Database.forDataSource(excelDs).withDynTransaction {
      ed.foreach{ data =>
        insertTextPropertyValue(data.externalId, "Pysäkin nimi", data.stopNameFi, data.stopNameSv);

        insertTextPropertyValue(data.externalId, "Pysäkin suunta", data.direction);

        insertTextPropertyValue(data.externalId, "Pysäkin saavutettavuus", data.reachability);

        insertTextPropertyValue(data.externalId, "Esteettömyystiedot", data.accessibility);

        insertTextPropertyValue(data.externalId, "Pysäkin tunnus", data.internalId);

        insertTextPropertyValue(data.externalId, "Kommentit", data.equipments);
      }
    }
  }

  def insertTextPropertyValue(externalId: Long, propertyName: String, value: String) {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
      values (primary_key_seq.nextval, (select id from property where NAME_FI = ${propertyName}),
      (select id from asset where external_id = ${externalId}),
      ${value}, 'samulin_ja_eskon_konversio')
    """.execute
  }

  def insertTextPropertyValue(externalId: Long, propertyName: String, valueFi: String, valueSv: String) {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, value_sv, created_by)
      values (primary_key_seq.nextval, (select id from property where NAME_FI = ${propertyName}),
      (select id from asset where external_id = ${externalId}),
      ${valueFi}, ${valueSv}, 'samulin_ja_eskon_konversio')
    """.execute
  }
}

object BustStopExcelDataImporter extends App {
  val importer = new BustStopExcelDataImporter()
  importer.insertExcelData(importer.importDataFromExcel())
}
