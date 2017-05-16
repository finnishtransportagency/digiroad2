package fi.liikennevirasto.viite


import fi.liikennevirasto.digiroad2.util.TierekisteriAuthPropertyReader
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
/**
  * Created by alapeijario on 15.5.2017.
  */
case class changeproject(id:Long, name:String, user:String, ely:Int, change_date:String, change_info:Seq[changeInfoitem])
case class changeInfoitem(changetype :Int, continuity:Int, road_type:Int, source:changeInfoRoadParts,target:changeInfoRoadParts)
case class changeInfoRoadParts(tie :Long, ajr:Long, aosa:Long,aet:Double,losa:Long, let:Double)  //roadnumber,track,roadparts beggining, start_part_M,roadpart_end, end_part_M

class ViiteTierekisteriClient() {
  private val auth = new TierekisteriAuthPropertyReader
  private val trEndPointURL="http://172.17.204.33:8013/trrest/addresschange/"

  private val client =HttpClientBuilder.create().build

  def createJsonmessage() = {
    /*  val lines = fromFile("digiroad2-oracle\\conf\\dev\\testjson.txt").mkString

          val testChanage = changeInfoitem(2, 1, 1, (changeInfoRoadParts(None, None,None, None, None, None)), changeInfoRoadParts(1742, 0, 1, 0, 1, 1000))
          val testdata = changeproject(13253, "test", "user", 1, "2017-06-01", Seq {
            testChanage
          })

          val jsonObj = Map (
          "id" -> testdata.id,
          "name" -> testdata.name,
          "user" -> testdata.user,
            "ely" -> testdata.ely,
            "change_data" -> testdata.change_date
            "change_info"{ testdata.change_info.map(
              "change_type"-> Seq(
                for (change<-
                Map(

                )
              )
            )



          val json = Serialization.write(jsonObj)
          new StringEntity(json, ContentType.APPLICATION_JSON)
   new StringEntity(lines, ContentType.APPLICATION_JSON)
*/
    new StringEntity("", ContentType.APPLICATION_JSON)

  }


def sendJsonMessage(): String ={
  val request = new HttpPost(trEndPointURL)
  request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
  request.setEntity(createJsonmessage())
  val response = client.execute(request)
""
}


def getprojectstatus(projectid:String): String =
  {
    val request = new HttpGet(trEndPointURL+projectid)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
    val response = client.execute(request)
    response.toString
  }

}









