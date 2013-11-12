package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCP}
import com.jolbox.bonecp
import java.util.Properties

object Digiroad2Context {
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

//  val featureProvider: FeatureProvider = Class.forName(properties.getProperty("digiroad2.featureProvider")).newInstance().asInstanceOf[FeatureProvider]
}