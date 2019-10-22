package fi.liikennevirasto.digiroad2

import java.io.InputStream
import java.util.Properties

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset.{PointAssetValue, _}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import org.apache.commons.lang3.StringUtils.isBlank
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.TrafficSignTypeGroup.AdditionalPanels
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.service.pointasset.{AdditionalPanelInfo, IncomingTrafficSign, TrafficSignService}
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter.viiteClient
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import java.io.InputStreamReader
import java.text.Normalizer

import fi.liikennevirasto.digiroad2.asset.ServicePointsClass.{Unknown => _, _}
import fi.liikennevirasto.digiroad2.dao.pointasset.{IncomingService, IncomingServicePoint}

import scala.util.Try

sealed trait Status {
  def value : Int
  def description: String
  def descriptionFi: String
}

object Status {
  val values : Set[Status] = Set(InProgress, OK, NotOK, Abend)

  def apply(value: Int) : Status = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object InProgress extends Status {def value = 1; def description = "In progress ..."; def descriptionFi = "Lataus kesken"}
  case object OK extends Status {def value = 2; def description = "All records was treated "; def descriptionFi = "Kaikki kohteet käsitelty"}
  case object NotOK extends Status {def value = 3; def description = "Process Executed but some record fails"; def descriptionFi = "Kaikki kohteet käsitelty, joitakin virhetilanteita"}
  case object Abend extends Status {def value = 4; def description = "Process fail"; def descriptionFi = "Lataus epäonnistunut"}
  case object Unknown extends Status {def value = 99; def description = "Unknown Status Type"; def descriptionFi = "Tuntematon tilakoodi"}
}

case class ImportStatusInfo(id: Long, status: Int, statusDescription: String, fileName: String, createdBy: Option[String], createdDate: Option[DateTime], logType: String, content: Option[String])

class RoadLinkNotFoundException(linkId: Int) extends RuntimeException

case class IncompleteRow(missingParameters: List[String], csvRow: String)
case class MalformedRow(malformedParameters: List[String], csvRow: String)
case class ExcludedRow(affectedRows: String, csvRow: String)
case class AssetProperty(columnName: String, value: Any)

sealed trait ImportResult {
  val incompleteRows: List[IncompleteRow]
  val malformedRows: List[MalformedRow]
  val excludedRows: List[ExcludedRow]
}

trait CsvDataImporterOperations {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def eventBus: DigiroadEventBus

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService(viiteClient)
  }

  lazy val tierekisteriMassTransitStopClient: TierekisteriMassTransitStopClient = {
    new TierekisteriMassTransitStopClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build)
  }

  lazy val roadAddressesService: RoadAddressService = {
    new RoadAddressService(viiteClient)
  }

  type MalformedParameters = List[String]
  type ParsedProperties = List[AssetProperty]
  type ParsedRow = (MalformedParameters, ParsedProperties)

  type ImportResultData <: ImportResult

  val logInfo : String

  def mappingContent(result: ImportResultData): String  = {
    val excludedResult = result.excludedRows.map{rows => "<li>" + rows.affectedRows ->  rows.csvRow + "</li>"}
    val incompleteResult = result.incompleteRows.map{ rows=> "<li>" + rows.missingParameters.mkString(";") -> rows.csvRow + "</li>"}
    val malformedResult = result.malformedRows.map{ rows => "<li>" + rows.malformedParameters.mkString(";") -> rows.csvRow + "</li>"}

    s"<ul> excludedLinks: ${excludedResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul> incompleteRows: ${incompleteResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul> malformedRows: ${malformedResult.mkString.replaceAll("[(|)]{1}","")} </ul>"
  }

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

    def getByUser(username: String) : Seq[ImportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        importLogDao.getByUser(username)
      }
    }

    def getById(id: Long) : Option[ImportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        importLogDao.get(id)
      }
    }

    def getByIds(ids: Set[Long]) : Seq[ImportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        importLogDao.getByIds(ids)
      }
    }

    def update(id: Long, status: Status, content: Option[String] = None) : Long  = {
      OracleDatabase.withDynTransaction {
        importLogDao.update(id, status, content)
      }
    }

    def create(username: String, logInfo: String, fileName: String) : Long  = {
      OracleDatabase.withDynTransaction {
        importLogDao.create(username, logInfo, fileName)
      }
    }
}

class TrafficSignCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl, eventBusImpl) {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"
  override val logInfo = "traffic sign import"

  case class CsvTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty], validityDirection: Int, bearing: Option[Int], mValue: Double, roadLink: RoadLink, isFloating: Boolean)
  case class NotImportedData(reason: String, csvRow: String)
  case class CsvAssetRowAndRoadLink(properties: CsvAssetRow, roadLink: Seq[VVHRoadlink])

  case class ImportResultTrafficSign(incompleteRows: List[IncompleteRow] = Nil,
                          malformedRows: List[MalformedRow] = Nil,
                          excludedRows: List[ExcludedRow] = Nil,
                          notImportedData: List[NotImportedData] = Nil,
                          createdData: List[CsvAssetRowAndRoadLink] = Nil) extends ImportResult

  type ImportResultData = ImportResultTrafficSign

  case class CsvAssetRow(properties: Seq[AssetProperty])
  type ParsedCsv = (MalformedParameters, Seq[CsvAssetRowAndRoadLink])

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, eventBusImpl)

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
  val mappings : Map[String, String] = longValueFieldMappings ++ nonMandatoryMappings ++ codeValueFieldMappings

  private val mandatoryFields = List("koordinaatti x", "koordinaatti y", "liikennemerkin tyyppi")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  private def verifyDoubleType(parameterName: String, parameterValue: String): ParsedRow = {
    if(parameterValue.matches("[0-9.]*")) {
      (Nil, List(AssetProperty(columnName = longValueFieldMappings(parameterName), value = BigDecimal(parameterValue))))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def verifyValueCode(parameterName: String, parameterValue: String): ParsedRow = {
    if(parameterValue.forall(_.isDigit) && TrafficSignType.applyTRValue(parameterValue.toInt).source.contains("CSVimport")){
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    }else{
      (List(parameterName), Nil)
    }
  }

  def tryToInt(propertyValue: String ) : Option[Int] = {
    Try(propertyValue.toInt).toOption
  }

  private def verifyData(parsedRow: CsvAssetRow, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>

        val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(user, Point(lon.toLong, lat.toLong))
        if(roadLinks.isEmpty) {
          (List(s"Unathorized municipality or nonexistent roadlink near asset position"), Seq())
        } else
          (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
      case _ =>
        (Nil, Nil) //That condition is already checked on assetRowToProperties
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
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

  private def generateBaseProperties(trafficSignAttributes: CsvAssetRow) : Set[SimplePointAssetProperty] = {
    val valueProperty = tryToInt(getPropertyValue(trafficSignAttributes, "value").toString).map { value =>
      SimplePointAssetProperty(valuePublicId, Seq(PropertyValue(value.toString)))}

    val additionalInfo = getPropertyValue(trafficSignAttributes, "additionalInfo").toString
    val additionalProperty = if(additionalInfo.nonEmpty)
        Some(SimplePointAssetProperty(infoPublicId, Seq(PropertyValue(additionalInfo))))
      else
        None

    val typeProperty = SimplePointAssetProperty(typePublicId, Seq(PropertyValue(TrafficSignType.applyTRValue(getPropertyValue(trafficSignAttributes, "trafficSignType").toString.toInt).OTHvalue.toString)))

    Set(Some(typeProperty), valueProperty, additionalProperty).flatten
  }

  def recalculateBearing(bearing: Option[Int]): (Option[Int], Option[Int]) = {
    bearing match {
      case Some(assetBearing) =>
        val validityDirection = trafficSignService.getAssetValidityDirection(assetBearing)
        val readjustedBearing = if(validityDirection == SideCode.AgainstDigitizing.value) {
          if(assetBearing > 90 && assetBearing < 180)
            assetBearing + 180
          else
            Math.abs(assetBearing - 180)
        } else assetBearing

        (Some(readjustedBearing), Some(validityDirection))
      case _ =>
        (None, None)
    }
  }

  def createTrafficSigns(trafficSignAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultTrafficSign ): ImportResultTrafficSign = {

    val signs = trafficSignAttributes.map { trafficSignAttribute =>
      val properties = trafficSignAttribute.properties
      val nearbyLinks = trafficSignAttribute.roadLink
      val optBearing = tryToInt(getPropertyValue(properties, "bearing").toString)
      val twoSided = getPropertyValue(properties, "twoSided") match {
        case "1" => true
        case _ => false
      }
      val lon = getPropertyValue(properties, "lon").asInstanceOf[BigDecimal].toLong
      val lat = getPropertyValue(properties, "lat").asInstanceOf[BigDecimal].toLong

      val (assetBearing, assetValidityDirection) = recalculateBearing(optBearing)

      val closestRoadLinks = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)

      val possibleRoadLinks = roadLinkService.filterRoadLinkByBearing(assetBearing, assetValidityDirection, Point(lon, lat), closestRoadLinks)

      val roadLinks = possibleRoadLinks.filter(_.administrativeClass != State)
      val roadLink = if (roadLinks.nonEmpty) {
        possibleRoadLinks.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(Point(lon.toLong, lat.toLong), r.geometry))
      } else
        closestRoadLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon.toLong, lat.toLong), r.geometry))

      val validityDirection = if(assetBearing.isEmpty) {
        trafficSignService.getValidityDirection(Point(lon, lat), roadLink, assetBearing, twoSided)
      } else assetValidityDirection.get

      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(lon, lat), roadLink.geometry)

      (properties, CsvTrafficSign(lon, lat, roadLink.linkId, generateBaseProperties(properties), validityDirection, assetBearing, mValue, roadLink, (assetBearing.isEmpty && roadLinks.isEmpty || roadLinks.size > 1) || (assetBearing.nonEmpty && roadLinks.isEmpty)))
    }

    val (additionalPanelInfo, trafficSignInfo) = signs.partition{ case(_, sign) =>
      TrafficSignType.applyOTHValue(sign.propertyData.find(p => p.publicId == typePublicId).get.values.head.asInstanceOf[PropertyValue].propertyValue.toString.toInt).group == AdditionalPanels}

    val additionalPanels = additionalPanelInfo.map {case (csvRow, panel) => (csvRow, AdditionalPanelInfo(panel.mValue, panel.linkId, panel.propertyData, panel.validityDirection, Some(Point(panel.lon, panel.lat))))}.toSet

    val usedAdditionalPanels = trafficSignInfo.flatMap { case (csvRow, sign) =>
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(sign.lon, sign.lat), sign.roadLink.geometry)
      val bearing = if(sign.bearing.isEmpty && !sign.isFloating)
        Some(GeometryUtils.calculateBearing(sign.roadLink.geometry, Some(mValue)))
      else
        sign.bearing

      val signType = sign.propertyData.find(p => p.publicId == typePublicId).get.values.headOption.get.asInstanceOf[PropertyValue].propertyValue.toString.toInt
      val filteredAdditionalPanel = trafficSignService.distinctPanels(trafficSignService.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, signType, sign.roadLink.geometry, additionalPanels.map(_._2)))

      if (filteredAdditionalPanel.size <= 3) {
        val propertyData = trafficSignService.additionalPanelProperties(filteredAdditionalPanel) ++ sign.propertyData
        try {
          trafficSignService.createFromCoordinates(IncomingTrafficSign(sign.lon, sign.lat, sign.roadLink.linkId, propertyData, sign.validityDirection, bearing), sign.roadLink, user.username, sign.isFloating)
        } catch {
          case ex: NoSuchElementException => NotImportedData(reason = "Additional Panel Without main Sign Type", csvRow = rowToString(csvRow.properties.flatMap{x => Map(x.columnName -> x.value)}.toMap))
        }
        filteredAdditionalPanel
      } else Seq()
    }

    val unusedAdditionalPanels = additionalPanels.filterNot{ panel => usedAdditionalPanels.contains(panel._2)}.toSeq.map { notImportedAdditionalPanel =>
      NotImportedData(reason = "Additional Panel Without main Sign Type", csvRow = rowToString(notImportedAdditionalPanel._1.properties.flatMap{x => Map(x.columnName -> x.value)}.toMap))
    }

    result.copy(notImportedData = unusedAdditionalPanels.toList ++ result.notImportedData)
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, municipalitiesToExpire: Set[Int]) : Unit = {
  val logId = create(user.username, logInfo, fileName)

    try {
      val result = processing(inputStream, municipalitiesToExpire, user)
      result match {
        case ImportResultTrafficSign(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result) +
            s"<ul>notImportedData: ${result.notImportedData.map{ rows => "<li>" + rows.reason -> rows.csvRow + "</li>"}.mkString.replaceAll("[(|)]{1}","")}</ul>"
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, municipalitiesToExpire: Set[Int], user: User): ImportResultTrafficSign = {
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
        val (notImportedParameters, parsedRowAndRoadLink) = verifyData(CsvAssetRow(properties), user)

        if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
          result.copy(
            incompleteRows = missingParameters match {
              case Nil => result.incompleteRows
              case parameters =>
                IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
            },
            malformedRows = malformedParameters match {
              case Nil => result.malformedRows
              case parameters =>
                MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
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
                CsvAssetRowAndRoadLink(properties = parameters.head.properties, roadLink = parameters.head.roadLink) :: result.createdData
            })
        }
      }

      createTrafficSigns(result.createdData, user, result)
      }
    }
}

class RoadLinkCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl, eventBusImpl) {
override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
override def roadLinkService: RoadLinkService = roadLinkServiceImpl
override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

  case class NonUpdatedLink(linkId: Long, csvRow: String)
  case class ImportResultRoadLink(nonUpdatedLinks: List[NonUpdatedLink] = Nil,
                                  incompleteRows: List[IncompleteRow] = Nil,
                                  malformedRows: List[MalformedRow] = Nil,
                                  excludedRows: List[ExcludedRow] = Nil) extends ImportResult

  case class CsvRoadLinkRow(linkId: Int, objectID: Int = 0, properties: Seq[AssetProperty])

  type ImportResultData = ImportResultRoadLink

  override val logInfo: String = "road link import"

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

  val mappings : Map[String, String] = textFieldMappings ++ intFieldMappings ++ codeValueFieldMappings

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

      roadLinkAttribute.properties.foreach { prop =>
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

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  def verifyValueCode(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit) && autorizedValues.contains(parameterValue.toInt)) {
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def linkRowToProperties(csvRowWithHeaders: Map[String, Any]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(mandatoryFields) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = textFieldMappings(key), value = value) :: result._2)
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
    val logId =  create(username,  logInfo, fileName)

    try {
      val result = processing(inputStream, username)
      result match {
        case ImportResultRoadLink(Nil, Nil, Nil, Nil) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result) +
          s"<ul>nonExistingAssets: ${result.nonUpdatedLinks.map{ rows => "<li>" + rows.linkId -> rows.csvRow + "</li>"}.mkString.replaceAll("[(|)]{1}","")}</ul>"
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, username: String): ImportResultRoadLink = {
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
          incompleteRows = missingParameters match {
            case Nil => result.incompleteRows
            case parameters => IncompleteRow(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteRows
          },
          malformedRows = malformedParameters match {
            case Nil => result.malformedRows
            case parameters => MalformedRow(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedRows
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
              updateRoadLinkOTH(parsedRowOTH, Some(username), hasDirectionType) match {
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
            excludedRows = unauthorizedAdminClass match {
              case Nil => result.excludedRows
              case parameters => ExcludedRow(affectedRows = parameters.mkString("/"), csvRow = rowToString(row)) :: result.excludedRows
            }
          )
        }
      }
    }
  }
}

class MaintenanceRoadCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl, eventBusImpl) {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

  case class ImportMaintenanceRoadResult(incompleteRows: List[IncompleteRow] = Nil,
                                         malformedRows: List[MalformedRow] = Nil,
                                         excludedRows: List[ExcludedRow] = Nil) extends ImportResult

  type ImportResultData = ImportMaintenanceRoadResult
  case class CsvAssetRow(properties: Seq[AssetProperty])
  override val logInfo: String = "maintenance import"

  lazy val maintenanceService: MaintenanceService = new MaintenanceService(roadLinkService, eventBusImpl)

  private val intFieldMappings = Map(
    "new_ko" -> "rightOfUse",
    "or_access" -> "maintenanceResponsibility",
    "linkid" -> "linkid"
  )

  val mappings : Map[String, String] = intFieldMappings

  private val mandatoryFields = List("linkid", "new_ko", "or_access")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
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

  def getPropertyValue(maintenanceRoadAttributes: CsvAssetRow, propertyName: String): DynamicPropertyValue = {
    DynamicPropertyValue(maintenanceRoadAttributes.properties.find(prop => prop.columnName == propertyName).map(_.value).get)
  }

  def createMaintenanceRoads(maintenanceRoadAttributes: CsvAssetRow, username: String): Unit = {
    val linkId = getPropertyValue(maintenanceRoadAttributes, "linkid").asInstanceOf[Integer].toLong
    val newKoProperty = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(getPropertyValue(maintenanceRoadAttributes, "rightOfUse")))
    val orAccessProperty = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(getPropertyValue(maintenanceRoadAttributes, "maintenanceResponsibility")))

    roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(linkId)).map { roadlink =>
      val values = DynamicValue(DynamicAssetValue(Seq(newKoProperty, orAccessProperty)))
      maintenanceService.createWithHistory(MaintenanceRoadAsset.typeId, linkId, values,
        SideCode.BothDirections.value, Measures(0, roadlink.length), username, Some(roadlink))
    }
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String) {

    val logId = create(username, logInfo, fileName)
    try {
      val result = processing(inputStream, username)
      result match {
        case ImportMaintenanceRoadResult(Nil, Nil, Nil) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result)
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
    } finally {
      inputStream.close()
    }
  }


  def processing(inputStream: InputStream, username: String): ImportMaintenanceRoadResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportMaintenanceRoadResult()) { (result, row) =>
      val csvRow = row.map(r => (r._1.toLowerCase, r._2))
      val missingParameters = findMissingParameters(csvRow)
      val (malformedParameters, properties) = assetRowToProperties(csvRow)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty) {
        result.copy(
          incompleteRows = missingParameters match {
            case Nil => result.incompleteRows
            case parameters => IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
          },
          malformedRows = malformedParameters match {
            case Nil => result.malformedRows
            case parameters => MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
          })

      } else {
        val parsedRow = CsvAssetRow(properties = properties)
        createMaintenanceRoads(parsedRow, username)
        result
      }

    }
  }
}

class MassTransitStopCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl, eventBusImpl) {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

    case class NonExistingAsset(externalId: Long, csvRow: String)
    case class GenericException(reason: String, csvRow: String)
    case class ImportResultMassTransitStop(nonExistingAssets: List[NonExistingAsset] = Nil,
                                           incompleteRows: List[IncompleteRow] = Nil,
                                           malformedRows: List[MalformedRow] = Nil,
                                           excludedRows: List[ExcludedRow] = Nil,
                                           genericExceptionRows: List[GenericException] = Nil) extends ImportResult

  class AssetNotFoundException(externalId: Long) extends RuntimeException
  case class CsvAssetRow(externalId: Long, properties: Seq[AssetProperty])
  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  type ImportResultData = ImportResultMassTransitStop

  override val logInfo: String = "bus stop import"
  lazy val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val tierekisteriClient: TierekisteriMassTransitStopClient = tierekisteriMassTransitStopClient
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventBusImpl, roadLinkServiceImpl, roadAddressService)
  }

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

  val mappings : Map[String, String]= textFieldMappings ++ multipleChoiceFieldMappings ++ singleChoiceFieldMappings

  val MandatoryParameters: Set[String] = mappings.keySet + "Valtakunnallinen ID"


  private def resultWithType(result: (MalformedParameters, List[AssetProperty]), assetType: Int): ParsedRow = {
    result.copy(_2 = result._2 match {
      case List(AssetProperty("pysakin_tyyppi", xs)) => List(AssetProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.asInstanceOf[Seq[PointAssetValue]].toList))
      case _ => List(AssetProperty("pysakin_tyyppi", Seq(PropertyValue(assetType.toString))))
    })
  }

  private def assetTypeToProperty(assetTypes: String): ParsedRow = {
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

  private def assetSingleChoiceToProperty(parameterName: String, assetSingleChoice: String): ParsedRow = {
    // less than ideal design but the simplest solution. DO NOT REPEAT IF MORE FIELDS REQUIRE CUSTOM VALUE VALIDATION
    val isValidStopAdminstratorValue = singleChoiceFieldMappings(parameterName) == stopAdministratorProperty && stopAdministratorValueMappings(assetSingleChoice)

    if (singleChoiceValueMappings(assetSingleChoice) || isValidStopAdminstratorValue) {
      (Nil, List(AssetProperty(singleChoiceFieldMappings(parameterName), List(PropertyValue(assetSingleChoice)))))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, parameter) =>
      val (key, value) = parameter
      if(isBlank(value)) {
        result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = textFieldMappings(key), value = Seq(PropertyValue(value))) :: result._2)
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

  private def municipalityValidation(municipality: Int)(user: User): Unit = {
    if (!user.isAuthorizedToWrite(municipality)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }
  }

  private def updateAssetByExternalId(externalId: Long, properties: Seq[AssetProperty], user: User): MassTransitStopWithProperties = {
    val optionalAsset = massTransitStopService.getMassTransitStopByNationalId(externalId, municipalityValidation)
    optionalAsset match {
      case Some(asset) =>
        massTransitStopService.updateExistingById(asset.id, None, properties.map(prop =>SimplePointAssetProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet, user.username, (_, _) => Unit)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  private def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], username: String): Either[AdministrativeClass, MassTransitStopWithProperties] = {
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
        if (roadTypeLimitations(roadLinkType)) Right(massTransitStopService.updateExistingById(asset.id, None, properties.map(prop =>SimplePointAssetProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet, username, (_, _) => Unit))
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

  def updateAsset(externalId: Long, properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], user: User): ExcludedRoadLinkTypes = {
    // Remove livi-id from properties, we don't want to change is with CSV
    val propertiesWithoutLiviId = properties.filterNot(_.columnName == "yllapitajan_koodi")
    if(roadTypeLimitations.nonEmpty) {
      val result: Either[AdministrativeClass, MassTransitStopWithProperties] = updateAssetByExternalIdLimitedByRoadType(externalId, propertiesWithoutLiviId, roadTypeLimitations, user.username)
      result match {
        case Left(roadLinkType) => List(roadLinkType)
        case _ => Nil
      }
    } else {
      updateAssetByExternalId(externalId, propertiesWithoutLiviId, user)
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

  def importAssets(inputStream: InputStream, fileName: String, user: User, roadTypeLimitations: Set[AdministrativeClass]) : Unit = {

    val logId = create(user.username, logInfo, fileName)
    fork {
      try {
        val result = processing(inputStream, user, roadTypeLimitations)
        result match {
          case ImportResultMassTransitStop(Nil, Nil, Nil, Nil, Nil) => update(logId, Status.OK)
          case _ =>
            val content = mappingContent(result) +
              s"<ul>nonExistingAssets: ${result.nonExistingAssets.map{ rows => "<li>" + rows.externalId -> rows.csvRow + "</li>"}.mkString.replaceAll("[(|)]{1}","")}</ul>" +
              s"<ul>clientExceptions: ${result.genericExceptionRows.map{ rows => "<li>" + rows.reason -> rows.csvRow + "</li>"}.mkString.replaceAll("[(|)]{1}","")}</ul>"
            update(logId, Status.NotOK, Some(content))
        }
      } catch {
        case e: Exception =>
          update(logId, Status.Abend, Some( "Latauksessa tapahtui odottamaton virhe: " + e.toString))
      } finally {
        inputStream.close()
      }
    }
  }

  def processing(inputStream: InputStream, user: User, roadTypeLimitations: Set[AdministrativeClass] = Set()): ImportResultMassTransitStop = {
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
          val excludedRows = updateAsset(parsedRow.externalId, parsedRow.properties, roadTypeLimitations, user)
            .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
          result.copy(excludedRows = excludedRows ::: result.excludedRows)
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingAssets = NonExistingAsset(externalId = parsedRow.externalId, csvRow = rowToString(row)) :: result.nonExistingAssets)
          case ex: Exception => result.copy(genericExceptionRows = GenericException(reason = ex.getMessage,csvRow = rowToString(row)) :: result.genericExceptionRows)
        }
      } else {
        result.copy(
          incompleteRows = missingParameters match {
            case Nil => result.incompleteRows
            case parameters => IncompleteRow(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteRows
          },
          malformedRows = malformedParameters match {
            case Nil => result.malformedRows
            case parameters => MalformedRow(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedRows
          }
        )
      }
    }
  }
}

class PointAssetCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl, eventBusImpl) {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient


  case class CsvPointAssetRow(properties: Seq[AssetProperty])
  case class CsvAssetRowAndRoadLink(properties: CsvPointAssetRow, roadLink: Seq[VVHRoadlink])

  case class NotImportedData(reason: String, csvRow: String)
  case class ImportResultPointAsset(incompleteRows: List[IncompleteRow] = Nil,
                                    malformedRows: List[MalformedRow] = Nil,
                                    excludedRows: List[ExcludedRow] = Nil,
                                    notImportedData: List[NotImportedData] = Nil,
                                    createdData: List[CsvAssetRowAndRoadLink] = Nil) extends ImportResult

  case class CsvBasePointAsset(incomingPointAsset: IncomingPointAsset, roadLink: RoadLink, notImportedData: Seq[NotImportedData], isFloating: Boolean)

  type ImportResultData = ImportResultPointAsset
  type ParsedCsv = (MalformedParameters, Seq[CsvAssetRowAndRoadLink])

  override val logInfo = "point asset import"

  final val MinimumDistanceFromRoadLink: Double = 3.0

  final val commonFieldsMapping = Map(
    "koordinaatti x" -> "lon",
    "koordinaatti y" -> "lat"
  )

  val longValueFieldsMapping: Map[String, String] = Map()
  val codeValueFieldsMapping: Map[String, String] = Map()
  val stringValueFieldsMapping: Map[String, String] = Map()

  val mandatoryFieldsMapping: Map[String, String] = Map()
  val specificFieldsMapping: Map[String, String] = Map()
  val nonMandatoryFieldsMapping: Map[String, String] = Map()

  val mandatoryFields: Set[String] = Set()

  def checkMinimumDistanceFromRoadLink(pointPosition: Point, linkGeometry: Seq[Point]): Boolean = {
    GeometryUtils.minimumDistance(pointPosition, linkGeometry) >= MinimumDistanceFromRoadLink
  }

  def findMissingParameters(csvRoadWithHeaders: Map[String, String]): List[String] = {
    mandatoryFields.diff(csvRoadWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": " + value + "'" } mkString ", "
  }

  def verifyDoubleType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.matches("[0-9.]*")) {
      (Nil, List(AssetProperty(columnName = longValueFieldsMapping(parameterName), value = BigDecimal(parameterValue))))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyCodeType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit)) {
      (Nil, List(AssetProperty(columnName = codeValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyStringType(parameterName: String, parameterValue: String): ParsedRow = {
    if(parameterValue.trim.forall(_.isLetter)) {
      (Nil, List(AssetProperty(columnName = stringValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def getPropertyValue(pointAssetAttributes: CsvPointAssetRow, propertyName: String): Any = {
    pointAssetAttributes.properties.find(prop => prop.columnName == propertyName).map(_.value).get
  }

  def getPropertyValueOption(pointAssetAttributes: CsvPointAssetRow, propertyName: String): Option[Any] = {
    val property = pointAssetAttributes.properties.find(prop => prop.columnName == propertyName)
    if(property.exists(prop => prop.value.toString.trim.nonEmpty)) property.map(_.value) else None
  }

  def getCoordinatesFromProperties(csvProperties: CsvPointAssetRow): Point = {
    val lon = getPropertyValue(csvProperties, "lon").asInstanceOf[BigDecimal].toLong
    val lat = getPropertyValue(csvProperties, "lat").asInstanceOf[BigDecimal].toLong
    Point(lon, lat)
  }

  def verifyData(parsedRow: CsvPointAssetRow, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(user, Point(lon.toLong, lat.toLong))
        roadLinks.isEmpty match {
          case true => (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
          case false => (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
        }
      case _ =>
        (Nil, Nil)
    }
  }

  def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) {
      (result, parameter) =>
        val (key, value) = parameter

        if (isBlank(value.toString)) {
          if (mandatoryFields.contains(key))
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
            result
        } else {
          if (longValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (codeValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyCodeType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (stringValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyStringType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if(mandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = mandatoryFieldsMapping(key), value = value) :: result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
            result
        }
    }
}

  def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = throw new UnsupportedOperationException("Not supported method")

  def importAssets(inputStream: InputStream, fileName: String, user: User): Unit = {
    val logId = create(user.username, logInfo, fileName)

    try {
      val result = processing(inputStream, user)
      result match {
        case ImportResultPointAsset(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result) +
            s"<ul>notImportedData: ${result.notImportedData.map{ rows => "<li>" + rows.reason -> rows.csvRow + "</li>"}.mkString.replaceAll("[(|)]{1}","")}</ul>"
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Problems creating point asset: " + e.toString))
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, user: User): ImportResultPointAsset = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    withDynTransaction {
      val result = csvReader.allWithHeaders().foldLeft(ImportResultPointAsset()) {
        (result, row) =>
          val csvRow = row.map(r => (r._1.toLowerCase(), r._2))
          val missingParameters = findMissingParameters(csvRow)
          val (malformedParameters, properties) = assetRowToProperties(csvRow)
          val (notImportedParameters, parsedRowAndRoadLink) = verifyData(CsvPointAssetRow(properties), user)

          if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
            result.copy(
              incompleteRows = missingParameters match {
                case Nil => result.incompleteRows
                case parameters =>
                  IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
              },
              malformedRows = malformedParameters match {
                case Nil => result.malformedRows
                case parameters =>
                  MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
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
                  CsvAssetRowAndRoadLink(properties = parameters.head.properties, roadLink = parameters.head.roadLink) :: result.createdData
              })
          }
      }
      createAsset(result.createdData, user, result)
    }
  }

}

class ObstaclesCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter(roadLinkServiceImpl, eventBusImpl) {

  override val longValueFieldsMapping: Map[String, String] = commonFieldsMapping
  override val codeValueFieldsMapping: Map[String, String] = Map("esterakennelman tyyppi" -> "type")
  override val mandatoryFieldsMapping: Map[String, String] = commonFieldsMapping ++ codeValueFieldsMapping

  override val mandatoryFields: Set[String] = mandatoryFieldsMapping.keySet

  lazy val obstaclesService: ObstacleService = new ObstacleService(roadLinkService)

  private val allowedTypeValues: Seq[Int] = Seq(1, 2)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val notImportedObstacle = pointAssetAttributes.flatMap { obstacleAttribute =>
      val csvProperties = obstacleAttribute.properties
      val nearbyLinks = obstacleAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)
      val obstacleType = getPropertyValue(csvProperties, "type").asInstanceOf[String].toInt

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      val validData =
        if(!allowedTypeValues.contains(obstacleType))
          Seq(NotImportedData(reason = s"Obstacle type $obstacleType does not exist.", csvRow = rowToString(csvProperties.properties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
        else
          Seq()

      if(validData.isEmpty)
        obstaclesService.createFromCoordinates(IncomingObstacle(position.x, position.y, nearestRoadLink.linkId, Set(SimplePointAssetProperty(obstaclesService.typePublicId, Seq(PropertyValue(obstacleType.toString))))), nearestRoadLink, user.username, floating)

      validData
    }

    result.copy(notImportedData = notImportedObstacle.toList ++ result.notImportedData)
  }
}

class TrafficLightsCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter(roadLinkServiceImpl, eventBusImpl) {
  override val longValueFieldsMapping = commonFieldsMapping
  override val mandatoryFieldsMapping = commonFieldsMapping

  override val mandatoryFields = mandatoryFieldsMapping.keySet

  lazy val trafficLightsService: TrafficLightService = new TrafficLightService(roadLinkService)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    pointAssetAttributes.foreach { trafficLightAttribute =>
      val csvProperties = trafficLightAttribute.properties
      val nearbyLinks = trafficLightAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      trafficLightsService.createFromCoordinates(IncomingTrafficLight(position.x, position.y, nearestRoadLink.linkId, Set()), nearestRoadLink, user.username, floating)
    }

    result
  }
}

class PedestrianCrossingCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter(roadLinkServiceImpl, eventBusImpl) {
  override val longValueFieldsMapping: Map[String, String] = commonFieldsMapping
  override val mandatoryFieldsMapping: Map[String, String] = commonFieldsMapping

  override val mandatoryFields: Set[String] = mandatoryFieldsMapping.keySet

  lazy val pedestrianCrossingService: PedestrianCrossingService = new PedestrianCrossingService(roadLinkService, eventBusImpl)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    pointAssetAttributes.foreach { pedestrianCrossingAttribute =>
      val csvProperties = pedestrianCrossingAttribute.properties
      val nearbyLinks = pedestrianCrossingAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      pedestrianCrossingService.createFromCoordinates(IncomingPedestrianCrossing(position.x, position.y, nearestRoadLink.linkId, Set()), nearestRoadLink, user.username, floating)
    }

    result
  }
}

class RailwayCrossingCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter(roadLinkServiceImpl, eventBusImpl) {
  override val longValueFieldsMapping: Map[String, String] = commonFieldsMapping
  override val codeValueFieldsMapping: Map[String, String] = Map("turvavarustus" -> "safety equipment")
  override val specificFieldsMapping: Map[String, String] = Map("tasoristeystunnus" -> "id")
  override val nonMandatoryFieldsMapping: Map[String, String] = Map("nimi" -> "name")
  override val mandatoryFieldsMapping: Map[String, String] = commonFieldsMapping ++ codeValueFieldsMapping ++ specificFieldsMapping

  override val mandatoryFields: Set[String] = mandatoryFieldsMapping.keySet

  lazy val railwayCrossingService: RailwayCrossingService = new RailwayCrossingService(roadLinkService)

  val allowedSafetyEquipmentValues: Seq[Int] = Seq(1,2,3,4,5)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val notImportedRailwayCrossings = pointAssetAttributes.flatMap { railwayCrossingAttribute =>
      val csvProperties = railwayCrossingAttribute.properties
      val nearbyLinks = railwayCrossingAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)
      val code = getPropertyValue(csvProperties, "id").asInstanceOf[String]
      val safetyEquipment = getPropertyValue(csvProperties, "safety equipment").asInstanceOf[String].toInt
      val optName = getPropertyValueOption(csvProperties, "name").map(_.toString)

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      val validData =
        if (!allowedSafetyEquipmentValues.contains(safetyEquipment))
          Seq(NotImportedData(reason = s"Railway Crossing safety equipment type $safetyEquipment does not exist.", csvRow = rowToString(csvProperties.properties.flatMap { x => Map(x.columnName -> x.value) }.toMap)))
        else
          Seq()

      if (validData.isEmpty) {
        val name =  if (optName.nonEmpty) Seq(SimplePointAssetProperty(railwayCrossingService.namePublicId, Seq(PropertyValue(optName.get)))) else Seq()

        val propertyData = Set(SimplePointAssetProperty(railwayCrossingService.codePublicId, Seq(PropertyValue(code))),
        SimplePointAssetProperty(railwayCrossingService.safetyEquipmentPublicId, Seq(PropertyValue(safetyEquipment.toString)))) ++ name

      railwayCrossingService.createFromCoordinates(IncomingRailwayCrossing(position.x, position.y, nearestRoadLink.linkId, propertyData), nearestRoadLink, user.username, floating)
    }
      validData
    }
    result.copy(notImportedData = notImportedRailwayCrossings.toList ++ result.notImportedData)
  }
}

class ServicePointCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter(roadLinkServiceImpl, eventBusImpl) {
  override val longValueFieldsMapping: Map[String, String] = commonFieldsMapping
  override val stringValueFieldsMapping: Map[String, String] = Map("palvelun tyyppi" -> "type")
  override val nonMandatoryFieldsMapping: Map[String, String] = Map(
    "tarkenne" -> "type extension",
    "palvelun nimi" -> "name",
    "palvelun lisätieto" -> "additional info",
    "viranomaisdataa" -> "is authority data",
    "pysäkköintipaikkojen lukumäärä" -> "parking place count"
  )
  override val mandatoryFieldsMapping: Map[String, String] = commonFieldsMapping ++ stringValueFieldsMapping

  override val mandatoryFields: Set[String] = mandatoryFieldsMapping.keySet

  lazy val servicePointService: ServicePointService = new ServicePointService

  case class CsvServicePoint(position: Point, incomingService: IncomingService, roadLink: RoadLink, importInformation: Seq[NotImportedData] = Seq.empty)

  private def serviceTypeConverter(serviceType: String): Int = {
    val value = Normalizer.normalize(serviceType, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("-|\\s", "").toLowerCase
    ServicePointsClass.apply(value)
  }

  private def authorityDataConverter(value: String): Boolean = {
    val authorityDataValue = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("-|\\s", "").toLowerCase
    authorityDataValue match {
      case "kylla" => true
      case _ => false
    }
  }

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val incomingServicePoint = pointAssetAttributes.map { servicePointAttribute =>
      val csvProperties = servicePointAttribute.properties
      val nearbyLinks = servicePointAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val serviceType = getPropertyValue(csvProperties, "type").asInstanceOf[String]
      val typeExtension = getPropertyValueOption(csvProperties, "type extension").map(_.toString)
      val name = getPropertyValueOption(csvProperties, "name").map(_.toString)
      val additionalInfo = getPropertyValueOption(csvProperties, "additional info").map(_.toString)
      val isAuthorityData = getPropertyValue(csvProperties, "is authority data").asInstanceOf[String]
      val parkingPlaceCount = getPropertyValueOption(csvProperties, "parking place count").map(_.toString.toInt)

      val validatedServiceType = serviceTypeConverter(serviceType)
      val validatedTypeExtension = ServicePointsClass.getTypeExtensionValue(typeExtension.get, validatedServiceType)
      val validatedAuthorityData = authorityDataConverter(isAuthorityData)

      val incomingService = IncomingService(validatedServiceType, name, additionalInfo, validatedTypeExtension, parkingPlaceCount, validatedAuthorityData)

      val servicePointInfo =
        if(validatedServiceType == ServicePointsClass.Unknown.value)
          Seq(NotImportedData(reason = s"Service Point type $serviceType does not exist.", csvRow = rowToString(csvProperties.properties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
        else
          Seq()

      CsvServicePoint(position, incomingService, nearestRoadLink, servicePointInfo)
    }

    val (validServicePoints, nonValidServicePoints) = incomingServicePoint.partition(servicePoint => servicePoint.importInformation.isEmpty)
    val notImportedInfo = nonValidServicePoints.flatMap(_.importInformation)
    val groupedServicePoints = validServicePoints.groupBy(_.position)

    val incomingServicePoints = groupedServicePoints.map { servicePoint =>
      (IncomingServicePoint(servicePoint._1.x, servicePoint._1.y, servicePoint._2.map(_.incomingService).toSet, Set()), servicePoint._2.map(_.roadLink).head.municipalityCode)
    }

    incomingServicePoints.foreach { incomingAsset =>
      try {
        servicePointService.create(incomingAsset._1, incomingAsset._2, user.username, false)
      } catch {
        case e: ServicePointException => result.copy(notImportedData = List(NotImportedData(reason = e.getMessage, csvRow = "")) ++ result.notImportedData)
      }
    }

    result.copy(notImportedData = notImportedInfo.toList ++ result.notImportedData)
  }
}

class CsvDataImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl
  override val logInfo: String = ""
}
