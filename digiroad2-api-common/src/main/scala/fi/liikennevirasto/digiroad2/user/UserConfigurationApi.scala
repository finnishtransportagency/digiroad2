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
    response.setHeader(Digiroad2ServerOriginatedResponseHeader, "true")
  }

  get("/user/:username") {
    params.get("username").flatMap {
      userProvider.getUser
    } match {
      case Some(u) => u
      case None => NotFound("User not found: " + params.get("username").getOrElse(""))
    }
  }

  post("/user") {
    // TODO: create user atomically in provider
    val user = parsedBody.extract[User]
    userProvider.getUser(user.username) match {
      case Some(name) => Conflict("User already exists: " + name)
      case None =>
        userProvider.createUser(user.username, user.configuration)
        userProvider.getUser(user.username)
    }
  }

  put("/user/:username/municipalities") {
    // TODO: when implementing UI, use municipalities of client to determine authorization, only modify (add/remove) authorized municipalities
    val municipalities = parsedBody.extract[List[Int]].toSet
    params.get("username").flatMap {
      userProvider.getUser
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
      val elements = line.trim.split(";").toIndexedSeq
      if (elements.length < 2) {
        logger.warn("Discarding line: " + line)
      } else {
        val municipalitiesOfEly = parseInputToInts(elements, 1) match {
          case None => Set()
          case Some(numbers) => municipalityProvider.getMunicipalities(numbers.toSet).toSet
        }
        val municipalities = parseInputToInts(elements, 2).getOrElse(Set()).toSet
        val (username, municipalityNumbers) = (elements.head, municipalitiesOfEly ++ municipalities)
        userProvider.getUser(username) match {
          case Some(u) =>
            val updatedUser = u.copy(configuration = u.configuration.copy(authorizedMunicipalities = municipalityNumbers))
            userProvider.saveUser(updatedUser)
          case None =>
            userProvider.createUser(username, Configuration(authorizedMunicipalities = municipalityNumbers))
        }
      }
    }
  }

  post("/newuser") {
    if (!userProvider.getCurrentUser().isOperator()) {
      halt(Forbidden("Vain operaattori voi lisätä käyttäjiä"))
    }
    val (username, elyNumber, municipalities, roleName, authorizationArea) =
      (request.getParameter("username"), request.getParameter("elyNumber"),
        request.getParameter("municipalityNumbers"), request.getParameter("roleName"), request.getParameter("authorizationArea"))

    val municipalitiesOfEly = splitToInts(elyNumber) match {
      case None => Set()
      case Some(numbers) => municipalityProvider.getMunicipalities(numbers.toSet)
    }
    val municipalityNumbers =  municipalitiesOfEly ++ splitToInts(municipalities).getOrElse(Set())

    val availableRoles = Set(Role.BusStopMaintainer, Role.Operator, Role.Premium, Role.ServiceRoadMaintainer)
    val roles = availableRoles.find(_ == roleName) match{
      case Some(role) => Set[String](role)
      case _ => Set[String]()
    }

    val authorizedAreas = authorizationArea.toInt match {
      case authId if (1 to 12).contains(authId) && roleName == Role.ServiceRoadMaintainer  => Set[Int](authId)
      case _ => Set[Int]()
    }

  userProvider.getUser(username) match {
      case Some(u) =>
        val updatedUser = u.copy(configuration = u.configuration.copy(authorizedMunicipalities = municipalityNumbers.toSet, authorizedAreas = authorizedAreas, roles = roles))
        userProvider.saveUser(updatedUser)
      case None =>
        userProvider.createUser(username, Configuration(authorizedMunicipalities = municipalityNumbers.toSet, authorizedAreas = authorizedAreas, roles = roles))
    }
  }

  put("/user/:username/roles") {
    val newRoles = parsedBody.extract[List[String]].toSet
    params.get("username").flatMap {
      userProvider.getUser
    }.map { u =>
      val updatedUser = u.copy(configuration = u.configuration.copy(roles = newRoles))
      userProvider.saveUser(updatedUser)
      updatedUser
    } match {
      case Some(updated) => updated
      case None => NotFound("User not found: " + params("username"))
    }
  }

  def parseInputToInts(input: Seq[String], position: Int): Option[Seq[Int]] = {
    if (!input.isDefinedAt(position)) return None
    splitToInts(input(position))
  }

  def splitToInts(numbers: String) : Option[Seq[Int]] = {
    val split = numbers.split(",").filterNot(_.trim.isEmpty)
    split match {
      case Array() => None
      case _ => Some(split.map(_.trim.toInt).toSeq)
    }
  }

  error {
    case e: Exception =>
      logger.error("API Error", e)
      InternalServerError("Internal Error")
  }
}