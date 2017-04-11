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
  //val roadLinkService: RoadLinkService //replaced by roadLinkServiceDAO
  val vvhClient: VVHClient
  val userProvider: UserProvider

  case class CsvRoadLinkRow(linkId:	Int, administrativeClass: Int, functionalClass: Int, trafficDirection: Int, linkType: Int,
                            verticalLevel: Int, municipalityCode: Int, roadNameFi: String, roadNameSe: String, roadNameSm: String,
                            minAddressNumRight: Int, maxAddressNumRight: Int, minAddressNumLeft: Int, maxAddressNumLeft: Int,
                            linkStatus: Int)

  type ParsedProperties  = List[ImportResult]
  type MalformedParameters = List[String]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)

  type ExcludedRoadLinkTypes = List[AdministrativeClass]
  type IncompleteLink = List[String]

  private val isValidAdministrativeClass = Set(2,3,99)

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

  val mappings = textFieldMappings ++ intFieldMappings

 // val MandatoryParameters: Set[String] = mappings.keySet + "Linkin ID"
  //private val MandatoryParameter: String = "Linkin ID"
  //TODO return only one string instead of List Strings
//  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
//    csvRowWithHeaders.find(_._1 == "Linkin ID").map(_._2) match {
//      case Some(value) => List[String]()
//      case _ => List("Linkin ID")
//    }
//  }

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    //MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
    List[String]()
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String]): ExcludedRoadLinkTypes = {

    if (!isBlank(roadLinkAttribute.administrativeClass.toString)) {
      if (isValidAdministrativeClass.contains(roadLinkAttribute.administrativeClass)) {
        val optionalAdministrativeClassValue: Option[Int] = RoadLinkServiceDAO.getAdministrativeClassValue(roadLinkAttribute.linkId)
        optionalAdministrativeClassValue match {
          case Some(existingValue) =>
            RoadLinkServiceDAO.updateAdministrativeClass(roadLinkAttribute.linkId, username, existingValue, roadLinkAttribute.administrativeClass)
          case None =>
            RoadLinkServiceDAO.insertAdministrativeClass(roadLinkAttribute.linkId, username, roadLinkAttribute.administrativeClass)
        }
      } else
        Nil
    }

    if (!isBlank(roadLinkAttribute.trafficDirection.toString)) {
      val optionalTrafficDirectionValue: Option[Int] = RoadLinkServiceDAO.getTrafficDirectionValue(roadLinkAttribute.linkId)
      optionalTrafficDirectionValue match {
        case Some(existingValue) =>
          RoadLinkServiceDAO.updateTrafficDirection(roadLinkAttribute.linkId, username, existingValue, roadLinkAttribute.administrativeClass)
        case None =>
          RoadLinkServiceDAO.insertTrafficDirection(roadLinkAttribute.linkId, username, roadLinkAttribute.administrativeClass)
      }
    }

    if (!isBlank(roadLinkAttribute.linkType.toString)) {
      val optionalLinkTypeValue: Option[Int] = RoadLinkServiceDAO.getLinkTypeValue(roadLinkAttribute.linkId)
      optionalLinkTypeValue match {
        case Some(existingValue) =>
          RoadLinkServiceDAO.updateAdministrativeClass(roadLinkAttribute.linkId, username, existingValue, roadLinkAttribute.administrativeClass)
        case None =>
          RoadLinkServiceDAO.insertAdministrativeClass(roadLinkAttribute.linkId, username, roadLinkAttribute.administrativeClass)
      }
    }
    Nil
    //TODO confirm when the AdministrativeClass is equal 1 if needs to delete
    //    } else
    ///    roadLinkAttribute.linkId
  }


  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): MalformedParameters = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters) { (result, parameter) =>
      val (key, value) = parameter
      if (isBlank(value)) {
        result
      } else {
        if (textFieldMappings.contains(key)) {
          val malformedParameters = textFieldMappings(key)
          //result.copy(_2 = malformedParameters :: result._2)
          result
        } else if (intFieldMappings.contains(key)) {

          val malformedParameters = intFieldMappings(key)
          result
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
      val malformedParameters = assetRowToProperties(row)
      if (missingParameters.isEmpty && malformedParameters.isEmpty ) {
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