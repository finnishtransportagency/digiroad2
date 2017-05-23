package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.impl.client.HttpClientBuilder
import org.scalatest.{FunSuite, Matchers}

import scala.reflect.io.File

/**
  * Created by alapeijario on 17.5.2017.
  */
class ViiteTierekisteriClientSpec extends FunSuite with Matchers {

  val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  def getRestEndPoint: String = {
    val loadedKeyString = dr2properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint")
    println("viite-endpoint = "+loadedKeyString)
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TierekisteriViiteRestApiEndPoint")
    loadedKeyString
  }

  private def testConnection: Boolean = {
    val url = dr2properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint")
    val request = new HttpGet(url)
    request.setConfig(RequestConfig.custom().setConnectTimeout(2500).build())
    val client = HttpClientBuilder.create().build()
    try {
      val response = client.execute(request)
      try {
        response.getStatusLine.getStatusCode >= 200
      } finally {
        response.close()
      }
    } catch {
      case e: HttpHostConnectException =>
        false
      case e: ConnectTimeoutException =>
        false
      case e: ConnectException =>
        false
    }
  }

  test("TR-connection Create test") {
    assume(testConnection)
    val message= ViiteTierekisteriClient.sendJsonMessage(ChangeProject(0, "Testproject", "TestUser", 3, "2017-06-01", Seq {
      ChangeInfoItem(2, 1, 1, ChangeInfoRoadParts(None, None, None, None, None, None), ChangeInfoRoadParts(Option(403), Option(0), Option(8), Option(0), Option(8), Option(1001))) // projectid 0 wont be added to TR
    }))
    message.projectId should be (0)
    message.status should be (201)
    message.reason should startWith ("Created")
  }

  test("Get project status from TR") {
    assume(testConnection)
    val response = ViiteTierekisteriClient.getProjectStatus("0")
    response == null should be (false)
  }
}
