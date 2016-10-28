package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import javax.sql.DataSource

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.io.Source

class MunicipalityCodeImporter {
  val ds: DataSource = initDataSource

  def importMunicipalityCodes() = {
    OracleDatabase.withDynTransaction {
      try {
        val src = Source.fromInputStream(getClass.getResourceAsStream("/kunnat_ja_elyt_2014.csv"))
        src.getLines().toList.drop(1).map(row => {
          val elems = row.replace("\"", "").split(";")
          val roadMaintainerID = elems(3) match {
            case "1" => 14
            case "2" => 12
            case "3" => 10
            case "4" => 9
            case "5" => 8
            case "6" => 4
            case "7" => 2
            case "8" => 3
            case "9" => 1
            case "0" => 0
          }
          sqlu"""
          insert into municipality(id, name_fi, name_sv, ely_nro, road_maintainer_id) values( ${elems(0).toInt}, ${elems(1)}, ${elems(2)}, ${elems(3).toInt} ,$roadMaintainerID)
        """.execute
        })
      } catch {
        case  npe: NullPointerException => {
          println("//kunnat_ja_elyt_2014.csv was not found, skipping.")
        }
      }
    }
  }

  private[this] def initDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val cfg = new BoneCPConfig(localProperties)
    new BoneCPDataSource(cfg)
  }

  lazy val localProperties: Properties = {
    val props = new Properties()
    try {
      props.load(getClass.getResourceAsStream("/bonecp.properties"))
    } catch {
      case e: Exception => throw new RuntimeException("Can't load local.properties for env: " + System.getProperty("env"), e)
    }
    props
  }
}

object MunicipalityCodeImporter extends App {
  new MunicipalityCodeImporter().importMunicipalityCodes()
}
