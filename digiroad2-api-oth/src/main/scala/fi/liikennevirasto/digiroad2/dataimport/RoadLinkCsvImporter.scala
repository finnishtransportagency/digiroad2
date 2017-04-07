package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkProperties
import fi.liikennevirasto.digiroad2.{RoadLinkService, VVHClient}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.apache.commons.lang3.StringUtils.isBlank

object RoadLinkCsvImporter {

  object CsvImporter {
    case class NonExistingLink(externalId: Long, csvRow: String)
    case class IncompleteLink(missingParameters: List[String], csvRow: String)
    case class MalformedLink(malformedParameters: List[String], csvRow: String)
    case class ExcludedLink(affectedRoadLinkType: String, csvRow: String)
    case class ImportResult(nonExistingLinks: List[NonExistingLink] = Nil,
                            incompleteLinks: List[IncompleteLink] = Nil,
                            malformedLinks: List[MalformedLink] = Nil,
                            excludedLinks: List[ExcludedLink] = Nil)
  }

}
class RoadLinkNotFoundException(externalId: Long) extends RuntimeException

trait RoadLinkCsvImporter {
  val roadLinkService: RoadLinkService
  val vvhClient: VVHClient
  val userProvider: UserProvider

  case class CsvRoadLinkRow(externalId: Long, properties: Seq[RoadLinkProperties])

  type ParsedProperties = List[RoadLinkProperties]

  private val textFieldMappings = Map(
    "Linkin ID" -> "linkId",
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


//  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedProperties = {
//    csvRowWithHeaders.foldLeft(Nil: ParsedProperties) { (result, parameter) =>
//      val (key, value) = parameter
//      if(isBlank(value)) {
//        result
//      } else {
//        if (textFieldMappings.contains(key)) {
//          result.copy(_2 = SimpleProperty(publicId = textFieldMappings(key), values = Seq(PropertyValue(value))) :: result._2)
//        } else if (multipleChoiceFieldMappings.contains(key)) {
//          val (malformedParameters, properties) = assetTypeToProperty(value)
//          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
//        } else if (singleChoiceFieldMappings.contains(key)) {
//          val (malformedParameters, properties) = assetSingleChoiceToProperty(key, value)
//          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
//        } else {
//          result
//        }
//      }
//    }
//  }
//
//    def importRoadLinks(inputStream: InputStream): ImportResult = {
//      val streamReader = new InputStreamReader(inputStream, "UTF-8")
//      val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
//        override val delimiter: Char = ';'
//      })
//      csvReader.all() => ImportResult
//      }
//    }

}