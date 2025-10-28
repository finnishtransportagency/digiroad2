package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimePropertyFormatMs
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporterOperations, DigiroadEventBus, ExcludedRow, ImportResult, IncompleteRow, MalformedRow, Status, Track}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, ConstructionType, DateParser, MTKClass, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.ComplementaryLinkDAO
import fi.liikennevirasto.digiroad2.linearasset.{ReasonOfCreation, SurfaceRelation, SurfaceType}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.KgvUtil.extractTrafficDirection
import org.apache.commons.lang3.StringUtils.isBlank
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadLinkCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  case class NotImportedData(linkId: String, csvRow: String)
  case class ImportResultRoadLink(notImportedData: List[NotImportedData] = Nil,
                                  incompleteRows: List[IncompleteRow] = Nil,
                                  malformedRows: List[MalformedRow] = Nil,
                                  excludedRows: List[ExcludedRow] = Nil) extends ImportResult

  case class CsvRoadLinkRow(properties: Seq[AssetProperty])

  val complementaryLinkDAO = new ComplementaryLinkDAO
  val logger = LoggerFactory.getLogger(getClass)

  type ImportResultData = ImportResultRoadLink

  val allowedAdminClasses = AdministrativeClass.values.map(_.value)
  val allowedMTKClasses = MTKClass.values.map(_.value)
  val allowedSurfaceTypes = SurfaceType.values.filter(st => st.value == 1 || st.value == 2).map(_.value)
  val allowedConstructionTypes = ConstructionType.values.map(_.value)
  val allowedSurfaceRelations = SurfaceRelation.values.map(_.value)
  val allowedTrackCodes = Track.values.map(_.value)
  val allowedReasonsOfCreation = ReasonOfCreation.values.map(_.value)

  private val singleChoiceIntMappings = Map(
    "tasosijainti" -> "surfacerelation",
    "hallinnollinen luokka" -> "adminclass",
    "mtk luokka" -> "roadclass",
    "päällystetieto" -> "surfacetype",
    "elinkaaren tila" -> "lifecyclestatus",
    "yksisuuntaisuus"-> "directiontype",
    "ajoratakoodi" -> "trackcode",
    "luontisyy" -> "cust_owner"
  )

  private val intFieldMappings = Map(
    "vvh id" -> "vvh_id",
    "datasource" -> "datasource",
    "kuntakoodi" -> "municipalitycode",
    "tienumero" -> "roadnumber",
    "tieosanumero" -> "roadpartnumber"
  ) ++ singleChoiceIntMappings

  private val floatFieldMappings = Map ("tielinkin pituus" -> "horizontallength")

  private val dateFieldMappings = Map(
    "alkupäivämäärä" -> "starttime",
    "viimeisin muokkauspäivämäärä" -> "versionstarttime"
  )

  private val textFieldMappings = Map(
    "linkin id" -> "link_id",
    "tiennimi (suomi)"-> "roadnamefin",
    "tiennimi (ruotsi)" -> "roadnameswe",
    "tiennimi (inarinsaame)" -> "roadnamesmn",
    "tiennimi (koltansaame)" -> "roadnamesms",
    "tiennimi (pohjoissaame)" -> "roadnamesme",
    "käyttäjä" -> "created_user",
    "geometria" -> "shape"
  )

  val mappings: Map[String, String] = textFieldMappings ++ intFieldMappings ++ floatFieldMappings ++ dateFieldMappings

  private val mandatoryKeys: Set[String] = Set(
    "linkin id",
    "hallinnollinen luokka",
    "kuntakoodi",
    "mtk luoukka",
    "päällystetieto",
    "elinkaaren tila",
    "yksisuuntaisuus",
    "tasosijainti",
    "tielinkin pituus",
    "alkupäivämäärä",
    "käyttäjä",
    "geometria",
    "luontisyy"
  ).filter(mappings.contains)

  private def mandatoryParameters: Map[String, String] = mappings.filter {
      case (_, keyName ) => mandatoryKeys.contains(keyName)
  }

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    val keysInRow: Set[String] = csvRowWithHeaders.keySet.flatMap(mappings.get)
    val mandatoryKeys = mandatoryParameters.values.toSet
    (mandatoryKeys -- keysInRow).toList
  }

  case class ComplementaryLinkParams(
                                      vvhid: Option[Int],
                                      linkid: String,
                                      datasource: Option[Int],
                                      adminclass: Int,
                                      municipalitycode: Int,
                                      roadclass: Int,
                                      roadnamefin: Option[String],
                                      roadnameswe: Option[String],
                                      roadnamesme: Option[String],
                                      roadnamesmn: Option[String],
                                      roadnamesms: Option[String],
                                      roadnumber: Option[Int],
                                      roadpartnumber: Option[Int],
                                      surfacetype: Int,
                                      lifecyclestatus: Int,
                                      directiontype: Int,
                                      surfacerelation: Int,
                                      horizontallength: Float,
                                      starttime: DateTime,
                                      created_user: String,
                                      versionstarttime: Option[DateTime],
                                      shape: String,
                                      trackcode: Option[Int],
                                      cust_owner: Int
                                    )

  def mapPropertiesToParams(props: Seq[AssetProperty]): ComplementaryLinkParams = {
    def getOptString(key: String): Option[String] =
      props.find(_.columnName == key).map(_.value.toString).filter(_.nonEmpty)

    def getString(key: String): String =
      getOptString(key).getOrElse(throw new Exception(s"Missing required field $key"))

    def getOptInt(key: String): Option[Int] =
      getOptString(key).flatMap(s => scala.util.Try(s.toInt).toOption)

    def getInt(key: String): Int =
      getOptInt(key).getOrElse(throw new Exception(s"Missing or invalid int for $key"))

    def getOptFloat(key: String): Option[Float] =
      getOptString(key).flatMap(s => scala.util.Try(s.toFloat).toOption)

    def getFloat(key: String): Float =
      getOptFloat(key).getOrElse(throw new Exception(s"Missing or invalid float for $key"))

    def getOptDateTime(key: String): Option[DateTime] =
      getOptString(key).flatMap { s =>
        try Some(DateParser.stringToDate(s, DateTimePropertyFormatMs))
        catch { case _: Throwable => None }
      }

    def getDateTime(key: String): DateTime =
      getOptDateTime(key).getOrElse(throw new Exception(s"Missing or invalid DateTime for $key"))

    ComplementaryLinkParams(
      vvhid = getOptInt("vvh_id"),
      linkid = getString("link_id"),
      datasource = getOptInt("datasource"),
      adminclass = getInt("adminclass"),
      municipalitycode = getInt("municipalitycode"),
      roadclass = getInt("roadclass"),
      roadnamefin = getOptString("roadnamefin"),
      roadnameswe = getOptString("roadnameswe"),
      roadnamesme = getOptString("roadnamesme"),
      roadnamesmn = getOptString("roadnamesmn"),
      roadnamesms = getOptString("roadnamesms"),
      roadnumber = getOptInt("roadnumber"),
      roadpartnumber = getOptInt("roadpartnumber"),
      surfacetype = getInt("surfacetype"),
      lifecyclestatus = getInt("lifecyclestatus"),
      directiontype = getInt("directiontype"),
      surfacerelation = getInt("surfacerelation"),
      horizontallength = getFloat("horizontallength"),
      starttime = getDateTime("starttime"),
      created_user = getString("created_user"),
      versionstarttime = getOptDateTime("versionstarttime"),
      shape = getString("shape"),
      trackcode = getOptInt("trackcode"),
      cust_owner = getInt("cust_owner")
    )
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow): Option[String] = {
    try {
      val params = mapPropertiesToParams(roadLinkAttribute.properties)

      complementaryLinkDAO.insertComplementaryLinks(
        params.vvhid, params.linkid, params.datasource, params.adminclass, params.municipalitycode, params.roadclass,
        params.roadnamefin, params.roadnameswe, params.roadnamesme, params.roadnamesmn, params.roadnamesms,
        params.roadnumber, params.roadpartnumber, params.surfacetype, params.lifecyclestatus, params.directiontype,
        params.surfacerelation, params.horizontallength, params.starttime, params.created_user, params.versionstarttime,
        params.shape, params.trackcode, params.cust_owner
      )
      None
    } catch {
      case ex: Exception =>
        val linkId = roadLinkAttribute.properties.find(_.columnName == "link_id").map(_.value.toString).getOrElse("Unknown")
        logger.error(s"Error updating road link OTH for link_id=$linkId", ex)
        Some(linkId)
    }
  }

  private def verifyIntType(parameterName: String, parameterValue: String): ParsedRow = {
    parameterValue.forall(_.isValidInt) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  private def verifyFloatType(parameterName: String, parameterValue: String): ParsedRow = {
    try {
      val floatValue = parameterValue.toFloat
      (Nil, List(AssetProperty(columnName = floatFieldMappings(parameterName), value = floatValue)))
    } catch {
      case _: NumberFormatException => (List(parameterName), Nil)
    }
  }

  def verifyDateType(parameterName: String, parameterValue: String): ParsedRow = {
    try {
      val normalizedValue = DateParser.normalizeDateFormat(parameterValue)
      (Nil, List(AssetProperty(columnName = dateFieldMappings(parameterName), value = normalizedValue)))
    } catch {
      case _: Throwable => (List(parameterName), Nil)
    }
  }

  def verifySingleChoice(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit)) {
      val value = parameterValue.toInt
      val internalName = singleChoiceIntMappings.getOrElse(parameterName, intFieldMappings.getOrElse(parameterName, ""))

      internalName match {
        case "adminclass" =>
          if (allowedAdminClasses.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case "roadclass" =>
          if (allowedMTKClasses.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case "surfacetype" =>
          if (allowedSurfaceTypes.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case "lifecyclestatus" =>
          if (allowedConstructionTypes.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case "directiontype" =>
          val direction = extractTrafficDirection(Some(value))
          if (direction != TrafficDirection.UnknownDirection) {
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          } else {
            (List(parameterName), Nil)
          }
        case "surfacerelation" =>
          if (allowedSurfaceRelations.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case "trackcode" =>
          if (allowedTrackCodes.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case "cust_owner" =>
          if (allowedReasonsOfCreation.contains(value))
            (Nil, List(AssetProperty(columnName = internalName, value = value)))
          else
            (List(parameterName), Nil)
        case _ =>
          (Nil, List(AssetProperty(columnName = internalName, value = value)))
      }
    } else {
      // Not a digit
      (List(parameterName), Nil)
    }
  }

  private def linkRowToProperties(csvRowWithHeaders: Map[String, Any]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryKeys.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = textFieldMappings(key), value = value) :: result._2)
        } else if (singleChoiceIntMappings.contains(key)) {
          val (malformedParameters, properties) = verifySingleChoice(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyIntType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (floatFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyFloatType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (dateFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyDateType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else
          result
      }
    }
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String, logId: Long): Long = {
    try {
      val result = processing(inputStream, username)
      result match {
        case ImportResultRoadLink(Nil, Nil, Nil, Nil) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result) +
            s"<ul>nonExistingAssets: ${result.notImportedData.map{ rows => "<li>" + rows.linkId -> rows.csvRow + "</li>"}.mkString.replaceAll("[(|)]{1}","")}</ul>"
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, username: String): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    withDynTransaction {
      csvReader.allWithHeaders().foldLeft(ImportResultRoadLink()) { (result, row) =>
        val csvRow = row.map(r => (r._1.toLowerCase, r._2))
        val missingParameters = findMissingParameters(csvRow)
        val (malformedParameters, properties) = linkRowToProperties(csvRow)

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
          val parsedRow = CsvRoadLinkRow(properties = properties)
          updateRoadLinkOTH(parsedRow)
          result
        }
      }
    }
  }
}