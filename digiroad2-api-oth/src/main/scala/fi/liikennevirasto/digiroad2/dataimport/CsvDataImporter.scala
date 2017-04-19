package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dataimport.DataCsvImporter.RoadLinkCsvImporter._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.roadlinkservice.oracle.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.{RoadLinkService, VVHClient}
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

  case class LinkProperty(columnName: String, value: String)
  case class CsvRoadLinkRow(linkId: Int, properties: Seq[LinkProperty])

  type ParsedProperties = List[LinkProperty]
  type MalformedParameters = List[String]
  type ParsedLinkRow = (MalformedParameters, ParsedProperties)
  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  private val administrativeClassLimitations: Set[AdministrativeClass] = Set(Municipality, Private, Unknown)

  private val intFieldMappings = Map(
    "Hallinnollinen luokka" -> "administrativeClass",
    "Toiminnallinen luokka" -> "functionalClass",
    "Liikennevirran suunta" -> "trafficDirection",
    "Tielinkin tyyppi" -> "linkType",
    "Tasosijainti" -> "verticalLevel",
    "Kuntanumero" -> "municipalityCode",
    "Osoitenumerot oikealla alku" -> "minAddressNumberRight",
    "Osoitenumerot oikealla loppu" -> "maxAddressNumberRight",
    "Osoitenumerot vasemmalla alku" -> "minAddressNumberLeft",
    "Osoitenumerot vasemmalla loppu" -> "maxAddressNumberLeft",
    "Linkin tila" -> "linkStatus"
  )

  private val fieldInOTH = List("linkType", "functionalClass")

  private val textFieldMappings = Map(
    "Tien nimi (suomi)" -> "roadNameFi",
    "Tien nimi (ruotsi)" -> "roadNameSe",
    "Tien nimi (saame)" -> "roadNameSm"
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

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String], hasTrafficDirectionChange: Boolean): List[String] = {
    OracleDatabase.withDynTransaction {
      if (hasTrafficDirectionChange) {
        //if exist some changes in OTH database will be deleted
        RoadLinkServiceDAO.getTrafficDirectionValue(roadLinkAttribute.linkId) match {
          case Some(value) => RoadLinkServiceDAO.deleteTrafficDirection(roadLinkAttribute.linkId)
          case _ => None
        }
      }

      roadLinkAttribute.properties.map { prop =>
        if (prop.columnName.equals("linkType")) {
          val optionalLinkTypeValue: Option[Int] = RoadLinkServiceDAO.getLinkTypeValue(roadLinkAttribute.linkId)
          optionalLinkTypeValue match {
            case Some(existingValue) =>
              RoadLinkServiceDAO.updateLinkType(roadLinkAttribute.linkId, username, existingValue, prop.value.toInt)
            case None =>
              List("linkType")
            // RoadLinkServiceDAO.insertLinkType(roadLinkAttribute.linkId, username, prop.value.toInt)
          }
        }

        if (prop.columnName.equals("functionalClass")) {
          val optionalAdministrativeClassValue: Option[Int] = RoadLinkServiceDAO.getFunctionalClassValue(roadLinkAttribute.linkId)
          optionalAdministrativeClassValue match {
            case Some(existingValue) =>
              RoadLinkServiceDAO.updateFunctionalClass(roadLinkAttribute.linkId, username, existingValue, prop.value.toInt)
            case None =>
              List("functionalClass")
            // RoadLinkServiceDAO.insertFunctionalClass(roadLinkAttribute.linkId, username, prop.value.toInt)
          }
        }
      }
      Nil
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

  def validateAdministrativeClass(properties: Seq[LinkProperty]): ExcludedRoadLinkTypes = {
    Nil
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
      val excludedRecord = validateAdministrativeClass(properties)
      if (missingParameters.isEmpty && malformedParameters.isEmpty && excludedRecord.isEmpty) {
        try {
          val (propertieOTH, propertieVVH) = properties.partition(a => fieldInOTH.contains(a.columnName))

          if (propertieOTH.size > 0) {
            val parsedRowOTH = CsvRoadLinkRow(row("Linkin ID").toInt, properties = propertieOTH)
            val nonExistdLinks = updateRoadLinkOTH(parsedRowOTH, Some(userProvider.getCurrentUser().username), propertieVVH.exists(_.columnName == "trafficDirection"))
              .map(nonExistRoadLinkType => NonExistingLink(linkIdandField = nonExistRoadLinkType, csvRow = rowToString(row)))
            result.copy(nonExistingLinks = nonExistdLinks ::: result.nonExistingLinks)

          } else {
            result
          }
          //            if (propertieVVH.size > 0) {
          //                val parsedRowVVH = CsvRoadLinkRow(row("Linkin ID").toInt, properties = propertieVVH )
          //            }

        } catch {
          case e: RoadLinkNotFoundException => result.copy(nonExistingLinks = NonExistingLink(linkIdandField = row("Linkin ID").toString, csvRow = rowToString(row)) :: result.nonExistingLinks)
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
          }
        )
      }
    }
  }
}