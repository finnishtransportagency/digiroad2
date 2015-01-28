package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{Property, AssetWithProperties}
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, CommonAssetProperty, OracleSpatialAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.scalatra.{BadRequest, ScalatraServlet, ScalatraBase}
import org.scalatra.json.JacksonJsonSupport
import org.slf4j.LoggerFactory
import org.scalatra.auth.strategy.{BasicAuthStrategy, BasicAuthSupport}
import org.scalatra.auth.{ScentrySupport, ScentryConfig}
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.Properties
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

  def extractModificationTime(asset: AssetWithProperties): (String, String) = {
    "muokattu_viimeksi" ->
      asset.modified.modificationTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print(_))
        .getOrElse(asset.created.modificationTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print(_))
        .getOrElse(""))
  }

  private def toGeoJSON(input: Iterable[AssetWithProperties]): Map[String, Any] = {
    Map(
      "type" -> "FeatureCollection",
      "features" -> input.map {
        case (asset) => Map(
          "type" -> "Feature",
          "id" -> asset.id,
          "geometry" -> Map("type" -> "Point", "coordinates" -> List(asset.lon, asset.lat)),
          "properties" -> Map(
            extractModificationTime(asset),
            extractPropertyValue("pysakin_tyyppi", asset.propertyData, propertyValuesToIntList),
            extractPropertyValue("nimi_suomeksi", asset.propertyData, propertyValuesToString),
            extractPropertyValue("nimi_ruotsiksi", asset.propertyData, propertyValuesToString),
            extractPropertyValue("tietojen_yllapitaja", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("yllapitajan_tunnus", asset.propertyData, propertyValuesToString),
            extractPropertyValue("yllapitajan_koodi", asset.propertyData, propertyValuesToString),
            extractPropertyValue("matkustajatunnus", asset.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_x", asset.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_y", asset.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_z", asset.propertyData, propertyValuesToString),
            extractPropertyValue("liikennointisuunta", asset.propertyData, propertyValuesToString),
            extractPropertyValue("vaikutussuunta", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("ensimmainen_voimassaolopaiva", asset.propertyData, propertyValuesToString),
            extractPropertyValue("viimeinen_voimassaolopaiva", asset.propertyData, propertyValuesToString),
            extractPropertyValue("aikataulu", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("katos", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("mainoskatos", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("penkki", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("sahkoinen_aikataulunaytto", asset.propertyData, firstPropertyValueToInt),
            extractPropertyValue("pyorateline", asset.propertyData, firstPropertyValueToInt))
       )
      })
  }

  private def withDynSession[T](f: => T) = Database.forDataSource(ds).withDynSession(f)

  get("/:assetType") {
    contentType = formats("json")
    params.get("municipality").map { municipality =>
      val municipalityNumber = municipality.toInt
      val assetType = params("assetType")
      assetType match {
        case "mass_transit_stops" => withDynSession { OracleSpatialAssetDao.getAssetsByMunicipality(municipalityNumber) }
        case "mass_transit_stops_2" => withDynSession { toGeoJSON(OracleSpatialAssetDao.getAssetsByMunicipality(municipalityNumber)) }
        case "speed_limits" => withDynSession { OracleLinearAssetDao.getByMunicipality(municipalityNumber) }
        case "total_weight_limits" => NumericalLimitService.getByMunicipality(30, municipalityNumber)
        case "trailer_truck_weight_limits" => NumericalLimitService.getByMunicipality(40, municipalityNumber)
        case "axle_weight_limits" => NumericalLimitService.getByMunicipality(50, municipalityNumber)
        case "bogie_weight_limits" => NumericalLimitService.getByMunicipality(60, municipalityNumber)
        case "height_limits" => NumericalLimitService.getByMunicipality(70, municipalityNumber)
        case "length_limits" => NumericalLimitService.getByMunicipality(80, municipalityNumber)
        case "width_limits" => NumericalLimitService.getByMunicipality(90, municipalityNumber)
        case "blocked_passages" => PointAssetService.getByMunicipality(16, municipalityNumber)
        case "barrier_gates" => PointAssetService.getByMunicipality(3, municipalityNumber)
        case "road_link_properties" => RoadLinkService.getByMunicipalityWithProperties(municipalityNumber)
        case _ => BadRequest("Invalid asset type")
      }
    } getOrElse {
      BadRequest("Missing mandatory 'municipality' parameter")
    }
  }
}
