package fi.liikennevirasto.digiroad2.user

import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import fi.liikennevirasto.digiroad2.authentication.JWTAuthentication
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.slf4j.LoggerFactory
import org.json4s.{DefaultFormats, Formats}

class UserConfigurationApi extends ScalatraServlet with JacksonJsonSupport
  with CorsSupport with JWTAuthentication {

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
      userProvider.getUser(_)
    } match {
      case Some(u) => u
      case None => NotFound("User not found: " + params.get("username").getOrElse(""))
    }
  }

  post("/user") {
    val user = parsedBody.extract[User]
    userProvider.getUser(user.username) match {
      case Some(name) => Conflict("User already exists: " + name)
      case None =>
        userProvider.createUser(user.username, user.configuration, user.name)
        userProvider.getUser(user.username)
    }
  }

  put("/user/:username/municipalities") {
    val municipalities = parsedBody.extract[List[Int]].toSet
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

  put("/user/:username/name") {
    val name = parsedBody.extract[String]
    params.get("username").flatMap {
      userProvider.getUser(_)
    }.map { u =>
      val updatedUser = u.copy(name = Some(name))
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
        val name = elements.lift(3).getOrElse("").trim
        val (username, municipalityNumbers) = (elements.head, municipalitiesOfEly ++ municipalities)
        userProvider.getUser(username) match {
          case Some(u) =>
            val updatedUser = u.copy(configuration = u.configuration.copy(authorizedMunicipalities = municipalityNumbers), name = Some(name))
            userProvider.saveUser(updatedUser)
          case None =>
            userProvider.createUser(username, Configuration(authorizedMunicipalities = municipalityNumbers), Some(name))
        }
      }
    }
  }

  post("/newuser") {
    if (!userProvider.getCurrentUser().isOperator()) {
      halt(Forbidden("Vain operaattori voi lisätä käyttäjiä"))
    }
    val (username, roleName, name) =
      (request.getParameter("username"), request.getParameterValues("roleName"), request.getParameter("name"))

    val elyNumber = request.getParameterValues("elyNumber") == null match {
      case false => request.getParameterValues("elyNumber").mkString(",")
      case true => ""
    }

    val municipalities = request.getParameterValues("municipalityNumbers")== null match {
      case false => request.getParameterValues("municipalityNumbers").mkString(",")
      case true => ""
    }

    val authorizationArea = request.getParameterValues("authorizationArea")== null match {
      case false => request.getParameterValues("authorizationArea").mkString(",")
      case true => ""
    }


    val municipalitiesOfEly = splitToInts(elyNumber) match {
      case None => Set()
      case Some(numbers) => municipalityProvider.getMunicipalities(numbers.toSet)
    }
    val municipalityNumbers =  municipalitiesOfEly ++ splitToInts(municipalities).getOrElse(Set())

    val availableRoles = Set(Role.ElyMaintainer, Role.Operator, Role.ServiceRoadMaintainer)
    val roles: Set[String] = roleName.filter(availableRoles.contains).toSet

    val authorizedAreas = splitToInts(authorizationArea) match {
      case Some(authIds) if roles.contains(Role.ServiceRoadMaintainer)  =>
        if(authIds.toSet.subsetOf((1 to 12).toSet))
          authIds.toSet
        else
          halt(BadRequest("Authorization area should be between 1 and 12"))
      case _ => Set[Int]()
    }

  userProvider.getUser(username) match {
      case Some(u) =>
        val updatedUser = u.copy(configuration = u.configuration.copy(authorizedMunicipalities = municipalityNumbers.toSet, authorizedAreas = authorizedAreas, roles = roles), name = Some(name))
        userProvider.saveUser(updatedUser)
      case None =>
        userProvider.createUser(username, Configuration(authorizedMunicipalities = municipalityNumbers.toSet, authorizedAreas = authorizedAreas, roles = roles), Some(name))
    }
  }

  put("/user/:username/roles") {
    val newRoles = parsedBody.extract[List[String]].toSet
    params.get("username").flatMap {
      userProvider.getUser(_)
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