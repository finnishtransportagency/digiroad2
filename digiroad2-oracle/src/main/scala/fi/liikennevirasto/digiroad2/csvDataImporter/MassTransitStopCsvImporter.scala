package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.{ImportLogDAO, MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{BusStopType, MassTransitStopService, MassTransitStopWithProperties, NewMassTransitStop, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import fi.liikennevirasto.digiroad2.util.LaneUtils.roadAddressService
import org.apache.commons.lang3.StringUtils.isBlank
import org.apache.http.impl.client.HttpClientBuilder

import scala.util.Try

class MassTransitStopCsvOperation(roadLinkClientImpl: RoadLinkClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  lazy val propertyUpdater = new Updater(roadLinkClientImpl: RoadLinkClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)

  private def getStrategies(): Seq[CsvOperations] = {
    Seq(propertyUpdater)
  }

  lazy val importLogDao: ImportLogDAO = new ImportLogDAO

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

    try {
      val strategy = getStrategy(csvRow.head)
      strategy.importAssets(csvRow: List[Map[String, String]], fileName, user, logId, roadTypeLimitations)

    } catch {
      case e: Exception =>
        PostGISDatabase.withDynTransaction  {
        importLogDao.update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString))
        }
      }
  }
}


trait MassTransitStopCsvImporter extends PointAssetCsvImporter {
  def roadLinkService: RoadLinkService
  def eventBus: DigiroadEventBus

  class AssetNotFoundException(nationalId: Long) extends RuntimeException

  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

      override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventBus, roadLinkService, roadAddressService)
  }

  private val isValidTypeEnumeration = Set(1, 2, 5, 99)
  private val singleChoiceValueMappings = Set(1, 2, 99).map(_.toString)
  private val stopAdministratorValueMappings = Set(1, 2, 3, 99).map(_.toString)
  private val serviceLevelValueMappings = Set(1, 2, 3, 4, 5, 6, 7, 8, 99).map(_.toString)

  private val textFieldMappings = Map(
    "pysäkin nimi" -> "nimi_suomeksi",
    "ylläpitäjän tunnus" -> "yllapitajan_tunnus",
    "matkustajatunnus" -> "matkustajatunnus",
    "pysäkin nimi ruotsiksi" -> "nimi_ruotsiksi",
    "liikennöintisuunta" -> "liikennointisuunta",
    "lisätiedot" -> "lisatiedot",
    "vyöhyketieto" -> "vyohyketieto",
    "vaihtoehtoinen link_id" -> "alternative_link_id",
    "pysäkin omistaja" -> "pysakin_omistaja",
    "osoite suomeksi" -> "osoite_suomeksi",
    "osoite ruotsiksi" -> "osoite_ruotsiksi",
    "laiturinumero" -> "laiturinumero",
    "esteettömyys liikuntarajoitteiselle" -> "esteettomyys_liikuntarajoitteiselle",
    "liityntäpysäköintipaikkojen määrä" -> "liityntapysakointipaikkojen_maara",
    "liityntäpysäköinnin lisätiedot" -> "liityntapysakoinnin_lisatiedot",
    "palauteosoite" -> "palauteosoite"
  )

  val massTransitStopType = Map("pysäkin tyyppi" -> "pysakin_tyyppi")

  val multipleChoiceFieldMappings = massTransitStopType

  val stopAdministratorProperty =  Map("tietojen ylläpitäjä" -> "tietojen_yllapitaja")

  val serviceLevelProperty = Map("pysäkin palvelutaso" -> "pysakin_palvelutaso")

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
    "roska-astia" -> "roska_astia"
  ) ++ stopAdministratorProperty ++ serviceLevelProperty

   protected val nationalIdMapping = Map("valtakunnallinen id" -> "national_id")

  override val intValueFieldsMapping = nationalIdMapping

  override val coordinateMappings = Map(
    "koordinaatti x" -> "maastokoordinaatti_x",
    "koordinaatti y" -> "maastokoordinaatti_y",
    "koordinaatti z" -> "maastokoordinaatti_z"
  )

  override val longValueFieldsMapping = coordinateMappings

  val inventoryDateMapping = Map("inventointipäivä" -> "inventointipaiva")

  override val dateFieldsMapping: Map[String, String] = Map(
    "ensimmäinen voimassaolopäivä" -> "ensimmainen_voimassaolopaiva",
    "viimeinen voimassaolopäivä" -> "viimeinen_voimassaolopaiva"
  ) ++ inventoryDateMapping

  val mandatoryMappings = nationalIdMapping ++ stopAdministratorProperty ++ massTransitStopType

  val mappings: Map[String, String] = textFieldMappings ++ multipleChoiceFieldMappings ++ singleChoiceFieldMappings ++ dateFieldsMapping ++ coordinateMappings

  private def municipalityValidation(municipality: Int)(user: User): Unit = {
    if (!user.isAuthorizedToWrite(municipality)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }
  }
  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData): ImportResultData =
    throw new UnsupportedOperationException("Not Supported Method")

  def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] =
    throw new UnsupportedOperationException("Not Supported Method")

  private def updateAssetByNationalId(nationalId: Long, optPosition: Option[Position],properties: Seq[AssetProperty], user: User): MassTransitStopWithProperties = {
    val optionalAsset = massTransitStopService.getMassTransitStopByNationalId(nationalId, municipalityValidation, false)
    optionalAsset match {
      case Some(asset) =>
        massTransitStopService.updateExistingById(asset.id, optPosition, convertProperties(properties).toSet, user.username, (_, _) => Unit, false)
      case None => throw new AssetNotFoundException(nationalId)
    }
  }

  def convertProperties(properties: Seq[AssetProperty]) : Seq[SimplePointAssetProperty] = {
    properties.map { prop =>
      prop.value match {
        case values if values.isInstanceOf[List[_]] =>
          SimplePointAssetProperty(prop.columnName, values.asInstanceOf[List[PropertyValue]])
        case _ =>
          SimplePointAssetProperty(prop.columnName, Seq(PropertyValue(prop.value.toString)))
      }
    }
  }

  case class CsvImportMassTransitStop(id: Long, floating: Boolean, roadLinkType: AdministrativeClass) extends FloatingAsset {}

  private def updateAssetByNationalIdLimitedByRoadType(nationalId: Long, properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], username: String, optPosition: Option[Position]): Either[AdministrativeClass, MassTransitStopWithProperties] = {
    def massTransitStopTransformation(stop: PersistedMassTransitStop): (CsvImportMassTransitStop, Option[FloatingReason]) = {
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(stop.linkId)
      val (floating, floatingReason) = massTransitStopService.isFloating(stop, roadLink)
      (CsvImportMassTransitStop(stop.id, floating, roadLink.map(_.administrativeClass).getOrElse(Unknown)), floatingReason)
    }

    val optionalAsset = massTransitStopService.getByNationalId(nationalId, municipalityValidation, massTransitStopTransformation, false)
    optionalAsset match {
      case Some(asset) =>
        val roadLinkType = asset.roadLinkType
        if (!roadTypeLimitations(roadLinkType)) Right(massTransitStopService.updateExistingById(asset.id, optPosition, convertProperties(properties).toSet, username, (_, _) => Unit, false))
        else Left(roadLinkType)
      case None => throw new AssetNotFoundException(nationalId)
    }
  }

  def updateAsset(nationalId: Long, optPosition: Option[Position], properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], user: User): ExcludedRoadLinkTypes = {
    // Remove livi-id from properties, we don't want to change is with CSV
    val propertiesWithoutLiviId = properties.filterNot(_.columnName == "yllapitajan_koodi")
    if (roadTypeLimitations.nonEmpty) {
      val result: Either[AdministrativeClass, MassTransitStopWithProperties] = updateAssetByNationalIdLimitedByRoadType(nationalId, propertiesWithoutLiviId, roadTypeLimitations, user.username, optPosition)
      result match {
        case Left(roadLinkType) => List(roadLinkType)
        case _ => Nil
      }
    } else {
      updateAssetByNationalId(nationalId,  optPosition, propertiesWithoutLiviId, user)
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
        if (mandatoryMappings.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else
          result
      } else if (value.equals("-")) {
          if (mandatoryMappings.contains(key)) {
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          } else if (singleChoiceFieldMappings.contains(key)) {
            val (malformedParameters, properties) = assetSingleChoiceToProperty(key, Unknown.value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (inventoryDateMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = inventoryDateMapping(key), value = Seq(PropertyValue(value))) :: result._2)
          } else if (dateFieldsMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = dateFieldsMapping(key), value = Seq(PropertyValue(""))) :: result._2)
          } else if (textFieldMappings.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = textFieldMappings(key), value = Seq(PropertyValue(""))) :: result._2)
          } else if (longValueFieldsMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = longValueFieldsMapping(key), value = Seq(PropertyValue(""))) :: result._2)
          } else if (multipleChoiceFieldMappings.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = multipleChoiceFieldMappings(key), value = Seq(PropertyValue(Unknown.value.toString))) :: result._2)
          } else result
      } else {
        if (longValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (dateFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyDateType(key, value)
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
      case name if stopAdministratorProperty.get(name).nonEmpty =>
        if (stopAdministratorValueMappings.contains(assetSingleChoice))
          (Nil, List(AssetProperty(singleChoiceFieldMappings(name), List(PropertyValue(assetSingleChoice)))))
        else
          (List(name), Nil)
      case name if serviceLevelProperty.get(name).nonEmpty =>
        if (serviceLevelValueMappings.contains(assetSingleChoice))
          (Nil, List(AssetProperty(serviceLevelProperty(name), List(PropertyValue(assetSingleChoice)))))
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

  def getNearestRoadLink(lon: Double, lat: Double, user: User, roadTypeLimitations: Set[AdministrativeClass], stopType: BusStopType): Seq[RoadLink] = {
    val isCarStop = if (stopType != BusStopType.Tram) true else false
    val closestRoadLinks = roadLinkService.getClosestRoadlinkForCarTraffic(user, Point(lon, lat), isCarStop).
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
              case e: AssetNotFoundException =>
                result.copy(notImportedData = NotImportedData(reason = s"Asset not found -> ${row("valtakunnallinen id").toString}", csvRow = rowToString(row)) :: result.notImportedData)
              case ex: Exception =>
                result.copy(notImportedData = NotImportedData(reason = ex.getMessage, csvRow = rowToString(row)) :: result.notImportedData)
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
    val missingMandatory = mandatoryMappings.keySet.diff(csvRowWithHeaders.keys.toSet).toList
    val actionParameters = mandatoryMappings.keySet.toList.diff(missingMandatory)
    decisionFieldsMapping.keys.toSeq.sorted.equals(actionParameters.sorted)
  }
}

class Updater(roadLinkClientImpl: RoadLinkClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkClientImpl
  override def eventBus: DigiroadEventBus = eventBusImpl

  override def mandatoryFields: Set[String] = nationalIdMapping.keySet ++ stopAdministratorProperty.keySet ++ massTransitStopType.keySet
  override val decisionFieldsMapping = nationalIdMapping ++ stopAdministratorProperty ++ massTransitStopType
  override def mandatoryFieldsMapping: Map[String, String] = mappings ++ nationalIdMapping

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    println("Updating busStop")
    val nationalId = getPropertyValue(properties, "national_id").toString.toInt
    updateAsset(nationalId, None, properties.filterNot(_.columnName == "national_id"), roadTypeLimitations, user)
      .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
  }
}