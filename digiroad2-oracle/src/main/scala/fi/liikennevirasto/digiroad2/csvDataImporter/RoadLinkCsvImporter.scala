package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporterOperations, DigiroadEventBus, ExcludedRow, ImportResult, IncompleteRow, MalformedRow, Status}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, State}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.apache.commons.lang3.StringUtils.isBlank
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadLinkCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  case class NonUpdatedLink(linkId: String, csvRow: String)
  case class ImportResultRoadLink(nonUpdatedLinks: List[NonUpdatedLink] = Nil,
                                  incompleteRows: List[IncompleteRow] = Nil,
                                  malformedRows: List[MalformedRow] = Nil,
                                  excludedRows: List[ExcludedRow] = Nil) extends ImportResult

  case class CsvRoadLinkRow(linkId: String, objectID: Int = 0, properties: Seq[AssetProperty])

  type ImportResultData = ImportResultRoadLink

  private val administrativeClassLimitations: List[AdministrativeClass] = List(State)
  val authorizedValues: List[Int] = List(-11, -1, 0, 1, 2, 3, 4, 5, 10)

  private val intFieldMappings = Map(
    "hallinnollinen luokka" -> "ADMINCLASS",
    "toiminnallinen luokka" -> "functional_Class",
    "liikennevirran suunta" -> "DIRECTIONTYPE",
    "tielinkin tyyppi" -> "link_Type",
    "kuntanumero" -> "MUNICIPALITYCODE",
    "osoitenumerot oikealla alku" -> "FROM_RIGHT",
    "osoitenumerot oikealla loppu" -> "TO_RIGHT",
    "osoitenumerot vasemmalla alku" -> "FROM_LEFT",
    "osoitenumerot vasemmalla loppu" -> "TO_LEFT",
    "linkin tila" -> "CONSTRUCTIONTYPE"
  )

  private val codeValueFieldMappings = Map(
    "tasosijainti" -> "VERTICALLEVEL"
  )

  private val fieldInOTH = List("link_Type", "functional_Class")

  private val textFieldMappings = Map(
    "tien nimi (suomi)" -> "ROADNAME_FI",
    "tien nimi (ruotsi)" -> "ROADNAME_SE",
    "tien nimi (pohjoissaame)" -> "ROADNAMESME",
    "tien nimi (inarinsaame)" -> "ROADNAMESMN",
    "tien nimi (koltansaame)" -> "ROADNAMESMS"
  )

  private val mandatoryFields = "linkin id"

  val mappings : Map[String, String] = textFieldMappings ++ intFieldMappings ++ codeValueFieldMappings

  val MandatoryParameters: Set[String] = mappings.keySet + mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String], hasTrafficDirectionChange: Boolean): Option[String] = {
    try {
      if (hasTrafficDirectionChange) {
        RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.TrafficDirection, roadLinkAttribute.linkId) match {
          case Some(value) => RoadLinkOverrideDAO.delete(RoadLinkOverrideDAO.TrafficDirection, roadLinkAttribute.linkId)
          case _ => None
        }
      }

      roadLinkAttribute.properties.foreach { prop =>
        val optionalLinkTypeValue: Option[Int] = RoadLinkOverrideDAO.get(prop.columnName, roadLinkAttribute.linkId)
        optionalLinkTypeValue match {
          case Some(existingValue) =>
            RoadLinkOverrideDAO.update(prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt, existingValue)
          case None =>
            RoadLinkOverrideDAO.insert(prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt)
        }
      }
      None
    } catch {
      case ex: Exception => Some(roadLinkAttribute.linkId)
    }
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  def verifyValueCode(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit) && authorizedValues.contains(parameterValue.toInt)) {
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
        } else
          result
      }
    }
  }

  def validateAdministrativeClass(optionalAdminClassValue: Option[Any], dataLocation: String): List[String] = {
    optionalAdminClassValue match {
      case Some(adminClassValue) if administrativeClassLimitations.contains(AdministrativeClass.apply(adminClassValue.toString.toInt)) =>
        List("AdminClass value State found on " ++ dataLocation)
      case _ => List()
    }
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String, logId: Long): Long = {
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
    def getComplementaryInfo(linkId: String) = {
      roadLinkService.fetchComplimentaryByLinkId(linkId) match {
        case Some(fetchedRoadLink) => (fetchedRoadLink.attributes.get("OBJECTID"), fetchedRoadLink.administrativeClass.value)
        case _ => None
      }
    }
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
        val (objectId, oldAdminClassValue) = getComplementaryInfo(row("linkin id")) match {
          case None => (None, None)
          case (Some(objId), adminClass) => (objId, adminClass)
        }

        val unauthorizedAdminClass = (oldAdminClassValue match {
          case None => List("AdminClass value Unknown found on Database")
          case adminClassValue => validateAdministrativeClass(Some(adminClassValue), "Database")
        }) ++ validateAdministrativeClass(properties.filter(prop => prop.columnName == "ADMINCLASS").map(_.value).headOption, "CSV")

        if (unauthorizedAdminClass.isEmpty && objectId != None) {
          val propertiesOTH = properties.partition(a => fieldInOTH.contains(a.columnName))._1
          val hasDirectionType = properties.exists(_.columnName == "DIRECTIONTYPE")

          withDynTransaction {
            if (propertiesOTH.nonEmpty || hasDirectionType) {
              val parsedRowOTH = CsvRoadLinkRow(row("linkin id"), properties = propertiesOTH)
              updateRoadLinkOTH(parsedRowOTH, Some(username), hasDirectionType) match {
                case None => result
                case Some(value) =>
                  result.copy(nonUpdatedLinks = NonUpdatedLink(linkId = value, csvRow = rowToString(row)) :: result.nonUpdatedLinks)
              }
            }
          result
          }
        } else {
          result.copy(
            nonUpdatedLinks = objectId match {
              case None => NonUpdatedLink(linkId = row("linkin id"), csvRow = rowToString(row)) :: result.nonUpdatedLinks
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