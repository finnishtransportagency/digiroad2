package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

import fi.liikennevirasto.viite.RoadType.PublicRoad
import fi.liikennevirasto.viite.dao.Discontinuity.Continuous
import fi.liikennevirasto.viite.dao._
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, StreamInput, StringInput}
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by alapeijario on 17.5.2017.
  */
class ViiteTierekisteriClientSpec extends FunSuite with Matchers {

  val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  val defaultChangeInfo=RoadAddressChangeInfo(AddressChangeType.apply(2),
    RoadAddressChangeSection(None, None, None, None, None, None, None, None, None),
    RoadAddressChangeSection(Option(403), Option(0), Option(8), Option(0), Option(8), Option(1001),
      Option(RoadType.PublicRoad), Option(Discontinuity.Continuous), Option(5)), Discontinuity.apply(1), RoadType.apply(1), false)

  def getRestEndPoint: String = {
    val loadedKeyString = dr2properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint", "http://localhost:8080/api/tierekisteri/")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TierekisteriViiteRestApiEndPoint")
    loadedKeyString
  }

  private def testConnection: Boolean = {
    // If you get NPE here, you have not included main module (digiroad2) as a test dependency to digiroad2-viite
    val url = dr2properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint", "http://localhost:8080/api/tierekisteri/")
    val request = new HttpGet(url)
    request.setConfig(RequestConfig.custom().setConnectTimeout(2500).build())
    val client = HttpClientBuilder.create().build()
    try {
      val response = client.execute(request)
      try {
        response.getStatusLine.getStatusCode >= 200
      } finally {
        response.close()
      }
    } catch {
      case e: HttpHostConnectException =>
        false
      case e: ConnectTimeoutException =>
        false
      case e: ConnectException =>
        false
    }
  }

  test("TR-connection Create test") {
    assume(testConnection)
    val message= ViiteTierekisteriClient.sendJsonMessage(ChangeProject(0, "Testproject", "TestUser", 3, "2017-06-01", Seq {
      defaultChangeInfo // projectid 0 wont be added to TR
    }))
    message.projectId should be (0)
    message.status should be (201)
    message.reason should startWith ("Created")
  }

  test("Get project status from TR") {
    assume(testConnection)
    val response = ViiteTierekisteriClient.getProjectStatus(0)
    response == null should be (false)
  }

  test("Check that project_id is replaced with tr_id attribute") {
    val change=ViiteTierekisteriClient.convertToChangeProject(List(ProjectRoadAddressChange(100L, Some("testproject"), 1, "user", DateTime.now(),defaultChangeInfo,DateTime.now(),Some(2))))
    change.id should be (2)
  }
  test("parse changeinforoadparts from json") {
    val string = "{" +
      "\"tie\": 1," +
      "\"ajr\": 0," +
      "\"aosa\": 10," +
      "\"aet\":0," +
      "\"losa\": 10," +
      "\"let\": 1052" +
      "}"
    implicit val formats = DefaultFormats + ChangeInfoRoadPartsSerializer
    val cirp = parse(StringInput(string)).extract[RoadAddressChangeSection]
    cirp.roadNumber should be (Some(1))
    cirp.trackCode should be (Some(0))
    cirp.startRoadPartNumber should be (Some(10))
    cirp.startAddressM should be (Some(0))
    cirp.endRoadPartNumber should be (Some(10))
    cirp.endAddressM should be (Some(1052))
  }

  test("parse TRProjectStatus to and from json") {
    implicit val formats = DefaultFormats + TRProjectStatusSerializer
    val trps = TRProjectStatus(Some(1), Some(2), Some(3), Some(4), Some("status"), Some("name"), Some("change_date"),
      Some(5), Some("muutospvm"), Some("user"), Some("published_date"), Some(6), Some("error_message"), Some("start_time"),
      Some("end_time"), Some(7))
    val string = Serialization.write(trps).toString
    val trps2 = parse(StringInput(string)).extract[TRProjectStatus]
    trps2 should be (trps)
  }

  test("Parse example messages TRStatus") {
    implicit val formats = DefaultFormats + TRProjectStatusSerializer
    val string = "{\n\"id_tr_projekti\": 1162,\n\"projekti\": 1731,\n\"id\": 13255,\n\"tunnus\": 5,\n\"status\": \"T\"," +
      "\n\"name\": \"test\",\n\"change_date\": \"2017-06-01\",\n\"ely\": 1,\n\"muutospvm\": \"2017-05-15\",\n\"user\": \"user\"," +
      "\n\"published_date\": \"2017-05-15\",\n\"job_number\": 28,\n\"error_message\": null,\n\"start_time\": \"2017-05-15\"," +
      "\n\"end_time\": \"2017-05-15\",\n\"error_code\": 0\n}"
    parse(StringInput(string)).extract[TRProjectStatus]
  }
  test("Parse example messages ChangeInfo") {
    implicit val formats = DefaultFormats + TRProjectStatusSerializer + ChangeInfoItemSerializer + ChangeProjectSerializer
    val string = "{\n\t\"id\": 8914,\n\t\"name\": \"Numerointi\",\n\t\"user\": \"user\",\n\t\"ely\": 9,\n\t\"change_date\": \"2017-06-01\"," +
      " \n\t\"change_info\":[ {\n\t\t\"change_type\": 4,\n\t\t\"source\": {\n\t\t\t\"tie\": 11007," +
      "\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 2,\n\t\t\t\"aet\": 0,\n\t\t\t\"losa\": 2,\n\t\t\t\"let\": 1895\n\t\t}," +
      "\n\t\t\"target\": {\n\t\t\t\"tie\": 11007,\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 1,\n\t\t\t\"aet\": 3616," +
      "\n\t\t\t\"losa\": 1,\n\t\t\t\"let\": 5511\n\t\t},\n\t\t\"continuity\": 5,\n\t\t\"road_type\": 821\n\t}]\n}"
    parse(StringInput(string)).extract[ChangeProject]
  }

  test("parse change project object back and forth") {
    implicit val formats = DefaultFormats + TRProjectStatusSerializer + ChangeInfoItemSerializer + ChangeProjectSerializer
    val string = "{\n\t\"id\": 8914,\n\t\"name\": \"Numerointi\",\n\t\"user\": \"user\",\n\t\"ely\": 9,\n\t\"change_date\": \"2017-06-01\"," +
      " \n\t\"change_info\":[ {\n\t\t\"change_type\": 4,\n\t\t\"source\": {\n\t\t\t\"tie\": 11007," +
      "\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 2,\n\t\t\t\"aet\": 0,\n\t\t\t\"losa\": 2,\n\t\t\t\"let\": 1895\n\t\t}," +
      "\n\t\t\"target\": {\n\t\t\t\"tie\": 11007,\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 1,\n\t\t\t\"aet\": 3616," +
      "\n\t\t\t\"losa\": 1,\n\t\t\t\"let\": 5511\n\t\t},\n\t\t\"continuity\": 5,\n\t\t\"road_type\": 821\n\t}]\n}"
    val parsedProject = parse(StringInput(string)).extract[ChangeProject]
    val reparsed = parse(StreamInput(ViiteTierekisteriClient.createJsonMessage(parsedProject).getContent)).extract[ChangeProject]
    parsedProject should be (reparsed)
    reparsed.id should be (8914)
    reparsed.changeDate should be ("2017-06-01")
    reparsed.changeInfoSeq should have size (1)
  }

  test("multiple changes in project") {
    implicit val formats = DefaultFormats + TRProjectStatusSerializer + ChangeInfoItemSerializer + ChangeProjectSerializer
    val string = "{\n\t\"id\": 8914,\n\t\"name\": \"Numerointi\",\n\t\"user\": \"user\",\n\t\"ely\": 9,\n\t\"change_date\": \"2017-06-01\"," +
      " \n\t\"change_info\":[ {\n\t\t\"change_type\": 4,\n\t\t\"source\": {\n\t\t\t\"tie\": 11007," +
      "\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 2,\n\t\t\t\"aet\": 0,\n\t\t\t\"losa\": 2,\n\t\t\t\"let\": 1895\n\t\t}," +
      "\n\t\t\"target\": {\n\t\t\t\"tie\": 11007,\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 1,\n\t\t\t\"aet\": 3616," +
      "\n\t\t\t\"losa\": 1,\n\t\t\t\"let\": 5511\n\t\t},\n\t\t\"continuity\": 5,\n\t\t\"road_type\": 821\n\t}," +
      "{\n\t\t\"change_type\": 4,\n\t\t\"source\": {\n\t\t\t\"tie\": 11007," +
      "\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 3,\n\t\t\t\"aet\": 0,\n\t\t\t\"losa\": 3,\n\t\t\t\"let\": 95\n\t\t}," +
      "\n\t\t\"target\": {\n\t\t\t\"tie\": 11007,\n\t\t\t\"ajr\": 0,\n\t\t\t\"aosa\": 1,\n\t\t\t\"aet\": 5511," +
      "\n\t\t\t\"losa\": 1,\n\t\t\t\"let\": 5606\n\t\t},\n\t\t\"continuity\": 1,\n\t\t\"road_type\": 821\n\t}"+
      "]\n}"
    val parsedProject = parse(StringInput(string)).extract[ChangeProject]
    val reparsed = parse(StreamInput(ViiteTierekisteriClient.createJsonMessage(parsedProject).getContent)).extract[ChangeProject]
    parsedProject should be (reparsed)
    reparsed.changeInfoSeq should have size (2)
    val part2 = reparsed.changeInfoSeq.find(_.source.startRoadPartNumber.get == 2)
    val part3 = reparsed.changeInfoSeq.find(_.source.startRoadPartNumber.get == 3)
    part2.nonEmpty should be (true)
    part3.nonEmpty should be (true)
  }
}
