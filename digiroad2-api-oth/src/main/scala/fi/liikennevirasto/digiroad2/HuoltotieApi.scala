package fi.liikennevirasto.digiroad2

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.slf4j.LoggerFactory



class HuoltotieApi(val linearAssetService: LinearAssetService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)

  protected implicit def jsonFormats: Formats = DefaultFormats
  val typeId = LinearAssetTypes.MaintenanceRoadAssetTypeId

  get("/huoltotiet"){
    //TODO: use method to fetch all active maintenance
    val maintenanceAsset = linearAssetService.getActiveHuoltotie()
    // TODO : toJSON(maintenanceAsset)
  }

  get("/huoltotiet/:areaId"){
    var areaId = params("areaId")
    val maintenanceAsset = linearAssetService.getActiveHuoltotieByPolygon(areaId, typeId)
    val x = maintenanceAsset
    //TODO : toJSON(maintenanceAsset)
  }
}
