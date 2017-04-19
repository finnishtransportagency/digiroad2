package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, PropertyValue, SimpleProperty}
import fi.liikennevirasto.digiroad2.dataimport.DataCsvImporter.RoadLinkCsvImporter._
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkProperties
import fi.liikennevirasto.digiroad2.roadlinkservice.oracle.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.{RoadLinkService, VVHClient}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.apache.commons.lang3.StringUtils.isBlank

object DataCsvImporter {

  object RoadLinkCsvImporter {
    case class NonExistingLink(linkId: Int, csvRow: String)
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
  case class CsvRoadLinkRow(linkId:	Int, properties: Seq[LinkProperty])

  type ParsedProperties = List[LinkProperty]
  type MalformedParameters = List[String]
  type ParsedLinkRow = (MalformedParameters, ParsedProperties)

  type ExcludedRoadLinkTypes = List[AdministrativeClass]
  type IncompleteLink = List[String]

  private val isValidAdministrativeClass = Seq(2,3,4)

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

  private def findMandatoryParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    csvRowWithHeaders.get(mandatoryFields)
    match {
      case Some(value)
        if (value.isEmpty || !value.forall(_.isDigit)) => List(mandatoryFields)
      case _ => Nil
    }
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String]) : ExcludedRoadLinkTypes = {

    roadLinkAttribute.properties.map { prop =>
      // in new specification the values in this table will be deleted
      if (prop.columnName.equals("trafficDirection")) {
        val optionalTrafficDirectionValue: Option[Int] = RoadLinkServiceDAO.getTrafficDirectionValue(roadLinkAttribute.linkId)
        optionalTrafficDirectionValue match {
          case Some(existingValue) =>
            RoadLinkServiceDAO.deleteTrafficDirection(roadLinkAttribute.linkId)
          case None =>
            None
        }
      }

      if (prop.columnName.equals("linkType")) {
        val optionalLinkTypeValue: Option[Int] = RoadLinkServiceDAO.getLinkTypeValue(roadLinkAttribute.linkId)
        optionalLinkTypeValue match {
          case Some(existingValue) =>
            RoadLinkServiceDAO.updateLinkType(roadLinkAttribute.linkId, username, existingValue, prop.value.toInt)
          case None =>
            RoadLinkServiceDAO.insertLinkType(roadLinkAttribute.linkId, username, prop.value.toInt)
        }
      }

      if (prop.columnName.equals("functionalClass")) {
        val optionalAdministrativeClassValue: Option[Int] = RoadLinkServiceDAO.getFunctionalClassValue(roadLinkAttribute.linkId)
        optionalAdministrativeClassValue match {
          case Some(existingValue) =>
            RoadLinkServiceDAO.updateFunctionalClass(roadLinkAttribute.linkId, username, existingValue, prop.value.toInt)
          case None =>
            RoadLinkServiceDAO.insertFunctionalClass(roadLinkAttribute.linkId, username, prop.value.toInt)
        }
      }
    }
    Nil
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
        result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = LinkProperty(columnName = textFieldMappings(key), value = value):: result._2)
        } else if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else
          result
      }
    }
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'"} mkString ", "
  }

  def importLinkAttribute(inputStream: InputStream): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
      val missingParameters = findMissingParameters(row)
      val (malformedFields, properties) = linkRowToProperties(row)
      val malformedParameters = malformedFields ::: findMandatoryParameters(row)
      if (missingParameters.isEmpty && malformedParameters.isEmpty) {
          val parsedRow = CsvRoadLinkRow(row("Linkin ID").toInt, properties = properties )
        try {
          // if all things ok
          //call updateOTHdata
          val excludedLinks = updateRoadLinkOTH(parsedRow, Some(userProvider.getCurrentUser().username))
            .map(excludedRoadLinkType => ExcludedLink(affectedRoadLinkType = excludedRoadLinkType.toString, csvRow = rowToString(row)))
          result.copy(excludedLinks = excludedLinks ::: result.excludedLinks)
        } catch {
          case e: RoadLinkNotFoundException => result.copy(nonExistingLinks = NonExistingLink(linkId = parsedRow.linkId, csvRow = rowToString(row)) :: result.nonExistingLinks)
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