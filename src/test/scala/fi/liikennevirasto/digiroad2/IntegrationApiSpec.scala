package fi.liikennevirasto.digiroad2

import java.util.Properties
import org.scalatest.{Tag, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64


class IntegrationApiSpec extends FunSuite with ScalatraSuite {

  addServlet(classOf[IntegrationApi], "/*")

  val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/authentication.properties"))
    props
  }

  private def getProperty(name: String): String = {
    val property = properties.getProperty(name)
    if (property != null) {
      property
    } else {
      throw new RuntimeException(s"cannot find property $name")
    }
  }

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes())
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }

  val username = getProperty("authentication.basic.username")
  val password = getProperty("authentication.basic.password")

  test("Should require correct authentication", Tag("db")) {
    get("/data") {
      status should equal(401)
    }
    getWithBasicUserAuth("/data", "nonexisting", "incorrect") {
      status should equal(401)
    }
    getWithBasicUserAuth("/data", username, password) {
      status should equal(200)
    }
  }

  test("Get assets requires municipality number") {
    getWithBasicUserAuth("/assets", "kalpa", "kalpa") {
      status should equal(400)
    }
    getWithBasicUserAuth("/assets?municipality=235", username, password) {
      status should equal(200)
    }
  }

}
