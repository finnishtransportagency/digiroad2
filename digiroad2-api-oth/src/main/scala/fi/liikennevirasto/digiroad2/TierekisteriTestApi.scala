package fi.liikennevirasto.digiroad2

import org.apache.commons.logging.LogFactory
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport

class TierekisteriTestApi extends ScalatraServlet with JacksonJsonSupport {

  override protected implicit def jsonFormats: Formats = DefaultFormats

  lazy val logger = LogFactory.getLog(getClass)

  before() {
    contentType = formats("json")
  }

  def mandatoryFields = Seq("valtakunnallinen_id", "livitunnus", "tie", "aosa", "puoli", "ajr", "aet",
    "pikavuoro", "kayttajatunnus", "pysakin_tyyppi", "inventointipvm")

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

  val trafficVolume: Map[String, Any] ={
    Map(
      "tietolaji" -> "tl201",          //Field code
      "tie" -> 45,                     //Road number
      "osa" -> 1,                      //Road part number
      "aet" -> 0,                      //Start distance
      "let" -> 100,                    //End distance
      "KTV" -> 1                       //placeholder value for traffic volume
    )
  }

  get("/pysakit/:id"){
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
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
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
    List(
      massTransitStop,
      massTransitStopWithOnlyMandatoryParameters
    )
  }

  put("/pysakit/:liviId") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
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
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
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
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
    val liviId = params("liviId")

    if(liviId != "OTHJ208914")
      halt(NotFound())

    halt(NoContent())
  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
    val fieldCode = params("fieldCode")
    val roadNumber = params("roadNumber")
    if(fieldCode == trafficVolume.get("tietolaji") && roadNumber == trafficVolume.get("tie") )
      trafficVolume.get("KTV")

  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber/:roadPartNumber") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
    val fieldCode = params("fieldCode")
    val roadNumber = params("roadNumber")
    val roadPartNumber = params("roadPartNumber")

    trafficVolume.get("KTV")

  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber/:roadPartNumber/:startDistance") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
    val fieldCode = params("fieldCode")
    val roadNumber = params("roadNumber")
    val roadPartNumber = params("roadPartNumber")
    val startDistance = params("startDistance")

    trafficVolume.get("KTV")

  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber/:roadPartNumber/:startDistance/:endPart/:endDistance") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))
    val fieldCode = params("fieldCode")
    val roadNumber = params("roadNumber")
    val roadPartNumber = params("roadPartNumber")
    val startDistance = params("startDistance")
    val endDistance = params("endDistance")

    trafficVolume.get("KTV")

  }

}

