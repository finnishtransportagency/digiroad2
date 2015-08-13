package fi.liikennevirasto.digiroad2.performance

object TestConfiguration {
  val host: String = System.getProperty("host")
  val users: Int = System.getProperty("users").toInt
  val apiUrl: String = "http://" + host + "/digiroad/api"
}
