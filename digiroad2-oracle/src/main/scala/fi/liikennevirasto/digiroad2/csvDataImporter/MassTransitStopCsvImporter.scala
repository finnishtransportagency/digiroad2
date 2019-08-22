package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, DigiroadEventBus, ExcludedRow, FloatingReason, GeometryUtils, IncompleteRow, MalformedRow, Point, Status}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, FloatingAsset, Position, PropertyValue, SimpleProperty, TrafficDirection, Unknown}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, NewMassTransitStop, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.apache.commons.lang3.StringUtils.isBlank

import scala.util.Try

class MassTransitStopCsvOperation(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  lazy val propertyUpdater = new Updater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)
  lazy val positionUpdater = new PositionUpdater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)
  lazy val creator = new Creator(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)

  private def getStrategies(): Seq[CsvOperations] = {
    Seq(propertyUpdater, creator, positionUpdater)
  }

  def getStrategy(csvRowWithHeaders: Map[String, String]): CsvOperations = {
    val strategies = getStrategies()
    strategies.find(strategy => strategy.is(csvRowWithHeaders)).getOrElse(throw new UnsupportedOperationException(s"Please check the combination between Koordinaatti and  Valtakunnallinen ID"))
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    val csvRead = csvReader.allWithHeaders()
    val csvRow = csvRead.map {
      r => r.toList.map ( p => (p._1.toLowerCase, p._2)).toMap
    }

    val strategy = getStrategy(csvRow.head)
    strategy.importAssets(csvRow: List[Map[String, String]], fileName, user, logId, roadTypeLimitations)
    }
}


trait MassTransitStopCsvImporter extends PointAssetCsvImporter {
  def roadLinkService: RoadLinkService
  def eventBus: DigiroadEventBus

  class AssetNotFoundException(externalId: Long) extends RuntimeException

  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  override val logInfo: String = "bus stop import"
  val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

      override val tierekisteriClient: TierekisteriMassTransitStopClient = tierekisteriMassTransitStopClient
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventBus, roadLinkService, roadAddressService)
  }
  override def mandatoryFieldsMapping: Map[String, String]

  private val isValidTypeEnumeration = Set(1, 2, 3, 4, 5, 99)
  private val singleChoiceValueMappings = Set(1, 2, 99).map(_.toString)
  private val stopAdministratorValueMappings = Set(1, 2, 3, 99).map(_.toString)

  private val textFieldMappings = Map(
    "pysäkin nimi" -> "nimi_suomeksi",
    "ylläpitäjän tunnus" -> "yllapitajan_tunnus",
    "matkustajatunnus" -> "matkustajatunnus",
    "pysäkin nimi ruotsiksi" -> "nimi_ruotsiksi",
    "liikennöintisuunta" -> "liikennointisuunta",
    "lisätiedot" -> "lisatiedot",
    "vyöhyketieto" -> "vyohyketieto"
  )

  val multipleChoiceFieldMappings = Map("pysäkin tyyppi" -> "pysakin_tyyppi")

  val stopAdministratorProperty =  Map("tietojen ylläpitäjä" -> "tietojen_yllapitaja")

  private val singleChoiceFieldMappings = Map(
    "aikataulu" -> "aikataulu",
    "katos" -> "katos",
    "mainoskatos" -> "mainoskatos",
    "penkki" -> "penkki",
    "pyöräteline" -> "pyorateline",
    "sähköinen aikataulunäyttö" -> "sahkoinen_aikataulunaytto",
    "valaistus" -> "valaistus",
    "saattomahdollisuus henkilöautolla" -> "saattomahdollisuus_henkiloautolla",
    "korotettu" -> "korotettu",
    "roska-astia" -> "roska_astia") ++
    stopAdministratorProperty

   protected val externalIdMapping = Map("valtakunnallinen id" -> "external_id")

  override val intValueFieldsMapping = externalIdMapping

  override val coordinateMappings = Map(
    "koordinaatti X" -> "maastokoordinaatti_x",
    "koordinaatti Y" -> "maastokoordinaatti_y"
  )

  override val longValueFieldsMapping = coordinateMappings

  val optionalMappings = externalIdMapping ++ coordinateMappings

  val mappings: Map[String, String] = textFieldMappings ++ multipleChoiceFieldMappings ++ singleChoiceFieldMappings

  private def municipalityValidation(municipality: Int)(user: User): Unit = {
    if (!user.isAuthorizedToWrite(municipality)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }
  }
  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData): ImportResultData =
    throw new UnsupportedOperationException("Not Supported Method")

  def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] =
    throw new UnsupportedOperationException("Not Supported Method")

  private def updateAssetByExternalId(externalId: Long, optPosition: Option[Position],properties: Seq[AssetProperty], user: User): MassTransitStopWithProperties = {
    val optionalAsset = massTransitStopService.getMassTransitStopByNationalId(externalId, municipalityValidation, false)
    optionalAsset match {
      case Some(asset) =>
        massTransitStopService.updateExistingById(asset.id, optPosition, convertProperties(properties).toSet, user.username, (_, _) => Unit, false)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  def convertProperties(properties: Seq[AssetProperty]) : Seq[SimpleProperty] = {
    properties.map { prop =>
      prop.value match {
        case values if values.isInstanceOf[List[_]] =>
          SimpleProperty(prop.columnName, values.asInstanceOf[List[PropertyValue]])
        case _ =>
          SimpleProperty(prop.columnName, Seq(PropertyValue(prop.value.toString)))
      }
    }
  }

  case class CsvImportMassTransitStop(id: Long, floating: Boolean, roadLinkType: AdministrativeClass) extends FloatingAsset {}

  private def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], username: String, optPosition: Option[Position]): Either[AdministrativeClass, MassTransitStopWithProperties] = {
    def massTransitStopTransformation(stop: PersistedMassTransitStop): (CsvImportMassTransitStop, Option[FloatingReason]) = {
      val roadLink = vvhClient.roadLinkData.fetchByLinkId(stop.linkId)
      val (floating, floatingReason) = massTransitStopService.isFloating(stop, roadLink)
      (CsvImportMassTransitStop(stop.id, floating, roadLink.map(_.administrativeClass).getOrElse(Unknown)), floatingReason)
    }

    val optionalAsset = massTransitStopService.getByNationalId(externalId, municipalityValidation, massTransitStopTransformation)
    optionalAsset match {
      case Some(asset) =>
        val roadLinkType = asset.roadLinkType
        if (roadTypeLimitations(roadLinkType)) Right(massTransitStopService.updateExistingById(asset.id, optPosition, convertProperties(properties).toSet, username, (_, _) => Unit, false))
        else Left(roadLinkType)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  def updateAsset(externalId: Long, optPosition: Option[Position], properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], user: User): ExcludedRoadLinkTypes = {
    // Remove livi-id from properties, we don't want to change is with CSV
    val propertiesWithoutLiviId = properties.filterNot(_.columnName == "yllapitajan_koodi")
    if (roadTypeLimitations.nonEmpty) {
      val result: Either[AdministrativeClass, MassTransitStopWithProperties] = updateAssetByExternalIdLimitedByRoadType(externalId, propertiesWithoutLiviId, roadTypeLimitations, user.username, optPosition)
      result match {
        case Left(roadLinkType) => List(roadLinkType)
        case _ => Nil
      }
    } else {
      updateAssetByExternalId(externalId,  optPosition, propertiesWithoutLiviId, user)
      Nil
    }
  }

  private def resultWithType(result: (MalformedParameters, List[AssetProperty]), assetType: Int): ParsedRow = {
    result.copy(_2 = result._2 match {
      case List(AssetProperty("pysakin_tyyppi", xs)) => List(AssetProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.asInstanceOf[Seq[PropertyValue]].toList))
      case _ => List(AssetProperty("pysakin_tyyppi", Seq(PropertyValue(assetType.toString))))
    })
  }

  private def maybeInt(string: String): Option[Int] = {
    Try(string.toInt).toOption
  }

  private def assetTypeToProperty(assetTypes: String): ParsedRow = {
    val invalidAssetType = (List("pysäkin tyyppi"), Nil)
    val types = assetTypes.split(',')
    if (types.isEmpty) invalidAssetType
    else {
      types.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, assetType) =>
        maybeInt(assetType.trim)
          .filter(isValidTypeEnumeration)
          .map(typeEnumeration => resultWithType(result, typeEnumeration))
          .getOrElse(invalidAssetType)
      }
    }
  }

  override def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, parameter) =>
      val (key, value) = parameter
      if (isBlank(value)) {
        if (mandatoryFieldsMapping.keySet.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (longValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if(intValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyIntType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (textFieldMappings.contains(key)) {
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

  private def assetSingleChoiceToProperty(parameterName: String, assetSingleChoice: String): ParsedRow = {
    parameterName match {
      case name if stopAdministratorProperty(name).nonEmpty =>
        if (stopAdministratorValueMappings.contains(assetSingleChoice))
          (Nil, List(AssetProperty(singleChoiceFieldMappings(name), List(PropertyValue(assetSingleChoice)))))
        else
          (List(name), Nil)
      case _ =>
        if (singleChoiceValueMappings(assetSingleChoice)) {
          (Nil, List(AssetProperty(singleChoiceFieldMappings(parameterName), List(PropertyValue(assetSingleChoice)))))
        } else {
          (List(parameterName), Nil)
        }
    }
  }

  def getNearestRoadLink(lon: Double, lat: Double, user: User, roadTypeLimitations: Set[AdministrativeClass]): Seq[VVHRoadlink] = {
    val closestRoadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(user, Point(lon, lat)).
      filterNot(road => roadTypeLimitations.contains(road.administrativeClass))
    if(closestRoadLinks.nonEmpty)
      Seq(closestRoadLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon, lat), r.geometry)))
    else
      Seq()
  }

  private def fork(f: => Unit): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        f
      }
    }).start()
  }

  def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
    update(logId, logInfo)
    fork {
      try {
        val result = processing(csvReader, user, roadTypeLimitations)
        result match {
          case ImportResultPointAsset(Nil, Nil, Nil, Nil, Nil) => update(logId, Status.OK)
          case _ =>
            val content = mappingContent(result)
              update(logId, Status.NotOK, Some(content))
        }
      } catch {
        case e: Exception =>
          update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString))
      }
    }
  }

  def processing(csvReader: List[Map[String, String]], user: User, roadTypeLimitations: Set[AdministrativeClass] = Set()): ImportResultPointAsset = {
    withDynTransaction {
      csvReader.foldLeft(ImportResultPointAsset()) {
        (result, row) =>
          val missingParameters = findMissingParameters(row)
          val (malformedParameters, properties) = assetRowToProperties(row)
          if (missingParameters.isEmpty && malformedParameters.isEmpty) {
            try {
              val excludedRows = createOrUpdate(row, roadTypeLimitations, user, properties)
              result.copy(excludedRows = excludedRows ::: result.excludedRows)
            } catch {
              case e: AssetNotFoundException => result.copy(notImportedData = NotImportedData(reason = s"Asset not found ${row("Valtakunnallinen ID").toString}", csvRow = rowToString(row)) :: result.notImportedData)
              case ex: Exception => result.copy(notImportedData = NotImportedData(reason = ex.getMessage, csvRow = rowToString(row)) :: result.notImportedData)
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
}

sealed trait ActionType

object ActionType {
  case object Create extends ActionType
  case object Update extends ActionType
  case object UpdatePosition extends ActionType
}

trait CsvOperations extends MassTransitStopCsvImporter {
  val decisionFieldsMapping : Map[String, String]

  def is(csvRowWithHeaders: Map[String, String]): Boolean = {
    val missingOptionals = optionalMappings.keySet.diff(csvRowWithHeaders.keys.toSet).toList
    val actionParameters = optionalMappings.keySet.toList.diff(missingOptionals)
    decisionFieldsMapping.keys.toSeq.sorted.equals(actionParameters.sorted)
  }
}

class Updater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override def mandatoryFieldsMapping: Map[String, String] = externalIdMapping

  override val decisionFieldsMapping = externalIdMapping

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    println("Updating busStop")
    val externalId = getPropertyValue(properties, "external_id").toString.toInt
    updateAsset(externalId, None, properties.filterNot(_.columnName == "external_id"), roadTypeLimitations, user)
      .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
  }
}

class Creator(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val decisionFieldsMapping = coordinateMappings

  override def mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ stopAdministratorProperty ++ multipleChoiceFieldMappings

  def getDirection(roadLink: RoadLink): ParsedProperties = {
      val direction = roadLink.trafficDirection  match {
        case TrafficDirection.BothDirections => TrafficDirection.TowardsDigitizing
        case _ => roadLink.trafficDirection
      }

    List(AssetProperty("vaikutussuunta", TrafficDirection.toSideCode(direction).value))
  }

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    println("Creating busStop")
    val lon = getPropertyValue(properties, "maastokoordinaatti_x").asInstanceOf[BigDecimal].toDouble
    val lat = getPropertyValue(properties, "maastokoordinaatti_y").asInstanceOf[BigDecimal].toDouble

    val nearestRoadLink = getNearestRoadLink(lon, lat, user, roadTypeLimitations)

    if(nearestRoadLink.isEmpty)
      List(ExcludedRow(affectedRows = "RoadLink no longer available", csvRow = rowToString(row)))
    else {
      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearestRoadLink)

      val prop = convertProperties(getDirection(roadLink.head) ++ properties)
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(lon, lat), roadLink.head.geometry)
      val bearing = GeometryUtils.calculateBearing(roadLink.head.geometry, Some(mValue))
      val point = GeometryUtils.calculatePointFromLinearReference(roadLink.head.geometry, mValue)
      val asset = NewMassTransitStop(point.map(_.x).getOrElse(lon), point.map(_.y).getOrElse(lat), roadLink.head.linkId, bearing, prop)

      massTransitStopService.checkDuplicates(asset) match {
        case Some(existingAsset) =>
          updateAsset(existingAsset.nationalId, None, properties, roadTypeLimitations, user)
            .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
        case _ =>
          massTransitStopService.createWithUpdateFloating(asset, user.username, roadLink.head)
          List()
      }
    }
  }
}

class PositionUpdater (roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val decisionFieldsMapping = coordinateMappings ++ externalIdMapping
  override def mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ externalIdMapping

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    println("Moving busStop")
    val lon = getPropertyValue(properties, "maastokoordinaatti_x").asInstanceOf[BigDecimal].toDouble
    val lat = getPropertyValue(properties, "maastokoordinaatti_y").asInstanceOf[BigDecimal].toDouble
    val externalId = getPropertyValue(properties, "external_id").toString.toInt

    val roadLink = roadLinkService.enrichRoadLinksFromVVH(getNearestRoadLink(lon, lat, user, roadTypeLimitations))

    if (roadLink.isEmpty)
      List(ExcludedRow(affectedRows = "roadLink no longer available", csvRow = rowToString(row)))
    else {
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(lon, lat), roadLink.head.geometry)
      val bearing = GeometryUtils.calculateBearing(roadLink.head.geometry, Some(mValue))
      val position = Some(Position(lon, lat, roadLink.head.linkId, Some(bearing)))
      updateAsset(externalId, position, properties.filterNot(_.columnName == "external_id"), roadTypeLimitations, user)
        .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
    }
  }
}