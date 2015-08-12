package fi.liikennevirasto.digiroad2.performance

object TestConfiguration {
  val host: String = System.getProperty("host")
  val apiUrl: String = "http://" + host + "/digiroad/api"
}
