package fi.liikennevirasto.digiroad2

import org.apache.commons.logging.LogFactory
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra._

/**
  * Created by pedrosag on 17-05-2017.
  */
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
  val SourceTieIsNullMessage = "Source Tie is null"
  val SourceAjrIsNullMessage = "Source Ajr is null"
  val SourceAosaIsNullMessage = "Source Aosa is null"
  val SourceAetIsNullMessage = "Source Aet is null"
  val SourceLosaIsNullMessage = "Source Losa is null"
  val SourceLetIsNullMessage = "Source Let is null"

  val TargetTieIsNullMessage = "Target Tie is null"
  val TargetAjrIsNullMessage = "Target Ajr is null"
  val TargetAosaIsNullMessage = "Target Aosa is null"
  val TargetAetIsNullMessage = "Target Aet is null"
  val TargetLosaIsNullMessage = "Target Losa is null"
  val TargetLetIsNullMessage = "Target Let is null"


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
    val projectId = project.get("id").get
    val keys = project.keySet.toList
    if(!projectId.equals(0)){
      halt(ExpectationFailed("Not the test project"));
    }
    val changeInfo = project.get(keys(1)).map(_.asInstanceOf[List[Map[String, Any]]].head)
    val changeType = changeInfo.get.get("change_type").get
    val source = changeInfo.get.get("source").map(_.asInstanceOf[Map[String, Any]]).get
    val target = changeInfo.get.get("target").map(_.asInstanceOf[Map[String, Any]]).get
    changeType match {
      case 1 => {
        //Source - not null
        //Target - all null
        println("Matched 1")
        if(source.get("tie").get == "null") {
          println(SourceTieIsNullMessage);
          halt(BadRequest(SourceTieIsNullMessage))
        }
        if(source.get("ajr").get == "null") {
          println(SourceAjrIsNullMessage);
          halt(BadRequest(SourceAjrIsNullMessage))
        }
        if(source.get("aosa").get == "null") {
          println(SourceAosaIsNullMessage);
          halt(BadRequest(SourceAosaIsNullMessage))
        }
        if(source.get("aet").get == "null") {
          println(SourceAetIsNullMessage);
          halt(BadRequest(SourceAetIsNullMessage))
        }
        if(source.get("losa").get == "null") {
          println(SourceLosaIsNullMessage);
          halt(BadRequest(SourceLosaIsNullMessage))
        }
        if(source.get("let").get == "null") {
          println(SourceLetIsNullMessage);
          halt(BadRequest(SourceLetIsNullMessage))
        }

        if(target.get("tie").get != "null") {
          println(TargetTieIsNullMessage);
          halt(BadRequest(TargetTieIsNullMessage))
        }
        if(target.get("ajr").get != "null") {
          println(TargetAjrIsNullMessage);
          halt(BadRequest(TargetAjrIsNullMessage))
        }
        if(target.get("aosa").get != "null") {
          println(TargetAosaIsNullMessage);
          halt(BadRequest(TargetAosaIsNullMessage))
        }
        if(target.get("aet").get != "null") {
          println(TargetAetIsNullMessage);
          halt(BadRequest(TargetAetIsNullMessage))
        }
        if(target.get("losa").get != "null") {
          println(TargetLosaIsNullMessage);
          halt(BadRequest(TargetLosaIsNullMessage))
        }
        if(target.get("let").get != "null") {
          println(TargetLetIsNullMessage);
          halt(BadRequest(TargetLetIsNullMessage))
        }

      }
      case 2 => {
        //Source - all null
        //Target - not null
        println("Matched 2")
        if(source.get("tie").get != "null") {
          println(SourceTieIsNullMessage);
          halt(BadRequest(SourceTieIsNullMessage))
        }
        if(source.get("ajr").get != "null") {
          println(SourceAjrIsNullMessage);
          halt(BadRequest(SourceAjrIsNullMessage))
        }
        if(source.get("aosa").get != "null") {
          println(SourceAosaIsNullMessage);
          halt(BadRequest(SourceAosaIsNullMessage))
        }
        if(source.get("aet").get != "null") {
          println(SourceAetIsNullMessage);
          halt(BadRequest(SourceAetIsNullMessage))
        }
        if(source.get("losa").get != "null") {
          println(SourceLosaIsNullMessage);
          halt(BadRequest(SourceLosaIsNullMessage))
        }
        if(source.get("let").get != "null") {
          println(SourceLetIsNullMessage);
          halt(BadRequest(SourceLetIsNullMessage))
        }

        if(target.get("tie").get == "null") {
          println(TargetTieIsNullMessage);
          halt(BadRequest(TargetTieIsNullMessage))
        }
        if(target.get("ajr").get == "null") {
          println(TargetAjrIsNullMessage);
          halt(BadRequest(TargetAjrIsNullMessage))
        }
        if(target.get("aosa").get == "null") {
          println(TargetAosaIsNullMessage);
          halt(BadRequest(TargetAosaIsNullMessage))
        }
        if(target.get("aet").get == "null") {
          println(TargetAetIsNullMessage);
          halt(BadRequest(TargetAetIsNullMessage))
        }
        if(target.get("losa").get == "null") {
          println(TargetLosaIsNullMessage);
          halt(BadRequest(TargetLosaIsNullMessage))
        }
        if(target.get("let").get == "null") {
          println(TargetLetIsNullMessage);
          halt(BadRequest(TargetLetIsNullMessage))
        }
      }
      case 3 => {
        //Source - not null
        //Target - not null
        println("Matched 3")
        if(source.get("tie").get == "null") {
          println(SourceTieIsNullMessage);
          halt(BadRequest(SourceTieIsNullMessage))
        }
        if(source.get("ajr").get == "null") {
          println(SourceAjrIsNullMessage);
          halt(BadRequest(SourceAjrIsNullMessage))
        }
        if(source.get("aosa").get == "null") {
          println(SourceAosaIsNullMessage);
          halt(BadRequest(SourceAosaIsNullMessage))
        }
        if(source.get("aet").get == "null") {
          println(SourceAetIsNullMessage);
          halt(BadRequest(SourceAetIsNullMessage))
        }
        if(source.get("losa").get == "null") {
          println(SourceLosaIsNullMessage);
          halt(BadRequest(SourceLosaIsNullMessage))
        }
        if(source.get("let").get == "null") {
          println(SourceLetIsNullMessage);
          halt(BadRequest(SourceLetIsNullMessage))
        }

        if(target.get("tie").get == "null") {
          println(TargetTieIsNullMessage);
          halt(BadRequest(TargetTieIsNullMessage))
        }
        if(target.get("ajr").get == "null") {
          println(TargetAjrIsNullMessage);
          halt(BadRequest(TargetAjrIsNullMessage))
        }
        if(target.get("aosa").get == "null") {
          println(TargetAosaIsNullMessage);
          halt(BadRequest(TargetAosaIsNullMessage))
        }
        if(target.get("aet").get == "null") {
          println(TargetAetIsNullMessage);
          halt(BadRequest(TargetAetIsNullMessage))
        }
        if(target.get("losa").get == "null") {
          println(TargetLosaIsNullMessage);
          halt(BadRequest(TargetLosaIsNullMessage))
        }
        if(target.get("let").get == "null") {
          println(TargetLetIsNullMessage);
          halt(BadRequest(TargetLetIsNullMessage))
        }
      }
      case 4 => {
        //Source - not null
        //Target - not null
        println("Matched 4")
        if(source.get("tie").get == "null") {
          println(SourceTieIsNullMessage);
          halt(BadRequest(SourceTieIsNullMessage))
        }
        if(source.get("ajr").get == "null") {
          println(SourceAjrIsNullMessage);
          halt(BadRequest(SourceAjrIsNullMessage))
        }
        if(source.get("aosa").get == "null") {
          println(SourceAosaIsNullMessage);
          halt(BadRequest(SourceAosaIsNullMessage))
        }
        if(source.get("aet").get == "null") {
          println(SourceAetIsNullMessage);
          halt(BadRequest(SourceAetIsNullMessage))
        }
        if(source.get("losa").get == "null") {
          println(SourceLosaIsNullMessage);
          halt(BadRequest(SourceLosaIsNullMessage))
        }
        if(source.get("let").get == "null") {
          println(SourceLetIsNullMessage);
          halt(BadRequest(SourceLetIsNullMessage))
        }

        if(target.get("tie").get == "null") {
          println(TargetTieIsNullMessage);
          halt(BadRequest(TargetTieIsNullMessage))
        }
        if(target.get("ajr").get == "null") {
          println(TargetAjrIsNullMessage);
          halt(BadRequest(TargetAjrIsNullMessage))
        }
        if(target.get("aosa").get == "null") {
          println(TargetAosaIsNullMessage);
          halt(BadRequest(TargetAosaIsNullMessage))
        }
        if(target.get("aet").get == "null") {
          println(TargetAetIsNullMessage);
          halt(BadRequest(TargetAetIsNullMessage))
        }
        if(target.get("losa").get == "null") {
          println(TargetLosaIsNullMessage);
          halt(BadRequest(TargetLosaIsNullMessage))
        }
        if(target.get("let").get == "null") {
          println(TargetLetIsNullMessage);
          halt(BadRequest(TargetLetIsNullMessage))
        }
      }
      case 5 => {
        //Source - not null
        //Target - all null
        println("Matched 5")
        if(source.get("tie").get == "null") {
          println(SourceTieIsNullMessage);
          halt(BadRequest(SourceTieIsNullMessage))
        }
        if(source.get("ajr").get == "null") {
          println(SourceAjrIsNullMessage);
          halt(BadRequest(SourceAjrIsNullMessage))
        }
        if(source.get("aosa").get == "null") {
          println(SourceAosaIsNullMessage);
          halt(BadRequest(SourceAosaIsNullMessage))
        }
        if(source.get("aet").get == "null") {
          println(SourceAetIsNullMessage);
          halt(BadRequest(SourceAetIsNullMessage))
        }
        if(source.get("losa").get == "null") {
          println(SourceLosaIsNullMessage);
          halt(BadRequest(SourceLosaIsNullMessage))
        }
        if(source.get("let").get == "null") {
          println(SourceLetIsNullMessage);
          halt(BadRequest(SourceLetIsNullMessage))
        }

        if(target.get("tie").get != "null") {
          println(TargetTieIsNullMessage);
          halt(BadRequest(TargetTieIsNullMessage))
        }
        if(target.get("ajr").get != "null") {
          println(TargetAjrIsNullMessage);
          halt(BadRequest(TargetAjrIsNullMessage))
        }
        if(target.get("aosa").get != "null") {
          println(TargetAosaIsNullMessage);
          halt(BadRequest(TargetAosaIsNullMessage))
        }
        if(target.get("aet").get != "null") {
          println(TargetAetIsNullMessage);
          halt(BadRequest(TargetAetIsNullMessage))
        }
        if(target.get("losa").get != "null") {
          println(TargetLosaIsNullMessage);
          halt(BadRequest(TargetLosaIsNullMessage))
        }
        if(target.get("let").get != "null") {
          println(TargetLetIsNullMessage);
          halt(BadRequest(TargetLetIsNullMessage))
        }
      }
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
