package fi.liikennevirasto.digiroad2

import org.apache.commons.logging.LogFactory
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport

import scala.util.parsing.json.JSON

class TierekisteriTestApi extends ScalatraServlet with JacksonJsonSupport {

  override protected implicit def jsonFormats: Formats = DefaultFormats

  lazy val logger = LogFactory.getLog(getClass)

  before() {
    contentType = formats("json")
  }

  def mandatoryFields = Seq("valtakunnallinen_id", "livitunnus", "tie", "aosa", "puoli", "ajr", "aet",
    "pikavuoro", "kayttajatunnus", "pysakin_tyyppi", "inventointipvm")

  def mandatoryTrFields = Seq("tietolaji", "tie")

  val massTransitStop: Map[String, Any] ={
    Map(
      "valtakunnallinen_id" -> 208914,
      "tie" -> 25823,
      "aosa" -> 104,
      "ajr" -> 0,
      "aet" -> 150,
      "puoli" -> "oikea",
      "pysakin_tunnus" -> "681",
      "nimi_fi" -> "Raisionjoki",
      "pysakin_tyyppi" -> "paikallis",
      "pikavuoro" -> "ei",
      "alkupvm" -> "2016-01-01",
      "loppupvm" -> "2016-01-02",
      "lakkautuspvm" -> "2016-01-03",
      "livitunnus" -> "OTHJ208914",
      "nimi_se" -> "Reso å",
      "varusteet" -> getEquipments,
      "kayttajatunnus" -> "KX123456",
      "inventointipvm" -> "2016-09-01"
    )
  }

  val massTransitStopWithOnlyMandatoryParameters: Map[String, Any] ={
    Map(
      "livitunnus" -> "OTHJ208910",
      "tie" -> 25823,
      "aosa" -> 104,
      "ajr" -> 0,
      "aet" -> 150,
      "puoli" -> "oikea",
      "pysakin_tyyppi" -> "kauko",
      "pikavuoro" -> "ei",
      "valtakunnallinen_id" -> 208910,
      "kayttajatunnus" -> "KX123456",
      "inventointipvm" -> "2015-01-01"
    )
  }

  def getEquipments():Map[String, Any] = {
    Map(
      "aikataulu" -> "ei_tietoa",
      "penkki" -> "ei_tietoa",
      "pyorateline" -> "ei_tietoa",
      "sahk_aikataulu" -> "ei_tietoa",
      "roskis" -> "ei_tietoa",
      "katos" -> "on",
      "mainoskatos" -> "ei",
      "saattomahd" -> "ei",
      "korotus" -> "on",
      "valaistus" -> "ei_tietoa"
    )
  }

  val trafficVolumeTRCode = "tl201"
  val lightingTRCode = "tl167"

  val trafficVolume: Map[String,List[Map[String, Any]]] ={
    Map(
      "Data" ->
        List(
          Map(
              "TIETOLAJI" -> trafficVolumeTRCode,   //Field code
              "TIE" -> 45,                          //Road number
              "OSA" -> 1,                           //Road part number
              "ETAISYYS" -> 0,                      //Start distance
              "LET" -> 0,                           //End distance
              "KVL" -> 1                            //placeholder value for traffic volume
            )
        )
    )
  }

  val trafficVolumeWithRequiredParameters: Map[String, Any] ={
    Map(
      "tietolaji" -> "tl201",          //Field code
      "tie" -> 45                      //Road number
    )
  }

  val lighting: Map[String,List[Map[String, Any]]] ={
    Map(
      "Data" ->
        List(
          Map(
            "TIETOLAJI" -> lightingTRCode,   //Field code
            "TIE" -> 45,                     //Road number
            "OSA" -> 1,                      //Road part number
            "ETAISYYS" -> 0,                 //Start distance
            "LET" -> 0                       //End distance
          )
        )
    )
  }

  def headerContainsAuth(header: Map[String, String]): Boolean = {
    //Checks that header contains X-OTH-Authorization and X-Authorization attribute with correct base64 value
    (header.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg==")) && header.exists(_==("X-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg==")))
  }

  get("/pysakit/:id"){
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))
    val liviId = params("id")
    if (liviId == "OTHJ208910")
      massTransitStopWithOnlyMandatoryParameters
    else if(liviId == "OTHJ208914")
      massTransitStop
    else if(liviId.startsWith("OTHJ"))
      massTransitStop
    else
      halt(NotFound())
  }

  get("/pysakit/") {
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))
    List(
      massTransitStop,
      massTransitStopWithOnlyMandatoryParameters
    )
  }

  put("/pysakit/:liviId") {
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))
    val liviId = params("liviId")
    if (liviId == "OTHJ20891499999999") {
      halt(BadRequest("Invalid 'mass transit stop' value for a field"))
    } else if (liviId == "OTHJ20891499Err") {
      halt(InternalServerError("Error in Tierekisteri System"))
    }

    val body = parsedBody.extract[Map[String, Any]]

    mandatoryFields.foreach { field =>
      body.get(field).getOrElse(halt(BadRequest("Malformed 'mass transit stop' parameter")))
    }


    halt(NoContent())
  }

  post("/pysakit/"){
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))

    val body = parsedBody.extract[Map[String, Any]]

    mandatoryFields.foreach{ field  =>
      body.get(field).getOrElse(halt(BadRequest("Malformed 'mass transit stop' parameter")))
    }

    val liviId =  body.get("livitunnus").get.toString

    if(liviId == "OTHJ208916")
      halt(Conflict())

    if(liviId.length > 10)
      halt(BadRequest())

    halt(NoContent())
  }

  delete("/pysakit/:liviId"){
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))
    val liviId = params("liviId")

    if(liviId != "OTHJ208914")
      halt(NotFound())

    halt(NoContent())
  }

  get("/tietolajit/:fieldCode/:roadNumber") {
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))

    val fieldCode = params("fieldCode")
    if (fieldCode == trafficVolumeTRCode) {
      trafficVolume
    } else if (fieldCode == lightingTRCode) {
      lighting
    }
  }

  get("/tietolajit/:fieldCode/:roadNumber/:roadPartNumber") {
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))

    val fieldCode = params("fieldCode")
    if (fieldCode == trafficVolumeTRCode) {
      trafficVolume
    } else if (fieldCode == lightingTRCode) {
      lighting
    }
  }

  get("/tietolajit/:fieldCode/:roadNumber/:roadPartNumber/:startDistance") {
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))

    val fieldCode = params("fieldCode")
    if (fieldCode == trafficVolumeTRCode) {
      trafficVolume
    } else if (fieldCode == lightingTRCode) {
      lighting
    }
  }

  get("/tietolajit/:fieldCode/:roadNumber/:roadPartNumber/:startDistance/:endPart/:endDistance") {
    if(!headerContainsAuth(request.headers)) halt(BadRequest("401 Unauthorized"))

    val fieldCode = params("fieldCode")
    if (fieldCode == trafficVolumeTRCode) {
      trafficVolume
    } else if (fieldCode == lightingTRCode) {
      lighting
    }
  }
}

