package fi.liikennevirasto.digiroad2.util

import scala.io.Source
import javax.sql.DataSource
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import java.util.Properties
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{StaticQuery => Q}
import Q.interpolation

class MunicipalityCodeImporter {
  val ds: DataSource = initDataSource

  def importMunicipalityCodes() = {
    Database.forDataSource(ds).withDynTransaction {
      val src = Source.fromInputStream(getClass.getResourceAsStream("/kunnat_ja_elyt_2014.csv"))
      src.getLines().toList.drop(1).map(row => {
        var elems = row.replace("\"", "").split(";");
        sqlu"""
          insert into municipality(id, name_fi, name_sv) values( ${elems(0).toInt}, ${elems(1)}, ${elems(2)} )
        """.execute
        sqlu"""
          insert into ely(id, name_fi, municipality_id) values( ${elems(3).toInt}, ${elems(4)}, ${elems(0).toInt} )
        """.execute
      })
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
