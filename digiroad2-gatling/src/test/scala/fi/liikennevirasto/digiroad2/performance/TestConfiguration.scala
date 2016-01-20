package fi.liikennevirasto.digiroad2.performance
import io.gatling.core.Predef._
import io.gatling.http.Predef._

object TestConfiguration {
  val host: String = System.getProperty("host")
  val users: Int = System.getProperty("users").toInt
  val username: String = System.getProperty("username")
  val baseUrl: String = "http://" + host + "/digiroad"
  val proxyHost: String = System.getProperty("proxyHost")
  val noProxyFor: Seq[String] = System.getProperty("noProxyFor").split(',')
  val httpConf = http
// TODO: enable proxy when 2.1.8 version is out - see https://github.com/gatling/gatling/issues/2795
//    .proxy(Proxy(proxyHost, 80)
//      .httpsPort(80))
//    .noProxyFor(noProxyFor: _*)
    .warmUp("http://localhost:8888/")
    .baseURL(baseUrl)
    .header("Cookie", "testusername=tarutest")
    .header("OAM_REMOTE_USER", TestConfiguration.username)
}
