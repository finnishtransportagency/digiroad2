import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.dataimport.{ImportDataApi, MassTransitStopImportApi}
import fi.liikennevirasto.digiroad2.user.UserConfigurationApi
import org.scalatra._
import javax.servlet.ServletContext


class
ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new Digiroad2Api(Digiroad2Context.roadLinkService,
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
    context.mount(new ViiteIntegrationApi(Digiroad2Context.roadAddressService), "/api/viite/integration/*")
    context.mount(new ChangeApi(), "/api/changes/*")
    context.mount(new MunicipalityApi(Digiroad2Context.onOffLinearAssetService, Digiroad2Context.roadLinkService), "/api/municipality/*")
    context.mount(new ViiteApi(Digiroad2Context.roadLinkService, Digiroad2Context.vvhClient,
      Digiroad2Context.roadAddressService, Digiroad2Context.projectService), "/api/viite/*")
    context.mount(new ServiceRoadAPI(Digiroad2Context.maintenanceRoadService, Digiroad2Context.roadLinkService ), "/api/livi/*")
    if (Digiroad2Context.getProperty("digiroad2.tierekisteri.enabled").toBoolean) {
      val url = Digiroad2Context.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint")
      if ("http://localhost.*/api/trrest/".r.findFirstIn(url).nonEmpty) {
        println("Using local tierekisteri mock at /api/trrest/")
        context.mount(new ViiteTierekisteriMockApi, "/api/trrest/*")
      } else {
        println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        println("NOTE! Tierekisteri integration enabled but not using local mock")
        println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
      }
    } else {
      // Mount for manual testing purposes but do not use them
      context.mount(new TierekisteriTestApi, "/api/tierekisteri/*")
      context.mount(new ViiteTierekisteriMockApi, "/api/trrest/*")
    }
  }
}
