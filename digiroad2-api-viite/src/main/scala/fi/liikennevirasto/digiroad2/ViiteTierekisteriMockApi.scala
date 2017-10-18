package fi.liikennevirasto.digiroad2

import org.apache.commons.logging.LogFactory
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra._

class ViiteTierekisteriMockApi extends ScalatraServlet with JacksonJsonSupport {

  var projectsReceived: Map[Long, Map[String, Any]] = Map()
  val SourceXIsNullMessage = "Source %s is null"
  val TargetXIsNullMessage = "Target %s is null"
  val SourceXNotNullMessage = "Source %s is not null"
  val TargetXNotNullMessage = "Target %s is not null"
  val ProjectIdAlreadyExists = Map("error_message" -> "Tierekisterissä on jo olemassa projekti tällä id:llä!")
  val MandatoryFieldMissing = Map("error_message" -> "%s is missing!")
  val IncorrectLengthOrValue = Map("error_message" -> "Field %s length or value is wrong!")

  override protected implicit def jsonFormats: Formats = DefaultFormats

  lazy val logger = LogFactory.getLog(getClass)

  before() {
    contentType = formats("json")
  }

  post("/addresschange/"){
    logger.info("POST /addresschange/")
    if (!request.headers.exists(_==("X-Authorization" -> "Basic aW5zZXJ0VFJ1c2VybmFtZTppbnNlcnRUUnBhc3N3b3Jk"))){
      logger.warn("POST not authorized")
      halt(Unauthorized("401 Unauthorized"))
    }
    val mappedObject = parsedBody.mapField{
      case (key, JString(value)) => (key, JString(value))
      case (key, JInt(value)) => (key, JInt(value))
      case (key, JObject(value)) => (key, mapNestedObject(JObject(value)))
      case x => x
    }
    val extractedProject = mappedObject.extract[Map[String, Any]]
    validateProject(extractedProject)
    logger.warn("Project validated")
    val id = extractedProject.get("id")
    if (id.isEmpty)
      BadRequest(Map("error_message" -> "id not found"))
    else {
      projectsReceived = projectsReceived ++ Map(anythingToLong(id.get) -> extractedProject)
      Created(Map("message" -> "Project created"))
    }
  }

  private def mapNestedObject(nestedJObject:JObject):JValue ={
    val things = nestedJObject.mapField{
      case (key, JString(value)) => (key, JString(value))
      case (key, JInt(value)) => (key, JInt(value))
      case (key, JObject(value)) => (key, mapNestedObject(JObject(value)))
      case x => x
    }
    things
  }

  private def failValidation(errorMessage: String): ActionResult = {
    failValidation(Map("error_message" -> errorMessage))
  }
  private def failValidation(errorMessage: Map[String, String]): ActionResult = {
    logger.info(s"Validation failed: $errorMessage")
    BadRequest(errorMessage)
  }
  private def validateProject(project: Map[String,Any]) ={
    def testNonNull(map: Map[String, Any], keys: Seq[String], errorTemplate: Map[String, String]): Unit = {
      if (keys.nonEmpty) {
        val key = keys.head
        val errorMessage = errorTemplate.mapValues(_.format(key))
        if (map(key) == "null") {
          halt(failValidation(errorMessage))
        }
        testNonNull(map, keys.tail, errorTemplate)
      }
    }

    def testIsNull(map: Map[String, Any], keys: Seq[String], errorTemplate: String): Unit = {
      val nonNullValues=map.filterNot(x=>x._2==null)
      if (nonNullValues.nonEmpty) {
        val errorMessage = errorTemplate.format(nonNullValues.head._1)
        halt(failValidation(errorMessage))
      }
    }

    def testValues(map: Map[String, Any], keys: Seq[String], errorTemplate: Map[String, String]): Unit = {
      if (keys.nonEmpty) {
        val key = keys.head
        val errorMessage = errorTemplate.mapValues(_.format(key))
        val value = anythingToLong(map(key))
        key match {
          case "aosa" | "losa" if value > 0 && value <= 999 =>
          case "tie" | "aet" | "let" if value >= 0 && value <= 99999 =>
          case "ajr" if value >= 0 && value <= 2 =>
          case _ =>
            logger.info(s"Validation failed: $errorMessage")
            halt(BadRequest(errorMessage))
        }
        testValues(map, keys.tail, errorTemplate)
      }
    }

    val changeInfoKeys = Seq("tie", "ajr", "aosa", "aet", "losa", "let")
    val projectId = project("id")
    val keys = project.keySet.toList
    if (projectsReceived.contains(anythingToLong(projectId)))
      halt(ExpectationFailed(ProjectIdAlreadyExists))
    val user = project.getOrElse("user", "").toString
    if (user.length == 0 || user.length > 10)
      halt(ExpectationFailed(IncorrectLengthOrValue.mapValues(_.format("user"))))
    val name = project.getOrElse("name", "").toString
    if (name.length == 0 || name.length > 30)
      halt(ExpectationFailed(IncorrectLengthOrValue.mapValues(_.format("name"))))
    val changeInfo = project.get(keys(1)).map(_.asInstanceOf[List[Map[String, Any]]].head)
    // TODO: changeInfo, changeType, changeDate, continuity validation
    val changeType = changeInfo.get("change_type")
    val source = changeInfo.get.get("source").map(_.asInstanceOf[Map[String, Any]]).get
    val target = changeInfo.get.get("target").map(_.asInstanceOf[Map[String, Any]]).get
    changeType match {
      case 1 =>
        //Source - not null
        //Target - all null
        logger.info("Matched 1")
        testNonNull(source, changeInfoKeys, MandatoryFieldMissing)
        // TODO: turn on this check when viite-tr-client is updated
//        testIsNull(target, changeInfoKeys, TargetXNotNullMessage)
        testValues(source, changeInfoKeys, IncorrectLengthOrValue)
      case 2 =>
        //Source - all null
        //Target - not null
        logger.info("Matched 2")
        testIsNull(source, changeInfoKeys, SourceXNotNullMessage)
        testNonNull(target, changeInfoKeys, MandatoryFieldMissing)
        testValues(target, changeInfoKeys, IncorrectLengthOrValue)
      case 3 =>
        //Source - not null
        //Target - not null
        logger.info("Matched 3")
        testNonNull(source, changeInfoKeys, MandatoryFieldMissing)
        testNonNull(target, changeInfoKeys, MandatoryFieldMissing)
        testValues(source, changeInfoKeys, IncorrectLengthOrValue)
        testValues(target, changeInfoKeys, IncorrectLengthOrValue)
      case 4 =>
        //Source - not null
        //Target - not null
        logger.info("Matched 4")
        testNonNull(source, changeInfoKeys, MandatoryFieldMissing)
        testNonNull(target, changeInfoKeys, MandatoryFieldMissing)
        testValues(source, changeInfoKeys, IncorrectLengthOrValue)
        testValues(target, changeInfoKeys, IncorrectLengthOrValue)
      case 5 =>
        //Source - not null
        //Target - all null
        logger.info("Matched 5")
        testNonNull(source, changeInfoKeys, MandatoryFieldMissing)
        testIsNull(target, changeInfoKeys, TargetXNotNullMessage)
        testValues(source, changeInfoKeys, IncorrectLengthOrValue)
    }
  }

  get("/addresschange/:projectId"){
    logger.info(s"GET /addresschange/${params("projectId")}")
    if (!request.headers.exists(_==("X-Authorization" -> "Basic aW5zZXJ0VFJ1c2VybmFtZTppbnNlcnRUUnBhc3N3b3Jk"))){
      logger.warn("GET not authorized")
      halt(BadRequest("401 Unauthorized"))
    } else {
      logger.info("Passed the authorization verification")
      logger.info("Trying to get the project Id")
      val projectId = anythingToLong(params("projectId"))
      logger.debug("If this is correct, project Id should be: " + projectId)
      if (projectsReceived.contains(projectId)){
        toProjectResponseObject(projectsReceived(projectId))
      }
      else {
        logger.info("404: projectId was: " + projectId)
        halt(NotFound(s"project with id $projectId was not found"))
      }
    }
  }

  private def anythingToLong(a: Any): Long = {
    a match {
      case s: String => s.toLong
      case b: BigInt => b.longValue()
      case i: Int => i.toLong
      case l: Long => l
      case _ => a.toString.toLong
    }
  }

  private def toProjectResponseObject(project: Map[String, Any]) = {
    val id = anythingToLong(project("id"))
    // Use project name "error:%d" to get error from the list below. For example "error:-6801"
    val (errorCode, error) = project("name").toString match {
      case s if s.startsWith("error:") =>
        val c = s.replaceFirst("error:", "").toLong
        (c, errorCodes(c))
      case _ =>
        (0, null)
    }
    logger.info(s"Project id $id with name ${project("name")} has error code $errorCode, $error")
    Map (
      "id_tr_projekti" -> (1000 + id),
      "projekti" -> id,
      "id" -> id,
      "tunnus" -> 5,
      "status" -> (errorCode match {
        case 0 => "T"
        case _ => "V"
      }),
      "name" -> project("name").toString,
      "change_date" -> project("change_date").toString,
      "ely" -> project("ely").toString,
      "muutospvm" -> project("change_date").toString,
      "user" -> project("user").toString,
      "published_date" -> project("change_date").toString,
      "job_number" -> (20+id),
      "error_message" -> error,
      "start_time" -> project("change_date").toString,
      "end_time" -> project("change_date").toString,
      "error_code" -> errorCode)
  }

  val errorCodes: Map[Long, String] = Map(
    -6007L -> "Projektin pvm ei saa olla tyhjä.",
    -6001L -> "Projektin nimi ei saa olla tyhjä.",
    -6091L -> "Tie ja Aosa ovat pakollisia.",
    -6801L -> "Epäjatkuvuus voi olla vain tieosan viimeisen pätkän lopussa.",
    -6802L -> "Virheellinen tietyyppi tai tietyyppi puuttuu.",
    -6803L -> "Koko tieosalla pitää olla sama piiri.",
    -6804L -> "Jatkuu -tiedon sallitut arvot ovat 1-5.",
    -6701L -> "Historia -tiedon päivitys ei onnistu.",
    -6702L -> "Ajorata -tiedon päivitys ei onnistu.",
    -6703L -> "Osoitemuutoksen päivitys ei onnistu.",
    -6805L -> "Tie,  A_osa, Aet, L_osa ja Let eivät saa olla tyhjiä.",
    -6023L -> "Tieosan pituus ei löydy.",
    -6901L -> "Tiellä ei ole voimassa olevaa nimeä.",
    -6902L -> "Virheellinen tien nimen alkupvm.",
    -6903L -> "Tien nimi on jo olemassa.",
    -6904L -> "Virheellinen tien nimen loppupvm.",
    -6950L -> "Korjausprojektin pvm pitää olla kuluvan vuoden pvm ja pienempi kuin kuluva päivä.",
    -6081L -> "Tie/Aosa virheellinen.",
    -6082L -> "Tie/Losa virheellinen.",
    -6083L -> "Loppuosa pitää olla suurempi kuin Alkuosa tai tyhjä.",
    -6084L -> "Tie/Aosa on jo varattu toiseen projektiin.",
    -6071L -> "Tieosa ei ole projektissa.",
    -6093L -> "Tieosuus ei ole olemassa.",
    -6094L -> "Tieosuuden tarkistus ei onnistu.",
    -6024L -> "Kokonaista tieosaa ei voi ilmoittaa 'Ennallaan' olevaksi.",
    -6025L -> "'Ennallaan'-ilmoituksen tarkistus ei onnistu.",
    -6051L -> "Virheellinen tieosa.",
    -6053L -> "Nykyisten ja uusien tieosien lukumäärät eivät ole samat.",
    -6054L -> "'Numerointi'-ilmoituksen tarkistus ei onnistu.",
    -6072L -> "Tieosa ei ole kokonaan käsitelty.",
    -6042L -> "'Siirto'-ilmoituksen tarkistus ei onnistu.",
    -6032L -> "'Uusi'-ilmoituksen tarkistus ei onnistu.",
    -6061L -> "'Lakkautus'-ilmoituksen tarkistus ei onnistu.",
    -6055L -> "'Poisto' -ilmoituksen tarkistus ei onnistu.",
    -6056L -> "'Pidentäminen' -ilmoituksen tarkistus ei onnistu.",
    -6075L -> "Projektissa päällekkäisiä nykyosoitteen osuuksia.",
    -6076L -> "Toisen ajoradan ilmoitus puuttuu tai on virheellinen.",
    -6077L -> "Projektissa päällekkäisiä uuden osoitteen osuuksia.",
    -6078L -> "Numerointi -ilmoituksen alku- ja lopputieosan väliin jääviä kaikkia tieosia ei ole varattu projektiin.",
    -6079L -> "Lakkautus -ilmoituksen alku- ja lopputieosan väliin jääviä kaikkia tieosia ei ole varattu projektiin.",
    -6080L -> "Projektiin varattuja tieosia ei ole käsitelty kokonaan.",
    -6200L -> "Ilmoitusta ei ole valittu.",
    -6100L -> "Ajoradan osuuden lisäys ei onnistu.",
    -6102L -> "Ajoradan osuus on jo lisätty.",
    -6073L -> "Projektin tarkistus ei onnistu.",
    -7001L -> "Projektin päivitys ei onnistunut."
  )
}
