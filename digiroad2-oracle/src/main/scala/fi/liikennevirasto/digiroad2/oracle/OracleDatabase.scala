package fi.liikennevirasto.digiroad2.oracle

import javax.sql.DataSource
import java.util.Properties
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
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
      case e: Exception => throw new RuntimeException("Can't load " + resourcePath + " for env: " + System.getProperty("digiroad2.env"), e)
    }
    props
  }

  def boundingBoxFilter(bounds: BoundingRectangle): String = {
    val leftBottomX = bounds.leftBottom.x
    val leftBottomY = bounds.leftBottom.y
    val rightTopX = bounds.rightTop.x
    val rightTopY = bounds.rightTop.y
    s"""
        mdsys.sdo_filter(shape,
                         sdo_cs.viewport_transform(
                         mdsys.sdo_geometry(
                         2003,
                         0,
                         NULL,
                         mdsys.sdo_elem_info_array(1,1003,3),
                         mdsys.sdo_ordinate_array($leftBottomX,
                                                  $leftBottomY,
                                                  $rightTopX,
                                                  $rightTopY)
                         ),
                         3067
                         ),
                         'querytype=WINDOW'
                         ) = 'TRUE'
    """
  }
}