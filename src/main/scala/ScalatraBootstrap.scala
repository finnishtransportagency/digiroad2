import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.dataimport.MassTransitStopImportApi
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
      Digiroad2Context.linearAssetService
    ), "/api/*")
    context.mount(new SessionApi, "/api/auth/*")
    context.mount(new UserConfigurationApi, "/api/userconfig/*")
    context.mount(new PingApi, "/api/ping/*")
    context.mount(new MassTransitStopImportApi, "/api/import/*")
    Digiroad2Context.massTransitStopService.massTransitStopEnumeratedPropertyValues
    context.mount(new IntegrationApi(Digiroad2Context.massTransitStopService), "/api/integration/*")
    context.mount(new ViiteIntegrationApi(Digiroad2Context.roadAddressService), "/api/viite/integration/*")
    context.mount(new ChangeApi(), "/api/changes/*")
    context.mount(new ViiteApi(Digiroad2Context.roadLinkService, Digiroad2Context.vvhClient, Digiroad2Context.roadAddressService), "/api/viite/*")
    context.mount(new MaintenanceRoadApi(Digiroad2Context.linearAssetService), "/api/livi/*")
    if (!Digiroad2Context.getProperty("digiroad2.tierekisteri.enabled").toBoolean) {
      context.mount(new TierekisteriTestApi, "/api/tierekisteri/*")
    }
  }
}
