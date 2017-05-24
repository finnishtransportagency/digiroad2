package fi.liikennevirasto.digiroad2

import org.apache.commons.logging.LogFactory
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra._

class ViiteTierekisteriTestApi extends ScalatraServlet with JacksonJsonSupport {

  val projectResponseObject = Map (
    "id_tr_projekti" -> 1162,
    "projekti" -> 0,
    "id" -> 13255,
    "tunnus" -> 5,
    "status" -> "T",
    "name" -> "test",
    "change_date" -> "2017-06-01",
    "ely" -> 1,
    "muutospvm" -> "2017-05-15",
    "user" -> "user",
    "published_date" -> "2017-05-15",
    "job_number" -> 28,
    "error_message" -> null,
    "start_time" -> "2017-05-15",
    "end_time" -> "2017-05-15",
    "error_code" -> 0);
  val SourceXIsNullMessage = "Source %s is null"
  val TargetXIsNullMessage = "Target %s is null"
  val SourceXNotNullMessage = "Source %s is not null"
  val TargetXNotNullMessage = "Target %s is not null"


  override protected implicit def jsonFormats: Formats = DefaultFormats

  lazy val logger = LogFactory.getLog(getClass)

  before() {
    contentType = formats("json")
  }

  post("/addresschange/"){
    println("Got regular url")
    println(request.toString)
    if (!request.headers.exists(_==("X-OTH-Authorization" -> "Basic dHJyZXN0Omxva2FrdXUyMDE2dGllcmVraXN0ZXJp"))){
      halt(BadRequest("401 Unauthorized"))
    }
    println(parsedBody.toString)
    val mappedObject = parsedBody.mapField{
      case (key, JString(value)) => (key, JString(value))
      case (key, JInt(value)) => (key, JInt(value))
      case (key, JObject(value)) => (key, mapNestedObject(JObject(value)))
      case x => x
    }
    val extractedProject = mappedObject.extract[Map[String, Any]]
    validateProject(extractedProject)
    Created()
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

  private def validateProject(project: Map[String,Any]) ={
    def testNonNull(map: Map[String, Any], keys: Seq[String], errorTemplate: String): Unit = {
      if (keys.nonEmpty) {
        val key = keys.head
        val errorMessage = errorTemplate.format(key)
        if (map(key) == "null") {
          println(errorMessage)
          halt(BadRequest(errorMessage))
        }
        testNonNull(map, keys.tail, errorTemplate)
      }
    }

    def testIsNull(map: Map[String, Any], keys: Seq[String], errorTemplate: String): Unit = {
      if (keys.nonEmpty) {
        val key = keys.head
        val errorMessage = errorTemplate.format(key)
        if (map(key) != "null") {
          println(errorMessage)
          halt(BadRequest(errorMessage))
        }
        testIsNull(map, keys.tail, errorTemplate)
      }
    }

    val changeInfoKeys = Seq("tie", "ajr", "aosa", "aet", "losa", "let")
    val projectId = project("id")
    val keys = project.keySet.toList
    if(!projectId.equals(0)){
      halt(ExpectationFailed("Not the test project"))
    }
    val changeInfo = project.get(keys(1)).map(_.asInstanceOf[List[Map[String, Any]]].head)
    val changeType = changeInfo.get("change_type")
    val source = changeInfo.get.get("source").map(_.asInstanceOf[Map[String, Any]]).get
    val target = changeInfo.get.get("target").map(_.asInstanceOf[Map[String, Any]]).get
    changeType match {
      case 1 =>
        //Source - not null
        //Target - all null
        println("Matched 1")
        testNonNull(source, changeInfoKeys, SourceXIsNullMessage)
        testIsNull(target, changeInfoKeys, TargetXNotNullMessage)
      case 2 =>
        //Source - all null
        //Target - not null
        println("Matched 2")
        testIsNull(source, changeInfoKeys, SourceXNotNullMessage)
        testNonNull(target, changeInfoKeys, TargetXIsNullMessage)
      case 3 =>
        //Source - not null
        //Target - not null
        println("Matched 3")
        testNonNull(source, changeInfoKeys, SourceXIsNullMessage)
        testNonNull(target, changeInfoKeys, TargetXIsNullMessage)
      case 4 =>
        //Source - not null
        //Target - not null
        println("Matched 4")
        testNonNull(source, changeInfoKeys, SourceXIsNullMessage)
        testNonNull(target, changeInfoKeys, TargetXIsNullMessage)
      case 5 =>
        //Source - not null
        //Target - all null
        println("Matched 5")
        testNonNull(source, changeInfoKeys, SourceXIsNullMessage)
        testIsNull(target, changeInfoKeys, TargetXNotNullMessage)
    }
  }

  get("/addresschange/:projectId"){
    println("Entered check project")
    println(request.toString)
    if (!request.headers.exists(_==("X-OTH-Authorization" -> "Basic dHJyZXN0Omxva2FrdXUyMDE2dGllcmVraXN0ZXJp"))){
      halt(BadRequest("401 Unauthorized"))
    } else {
      println("Passed the authorization verification")
      println("Trying to get the project Id")
      val projectId = params("projectId").toInt
      println("If this is correct, project Id should be: " + projectId)
      if(projectId == 0){
        println("Passed the project Id == 0 validation, outputting the projectId object bellow")
        println(projectResponseObject.toString())
        projectResponseObject
      }
      else {
        println("Halting, projectId was: " + projectId)
        halt(NotFound())
      }
    }
  }
}
