package fi.liikennevirasto.digiroad2

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.slf4j.LoggerFactory


case class Huoltotie(accessRights: Int,
                     maintenanceResponsibility: Int,
                     roadMaintenanceAssociation: String,
                     name: String,
                     address: String,
                     postalCode: String,
                     city: String,
                     phoneNumber1: String,
                     phoneNumber2: String,
                     additionalInfo: String)


class HuoltotieApi(val linearAssetService: LinearAssetService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)

  protected implicit def jsonFormats: Formats = DefaultFormats


  get("/huoltotiet"){
    val typeId = LinearAssetTypes.MaintenanceRoadAssetTypeId
    //TODO: use method to fetch all active maintenance
    val maintenanceAsset = linearAssetService.getActiveHuoltotie()
    // TODO : toJSON(maintenanceAsset)
  }

  get("/huoltotiet/:polygon"){
    var coordinates = params("polygon")
    //TODO: use method to fetch all active maintenance by polygon
    val maintenanceAsset = linearAssetService.getActiveHuoltotieByPolygon(coordinates)
    //TODO : toJSON(maintenanceAsset)
  }
}
