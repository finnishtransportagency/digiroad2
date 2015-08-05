package fi.liikennevirasto.digiroad2

import java.util.Properties
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, OracleSpatialAssetDao}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitTimeStamps, SpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.auth.strategy.{BasicAuthStrategy, BasicAuthSupport}
import org.scalatra.auth.{ScentryConfig, ScentrySupport}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{BadRequest, ScalatraBase, ScalatraServlet}
import org.slf4j.LoggerFactory

import scala.slick.driver.JdbcDriver.backend.Database

case class BasicAuthUser(username: String)

class IntegrationAuthStrategy(protected override val app: ScalatraBase, realm: String)
  extends BasicAuthStrategy[BasicAuthUser](app, realm) {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/authentication.properties"))
    props
  }

  private def getProperty(name: String): String = {
    val property = properties.getProperty(name)
    if (property != null) {
      property
    } else {
      throw new RuntimeException(s"cannot find property $name")
    }
  }

  def validate(username: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[BasicAuthUser] = {
    if (username == getProperty("authentication.basic.username") && password == getProperty("authentication.basic.password")) Some(BasicAuthUser(username))
    else None
  }

  def getUserId(user: BasicAuthUser)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.username
}

trait AuthenticationSupport extends ScentrySupport[BasicAuthUser] with BasicAuthSupport[BasicAuthUser] {
  self: ScalatraBase =>

  val realm = "Digiroad 2 Integration API"

  protected def fromSession = { case id: String => BasicAuthUser(id)  }
  protected def toSession = { case user: BasicAuthUser => user.username }

  protected val scentryConfig = (new ScentryConfig {}).asInstanceOf[ScentryConfiguration]

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies("Basic").unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register("Basic", app => new IntegrationAuthStrategy(app, realm))
  }
}

class IntegrationApi extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
  }

  private def propertyValuesToIntList(values: Seq[String]): Seq[Int] = { values.map(_.toInt) }

  private def propertyValuesToString(values: Seq[String]): String = { values.mkString }

  private def firstPropertyValueToInt(values: Seq[String]): Int = { values.headOption.map(_.toInt).getOrElse(99) }

  private def extractPropertyValue(key: String, properties: Seq[Property], transformation: (Seq[String] => Any)): (String, Any) = {
    val values: Seq[String] = properties.filter { property => property.publicId == key}.map { property =>
      property.values.map { value =>
        value.propertyValue
      }
    }.flatten
    key -> transformation(values)
  }

  def extractModificationTime(timeStamps: TimeStamps): (String, String) = {
    "muokattu_viimeksi" ->
      timeStamps.modified.modificationTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print(_))
        .getOrElse(timeStamps.created.modificationTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print(_))
        .getOrElse(""))
  }

  def extractModifier(massTransitStop: MassTransitStopWithTimeStamps): (String, String) = {
    "muokannut_viimeksi" ->  massTransitStop.modified.modifier
      .getOrElse(massTransitStop.created.modifier
      .getOrElse(""))
  }

  def extractBearing(massTransitStop: MassTransitStopWithTimeStamps): (String, Option[Int]) = { "suuntima" -> massTransitStop.bearing }

  def extractExternalId(massTransitStop: MassTransitStopWithTimeStamps): (String, Long) = { "valtakunnallinen_id" -> massTransitStop.nationalId }

  def extractFloating(massTransitStop: MassTransitStopWithTimeStamps): (String, Boolean) = { "kelluvuus" -> massTransitStop.floating }

  def extractMmlId(massTransitStop: RoadLinkStop): (String, Option[Long]) = { "mml_id" -> massTransitStop.mmlId }

  def extractMvalue(massTransitStop: RoadLinkStop): (String, Option[Double]) = { "m_value" -> massTransitStop.mValue }

  private def toGeoJSON(input: Iterable[MassTransitStopWithTimeStamps]): Map[String, Any] = {
    Map(
      "type" -> "FeatureCollection",
      "features" -> input.map {
        case (massTransitStop) => Map(
          "type" -> "Feature",
          "id" -> massTransitStop.id,
          "geometry" -> Map("type" -> "Point", "coordinates" -> List(massTransitStop.lon, massTransitStop.lat)),
          "properties" -> Map(
            extractModifier(massTransitStop),
            extractModificationTime(massTransitStop),
            extractBearing(massTransitStop),
            extractExternalId(massTransitStop),
            extractFloating(massTransitStop),
            extractMmlId(massTransitStop),
            extractMvalue(massTransitStop),
            extractPropertyValue("pysakin_tyyppi", massTransitStop.propertyData, propertyValuesToIntList),
            extractPropertyValue("nimi_suomeksi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("nimi_ruotsiksi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("tietojen_yllapitaja", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("yllapitajan_tunnus", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("yllapitajan_koodi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("matkustajatunnus", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_x", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_y", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_z", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("liikennointisuunta", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("vaikutussuunta", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("ensimmainen_voimassaolopaiva", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("viimeinen_voimassaolopaiva", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("aikataulu", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("katos", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("mainoskatos", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("penkki", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("sahkoinen_aikataulunaytto", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("valaistus", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("esteettomyys_liikuntarajoitteiselle", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("saattomahdollisuus_henkiloautolla", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("liityntapysakointipaikkojen_maara", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("liityntapysakoinnin_lisatiedot", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("pysakin_omistaja", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("palauteosoite", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("lisatiedot", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("pyorateline", massTransitStop.propertyData, firstPropertyValueToInt))
       )
      })
  }

  private def assetToIntegrationMassTransitStop(asset: AssetWithProperties): MassTransitStopWithTimeStamps =  {
    MassTransitStopWithTimeStamps(id = asset.id, nationalId = asset.nationalId, lon = asset.lon, lat = asset.lat,
      bearing = asset.bearing, propertyData = asset.propertyData, created = asset.created,
      modified = asset.modified, mmlId = None, mValue = None, floating = asset.floating)
  }

  private def withDynSession[T](f: => T) = Database.forDataSource(ds).withDynSession(f)

  private def getMassTransitStopsByMunicipality(municipalityNumber: Int): Iterable[MassTransitStopWithTimeStamps] = {
    useVVHGeometry match {
      case true =>
        massTransitStopService.getByMunicipality(municipalityNumber)
      case false =>
        withDynSession {
          OracleSpatialAssetDao.getAssetsByMunicipality(municipalityNumber).map(assetToIntegrationMassTransitStop)
        }
    }
  }

  private def speedLimitsToApi(speedLimits: Seq[SpeedLimit]): Seq[Map[String, Any]] = {
    speedLimits.map { speedLimit =>
      Map("id" -> (speedLimit.id + "-" + speedLimit.mmlId),
        "sideCode" -> speedLimit.sideCode.value,
        "points" -> speedLimit.points,
        "value" -> speedLimit.value.getOrElse(0),
        "startMeasure" -> speedLimit.startMeasure,
        "endMeasure" -> speedLimit.endMeasure,
        "mmlId" -> speedLimit.mmlId,
        extractModificationTime(SpeedLimitTimeStamps(speedLimit.id, Modification(speedLimit.createdDateTime, speedLimit.createdBy), Modification(speedLimit.modifiedDateTime, speedLimit.modifiedBy)))
      )
    }
  }

  private def roadLinkPropertiesToApi(roadLinks: Seq[VVHRoadLinkWithProperties]): Seq[Map[String, Any]] = {
    roadLinks.map{ roadLink =>
      Map("mmlId" -> roadLink.mmlId,
        "administrativeClass" -> roadLink.administrativeClass.value,
        "functionalClass" -> roadLink.functionalClass,
        "trafficDirection" -> roadLink.trafficDirection.value,
        "linkType" -> roadLink.linkType.value,
        "modifiedAt" -> roadLink.modifiedAt) ++ roadLink.attributes
    }
  }

  get("/:assetType") {
    contentType = formats("json")
    params.get("municipality").map { municipality =>
      val municipalityNumber = municipality.toInt
      val assetType = params("assetType")
      assetType match {
        case "mass_transit_stops" => toGeoJSON(getMassTransitStopsByMunicipality(municipalityNumber))
        case "speed_limits" => speedLimitsToApi(linearAssetProvider.getSpeedLimits(municipalityNumber))
        case "total_weight_limits" => NumericalLimitService.getByMunicipality(30, municipalityNumber)
        case "trailer_truck_weight_limits" => NumericalLimitService.getByMunicipality(40, municipalityNumber)
        case "axle_weight_limits" => NumericalLimitService.getByMunicipality(50, municipalityNumber)
        case "bogie_weight_limits" => NumericalLimitService.getByMunicipality(60, municipalityNumber)
        case "height_limits" => NumericalLimitService.getByMunicipality(70, municipalityNumber)
        case "length_limits" => NumericalLimitService.getByMunicipality(80, municipalityNumber)
        case "width_limits" => NumericalLimitService.getByMunicipality(90, municipalityNumber)
        case "blocked_passages" => PointAssetService.getByMunicipality(16, municipalityNumber)
        case "barrier_gates" => PointAssetService.getByMunicipality(3, municipalityNumber)
        case "traffic_lights" => PointAssetService.getByMunicipality(9, municipalityNumber)
        case "pedestrian_crossings" => PointAssetService.getByMunicipality(17, municipalityNumber)
        case "directional_traffic_signs" => PointAssetService.getDirectionalTrafficSignsByMunicipality(municipalityNumber)
        case "railway_crossings" => PointAssetService.getRailwayCrossingsByMunicipality(municipalityNumber)
        case "vehicle_allowed" => LinearAssetService.getByMunicipality(1, municipalityNumber)
        case "vehicle_not_allowed" => LinearAssetService.getByMunicipality(29, municipalityNumber)
        case "number_of_lanes" ⇒ LinearAssetService.getByMunicipality(5, municipalityNumber)
        case "roads_affected_by_thawing" ⇒ LinearAssetService.getByMunicipality(6, municipalityNumber)
        case "widths" ⇒ LinearAssetService.getByMunicipality(8, municipalityNumber)
        case "paved_roads" ⇒ LinearAssetService.getByMunicipality(26, municipalityNumber)
        case "lit_roads" ⇒ LinearAssetService.getByMunicipality(27, municipalityNumber)
        case "built_up_area" ⇒ LinearAssetService.getByMunicipality(30, municipalityNumber)
        case "speed_limits_during_winter" ⇒ LinearAssetService.getByMunicipality(31, municipalityNumber)
        case "traffic_volumes" ⇒ LinearAssetService.getByMunicipality(33, municipalityNumber)
        case "exit_numbers" ⇒ LinearAssetService.getByMunicipality(34, municipalityNumber)
        case "road_addresses" ⇒ LinearAssetService.getRoadAddressesByMunicipality(municipalityNumber)
        case "bridges_underpasses_and_tunnels" => LinearAssetService.getBridgesUnderpassesAndTunnelsByMunicipality(municipalityNumber)
        case "road_link_properties" => roadLinkPropertiesToApi(roadLinkService.getRoadLinksFromVVH(municipalityNumber))
        case "manoeuvres" =>  ManoeuvreService.getByMunicipality(municipalityNumber)
        case _ => BadRequest("Invalid asset type")
      }
    } getOrElse {
      BadRequest("Missing mandatory 'municipality' parameter")
    }
  }

  get("/service_points") {
    contentType = formats("json")
    PointAssetService.getServicePoints()
  }
}
