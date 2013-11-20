package fi.liikennevirasto.digiroad2

import java.util.Properties
import fi.liikennevirasto.digiroad2.feature.FeatureProvider
import fi.liikennevirasto.digiroad2.user.UserProvider

object Digiroad2Context {
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val featureProvider: FeatureProvider = {
    Class.forName(properties.getProperty("digiroad2.featureProvider")).newInstance().asInstanceOf[FeatureProvider]
  }

  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }
}