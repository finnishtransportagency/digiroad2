package fi.liikennevirasto.viite
import java.util.Properties

import fi.liikennevirasto.digiroad2.util.TierekisteriAuthPropertyReader
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.Serialization
import org.json4s.DefaultFormats



case class changepPoject(id:Long, name:String, user:String, ely:Int, change_date:String, change_info:Seq[changeInfoitem])
case class changeInfoitem(changetype :Int, continuity:Int, road_type:Int, source:changeInfoRoadParts,target:changeInfoRoadParts)
case class changeInfoRoadParts(tie :Option[Long], ajr:Option[Long], aosa:Option[Long],aet:Option[Double],losa:Option[Long], let:Option[Double])  //roadnumber,track,roadparts beggining, start_part_M,roadpart_end, end_part_M

class ViiteTierekisteriClient() {


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


  private val auth = new TierekisteriAuthPropertyReader

  private val client =HttpClientBuilder.create().build

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

def sendJsonMessage(trProject:changepPoject): (Int,String) ={
  val request = new HttpPost(getRestEndPoint+"addresschange/")
  request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
  request.setEntity(createJsonmessage(trProject))
 // request.setEntity(createJsonmessage(changepPoject(8912, "Testproject", "TestUser", 3, "2017-06-01", Seq {changeInfoitem(2, 1, 1, changeInfoRoadParts(None, None, None, None, None, None), changeInfoRoadParts(Option(403), Option(0), Option(8), Option(0), Option(8), Option(1001)))})))
  val response = client.execute(request)
  val statusCode = response.getStatusLine.getStatusCode
  val reason = response.getStatusLine.getReasonPhrase
  (statusCode,reason)
}

def getprojectstatus(projectid:String): String =
  {
    val request = new HttpGet(getRestEndPoint+"addresschange/"+projectid)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
    val response = client.execute(request)
    response.toString
  }
}