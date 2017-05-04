package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dataimport.DataCsvImporter.RoadLinkCsvImporter._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.roadlinkservice.oracle.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.{VVHClient}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.apache.commons.lang3.StringUtils.isBlank

object DataCsvImporter {

  object RoadLinkCsvImporter {
    case class NonExistingLink(linkIdandField: String, csvRow: String)
    case class IncompleteLink(missingParameters: List[String], csvRow: String)
    case class MalformedLink(malformedParameters: List[String], csvRow: String)
    case class ExcludedLink(affectedRoadLinkType: String, csvRow: String)
    case class ImportResult(nonExistingLinks: List[NonExistingLink] = Nil,
                            incompleteLinks: List[IncompleteLink] = Nil,
                            malformedLinks: List[MalformedLink] = Nil,
                            excludedLinks: List[ExcludedLink] = Nil)
  }
}

class RoadLinkNotFoundException(linkId: Int) extends RuntimeException

trait RoadLinkCsvImporter {
  val vvhClient: VVHClient
  val userProvider: UserProvider
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  case class LinkProperty(columnName: String, value: String)
  case class CsvRoadLinkRow(linkId: Int, properties: Seq[LinkProperty])

  type IncompleteParameters = List[String]
  type ParsedProperties = List[LinkProperty]
  type MalformedParameters = List[String]
  type ParsedLinkRow = (MalformedParameters, ParsedProperties)

  private val administrativeClassLimitations: List[AdministrativeClass] = List(State)

  private val intFieldMappings = Map(
    "Hallinnollinen luokka" -> "ADMINCLASS",
    "Toiminnallinen luokka" -> "functional_Class",
    "Liikennevirran suunta" -> "DIRECTIONTYPE",
    "Tielinkin tyyppi" -> "link_Type",
    "Tasosijainti" -> "VERTICALLEVEL",
    "Kuntanumero" -> "MUNICIPALITYCODE",
    "Osoitenumerot oikealla alku" -> "FROM_RIGHT",
    "Osoitenumerot oikealla loppu" -> "TO_RIGHT",
    "Osoitenumerot vasemmalla alku" -> "FROM_LEFT",
    "Osoitenumerot vasemmalla loppu" -> "TO_LEFT",
    "Linkin tila" -> "CONSTRUCTIONTYPE"
  )

  private val fieldInOTH = List("link_Type", "functional_Class")

  private val textFieldMappings = Map(
    "Tien nimi (suomi)" -> "ROADNAME_FI",
    "Tien nimi (ruotsi)" -> "ROADNAME_SE",
    "Tien nimi (saame)" -> "ROADNAME_SM"
  )

  private val mandatoryFields = "Linkin ID"


  val mappings = textFieldMappings ++ intFieldMappings

  val MandatoryParameters: Set[String] = mappings.keySet + mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  private def verifyMandatoryParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    csvRowWithHeaders.get(mandatoryFields)
    match {
      case Some(value)
        if (value.isEmpty || !value.forall(_.isDigit)) => List(mandatoryFields)
      case _ => Nil
    }
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String], hasTrafficDirectionChange: Boolean): IncompleteParameters = {
    try {
      if (hasTrafficDirectionChange) {
        RoadLinkServiceDAO.getTrafficDirectionValue(roadLinkAttribute.linkId) match {
          case Some(value) => RoadLinkServiceDAO.deleteTrafficDirection(roadLinkAttribute.linkId)
          case _ => None
        }
      }

      roadLinkAttribute.properties.map { prop =>
        val optionalLinkTypeValue: Option[Int] = RoadLinkServiceDAO.getLinkProperty(prop.columnName, prop.columnName, roadLinkAttribute.linkId)
        optionalLinkTypeValue match {
          case Some(existingValue) =>
            RoadLinkServiceDAO.updateExistingLinkPropertyRow(prop.columnName, prop.columnName, roadLinkAttribute.linkId, username, existingValue, prop.value.toInt)
          case None =>
            RoadLinkServiceDAO.insertNewLinkProperty(prop.columnName, prop.columnName, roadLinkAttribute.linkId, username, prop.value.toInt)
        }
      }
      List()
    } catch {
      case e: RoadLinkNotFoundException =>  List(roadLinkAttribute.linkId.toString)
    }
  }

  def updateRoadLinkInVVH(roadLinkVVHAttribute: CsvRoadLinkRow): IncompleteParameters = {
    val mapProperties = roadLinkVVHAttribute.properties.map { prop => prop.columnName -> prop.value }.toMap

    vvhClient.complementaryData.updateVVHFeatures(mapProperties ++ Map("LINKID" -> roadLinkVVHAttribute.linkId.toString)) match {
      case Right(error) => List(roadLinkVVHAttribute.linkId.toString)
      case _ => List()
    }
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedLinkRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(LinkProperty(columnName = intFieldMappings(parameterName), value = parameterValue)))
      case false => (List(parameterName), Nil)
    }
  }

  private def linkRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedLinkRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(mandatoryFields) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = LinkProperty(columnName = textFieldMappings(key), value = value) :: result._2)
        } else if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (mandatoryFields.contains(key) && !value.forall(_.isDigit)) {
          result.copy(_1 = List(mandatoryFields) ::: result._1, _2 = result._2)
        } else
          result
      }
    }
  }

  def validateAdministrativeClass(properties: Seq[LinkProperty]): Option[AdministrativeClass] = {
    properties.filter(_.columnName == "ADMINCLASS").map(_.value).headOption
    match {
      case None => None
      case Some(propertyValue) =>
        if (administrativeClassLimitations.contains(AdministrativeClass.apply(propertyValue.toInt))) {
          Some(AdministrativeClass.apply(propertyValue.toInt))
        } else {
          None
        }
    }
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  def importLinkAttribute(inputStream: InputStream): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = linkRowToProperties(row)
      val affectedRoadLinkType = validateAdministrativeClass(properties)
      if (missingParameters.isEmpty && malformedParameters.isEmpty && affectedRoadLinkType.isEmpty) {

        val (propertieOTH, propertieVVH) = properties.partition(a => fieldInOTH.contains(a.columnName))

        if (propertieOTH.size > 0 || propertieVVH.exists(_.columnName == "DIRECTIONTYPE")) {
          withDynTransaction {
            val parsedRowOTH = CsvRoadLinkRow(row("Linkin ID").toInt, properties = propertieOTH)
            val nonExistdLinks = updateRoadLinkOTH(parsedRowOTH, Some(userProvider.getCurrentUser().username), propertieVVH.exists(_.columnName == "DIRECTIONTYPE"))
              .map(nonExistRoadLinkType => NonExistingLink(linkIdandField = nonExistRoadLinkType, csvRow = rowToString(row)))
            result.copy(nonExistingLinks = nonExistdLinks ::: result.nonExistingLinks)
          }
        }

        if (propertieVVH.size > 0) {
          val parsedRowVVH = CsvRoadLinkRow(row("Linkin ID").toInt, properties = propertieVVH)
          val nonExistdLinks = updateRoadLinkInVVH(parsedRowVVH)
            .map(nonExistRoadLinkType => NonExistingLink(linkIdandField = nonExistRoadLinkType, csvRow = rowToString(row)))
          result.copy(nonExistingLinks = nonExistdLinks ::: result.nonExistingLinks)
        } else {
          result
        }

      } else {
        result.copy(
          incompleteLinks = missingParameters match {
            case Nil => result.incompleteLinks
            case parameters => IncompleteLink(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteLinks
          },
          malformedLinks = malformedParameters match {
            case Nil => result.malformedLinks
            case parameters => MalformedLink(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedLinks
          },
          excludedLinks = affectedRoadLinkType match {
            case None => result.excludedLinks
            case Some(parameters) => ExcludedLink(affectedRoadLinkType = parameters.toString, csvRow = rowToString(row)) :: result.excludedLinks
          }
        )
      }
    }
  }
}