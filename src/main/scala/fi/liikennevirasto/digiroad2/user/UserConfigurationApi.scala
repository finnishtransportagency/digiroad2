package fi.liikennevirasto.digiroad2.user

import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.slf4j.LoggerFactory
import org.json4s.{DefaultFormats, Formats}

class UserConfigurationApi extends ScalatraServlet with JacksonJsonSupport
  with CorsSupport with RequestHeaderAuthentication {

  val logger = LoggerFactory.getLogger(getClass)
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
  }

  get("/user/:username") {
    params.get("username").flatMap {
      userProvider.getUser(_)
    } match {
      case Some(u) => u
      case None => NotFound("User not found: " + params.get("username").getOrElse(""))
    }
  }

  post("/user") {
    // TODO: create user atomically in provider
    val user = parsedBody.extract[User]
    userProvider.getUser(user.username) match {
      case Some(user) => Conflict("User already exists: " + user)
      case None => {
        userProvider.createUser(user.username, user.configuration)
        userProvider.getUser(user.username)
      }
    }
  }

  put("/user/:username/municipalities") {
    // TODO: when implementing UI, use municipalities of client to determine authorization, only modify (add/remove) authorized municipalities
    val municipalities = parsedBody.extract[List[Long]].toSet
    params.get("username").flatMap {
      userProvider.getUser(_)
    }.map { u =>
      val updatedUser = u.copy(configuration = u.configuration.copy(authorizedMunicipalities = municipalities))
      userProvider.saveUser(updatedUser)
      updatedUser
    } match {
      case Some(updated) => updated
      case None => NotFound("User not found: " + params("username"))
    }
  }

  put("/municipalitiesbatch") {
    request.body.lines.foreach { line =>
      val elements = line.trim.split(",").toList
      if (elements.length < 2) {
        logger.warn("Discarding line: " + line)
      } else {
        val (username, municipalityNumbers) = (elements.head, elements.tail.map { m => m.trim.toLong }.toSet)
        userProvider.getUser(username) match {
          case Some(u) => {
            val updatedUser = u.copy(configuration = u.configuration.copy(authorizedMunicipalities = municipalityNumbers))
            userProvider.saveUser(updatedUser)
          }
          case None => {
            userProvider.createUser(username, Configuration(authorizedMunicipalities = municipalityNumbers))
          }
        }
      }
    }
  }

  error {
    case e: Exception => {
      logger.error("API Error", e)
      InternalServerError("Internal Error")
    }
  }
}