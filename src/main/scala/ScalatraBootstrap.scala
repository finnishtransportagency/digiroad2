import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.dataimport.{ImportDataApi, MassTransitStopImportApi}
import fi.liikennevirasto.digiroad2.user.UserConfigurationApi
import org.scalatra._
import javax.servlet.ServletContext


class
ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new Digiroad2Api(Digiroad2Context.roadLinkOTHService,
      Digiroad2Context.roadAddressesService,
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
      Digiroad2Context.pavingService,
      Digiroad2Context.roadWidthService
    ), "/api/*")
    context.mount(new SessionApi, "/api/auth/*")
    context.mount(new UserConfigurationApi, "/api/userconfig/*")
    context.mount(new PingApi, "/api/ping/*")
    context.mount(new MassTransitStopImportApi, "/api/import/*")
    context.mount(new ImportDataApi, "/api/import/data/*")
    Digiroad2Context.massTransitStopService.massTransitStopEnumeratedPropertyValues
    context.mount(new IntegrationApi(Digiroad2Context.massTransitStopService), "/api/integration/*")
    context.mount(new ChangeApi(), "/api/changes/*")
    context.mount(new MunicipalityApi(Digiroad2Context.onOffLinearAssetService,
      Digiroad2Context.roadLinkOTHService,
      Digiroad2Context.linearAssetService,
      Digiroad2Context.speedLimitService,
      Digiroad2Context.pavingService,
      Digiroad2Context.roadWidthService,
      Digiroad2Context.manoeuvreService,
      Digiroad2Context.assetService,
      Digiroad2Context.obstacleService,
      Digiroad2Context.pedestrianCrossingService,
      Digiroad2Context.railwayCrossingService,
      Digiroad2Context.trafficLightService
    ), "/api/municipality/*")
    context.mount(new ServiceRoadAPI(Digiroad2Context.maintenanceRoadService, Digiroad2Context.roadLinkOTHService ), "/api/livi/*")
    if (!Digiroad2Context.getProperty("digiroad2.tierekisteri.enabled").toBoolean) {
      // Mount for manual testing purposes but do not use them
      context.mount(new TierekisteriTestApi, "/api/tierekisteri/*")
    }
  }
}
