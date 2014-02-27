package fi.liikennevirasto.digiroad2.oracle

import javax.sql.DataSource
import java.util.Properties
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import org.joda.time.LocalDate
import java.sql.Date
import java.io.FileInputStream

object OracleDatabase {
  lazy val ds: DataSource = initDataSource

  lazy val localProperties: Properties = {
    loadProperties("/bonecp.properties")
  }

  def jodaToSqlDate(jodaDate: LocalDate): Date = {
    new Date(jodaDate.toDate.getTime)
  }

  def initDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val cfg = new BoneCPConfig(localProperties)
    new BoneCPDataSource(cfg)
  }

  def loadProperties(resourcePath: String): Properties = {
    val props = new Properties()
    try {
      props.load(getClass.getResourceAsStream(resourcePath))
    } catch {
      case e: Exception => throw new RuntimeException("Can't load " + resourcePath + " for env: " + System.getProperty("env"), e)
    }
    props
  }
}