package fi.liikennevirasto.digiroad2.authentication

import java.nio.charset.StandardCharsets
import java.util.Base64
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

import javax.servlet.http.HttpServletRequest
import org.json4s
import org.json4s.jackson.JsonMethods._
import org.slf4j.LoggerFactory

object JWTReader {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit lazy val formats = org.json4s.DefaultFormats

  def getUsername(jwt: String): String = {
    try {
      val jwtParts = jwt.split('.')
      val jwtPayloadBase64Encoded = jwtParts(1)
      val jwtPayload = new String(Base64.getDecoder.decode(jwtPayloadBase64Encoded), StandardCharsets.UTF_8)
      logger.debug(s"JWT Payload: $jwtPayload")
      parseUsernameFromJWTPayloadJSONString(jwtPayload)
    } catch {
      case e: Exception => logger.warn("Failed to parse JWT"); throw e
    }
  }

  def parseUsernameFromJWTPayloadJSONString(jsonString: String): String = {
    val json: json4s.JValue = parse(jsonString)
    (json \ "custom:uid").extractOrElse("")
  }

}

trait JWTAuthentication extends Authentication {
  private val jwtLogger = LoggerFactory.getLogger(getClass)

  val dataHeader = "X-Iam-Data"

  def authenticate(request: HttpServletRequest)(implicit userProvider: UserProvider): User = {
    val username: String = {
      val checkCookies = request.getCookies != null && request.getCookies.exists(p => p.getName == "testusername")
      // local development system
      if (Digiroad2Properties.authenticationTestMode && checkCookies) {
        request.getCookies.find(p => p.getName == "testusername").orNull.getValue
      } else {
        // In AWS use JWT
        val tokenHeaderValue = request.getHeader(dataHeader)
        JWTReader.getUsername(tokenHeaderValue)
      }
    }

    if (username.isEmpty) {
      jwtLogger.warn(s"Authentication failed. Missing username in JWT payload.")
      throw UnauthenticatedException()
    }

    val user = userProvider.getUser(username).getOrElse(viewerUser)
    jwtLogger.info(s"Authenticate request, remote user = $username.${if (user.isNotInDigiroad()) " (User not added in Digiroad.)" else ""}")
    user

  }
}