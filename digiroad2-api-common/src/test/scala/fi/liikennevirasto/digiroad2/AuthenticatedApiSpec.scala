package fi.liikennevirasto.digiroad2

import org.scalatest.FunSuite
import org.scalatra.test.scalatest.ScalatraSuite

import java.nio.charset.StandardCharsets
import java.util.Base64

trait AuthenticatedApiSpec extends FunSuite with ScalatraSuite {
  def getWithUserAuth[A](uri: String, username: String = "ely1")(f: => A): A = {
    val authHeader = authenticateAndGetHeader(username)
    get(uri, headers = authHeader)(f)
  }

  def getWithOperatorAuth[A](uri: String)(f: => A): A = getWithUserAuth(uri, "ely1")(f)

  def postJsonWithUserAuth[A](uri: String, body: Array[Byte], headers: Map[String, String] = Map(), username: String = "ely1")(f: => A): A = {
    post(uri, body, headers = authenticateAndGetHeader(username) + ("Content-type" -> "application/json") ++ headers)(f)
  }

  def putJsonWithUserAuth[A](uri: String, body: Array[Byte], headers: Map[String, String] = Map(), username: String = "ely1")(f: => A): A = {
    put(uri, body, headers = authenticateAndGetHeader(username) + ("Content-type" -> "application/json") ++ headers)(f)
  }

  def deleteWithUserAuth[A](uri: String,  headers: Map[String, String] = Map(), username: String = "ely1")(f: => A): A = {
    delete(uri, headers = authenticateAndGetHeader(username) ++ headers)(f)
  }

  def authenticateAndGetHeader(username: String): Map[String, String] = {
    val jwtToken= s"""{"custom:rooli":"int_kayttajat,Extranet_Kayttaja,arn:aws:iam::117531223221:role/DigiroadAdmin,arn:aws:iam::117531223221:saml-provider/VaylaTestOAM","sub":"2b5a2b65-ca06-46e2-8a52-a5190b495d12","email_verified":"false","custom:uid":"${username}","email":"other.user@sitowise.com","username":"vaylatestoam_other.usern@sitowise.com","exp":1591117019,"iss":"https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_oNzPsiXEJ"}""".stripMargin
    val jwtTokenInBase64=new String(Base64.getEncoder.withoutPadding().encode(jwtToken.getBytes(StandardCharsets.UTF_8)))
    Map("X-Iam-Data" ->("." + jwtTokenInBase64))
  }
}
