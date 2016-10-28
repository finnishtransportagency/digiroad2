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

  get("/pysakit/:id"){
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
    List(
      massTransitStop,
      massTransitStopWithOnlyMandatoryParameters
    )
  }

  put("/pysakit/:liviId") {
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
    val liviId = params("liviId")

    if(liviId != "OTHJ208914")
      halt(NotFound())

    halt(NoContent())
  }
}

