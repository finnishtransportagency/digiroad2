package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, PersistedLinearAsset, Properties}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

class MaintenanceRoadApi(val linearAssetService: LinearAssetService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  private val Kayttooikeus = "huoltotie_kayttooikeus"
  private val Huoltovastuu = "huoltotie_huoltovastuu"
  private val Tiehoitokunta = "huoltotie_tiehoitokunta"
  private val Nimi = "huoltotie_nimi"
  private val Osoite = "huoltotie_osoite"
  private val Postinumero = "huoltotie_postinumero"
  private val Postitoimipaikka = "huoltotie_postitoimipaikka"
  private val Puh1 = "huoltotie_puh1"
  private val Puh2 = "huoltotie_puh2"
  private val Lisatieto = "huoltotie_lisatieto"

  protected implicit def jsonFormats: Formats = DefaultFormats
  val typeId = LinearAssetTypes.MaintenanceRoadAssetTypeId

  private val trKayttooikeus = "kayttooikeus"
  private val trHuoltovastuu = "huoltovastuu"
  private val trTiehoitokunta = "tiehoitokunta"
  private val trNimi = "nimi"
  private val trOsoite = "osoite"
  private val trPostinumero = "postinumero"
  private val trPostitoimipaikka = "postitoimipaikka"
  private val trPuh1 = "puh1"
  private val trPuh2 = "puh2"
  private val trLisatieto = "lisatieto"

  get("/huoltotiet") {
    val maintenanceAsset = linearAssetService.getActiveMaintenanceRoad()
    createJson(maintenanceAsset)
  }

  get("/huoltotiet/:areaId"){
    var areaId = params("areaId")
    val maintenanceAsset = linearAssetService.getActiveMaintenanceRoadByPolygon(areaId, typeId)
    createJson(maintenanceAsset)
  }

  private def createJson(maintenanceAsset: Seq[PersistedLinearAsset]) = {
    maintenanceAsset.map {
      asset => createMap(asset.value.getOrElse(MaintenanceRoad(Seq())).asInstanceOf[MaintenanceRoad].maintenanceRoad)
    }
  }

  private def createMap(maintenanceRoad: Seq[Properties]): Map[String, Any] = {
    Map(
      trKayttooikeus -> getFieldValue(maintenanceRoad, Kayttooikeus),
      trHuoltovastuu -> getFieldValue(maintenanceRoad, Huoltovastuu),
      trTiehoitokunta -> getFieldValue(maintenanceRoad, Tiehoitokunta),
      trNimi -> getFieldValue(maintenanceRoad, Nimi),
      trOsoite -> getFieldValue(maintenanceRoad, Osoite),
      trPostinumero -> getFieldValue(maintenanceRoad, Postinumero),
      trPostitoimipaikka -> getFieldValue(maintenanceRoad, Postitoimipaikka),
      trPuh1 -> getFieldValue(maintenanceRoad, Puh1),
      trPuh2 -> getFieldValue(maintenanceRoad, Puh2),
      trLisatieto -> getFieldValue(maintenanceRoad, Lisatieto)
    )
  }

  private def getFieldValue(properties: Seq[Properties], publicId: String): Option[String] = {
    properties.find(p => p.publicId == publicId).map(_.value)
  }
}
