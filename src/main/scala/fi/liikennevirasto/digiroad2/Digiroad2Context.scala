package fi.liikennevirasto.digiroad2

import java.util.Properties
import fi.liikennevirasto.digiroad2.feature.FeatureProvider

object Digiroad2Context {
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val featureProvider: FeatureProvider = {
    try {
      Class.forName(properties.getProperty("digiroad2.featureProvider")).newInstance().asInstanceOf[FeatureProvider]
    } catch {
      // TODO: fix missing class in CI test issue (config/injection if required), and return to fail-fast mode on config error
      case e: Exception => {
        e.printStackTrace
        new FeatureProvider {
          def getBusStops() = List()
        }
      }
    }
  }
}