import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.dataexport.ExportDataApi
import fi.liikennevirasto.digiroad2.dataimport.ImportDataApi
import fi.liikennevirasto.digiroad2.user.UserConfigurationApi
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
      Digiroad2Context.vvhClient,
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
    context.mount(new PingApi, "/api/ping/*")
    context.mount(new ImportDataApi(Digiroad2Context.roadLinkService), "/api/import/*")
    context.mount(new ExportDataApi(Digiroad2Context.roadLinkService), "/api/export/*")
    Digiroad2Context.massTransitStopService.massTransitStopEnumeratedPropertyValues
    context.mount(new IntegrationApi(Digiroad2Context.massTransitStopService, swagger), "/api/integration/*")
    context.mount(new ChangeApi(swagger), "/api/changes/*")
    context.mount(new MunicipalityApi(Digiroad2Context.vvhClient,
      Digiroad2Context.roadLinkService,
      Digiroad2Context.speedLimitService,
      Digiroad2Context.pavedRoadService,
      Digiroad2Context.obstacleService,
      swagger
    ), "/api/municipality/*")
    context.mount(new ServiceRoadAPI(Digiroad2Context.maintenanceRoadService, Digiroad2Context.roadLinkService, swagger), "/api/livi/*")
    if (!Digiroad2Context.getProperty("digiroad2.tierekisteri.enabled").toBoolean) {
      // Mount for manual testing purposes but do not use them
      context.mount(new TierekisteriTestApi, "/api/tierekisteri/*")
    }
    context.mount(new ResourcesApp, "/api-docs")
  }
}
