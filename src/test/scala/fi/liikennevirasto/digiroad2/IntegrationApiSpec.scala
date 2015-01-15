package fi.liikennevirasto.digiroad2

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
    get("/data") {
      status should equal(401)
    }
    getWithBasicUserAuth("/data", "nonexisting", "incorrect") {
      status should equal(401)
    }
    getWithBasicUserAuth("/data", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

}
