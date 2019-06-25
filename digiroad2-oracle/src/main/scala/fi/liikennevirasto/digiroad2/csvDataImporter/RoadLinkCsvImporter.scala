package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporterOperations, DigiroadEventBus, ExcludedRow, ImportResult, IncompleteRow, MalformedRow, Status}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, State}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.apache.commons.lang3.StringUtils.isBlank
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class RoadLinkCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  case class NonUpdatedLink(linkId: Long, csvRow: String)
  case class ImportResultRoadLink(nonUpdatedLinks: List[NonUpdatedLink] = Nil,
                                  incompleteRows: List[IncompleteRow] = Nil,
                                  malformedRows: List[MalformedRow] = Nil,
                                  excludedRows: List[ExcludedRow] = Nil) extends ImportResult

  case class CsvRoadLinkRow(linkId: Int, objectID: Int = 0, properties: Seq[AssetProperty])

  type ImportResultData = ImportResultRoadLink

  override val logInfo: String = "road link import"

  private val administrativeClassLimitations: List[AdministrativeClass] = List(State)
  val authorizedValues: List[Int] = List(-11, -1, 0, 1, 2, 3, 4, 5, 10)

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