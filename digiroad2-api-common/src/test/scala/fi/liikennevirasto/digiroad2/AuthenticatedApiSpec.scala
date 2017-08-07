package fi.liikennevirasto.digiroad2

import org.scalatest.FunSuite
import org.scalatra.test.scalatest.ScalatraSuite

trait AuthenticatedApiSpec extends FunSuite with ScalatraSuite {
  def getWithUserAuth[A](uri: String, username: String = "test")(f: => A): A = {
    val authHeader = authenticateAndGetHeader(username)
    get(uri, headers = authHeader)(f)
  }

  def getWithOperatorAuth[A](uri: String)(f: => A): A = getWithUserAuth(uri, "test2")(f)

  def postJsonWithUserAuth[A](uri: String, body: Array[Byte], headers: Map[String, String] = Map(), username: String = "test")(f: => A): A = {
    post(uri, body, headers = authenticateAndGetHeader(username) + ("Content-type" -> "application/json") ++ headers)(f)
  }

  def putJsonWithUserAuth[A](uri: String, body: Array[Byte], headers: Map[String, String] = Map(), username: String = "test")(f: => A): A = {
    put(uri, body, headers = authenticateAndGetHeader(username) + ("Content-type" -> "application/json") ++ headers)(f)
  }

  def deleteWithUserAuth[A](uri: String,  headers: Map[String, String] = Map(), username: String = "test")(f: => A): A = {
    delete(uri, headers = authenticateAndGetHeader(username) ++ headers)(f)
  }

  def authenticateAndGetHeader(username: String): Map[String, String] = {
    Map("OAM_REMOTE_USER" -> username)
  }
}
