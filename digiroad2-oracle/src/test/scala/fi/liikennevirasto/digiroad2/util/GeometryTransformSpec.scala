package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.Point
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.HttpClientBuilder
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 29.7.2016.
  */
class GeometryTransformSpec extends FunSuite with Matchers {

  val transform = new GeometryTransform()
  val connectedToVKM = testConnection

  private def testConnection: Boolean = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    val url = properties.getProperty("digiroad2.VKMUrl")
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      response.getStatusLine.getStatusCode >= 200
    } catch {
      case e: HttpHostConnectException =>
        false
    } finally {
      response.close()
    }
  }

  test("URL params should be well-formed") {
    def urlParamMatch(urlParams: String, key: String, value: String) = {
      urlParams.matches("(^|.*&)"+key+"="+value+"(&.*|$)")
    }

    transform.urlParams(Map("tie"-> Option(50), "tieosa" -> None)) should be ("tie=50")
    val allParams = transform.urlParams(Map("tie" -> Option(50), "osa" -> Option(1), "etaisyys" -> Option(234),
      "ajorata" -> Option(Track.Combined.value), "x" -> Option(3874744.2331), "y" -> Option(339484.1234)))

    urlParamMatch(allParams, "tie", "50") should be (true)
    urlParamMatch(allParams, "osa", "1") should be (true)
    urlParamMatch(allParams, "etaisyys", "234") should be (true)
    urlParamMatch(allParams, "ajorata", "0") should be (true)
    urlParamMatch(allParams, "x", "3874744\\.2331") should be (true)
    urlParamMatch(allParams, "y", "339484\\.1234") should be (true)
  }

  test("json should be well-formed") {
    transform.jsonParams(List(Map("tie"-> Option(50), "tieosa" -> None))) should be ("{\"koordinaatit\":[{\"tie\":50}]}")
    transform.jsonParams(List(Map("x" -> Option(3874744.2331), "y" -> Option(339484.1234)))) should be ("{\"koordinaatit\":[{\"x\":3874744.2331,\"y\":339484.1234}]}")
  }

  test("VKM request") {
    assume(connectedToVKM)
    val coord = Point(358813,6684163)
    val roadAddress = transform.coordToAddress(coord, Option(110))
    roadAddress.road should be (110)
    roadAddress.roadPart should be >0
  }

  test("Multiple VKM requests") {
    assume(connectedToVKM)
    val coords = Seq(Point(358813,6684163), Point(358832,6684148), Point(358770,6684181))
    val roadAddresses = transform.coordsToAddresses(coords, Option(110))
    roadAddresses.foreach(_.road should be (110))
    roadAddresses.foreach(_.roadPart should be >0)
    roadAddresses.foreach(_.municipalityCode.isEmpty should be (false))
    roadAddresses.foreach(_.deviation.getOrElse(0.0) should be >20.0)
  }
}
