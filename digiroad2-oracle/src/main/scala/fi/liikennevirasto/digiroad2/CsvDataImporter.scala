package fi.liikennevirasto.digiroad2

import java.io.InputStream
import java.util.Properties

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{ImportLogDAO, MassTransitStopDao, MunicipalityDao, RoadLinkDAO}
import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, RoadLink, Properties => Props}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import org.apache.commons.lang3.StringUtils.isBlank
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.TrafficSignTypeGroup.AdditionalPanels
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.service.{RoadAddressesService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.service.pointasset.{AdditionalPanelInfo, IncomingTrafficSign, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter.viiteClient
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import java.io.InputStreamReader

import fi.liikennevirasto.digiroad2.middleware.CsvDataImporterInfo
import org.json4s.{DefaultFormats, Extraction}
import org.json4s.jackson.Serialization

import scala.util.Try


sealed trait Status {
  def value : Int
  def description: String
}

object Status {
  val values : Set[Status] = Set(InProgress, OK, NotOK, Abend)

  def apply(value: Int) : Status = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object InProgress extends Status {def value = 1; def description = "In progress ..."}
  case object OK extends Status {def value = 2; def description = "All records was treated "}
  case object NotOK extends Status {def value = 3; def description = "Process Executed but some record fails"}
  case object Abend extends Status {def value = 4; def description = "Process fail"}
  case object Unknown extends Status {def value = 99; def description = "Unknown Status Type"}
}

case class ImportStatusInfo(id: Long, status: Status, fileName: String, createdBy: Option[String], createdDate: Option[DateTime], logType: String, content: Option[String])

class RoadLinkNotFoundException(linkId: Int) extends RuntimeException

/*case class IncompleteAsset(missingParameters: List[String], csvRow: String)
case class MalformedAsset(malformedParameters: List[String], csvRow: String)
case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)

sealed trait  {
  val incompleImportResultteAssets: List[IncompleteAsset]
  val malformedAssets: List[MalformedAsset]
  val excludedAssets: List[ExcludedAsset]
}*/

trait CsvDataImporterOperations {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  lazy val roadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = { new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint")) }
  lazy val userProvider: UserProvider = {
    Class.forName(getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  lazy val roadAddressService: RoadAddressesService = {
    new RoadAddressesService(viiteClient)
  }

  lazy val tierekisteriMassTransitStopClient: TierekisteriMassTransitStopClient = {
    new TierekisteriMassTransitStopClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build)
  }

  lazy val roadAddressesService: RoadAddressesService = {
    new RoadAddressesService(viiteClient)
  }

//  type ImportResultData <: ImportResult

  val ROAD_LINK_LOG = "road link import"
  val TRAFFIC_SIGN_LOG = "traffic sign import"
  val DELETE_TRAFFIC_SIGN_LOG = "traffic sign delete"
  val MAINTENANCE_ROAD_LOG = "maintenance import"
  val BUS_STOP_LOG = "bus stop import"

  protected def getProperty(name: String) : String = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }

  val importLogDao: ImportLogDAO = new ImportLogDAO

    def getImportById(id: Long) : Option[ImportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        importLogDao.get(id)
      }
    }

    def getByUser(username: String, logTypes: Seq[String]) : Seq[ImportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        importLogDao.getByUser(username, logTypes)
      }
    }

    def getById(id: Long) : Option[ImportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        importLogDao.get(id)
      }
    }

    def update(id: Long, status: Status, content: Option[String] = None) : Long  = {
      OracleDatabase.withDynTransaction {
        importLogDao.update(id, status, content)
      }
    }

    def create(username: String, logType: String, fileName: String) : Long  = {
      OracleDatabase.withDynTransaction {
        importLogDao.create(username, logType, fileName)
      }
    }

//  def importAssets(inputStream: InputStream, roadTypeLimitations: Set[AdministrativeClass] = Set()): ImportResult

}
class TrafficSignCsvImporter extends CsvDataImporterOperations {
  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"

  case class CsvTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleTrafficSignProperty], validityDirection: Int, bearing: Option[Int], mValue: Double, roadLink: RoadLink, nearbyLinks: Seq[VVHRoadlink])

  type MalformedParameters = List[String]
  type ParsedProperties = List[AssetProperty]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)

  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class MalformedAsset(malformedParameters: List[String], csvRow: String)
  case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)
  case class NotImportedData(reason: String, csvRow: String)
  case class CsvAssetRowAndRoadLink(properties: CsvAssetRow, roadLink: Seq[VVHRoadlink], enrichedRoadLink: Seq[RoadLink] = Seq())
  case class ImportResultTrafficSign(incompleteAssets: List[IncompleteAsset] = Nil,
                          malformedAssets: List[MalformedAsset] = Nil,
                          excludedAssets: List[ExcludedAsset] = Nil,
                          notImportedData: List[NotImportedData] = Nil,
                          createdData: List[CsvAssetRowAndRoadLink] = Nil)
  case class AssetProperty(columnName: String, value: Any)
  case class CsvAssetRow(properties: Seq[AssetProperty])

  type ParsedCsv = (MalformedParameters, Seq[CsvAssetRowAndRoadLink])


  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, userProvider, eventbus)

  private val longValueFieldMappings = Map(
    "koordinaatti x" -> "lon",
    "koordinaatti y" -> "lat"
  )

  private val nonMandatoryMappings = Map(
    "arvo" -> "value",
    "kaksipuolinen merkki" -> "twoSided",
    "liikennevirran suunta" -> "trafficDirection",
    "suuntima" -> "bearing",
    "lisatieto" -> "additionalInfo"
  )

  private val codeValueFieldMappings = Map(
    "liikennemerkin tyyppi" -> "trafficSignType"
  )

  val mappings = longValueFieldMappings ++ nonMandatoryMappings ++ codeValueFieldMappings

  private val mandatoryFields = List("koordinaatti x", "koordinaatti y", "liikennemerkin tyyppi")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  private def verifyDoubleType(parameterName: String, parameterValue: String): ParsedAssetRow = {
    if(parameterValue.matches("[0-9.]*")) {
      (Nil, List(AssetProperty(columnName = longValueFieldMappings(parameterName), value = BigDecimal(parameterValue))))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def verifyValueCode(parameterName: String, parameterValue: String): ParsedAssetRow = {
    if(parameterValue.forall(_.isDigit) && TrafficSignType.applyTRValue(parameterValue.toInt).source.contains("CSVimport")){
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    }else{
      (List(parameterName), Nil)
    }
  }

  def getRightRoadLinkUsingBearing(assetBearing: Option[Int], assetCoordinates: Point): (Seq[VVHRoadlink], Seq[RoadLink]) = {
    val toleranceInDegrees = 25
    val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(userProvider.getCurrentUser(), assetCoordinates)

    assetBearing match {
      case Some(aBearing) if roadLinks.nonEmpty =>
        val enrichedRoadLinks = roadLinkService.enrichRoadLinksFromVVH(roadLinks)

        val filteredEnrichedRoadLinks =
          enrichedRoadLinks.filter { roadLink =>
            val aTrafficDirection = trafficSignService.getTrafficSignValidityDirection(assetCoordinates, roadLink.geometry)

            val mValue = GeometryUtils.calculateLinearReferenceFromPoint(assetCoordinates, roadLink.geometry)
            val roadLinkBearing = GeometryUtils.calculateBearing(roadLink.geometry, Some(mValue), Some(roadLink.length) )

            if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
              val reverseRoadLinkBearing =
                if (roadLinkBearing - 180 < 0) {
                  roadLinkBearing + 180
                } else {
                  roadLinkBearing - 180
                }

              Math.abs(aBearing - roadLinkBearing) <= toleranceInDegrees ||
                Math.abs(aBearing - reverseRoadLinkBearing) <= toleranceInDegrees
            } else {
              Math.abs(aBearing - roadLinkBearing) <= toleranceInDegrees &&
                (roadLink.trafficDirection == TrafficDirection.apply(aTrafficDirection) || TrafficDirection.apply(aTrafficDirection) == TrafficDirection.BothDirections)
            }
          }

        if (filteredEnrichedRoadLinks.nonEmpty) {
          (roadLinks.filter { rl => filteredEnrichedRoadLinks.exists(_.linkId == rl.linkId) }, filteredEnrichedRoadLinks)
        } else
          (roadLinks, enrichedRoadLinks)

      case _ =>
        (roadLinks, Seq())
    }
  }

  def tryToInt(propertyValue: String ) : Option[Int] = {
    Try(propertyValue.toInt).toOption
  }

  private def verifyData(parsedRow: CsvAssetRow): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]
    val bearing = tryToInt(getPropertyValue(parsedRow, "bearing").toString)

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val (roadLinks, enrichedRoadLinks) = getRightRoadLinkUsingBearing(bearing, Point(lon.toLong, lat.toLong))
        if(roadLinks.isEmpty) {
          (List(s"Unauthorized Municipality Or RoadLind inexistent near of Asset"), Seq())
        } else
          (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks, enrichedRoadLinks)))
      case _ =>
        (Nil, Nil) //That condition is already checked on assetRowToProperties
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
        }else
          result
      } else {
        if (longValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (codeValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueCode(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
        } else
          result
      }
    }
  }
  private def getPropertyValue(trafficSignAttributes: CsvAssetRow, propertyName: String) : Any = {
    trafficSignAttributes.properties.find (prop => prop.columnName == propertyName).map(_.value).get
  }

  private def getPropertyValueOption(trafficSignAttributes: CsvAssetRow, propertyName: String) : Option[Any] = {
    trafficSignAttributes.properties.find (prop => prop.columnName == propertyName).map(_.value)
  }

  private def generateBaseProperties(trafficSignAttributes: CsvAssetRow) : Set[SimpleTrafficSignProperty] = {
    val valueProperty = tryToInt(getPropertyValue(trafficSignAttributes, "value").toString).map { value =>
      SimpleTrafficSignProperty(valuePublicId, Seq(TextPropertyValue(value.toString)))}

    val additionalInfo = getPropertyValue(trafficSignAttributes, "additionalInfo").toString
    val additionalProperty = if(additionalInfo.nonEmpty)
        Some(SimpleTrafficSignProperty(infoPublicId, Seq(TextPropertyValue(additionalInfo))))
      else
        None

    val typeProperty = SimpleTrafficSignProperty(typePublicId, Seq(TextPropertyValue(TrafficSignType.applyTRValue(getPropertyValue(trafficSignAttributes, "trafficSignType").toString.toInt).OTHvalue.toString)))

    Set(Some(typeProperty), valueProperty, additionalProperty).flatten
  }

  def createTrafficSigns(trafficSignAttributes: Seq[CsvAssetRowAndRoadLink]): Seq[AdditionalPanelInfo] = {

    val signs = trafficSignAttributes.map { trafficSignAttribute =>
      val properties = trafficSignAttribute.properties
      val nearbyLinks = trafficSignAttribute.roadLink
      val enrichedNearbyLinks = trafficSignAttribute.enrichedRoadLink
      val optBearing = tryToInt(getPropertyValue(properties, "bearing").toString)
      val twoSided = getPropertyValue(properties, "twoSided") match {
        case "1" => true
        case _ => false
      }
      val lon = getPropertyValue(properties, "lon").asInstanceOf[BigDecimal].toLong
      val lat = getPropertyValue(properties, "lat").asInstanceOf[BigDecimal].toLong

      val closestRoadLink = Seq(enrichedNearbyLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon.toLong, lat.toLong), r.geometry))).head
      val validityDirection = trafficSignService.getValidityDirection(Point(lon, lat), closestRoadLink, optBearing, twoSided)

      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(lon, lat), closestRoadLink.geometry)
      val bearingRecalculation = GeometryUtils.calculateBearing(closestRoadLink.geometry, Some(mValue), Some(closestRoadLink.length) )
      CsvTrafficSign(lon, lat, closestRoadLink.linkId, generateBaseProperties(properties), validityDirection, Some(bearingRecalculation), mValue, closestRoadLink, nearbyLinks)
    }

    val (additionalPanelInfo, trafficSignInfo) = signs.partition{ sign =>
      TrafficSignType.applyOTHValue(sign.propertyData.find(p => p.publicId == typePublicId).get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toString.toInt).group == AdditionalPanels}

    val additionalPanels = additionalPanelInfo.map {panel => AdditionalPanelInfo(panel.mValue, panel.linkId, panel.propertyData, panel.validityDirection, Some(Point(panel.lon, panel.lat)))}.toSet

    val usedAdditionalPanels = trafficSignInfo.flatMap { sign =>
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(sign.lon, sign.lat), sign.roadLink.geometry)
      val signBearing = Some(GeometryUtils.calculateBearing(sign.roadLink.geometry, Some(mValue), Some(sign.roadLink.length)))
      val signType = sign.propertyData.find(p => p.publicId == typePublicId).get.values.headOption.get.asInstanceOf[TextPropertyValue].propertyValue.toString.toInt
      val filteredAdditionalPanel = trafficSignService.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, signType, sign.roadLink.geometry, additionalPanels, sign.nearbyLinks)

      if (filteredAdditionalPanel.size <= 3) {
        val propertyData = trafficSignService.additionalPanelProperties(filteredAdditionalPanel) ++ sign.propertyData
        trafficSignService.createFromCoordinates(IncomingTrafficSign(sign.lon, sign.lat, sign.roadLink.linkId, propertyData, sign.validityDirection, signBearing), sign.roadLink, sign.nearbyLinks)
        filteredAdditionalPanel
      } else Seq()
    }
    additionalPanels.filterNot(usedAdditionalPanels.toSet).toSeq
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String, municipalitiesToExpire: Set[Int]) : Long = {
    implicit val formats = DefaultFormats

    val logId = create(username, TRAFFIC_SIGN_LOG, fileName)

    try {
      val result = processing(inputStream, municipalitiesToExpire)
      val response = result match {
        case ImportResultTrafficSign(Nil, Nil, Nil, Nil, _) => "CSV tiedosto käsitelty." //succesfully processed
        case ImportResultTrafficSign(Nil, excludedLinks, Nil, Nil, _) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + Serialization.write(excludedLinks) //following links have been excluded
        case _ => Serialization.write(result.copy(createdData = Nil))
      }
      update(logId, Status.OK)
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
        throw e
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, municipalitiesToExpire: Set[Int]): ImportResultTrafficSign = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    withDynTransaction{
      trafficSignService.expireAssetsByMunicipalities(municipalitiesToExpire)
      val result = csvReader.allWithHeaders().foldLeft(ImportResultTrafficSign()) { (result, row) =>
        val csvRow = row.map(r => (r._1.toLowerCase, r._2))
        val missingParameters = findMissingParameters(csvRow)
        val (malformedParameters, properties) = assetRowToProperties(csvRow)
        val (notImportedParameters, parsedRowAndRoadLink) = verifyData(CsvAssetRow(properties))

        if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
          result.copy(
            incompleteAssets = missingParameters match {
              case Nil => result.incompleteAssets
              case parameters =>
                IncompleteAsset(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteAssets
            },
            malformedAssets = malformedParameters match {
              case Nil => result.malformedAssets
              case parameters =>
                MalformedAsset(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedAssets
            },
            notImportedData = notImportedParameters match {
              case Nil => result.notImportedData
              case parameters =>
                NotImportedData(reason = parameters.head, csvRow = rowToString(csvRow)) :: result.notImportedData
            })
        } else {
          result.copy(
            createdData = parsedRowAndRoadLink match {
              case Nil => result.createdData
              case parameters =>
                CsvAssetRowAndRoadLink(properties = parameters.head.properties, roadLink = parameters.head.roadLink, enrichedRoadLink = parameters.head.enrichedRoadLink) :: result.createdData
            })
        }
      }

      val notImportedAdditionalPanel = createTrafficSigns(result.createdData)
      val resultWithExcluded  = result.copy(notImportedData = notImportedAdditionalPanel.map{notImported =>
        NotImportedData(reason = "Additional Panel Without main Sign Type", csvRow = s"koordinaatti x: ${notImported.position.get.x}, koordinaatti y: ${notImported.position.get.y}, liikennevirran suunta: ${notImported.validityDirection}, " +
          s"liikennemerkin tyyppi: ${TrafficSignType.applyOTHValue(trafficSignService.getProperty(notImported.propertyData, typePublicId).get.propertyValue.toString.toInt).TRvalue}, " +
          s"arvo: ${trafficSignService.getProperty(notImported.propertyData, valuePublicId).getOrElse(TextPropertyValue("")).propertyValue}, " +
          s"lisätieto: ${trafficSignService.getProperty(notImported.propertyData, infoPublicId).getOrElse(TextPropertyValue("")).propertyValue}")
      }.toList ::: result.notImportedData)
      resultWithExcluded
      }







    }
}

class RoadLinkCsvImporter extends CsvDataImporterOperations {

  case class NonUpdatedLink(linkId: Long, csvRow: String)

  case class IncompleteLink(missingParameters: List[String], csvRow: String)

  case class MalformedLink(malformedParameters: List[String], csvRow: String)

  case class ExcludedLink(unauthorizedAdminClass: List[String], csvRow: String)

  case class ImportResultRoadLink(nonUpdatedLinks: List[NonUpdatedLink] = Nil,
                                  incompleteLinks: List[IncompleteLink] = Nil,
                                  malformedLinks: List[MalformedLink] = Nil,
                                  excludedLinks: List[ExcludedLink] = Nil)

  case class LinkProperty(columnName: String, value: Any)

  case class CsvRoadLinkRow(linkId: Int, objectID: Int = 0, properties: Seq[LinkProperty])

  type IncompleteParameters = List[String]
  type ParsedProperties = List[LinkProperty]
  type MalformedParameters = List[String]
  type ParsedLinkRow = (MalformedParameters, ParsedProperties)

  private val administrativeClassLimitations: List[AdministrativeClass] = List(State)
  val autorizedValues: List[Int] = List(-11, -1, 0, 1, 2, 3, 4, 5, 10)

  private val intFieldMappings = Map(
    "Hallinnollinen luokka" -> "ADMINCLASS",
    "Toiminnallinen luokka" -> "functional_Class",
    "Liikennevirran suunta" -> "DIRECTIONTYPE",
    "Tielinkin tyyppi" -> "link_Type",
    "Kuntanumero" -> "MUNICIPALITYCODE",
    "Osoitenumerot oikealla alku" -> "FROM_RIGHT",
    "Osoitenumerot oikealla loppu" -> "TO_RIGHT",
    "Osoitenumerot vasemmalla alku" -> "FROM_LEFT",
    "Osoitenumerot vasemmalla loppu" -> "TO_LEFT",
    "Linkin tila" -> "CONSTRUCTIONTYPE"
  )

  private val codeValueFieldMappings = Map(
    "Tasosijainti" -> "VERTICALLEVEL"
  )

  private val fieldInOTH = List("link_Type", "functional_Class")

  private val textFieldMappings = Map(
    "Tien nimi (suomi)" -> "ROADNAME_FI",
    "Tien nimi (ruotsi)" -> "ROADNAME_SE",
    "Tien nimi (saame)" -> "ROADNAME_SM"
  )

  private val mandatoryFields = "Linkin ID"


  val mappings = textFieldMappings ++ intFieldMappings ++ codeValueFieldMappings

  val MandatoryParameters: Set[String] = mappings.keySet + mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String], hasTrafficDirectionChange: Boolean): Option[Long] = {
    try {
      if (hasTrafficDirectionChange) {
        RoadLinkDAO.get(RoadLinkDAO.TrafficDirection, roadLinkAttribute.linkId) match {
          case Some(value) => RoadLinkDAO.delete(RoadLinkDAO.TrafficDirection, roadLinkAttribute.linkId)
          case _ => None
        }
      }

      roadLinkAttribute.properties.map { prop =>
        val optionalLinkTypeValue: Option[Int] = RoadLinkDAO.get(prop.columnName, roadLinkAttribute.linkId)
        optionalLinkTypeValue match {
          case Some(existingValue) =>
            RoadLinkDAO.update(prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt, existingValue)
          case None =>
            RoadLinkDAO.insert(prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt)
        }
      }
      None
    } catch {
      case ex: Exception => Some(roadLinkAttribute.linkId)
    }
  }

  def updateRoadLinkInVVH(roadLinkVVHAttribute: CsvRoadLinkRow): Option[Long] = {
    val timeStamps = new java.util.Date().getTime
    val mapProperties = roadLinkVVHAttribute.properties.map { prop => prop.columnName -> prop.value }.toMap ++ Map("LAST_EDITED_DATE" -> timeStamps) ++ Map("OBJECTID" -> roadLinkVVHAttribute.objectID)
    vvhClient.complementaryData.updateVVHFeatures(mapProperties) match {
      case Right(error) => Some(roadLinkVVHAttribute.linkId)
      case _ => None
    }
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedLinkRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(LinkProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  def verifyValueCode(parameterName: String, parameterValue: String): ParsedLinkRow = {
    if (parameterValue.forall(_.isDigit) && autorizedValues.contains(parameterValue.toInt)) {
      (Nil, List(LinkProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def linkRowToProperties(csvRowWithHeaders: Map[String, Any]): ParsedLinkRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(mandatoryFields) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = LinkProperty(columnName = textFieldMappings(key), value = value) :: result._2)
        } else if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (codeValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueCode(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (mandatoryFields.contains(key) && !value.toString.forall(_.isDigit)) {
          result.copy(_1 = List(mandatoryFields) ::: result._1, _2 = result._2)
        } else
          result
      }
    }
  }

  def validateAdministrativeClass(optionalAdminClassValue: Option[Any], dataLocation: String): List[String] = {
    optionalAdminClassValue match {
      case Some(adminClassValue) if administrativeClassLimitations.contains(AdministrativeClass.apply(adminClassValue.toString.toInt)) =>
        List("AdminClass value State found on  " ++ dataLocation)
      case _ => List()
    }
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String): Long = {
    implicit val formats = DefaultFormats

    val logId =  create(username,  ROAD_LINK_LOG, fileName)

    try {
      val result = processing(inputStream)
      val response : String = result match {
        case ImportResultRoadLink(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty." //succesfully processed
        case ImportResultRoadLink(Nil, Nil, Nil, excludedLinks) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + Serialization.write(excludedLinks) //following links have been excluded
        case _ => Serialization.write(result)
      }
      update(logId, Status.OK, Some(response))
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
        throw e
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream): ImportResultRoadLink = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResultRoadLink()) { (result, row) =>
      def getCompletaryVVHInfo(linkId: Long) = {
        vvhClient.complementaryData.fetchByLinkId(linkId) match {
          case Some(vvhRoadlink) => (vvhRoadlink.attributes.get("OBJECTID"), vvhRoadlink.administrativeClass.value)
          case _ => None
        }
      }

      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = linkRowToProperties(row)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty) {
        result.copy(
          incompleteLinks = missingParameters match {
            case Nil => result.incompleteLinks
            case parameters => IncompleteLink(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteLinks
          },
          malformedLinks = malformedParameters match {
            case Nil => result.malformedLinks
            case parameters => MalformedLink(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedLinks
          })
      } else {
        val (objectId, oldAdminClassValue) = getCompletaryVVHInfo(row("Linkin ID").toInt) match {
          case None => (None, None)
          case (Some(objId), adminClass) => (objId, adminClass)
        }

        val unauthorizedAdminClass = (oldAdminClassValue match {
          case None => List("AdminClass value Unknown found on VVH")
          case adminClassValue => validateAdministrativeClass(Some(adminClassValue), "VVH")
        }) ++ validateAdministrativeClass(properties.filter(prop => prop.columnName == "ADMINCLASS").map(_.value).headOption, "CSV")

        if (unauthorizedAdminClass.isEmpty && objectId != None) {
          val (propertiesOTH, propertiesVVH) = properties.partition(a => fieldInOTH.contains(a.columnName))
          val hasDirectionType = propertiesVVH.exists(_.columnName == "DIRECTIONTYPE")

          withDynTransaction {
            if (propertiesOTH.nonEmpty || hasDirectionType) {
              val parsedRowOTH = CsvRoadLinkRow(row("Linkin ID").toInt, properties = propertiesOTH)
              updateRoadLinkOTH(parsedRowOTH, Some(userProvider.getCurrentUser().username), hasDirectionType) match {
                case None => result
                case Some(value) =>
                  result.copy(nonUpdatedLinks = NonUpdatedLink(linkId = value, csvRow = rowToString(row)) :: result.nonUpdatedLinks)
              }
            }
            if (propertiesVVH.nonEmpty) {
              val parsedRowVVH = CsvRoadLinkRow(row("Linkin ID").toInt, objectId.toString.toInt, properties = propertiesVVH)
              updateRoadLinkInVVH(parsedRowVVH) match {
                case None => result
                case Some(value) =>
                  dynamicSession.rollback()
                  result.copy(nonUpdatedLinks = NonUpdatedLink(linkId = value, csvRow = rowToString(row)) :: result.nonUpdatedLinks)
              }
            } else result
          }
        } else {
          result.copy(
            nonUpdatedLinks = objectId match {
              case None => NonUpdatedLink(linkId = row("Linkin ID").toInt, csvRow = rowToString(row)) :: result.nonUpdatedLinks
              case _ => result.nonUpdatedLinks
            },
            excludedLinks = unauthorizedAdminClass match {
              case Nil => result.excludedLinks
              case parameters => ExcludedLink(unauthorizedAdminClass = parameters, csvRow = rowToString(row)) :: result.excludedLinks
            }
          )
        }
      }
    }
  }

//  def dataImport(csvFileInputStream: InputStream, fileName: String, username: String) {
//    val id =  create(username,  ROAD_LINK_LOG, fileName)
//
//    try {
//      val result = importLinkAttribute(csvFileInputStream)
//      val response = result match {
//        case ImportResultRoadLink(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty." //succesfully processed
//        case ImportResultRoadLink(Nil, Nil, Nil, excludedLinks) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedLinks)) //following links have been excluded
//        case _ => pretty(Extraction.decompose(result))
//      }
//      update(id, Status.OK, Some(response))
//
//
//    }catch
//  {
//    case e: Exception =>
//      update(id, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
//      throw e
//  }
//  finally{
//    csvFileInputStream.close()
//  }
//  redirect(url("/log/" + id + "/" + roadLinkCsvImporter.ROAD_LINK_LOG)) */
//}
}

class MaintenanceRoadCsvImporter extends CsvDataImporterOperations {
  type MalformedParameters = List[String]
  type ParsedProperties = List[AssetProperty]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)

  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class MalformedAsset(malformedParameters: List[String], csvRow: String)
  case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)
  case class ImportResult(incompleteAssets: List[IncompleteAsset] = Nil,
                          malformedAssets: List[MalformedAsset] = Nil,
                          excludedAssets: List[ExcludedAsset] = Nil)

//  override type ImportResult = ImportResult

  case class AssetProperty(columnName: String, value: Any)
  case class CsvAssetRow(properties: Seq[AssetProperty])

  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  lazy val maintenanceService: MaintenanceService = new MaintenanceService(roadLinkService, eventbus)

  private val intFieldMappings = Map(
    "new_ko" -> "rightOfUse",
    "or_access" -> "maintenanceResponsibility",
    "linkid" -> "linkid"
  )

  val mappings = intFieldMappings

  private val mandatoryFields = List("linkid", "new_ko", "or_access")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedAssetRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else
          result
      }
    }
  }

  def getPropertyValue(maintenanceRoadAttributes: CsvAssetRow, propertyName: String) = {
    maintenanceRoadAttributes.properties.find(prop => prop.columnName == propertyName).map(_.value).get
  }

  def createMaintenanceRoads(maintenanceRoadAttributes: CsvAssetRow): Unit = {
    val linkId = getPropertyValue(maintenanceRoadAttributes, "linkid").asInstanceOf[Integer].toLong
    val newKoProperty = Props("huoltotie_kayttooikeus", "single_choice", getPropertyValue(maintenanceRoadAttributes, "rightOfUse").toString)
    val orAccessProperty = Props("huoltotie_huoltovastuu", "single_choice", getPropertyValue(maintenanceRoadAttributes, "maintenanceResponsibility").toString)

    roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(linkId)).map { roadlink =>
      val values = MaintenanceRoad(Seq(newKoProperty, orAccessProperty))

      maintenanceService.createWithHistory(MaintenanceRoadAsset.typeId, linkId, values,
        SideCode.BothDirections.value, Measures(0, roadlink.length), userProvider.getCurrentUser().username, Some(roadlink))
    }
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String) {
    implicit val formats = DefaultFormats

    val logId = create(username, MAINTENANCE_ROAD_LOG, fileName)
    try {
      val result = processing(inputStream)
      val response = result match {
        case ImportResult(Nil, Nil, Nil) => "CSV tiedosto käsitelty." //succesfully processed
        case ImportResult(Nil, excludedLinks, Nil) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + Serialization.write(excludedLinks)//following links have been excluded
        case _ => Serialization.write(result)
      }
      update(logId, Status.OK, Some(response))
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
        throw e
    } finally {
      inputStream.close()
    }
  }


  def processing(inputStream: InputStream): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
      val csvRow = row.map(r => (r._1.toLowerCase, r._2))
      val missingParameters = findMissingParameters(csvRow)
      val (malformedParameters, properties) = assetRowToProperties(csvRow)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty) {
        result.copy(
          incompleteAssets = missingParameters match {
            case Nil => result.incompleteAssets
            case parameters => IncompleteAsset(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteAssets
          },
          malformedAssets = malformedParameters match {
            case Nil => result.malformedAssets
            case parameters => MalformedAsset(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedAssets
          })

      } else {
        val parsedRow = CsvAssetRow(properties = properties)
        createMaintenanceRoads(parsedRow)
        result
      }

    }
  }
}

class MassTransitStopCsvImporter extends CsvDataImporterOperations {
    case class NonExistingAsset(externalId: Long, csvRow: String)
    case class IncompleteAsset(missingParameters: List[String], csvRow: String)
    case class MalformedAsset(malformedParameters: List[String], csvRow: String)
    case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)
    case class ImportResultMassTransitStop(nonExistingAssets: List[NonExistingAsset] = Nil,
                            incompleteAssets: List[IncompleteAsset] = Nil,
                            malformedAssets: List[MalformedAsset] = Nil,
                            excludedAssets: List[ExcludedAsset] = Nil)

  class AssetNotFoundException(externalId: Long) extends RuntimeException

  lazy val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressesService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val tierekisteriClient: TierekisteriMassTransitStopClient = tierekisteriMassTransitStopClient
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventbus, roadLinkService, roadAddressService)
  }

  case class CsvAssetRow(externalId: Long, properties: Seq[SimpleProperty])
  type MalformedParameters = List[String]
  type ParsedProperties = List[SimpleProperty]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)
  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  private def maybeInt(string: String): Option[Int] = {
    try {
      Some(string.toInt)
    } catch {
      case e: NumberFormatException => None
    }
  }

  private val isValidTypeEnumeration = Set(1, 2, 3, 4, 5, 99)

  private val singleChoiceValueMappings = Set(1, 2, 99).map(_.toString)

  private val stopAdministratorProperty = "tietojen_yllapitaja"

  private val stopAdministratorValueMappings = Set(1, 2, 3, 99).map(_.toString)

  private val textFieldMappings = Map(
    "Pysäkin nimi" -> "nimi_suomeksi" ,
    "Ylläpitäjän tunnus" -> "yllapitajan_tunnus",
    "Matkustajatunnus" -> "matkustajatunnus",
    "Pysäkin nimi ruotsiksi" -> "nimi_ruotsiksi",
    "Liikennöintisuunta" -> "liikennointisuunta",
    "Lisätiedot" -> "lisatiedot",
    "Vyöhyketieto" -> "vyohyketieto"
  )

  private val multipleChoiceFieldMappings = Map(
    "Pysäkin tyyppi" -> "pysakin_tyyppi"
  )

  private val singleChoiceFieldMappings = Map(
    "Aikataulu" -> "aikataulu",
    "Katos" -> "katos",
    "Mainoskatos" -> "mainoskatos",
    "Penkki" -> "penkki",
    "Pyöräteline" -> "pyorateline",
    "Sähköinen aikataulunäyttö" -> "sahkoinen_aikataulunaytto",
    "Valaistus" -> "valaistus",
    "Saattomahdollisuus henkilöautolla" -> "saattomahdollisuus_henkiloautolla",
    "Korotettu" -> "korotettu",
    "Roska-astia" -> "roska_astia",
    "Tietojen ylläpitäjä" -> stopAdministratorProperty
  )

  val mappings = textFieldMappings ++ multipleChoiceFieldMappings ++ singleChoiceFieldMappings

  val MandatoryParameters: Set[String] = mappings.keySet + "Valtakunnallinen ID"


  private def resultWithType(result: (MalformedParameters, List[SimpleProperty]), assetType: Int): ParsedAssetRow = {
    result.copy(_2 = result._2 match {
      case List(SimpleProperty("pysakin_tyyppi", xs)) => List(SimpleProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.toList))
      case _ => List(SimpleProperty("pysakin_tyyppi", Seq(PropertyValue(assetType.toString))))
    })
  }

  private def assetTypeToProperty(assetTypes: String): ParsedAssetRow = {
    val invalidAssetType = (List("Pysäkin tyyppi"), Nil)
    val types = assetTypes.split(',')
    if(types.isEmpty) invalidAssetType
    else {
      types.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, assetType) =>
        maybeInt(assetType.trim)
          .filter(isValidTypeEnumeration)
          .map(typeEnumeration => resultWithType(result, typeEnumeration))
          .getOrElse(invalidAssetType)
      }
    }
  }

  private def assetSingleChoiceToProperty(parameterName: String, assetSingleChoice: String): ParsedAssetRow = {
    // less than ideal design but the simplest solution. DO NOT REPEAT IF MORE FIELDS REQUIRE CUSTOM VALUE VALIDATION
    val isValidStopAdminstratorValue = singleChoiceFieldMappings(parameterName) == stopAdministratorProperty && stopAdministratorValueMappings(assetSingleChoice)

    if (singleChoiceValueMappings(assetSingleChoice) || isValidStopAdminstratorValue) {
      (Nil, List(SimpleProperty(singleChoiceFieldMappings(parameterName), List(PropertyValue(assetSingleChoice)))))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
    csvRowWithHeaders.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, parameter) =>
      val (key, value) = parameter
      if(isBlank(value)) {
        result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = SimpleProperty(publicId = textFieldMappings(key), values = Seq(PropertyValue(value))) :: result._2)
        } else if (multipleChoiceFieldMappings.contains(key)) {
          val (malformedParameters, properties) = assetTypeToProperty(value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (singleChoiceFieldMappings.contains(key)) {
          val (malformedParameters, properties) = assetSingleChoiceToProperty(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else {
          result
        }
      }
    }
  }

  private def municipalityValidation(municipality: Int): Unit = {
    if (!userProvider.getCurrentUser().isAuthorizedToWrite(municipality)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }
  }

  private def updateAssetByExternalId(externalId: Long, properties: Seq[SimpleProperty]): MassTransitStopWithProperties = {
    val optionalAsset = massTransitStopService.getMassTransitStopByNationalId(externalId, municipalityValidation)
    optionalAsset match {
      case Some(asset) =>
        massTransitStopService.updateExistingById(asset.id, None, properties.toSet, userProvider.getCurrentUser().username, (_, _) => Unit)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  private def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[SimpleProperty], roadTypeLimitations: Set[AdministrativeClass]): Either[AdministrativeClass, MassTransitStopWithProperties] = {
    class CsvImportMassTransitStop(val id: Long, val floating: Boolean, val roadLinkType: AdministrativeClass) extends FloatingAsset {}
    def massTransitStopTransformation(stop: PersistedMassTransitStop): (CsvImportMassTransitStop, Option[FloatingReason]) = {
      val roadLink = vvhClient.roadLinkData.fetchByLinkId(stop.linkId)
      val (floating, floatingReason) = massTransitStopService.isFloating(stop, roadLink)
      (new CsvImportMassTransitStop(stop.id, floating, roadLink.map(_.administrativeClass).getOrElse(Unknown)), floatingReason)
    }

    val optionalAsset = massTransitStopService.getByNationalId(externalId, municipalityValidation, massTransitStopTransformation)
    optionalAsset match {
      case Some(asset) =>
        val roadLinkType = asset.roadLinkType
        if (roadTypeLimitations(roadLinkType)) Right(massTransitStopService.updateExistingById(asset.id, None, properties.toSet, userProvider.getCurrentUser().username, (_, _) => Unit))
        else Left(roadLinkType)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'"} mkString ", "
  }

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def updateAsset(externalId: Long, properties: Seq[SimpleProperty], roadTypeLimitations: Set[AdministrativeClass]): ExcludedRoadLinkTypes = {
    // Remove livi-id from properties, we don't want to change is with CSV
    val propertiesWithoutLiviId = properties.filterNot(_.publicId == "yllapitajan_koodi")
    if(roadTypeLimitations.nonEmpty) {
      val result: Either[AdministrativeClass, MassTransitStopWithProperties] = updateAssetByExternalIdLimitedByRoadType(externalId, propertiesWithoutLiviId, roadTypeLimitations)
      result match {
        case Left(roadLinkType) => List(roadLinkType)
        case _ => Nil
      }
    } else {
      updateAssetByExternalId(externalId, propertiesWithoutLiviId)
      Nil
    }
  }


  private def fork(f: => Unit): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        f
      }
    }).start()
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String, roadTypeLimitations: Set[AdministrativeClass]) : Unit = {

    val logId = create(username, BUS_STOP_LOG, fileName)

    implicit val formats = DefaultFormats
    fork {
      // Current user is stored in a thread-local variable (feel free to provide better solution)
      try {
        val result = processing(inputStream, roadTypeLimitations)
        val response = result match {
          case ImportResultMassTransitStop(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty."
          case ImportResultMassTransitStop(Nil, Nil, Nil, excludedAssets) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + Serialization.write(Extraction.decompose(excludedAssets))
          case _ => Serialization.write(Extraction.decompose(result))
        }
        update(logId, Status.OK, Some(response) )
      } catch {
        case e: Exception =>
          update(logId, Status.Abend, Some( "Latauksessa tapahtui odottamaton virhe: " + e.toString))
          throw e

      } finally {
        inputStream.close()
      }
    }
  }

  def processing(inputStream: InputStream, roadTypeLimitations: Set[AdministrativeClass] = Set()): ImportResultMassTransitStop = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResultMassTransitStop()) { (result, row) =>
      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = assetRowToProperties(row)
      if(missingParameters.isEmpty && malformedParameters.isEmpty) {
        val parsedRow = CsvAssetRow(externalId = row("Valtakunnallinen ID").toLong, properties = properties)
        try {
          val excludedAssets = updateAsset(parsedRow.externalId, parsedRow.properties, roadTypeLimitations)
            .map(excludedRoadLinkType => ExcludedAsset(affectedRoadLinkType = excludedRoadLinkType.toString, csvRow = rowToString(row)))
          result.copy(excludedAssets = excludedAssets ::: result.excludedAssets)
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingAssets = NonExistingAsset(externalId = parsedRow.externalId, csvRow = rowToString(row)) :: result.nonExistingAssets)
        }
      } else {
        result.copy(
          incompleteAssets = missingParameters match {
            case Nil => result.incompleteAssets
            case parameters => IncompleteAsset(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteAssets
          },
          malformedAssets = malformedParameters match {
            case Nil => result.malformedAssets
            case parameters => MalformedAsset(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedAssets
          }
        )
      }
    }
  }
}

class CsvDataImporter extends CsvDataImporterOperations
