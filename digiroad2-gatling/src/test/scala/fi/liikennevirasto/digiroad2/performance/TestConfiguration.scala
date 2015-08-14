package fi.liikennevirasto.digiroad2.performance
import io.gatling.core.Predef._
import io.gatling.http.Predef._

object TestConfiguration {
  val host: String = System.getProperty("host")
  val users: Int = System.getProperty("users").toInt
  val username: String = System.getProperty("username")
  val baseUrl: String = "http://" + host + "/digiroad"
  val httpConf = http
    .baseURL(baseUrl)
    .header("Cookie", "testusername=tarutest")
    .header("OAM_REMOTE_USER", TestConfiguration.username)
}
