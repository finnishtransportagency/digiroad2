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

  val trafficVolume: Seq[Map[String, Any]] ={
    Seq(
      Map(
      "tietolaji" -> "tl201",          //Field code
      "tie" -> 45,                     //Road number
      "osa" -> 1,                      //Road part number
      "aet" -> 0,                      //Start distance
      "let" -> 100,                    //End distance
      "KTV" -> 1                       //placeholder value for traffic volume
    ),
      Map(
      "tietolaji" -> "tl201",          //Field code
      "tie" -> 5,                     //Road number
      "osa" -> 201,                      //Road part number
      "aet" -> 0,                      //Start distance
      "let" -> 12.267,                    //End distance
      "KTV" -> 224                       //placeholder value for traffic volume
    ),
      Map(
      "tietolaji" -> "tl201",          //Field code
      "tie" -> 5,                     //Road number
      "osa" -> 201,                      //Road part number
      "aet" -> 661,                      //Start distance
      "let" -> 677.667,                    //End distance
      "KTV" -> 223                       //placeholder value for traffic volume
      )
    )
  }

  val trafficVolumeWithRequiredParameters: Map[String, Any] ={
    Map(
      "tietolaji" -> "tl201",          //Field code
      "tie" -> 45                      //Road number
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

    val ktvValues = trafficVolume.map( tv  =>  tv.keys)
    //TODO Check if mandatory fields are filled
    //    if( ! mandatoryTrFields.filterNot(mf => ktvValues.exists(tv => tv == mf)).isEmpty) {
    //      halt(BadRequest("Malformed 'traffic volume' parameter"))
    //    }
    trafficVolume.filter(x => x.getOrElse("tie", 0) == params("roadNumber").toInt)
   // Seq(trafficVolume)
  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber/:roadPartNumber") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))

    val ktvValues = trafficVolume.map( tv  =>  tv.keys)

    if( ! mandatoryTrFields.filterNot(mf => ktvValues.exists(tv => tv == mf)).isEmpty) {
      halt(BadRequest("Malformed 'traffic volume' parameter"))
    }
    trafficVolume.filter(x => x.getOrElse("tie", 0) == params("roadNumber") && x.getOrElse("osa", 0) == params("roadPartNumber"))

   // Seq(trafficVolume)
  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber/:roadPartNumber/:startDistance") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))

    val ktvValues = trafficVolume.map( tv  =>  tv.keys)

    if( ! mandatoryTrFields.filterNot(mf => ktvValues.exists(tv => tv == mf)).isEmpty) {
      halt(BadRequest("Malformed 'traffic volume' parameter"))
    }
    trafficVolume.filter(x => x.getOrElse("tie", 0) == params("roadNumber") && x.getOrElse("osa", 0) == params("roadPartNumber") && x.getOrElse("aet", 0) == params("startDistance"))
//    Seq(trafficVolume)
  }

  get("/trrest/tietolajit/:fieldCode/:roadNumber/:roadPartNumber/:startDistance/:endPart/:endDistance") {
    //Checks that header contains X-OTH-Authorization attribute with correct base64 value
    if (!request.headers.exists(_==("X-OTH-Authorization","Basic dXNlclhZWjpwYXNzd29yZFhZWg=="))) halt(BadRequest("401 Unauthorized"))

    val ktvValues = trafficVolume.map( tv  =>  tv.keys)

    if( ! mandatoryTrFields.filterNot(mf => ktvValues.exists(tv => tv == mf)).isEmpty) {
      halt(BadRequest("Malformed 'traffic volume' parameter"))
    }
    trafficVolume.filter(x => x.getOrElse("tie", 0) == params("roadNumber") && x.getOrElse("osa", 0) == params("roadPartNumber") && x.getOrElse("aet", 0) == params("startDistance") && x.getOrElse("let", 0) == params("endDistance"))
//    Seq(trafficVolume)
  }

}

