package fi.liikennevirasto.viite
import java.util.Properties

import fi.liikennevirasto.digiroad2.util.TierekisteriAuthPropertyReader
import fi.liikennevirasto.viite.dao.ProjectRoadAddressChange
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, StreamInput}
import org.json4s.Formats
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.json4s.jackson.JsonMethods.parse

import scala.util.control.NonFatal
case class changepPoject(id:Long, name:String, user:String, ely:Long, change_date:String, change_info:Seq[changeInfoitem])
case class trPojectStatus(id:Option[Long],id_tr_projekti:Option[Long],projekti:Option[Long],tunnus:Option[Long],status:Option[String],name:Option[String],change_date:Option[String],ely:Option[Int],muutospvm:Option[String],user:Option[String],published_date:Option[String],job_number:Option[Long],error_message:Option[String],start_time:Option[String],end_time:Option[String],error_code:Option[Int])
case class changeInfoitem(changetype :Int, continuity:Int, road_type:Int, source:changeInfoRoadParts,target:changeInfoRoadParts)
case class changeInfoRoadParts(tie :Option[Long], ajr:Option[Long], aosa:Option[Long],aet:Option[Double],losa:Option[Long], let:Option[Double])  //roadnumber,track,roadparts beggining, start_part_M,roadpart_end, end_part_M
case class ProjectChangeStatus(projectId: Long, status: Int, reason: String)

object ViiteTierekisteriClient {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  private def getRestEndPoint: String = {
    val loadedKeyString = properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint")
    println("viite-endpoint = "+loadedKeyString)
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TierekisteriViiteRestApiEndPoint")
    loadedKeyString
  }

  def sendRoadAddressChangeData(changeData: List[ProjectRoadAddressChange]) = {
    val projects = changeData.map(cd => {
      convertChangeDataToChangepPoject(cd)
    })
    val messageList =  projects.map(p =>{
      sendJsonMessage(p)
    })
    messageList
  }

  private def convertChangeDataToChangepPoject(changeData: ProjectRoadAddressChange): changepPoject = {
    val source = changeInfoRoadParts(changeData.changeInfo.source.roadNumber, changeData.changeInfo.source.trackCode, changeData.changeInfo.source.startRoadPartNumber, changeData.changeInfo.source.startAddressM, changeData.changeInfo.source.endRoadPartNumber, changeData.changeInfo.source.endAddressM)
    val target = changeInfoRoadParts(changeData.changeInfo.target.roadNumber, changeData.changeInfo.target.trackCode, changeData.changeInfo.target.startRoadPartNumber, changeData.changeInfo.target.startAddressM, changeData.changeInfo.target.endRoadPartNumber, changeData.changeInfo.target.endAddressM)
    val changeInfo = changeInfoitem(changeData.changeInfo.changeType.value, changeData.changeInfo.discontinuity.value, changeData.changeInfo.roadType.value, source, target)
    changepPoject(changeData.projectId, changeData.projectName.getOrElse(""), changeData.user, changeData.ely,DateTimeFormat.forPattern("yyyy-MM-DD").print(changeData.changeDate), Seq(changeInfo))
  }

  private val auth = new TierekisteriAuthPropertyReader

  private val client = HttpClientBuilder.create().build

  def createJsonmessage(trProject:changepPoject) = {
    implicit val formats = DefaultFormats
    val jsonObj = Map(
      "id" -> trProject.id,
      "name" -> trProject.name,
      "user" -> trProject.user,
      "ely" -> trProject.ely,
      "change_date" -> trProject.change_date,
      "change_info" -> {
        trProject.change_info.map(changeInfo =>
          Map(
            "change_type" -> changeInfo.changetype,
            "continuity" -> changeInfo.continuity,
            "road_type" -> changeInfo.road_type,
            "source" -> Map(
              "tie" -> changeInfo.source.tie.getOrElse("null"),
              "ajr" -> changeInfo.source.ajr.getOrElse("null"),
              "aosa" -> changeInfo.source.aosa.getOrElse("null"),
              "aet" -> changeInfo.source.aet.getOrElse("null"),
              "losa" -> changeInfo.source.losa.getOrElse("null"),
              "let" -> changeInfo.source.let.getOrElse("null")
            ),
            "target" -> Map(
              "tie" -> changeInfo.target.tie.getOrElse("null"),
              "ajr" -> changeInfo.target.ajr.getOrElse("null"),
              "aosa" -> changeInfo.target.aosa.getOrElse("null"),
              "aet" -> changeInfo.target.aet.getOrElse("null"),
              "losa" -> changeInfo.target.losa.getOrElse("null"),
              "let" -> changeInfo.target.let.getOrElse("null")
            )
          )
        )
      }
    )
    val json = Serialization.write(jsonObj)
    new StringEntity(json, ContentType.APPLICATION_JSON)
  }

  def sendJsonMessage(trProject:changepPoject): ProjectChangeStatus ={
    val request = new HttpPost(getRestEndPoint+"addresschange/")
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
    request.setEntity(createJsonmessage(trProject))
    val response = client.execute(request)
    val statusCode = response.getStatusLine.getStatusCode
    val reason = response.getStatusLine.getReasonPhrase
    ProjectChangeStatus(trProject.id, statusCode, reason)
  }

  def getProjectStatus(projectid:String): Map[String,Any] =
  {
    implicit val formats = DefaultFormats
    val request = new HttpGet(getRestEndPoint+"addresschange/"+projectid)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
    val response = client.execute(request)
    val receivedData=parse(StreamInput(response.getEntity.getContent)).extract[trPojectStatus]
    Map(
      "id"->receivedData.id,
      "id_tr_projekti"-> receivedData.id_tr_projekti.getOrElse("null"),
      "projekti"-> receivedData.projekti.getOrElse("null"),
      "tunnus"->receivedData.tunnus.getOrElse("null"),
      "status"->receivedData.status.getOrElse("null"),
      "name"-> receivedData.name.getOrElse("null"),
      "change_date"->receivedData.change_date.getOrElse("null"),
      "ely"->receivedData.ely.getOrElse("null"),
      "muutospvm"->receivedData.muutospvm.getOrElse("null"),
      "user"->receivedData.user.getOrElse("null"),
      "published_date"->receivedData.published_date.getOrElse("null"),
      "job_number"->receivedData.job_number.getOrElse("null"),
      "error_message"->receivedData.error_message.getOrElse("null"),
      "start_time"->receivedData.start_time.getOrElse("null"),
      "end_time"->receivedData.end_time.getOrElse("null"),
      "error_code"->receivedData.error_code.getOrElse("null")
    )

  }

  def getProjectStatusObject(projectid:String): Option[trPojectStatus] = {

    implicit val formats = DefaultFormats
    val request = new HttpGet(getRestEndPoint + "addresschange/" + projectid)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)

    val response = client.execute(request)
    try {
     val  receivedData = parse(StreamInput(response.getEntity.getContent)).extract[trPojectStatus]
    response.close()
      return Option(receivedData)
    } catch {
      case NonFatal(e) => None
    }finally {
    response.close()
  }
  }
}