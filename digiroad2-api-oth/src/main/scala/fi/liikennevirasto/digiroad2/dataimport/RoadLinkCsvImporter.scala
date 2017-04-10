package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.dataimport.RoadLinkCsvImporter.RoadLinkCsvImporter._
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkProperties
import fi.liikennevirasto.digiroad2.roadlinkservice.oracle.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.{RoadLinkService, VVHClient}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.apache.commons.lang3.StringUtils.isBlank

object RoadLinkCsvImporter {

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
  //val roadLinkService: RoadLinkService //replaced by roadLinkServiceDAO
  val vvhClient: VVHClient
  val userProvider: UserProvider

  case class CsvRoadLinkRow(linkId:	Int, administrativeClass: Int, functionalClass: Int, trafficDirection: Int, linkType: Int,
                            verticalLevel: Int, municipalityCode: Int, roadNameFi: String, roadNameSe: String, roadNameSm: String,
                            minAddressNumRight: Int, maxAddressNumRight: Int, minAddressNumLeft: Int, maxAddressNumLeft: Int,
                            linkStatus: Int)

  type ParsedProperties = List[CsvRoadLinkRow]
  type MalformedParameters = List[String]

  type ParsedLinkRow = (MalformedParameters, ParsedProperties)
  type ExcludedRoadLinkTypes = List[AdministrativeClass]


  private val isValidAdministrativeClass = Set(2,3,99)

  private val textFieldMappings = Map(
    "Hallinnollinen luokka" -> "administrativeClass",
    "Toiminnallinen luokka" -> "functionalClass",
    "Liikennevirran suunta" -> "trafficDirection",
    "Tielinkin tyyppi" -> "linkType",
    "Tasosijainti" -> "verticalLevel",
    "Kuntanumero" -> "municipalityCode",
    "Tien nimi (suomi)" -> "roadNameFi",
    "Tien nimi (ruotsi)" -> "roadNameSe",
    "Tien nimi (saame)" -> "roadNameSm",
    "Osoitenumerot oikealla alku" -> "minAddressNumberRight",
    "Osoitenumerot oikealla loppu" -> "maxAddressNumberRight",
    "Osoitenumerot vasemmalla alku" -> "minAddressNumberLeft",
    "Osoitenumerot vasemmalla loppu" -> "maxAddressNumberLeft",
    "Linkin tila" -> "linkStatus"
  )
  val mappings = textFieldMappings

  val MandatoryParameter: Set[String] = textFieldMappings.keySet + "Linkin ID"

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameter.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String]): ExcludedRoadLinkTypes = {
    if (isValidAdministrativeClass.contains(roadLinkAttribute.administrativeClass)) {
      val optionalAdministrativeClassValue: Option[Int] = RoadLinkServiceDAO.getAdministrativeClassValue(roadLinkAttribute.linkId)
      optionalAdministrativeClassValue match {
        case Some(existingValue) =>
          RoadLinkServiceDAO.updateAdministrativeClass(roadLinkAttribute.linkId, username, existingValue, roadLinkAttribute.administrativeClass)
        case None =>
          RoadLinkServiceDAO.insertAdministrativeClass(roadLinkAttribute.linkId, username, roadLinkAttribute.administrativeClass)
      }

      val optionalTrafficDirectionValue: Option[Int] = RoadLinkServiceDAO.getTrafficDirectionValue(roadLinkAttribute.linkId)
      optionalTrafficDirectionValue match {
        case Some(existingValue) =>
          RoadLinkServiceDAO.updateTrafficDirection(roadLinkAttribute.linkId, username, existingValue, roadLinkAttribute.administrativeClass)
        case None =>
          RoadLinkServiceDAO.insertTrafficDirection(roadLinkAttribute.linkId, username, roadLinkAttribute.administrativeClass)
      }

      val optionalLinkTypeValue: Option[Int] = RoadLinkServiceDAO.getLinkTypeValue(roadLinkAttribute.linkId)
      optionalLinkTypeValue match {
        case Some(existingValue) =>
          RoadLinkServiceDAO.updateAdministrativeClass(roadLinkAttribute.linkId, username, existingValue, roadLinkAttribute.administrativeClass)
        case None =>
          RoadLinkServiceDAO.insertAdministrativeClass(roadLinkAttribute.linkId, username, roadLinkAttribute.administrativeClass)
      }
      Nil

      //    } else
      ///    roadLinkAttribute.linkId
    }
    Nil
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedLinkRow = {
    csvRowWithHeaders.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, parameter) =>
      val (key, value) = parameter
      if (isBlank(value)) {
        result
      } else {
        //if (textFieldMappings.contains(key)) {
        //val (malformedParameters, properties) = textFieldMappings(key, value)
        //result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        //} else {
        result
        //}
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
      val (malformedParameters, properties) = assetRowToProperties(row)
      if (missingParameters.isEmpty /*&& malformedParameters.isEmpty*/ ) {
        val parsedRow = CsvRoadLinkRow(row("Linkin ID").toInt, row("Hallinnollinen luokka").toInt, row("Toiminnallinen luokka").toInt,
          row("Liikennevirran suunta").toInt, row("Tielinkin tyyppi").toInt, row("Tasosijainti").toInt, row("Kuntanumero").toInt,
          row("Tien nimi (suomi)").toString, row("Tien nimi (ruotsi)").toString, row("Tien nimi (saame)").toString,
          row("Osoitenumerot oikealla alku").toInt, row("Osoitenumerot oikealla loppu").toInt, row("Osoitenumerot vasemmalla alku").toInt,
          row("Osoitenumerot vasemmalla loppu").toInt, row("Linkin tila").toInt)



        try {
          // if all things ok
          //call updateOTHdata
          val excludedLinks = updateRoadLinkOTH(parsedRow, Some(userProvider.getCurrentUser().username)).map(excludedRoadLinkType => ExcludedLink(affectedRoadLinkType = excludedRoadLinkType.toString, csvRow = rowToString(row)))
          result.copy(excludedLinks = excludedLinks ::: result.excludedLinks)

          // val excludedLinks = updateRoadLinkVVH(parsedRow, Some(userProvider.getCurrentUser().username))
          //  .map(excludedRoadLinkType => ExcludedLink(affectedRoadLinkType = excludedRoadLinkType.toString, csvRow = rowToString(row)))
          //result.copy(excludedLinks = excludedLinks ::: result.excludedLinks)
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingLinks = NonExistingLink(linkId = parsedRow.linkId, csvRow = rowToString(row)) :: result.nonExistingLinks)
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