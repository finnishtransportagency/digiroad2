package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCP}
import com.jolbox.bonecp
import java.util.Properties
import java.io.IOException

object Digiroad2Context {
  lazy val connectionPool: BoneCP = initConnectionPool

  lazy val localProperties: Properties = {
    val props = new Properties()
    try {
      props.load(getClass.getResourceAsStream("/local.properties"))
    } catch {
      case e: Exception => throw new RuntimeException("Can't load local.properties for env: " + System.getProperty("env"), e)
    }
    props
  }

  private[this] def initConnectionPool: BoneCP = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val cfg = new bonecp.BoneCPConfig(localProperties)
    new BoneCP(cfg)
  }
}