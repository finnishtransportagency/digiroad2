import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.dataexport.ExportDataApi
import fi.liikennevirasto.digiroad2.dataimport.ImportDataApi
import fi.liikennevirasto.digiroad2.user.UserConfigurationApi
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.scalatra._

import javax.servlet.ServletContext


class
ScalatraBootstrap extends LifeCycle {
  implicit val swagger = new OthSwagger

  override def init(context: ServletContext) {
    context.mount(new Digiroad2Api(Digiroad2Context.roadLinkService,
      Digiroad2Context.roadAddressService,
      Digiroad2Context.speedLimitService,
      Digiroad2Context.obstacleService,
      Digiroad2Context.railwayCrossingService,
      Digiroad2Context.directionalTrafficSignService,
      Digiroad2Context.servicePointService,
      Digiroad2Context.roadLinkClient,
      Digiroad2Context.massTransitStopService,
      Digiroad2Context.linearAssetService,
      Digiroad2Context.linearMassLimitationService,
      Digiroad2Context.maintenanceRoadService,
      Digiroad2Context.pavedRoadService,
      Digiroad2Context.roadWidthService,
      Digiroad2Context.massTransitLaneService,
      Digiroad2Context.numberOfLanesService
    ), "/api/*")
    context.mount(new SessionApi, "/api/auth/*")
    context.mount(new UserConfigurationApi, "/api/userconfig/*")
    context.mount(new DebugApi, "/api/debug/*")
    context.mount(new PingApi, "/api/ping/*")
    context.mount(new ImportDataApi(Digiroad2Context.roadLinkService), "/api/import/*")
    context.mount(new ExportDataApi(Digiroad2Context.roadLinkService), "/api/export/*")
    Digiroad2Context.massTransitStopService.massTransitStopEnumeratedPropertyValues
    
    context.mount(new ResourcesApp, s"/api-docs")
    // external Api
    
    context.mount(new IntegrationApi(Digiroad2Context.massTransitStopService, swagger), 
                          s"/externalApi/integration/*")
    context.mount(new ChangeApi(swagger), 
                        s"/externalApi/changes/*")
    context.mount(new ServiceRoadAPI( Digiroad2Context.maintenanceRoadService, 
                                      Digiroad2Context.roadLinkService, swagger),
                          s"/externalApi/livi/*")
    context.mount(new LaneApi(swagger, Digiroad2Context.roadLinkService, Digiroad2Context.roadAddressService),
                          s"/externalApi/lanes/*")
  }
}
