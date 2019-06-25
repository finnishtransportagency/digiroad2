package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporterOperations, DigiroadEventBus, ExcludedRow, FloatingReason, ImportResult, IncompleteRow, MalformedRow, Status}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, FloatingAsset, PropertyValue, SimpleProperty, Unknown}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.apache.commons.lang3.StringUtils.isBlank

import scala.util.Try

class MassTransitStopCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  case class NonExistingAsset(externalId: Long, csvRow: String)
  case class GenericException(reason: String, csvRow: String)
  case class ImportResultMassTransitStop(nonExistingAssets: List[NonExistingAsset] = Nil,
                                         incompleteRows: List[IncompleteRow] = Nil,
                                         malformedRows: List[MalformedRow] = Nil,
                                         excludedRows: List[ExcludedRow] = Nil,
                                         genericExceptionRows: List[GenericException] = Nil) extends ImportResult

  class AssetNotFoundException(externalId: Long) extends RuntimeException
  case class CsvAssetRow(externalId: Option[Long], properties: Seq[AssetProperty])
  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  type ImportResultData = ImportResultMassTransitStop

  sealed trait ActionType
  object ActionType {
    case object Create extends ActionType
    case object Update extends ActionType
    case object UpdatePosition extends ActionType
    case object None extends ActionType
  }

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

  private val coordinateMappings = Map(
    "Koordinaatti X" -> "coordinate_x",
    "Koordinaatti Y" -> "coordinate_y"
  )
  private val externalIdMapping = Map(
    "Valtakunnallinen ID" -> "external_id"
  )

  val optionalMappings = externalIdMapping ++ coordinateMappings

  val mappings : Map[String, String]= textFieldMappings ++ multipleChoiceFieldMappings ++ singleChoiceFieldMappings

  val mandatoryParameters: Set[String] = mappings.keySet

  private def municipalityValidation(municipality: Int)(user: User): Unit = {
    if (!user.isAuthorizedToWrite(municipality)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }
  }

  private def updateAssetByExternalId(externalId: Long, properties: Seq[AssetProperty], user: User): MassTransitStopWithProperties = {
    val optionalAsset = massTransitStopService.getMassTransitStopByNationalId(externalId, municipalityValidation)
    optionalAsset match {
      case Some(asset) =>
        massTransitStopService.updateExistingById(asset.id, None, properties.map(prop =>SimpleProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet, user.username, (_, _) => Unit)
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
        if (roadTypeLimitations(roadLinkType)) Right(massTransitStopService.updateExistingById(asset.id, None, properties.map(prop =>SimpleProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet, username, (_, _) => Unit))
        else Left(roadLinkType)
      case None => throw new AssetNotFoundException(externalId)
    }
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

  abstract class CsvOperations {
    def is(row: Map[String, String]): Boolean

    private def maybeInt(string: String): Option[Int] = {
      try {
        Some(string.toInt)
      } catch {
        case e: NumberFormatException => None
      }
    }

    private def resultWithType(result: (MalformedParameters, List[AssetProperty]), assetType: Int): ParsedRow = {
      result.copy(_2 = result._2 match {
        case List(AssetProperty("pysakin_tyyppi", xs)) => List(AssetProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.asInstanceOf[Seq[PropertyValue]].toList))
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

    def specificValidation(result: ParsedRow, parameter: (String, String)): ParsedRow = {
      result
    }

    def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
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
            specificValidation(result, parameter)
          }
        }
      }
    }

    def specificParameters(): Set[String] = Set()

    def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
      (mandatoryParameters ++ specificParameters).diff(csvRowWithHeaders.keys.toSet).toList
    }

    def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {throw new UnsupportedOperationException}

    def singleRowProcess(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, result: ImportResultMassTransitStop) = {
      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = assetRowToProperties(row)
      if(missingParameters.isEmpty && malformedParameters.isEmpty) {
        try {
          val excludedRows = createOrUpdate(row, roadTypeLimitations, user, properties)
          result.copy(excludedRows = excludedRows ::: result.excludedRows)
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingAssets = NonExistingAsset(externalId = row("Valtakunnallinen ID").toLong, csvRow = rowToString(row)) :: result.nonExistingAssets)
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

  class Updater extends CsvOperations {
    override def is(csvRowWithHeaders: Map[String, String]) : Boolean = {
      val missingOptionals = optionalMappings.keySet.diff(csvRowWithHeaders.keys.toSet).toList
      val actionParameters = optionalMappings.keySet.toList.diff(missingOptionals)
      externalIdMapping.keys.toSeq.sorted.equals(actionParameters.sorted)
    }

    override def specificParameters(): Set[String] = externalIdMapping.keySet

    override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
      val parsedRow = CsvAssetRow(externalId = Some(row("Valtakunnallinen ID").toLong), properties = properties)
      updateAsset(parsedRow.externalId.get, parsedRow.properties, roadTypeLimitations, user)
        .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
    }

    override def specificValidation(result: ParsedRow, parameter: (String, String)): ParsedRow = {
      val (key, value) = parameter
      if (coordinateMappings.contains(key)) {
        if (Try(value.toString.toDouble).isSuccess) {
          (Nil, List(AssetProperty(coordinateMappings(key), List(PropertyValue(value)))))
        } else {
          (List(key), Nil)
        }
      } else {
        result
      }
    }
  }

  class Creator extends CsvOperations {
    override def is(csvRowWithHeaders: Map[String, String]) = {
      val missingOptionals = optionalMappings.keySet.diff(csvRowWithHeaders.keys.toSet).toList
      val actionParameters = optionalMappings.keySet.toList.diff(missingOptionals)
      coordinateMappings.keys.toSeq.sorted.equals(actionParameters.sorted)
    }

    override def specificParameters(): Set[String] = coordinateMappings.keySet

    override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
      val parsedRow = CsvAssetRow(None, properties = properties)
      updateAsset(parsedRow.externalId.get, parsedRow.properties, roadTypeLimitations, user) //call the create method, not update
        .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
    }

    override def specificValidation(result: ParsedRow, parameter: (String, String)): ParsedRow = {
      val (key, value) = parameter
      if (coordinateMappings.contains(key)) {
        if (Try(value.toString.toDouble).isSuccess) {
          (Nil, List(AssetProperty(coordinateMappings(key), List(PropertyValue(value)))))
        } else {
          (List(key), Nil)
        }
      } else {
        result
      }
    }
  }

  class PositionUpdater extends CsvOperations {
    override def is(csvRowWithHeaders: Map[String, String]) = {
      val missingOptionals = optionalMappings.keySet.diff(csvRowWithHeaders.keys.toSet).toList
      val actionParameters = optionalMappings.keySet.toList.diff(missingOptionals)
      optionalMappings.keys.toSeq.sorted.equals(actionParameters.sorted)
    }

    override def specificParameters(): Set[String] = (coordinateMappings ++ externalIdMapping).keySet

    override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
      val parsedRow = CsvAssetRow(externalId = Some(row("Valtakunnallinen ID").toLong), properties = properties)
      updateAsset(parsedRow.externalId.get, parsedRow.properties, roadTypeLimitations, user) //call the correct update method
        .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
    }
  }

  lazy val propertyUpdater = new Updater
  lazy val positionUpdater = new PositionUpdater
  lazy val creator = new Creator

  private def getStrategies(): Seq[CsvOperations] = {
    Seq(propertyUpdater, creator, positionUpdater)
  }

  private def getStrategy(csvRowWithHeaders: Map[String, String]): CsvOperations ={
    val strategies = getStrategies()
    strategies.find(strategy => strategy.is(csvRowWithHeaders)).getOrElse(throw new UnsupportedOperationException(s"Please check the combination between ${optionalMappings.keySet.mkString(",")}"))
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

    val strategy = getStrategy(csvReader.allWithHeaders().head)
    csvReader.allWithHeaders().foldLeft(ImportResultMassTransitStop()) { (result, row) =>
      strategy.singleRowProcess(row, roadTypeLimitations, user, result)
    }
  }
}