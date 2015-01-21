package fi.liikennevirasto.digiroad2

import java.util.Properties
import org.scalatest.{Tag, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64


class IntegrationApiSpec extends FunSuite with ScalatraSuite {

  addServlet(classOf[IntegrationApi], "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes())
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }

  test("Should require correct authentication", Tag("db")) {
    get("/mass_transit_stops") {
      status should equal(401)
    }
    getWithBasicUserAuth("/mass_transit_stops", "nonexisting", "incorrect") {
      status should equal(401)
    }
  }

  test("Get assets requires municipality number") {
    getWithBasicUserAuth("/mass_transit_stops", "kalpa", "kalpa") {
      status should equal(400)
    }
    getWithBasicUserAuth("/mass_transit_stops?municipality=235", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

}
