package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStreamReader, InputStream}
import com.github.tototoshi.csv._
import org.apache.commons.lang3.StringUtils.isBlank
import fi.liikennevirasto.digiroad2.asset.{AssetNotFoundException, AssetProvider, PropertyValue, SimpleProperty}

object CsvImporter {
  case class NonExistingAsset(externalId: Long, csvRow: String)
  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class MalformedAsset(malformedParameters: List[String], csvRow: String)
  case class ImportResult(nonExistingAssets: List[NonExistingAsset], incompleteAssets: List[IncompleteAsset], malformedAssets: List[MalformedAsset])
  case class CsvAssetRow(externalId: Long, properties: Seq[SimpleProperty])

  type MalformedParameters = List[String]
  type ParsedProperties = List[SimpleProperty]

  private def maybeInt(string: String): Option[Int] = {
    try {
      Some(string.toInt)
    } catch {
      case e: NumberFormatException => None
    }
  }

  private val isValidTypeEnumeration = Set(1, 2, 3, 4, 5, 99)

  private def resultWithType(result: (MalformedParameters, List[SimpleProperty]), assetType: Int): (MalformedParameters, ParsedProperties) = {
    result.copy(_2 = result._2 match {
      case List(SimpleProperty("pysakin_tyyppi", xs)) => List(SimpleProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.toList))
      case _ => List(SimpleProperty("pysakin_tyyppi", Seq(PropertyValue(assetType.toString))))
    })
  }

  private def assetTypeToProperty(assetTypes: String): (MalformedParameters, ParsedProperties) = {
    val invalidAssetTypes = (List("Pysäkin tyyppi"), List())
    val types = assetTypes.split(',')
    if(types.isEmpty) invalidAssetTypes
    else {
      val typeRegex = """^\s*(\d+)\s*$""".r
      types.foldLeft((List(): MalformedParameters, List(): ParsedProperties)) { (result, assetType) =>
        typeRegex.findFirstMatchIn(assetType) match {
          case Some(t) =>
            maybeInt(t.group(1)) match {
              case Some(i) => if(isValidTypeEnumeration(i)) resultWithType(result, i) else invalidAssetTypes
              case None => invalidAssetTypes
            }
          case None => invalidAssetTypes
        }
      }
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): (MalformedParameters, ParsedProperties) = {
    csvRowWithHeaders.foldLeft((List(): MalformedParameters, List(): ParsedProperties)) { (result, parameter) =>
      val (key, value) = parameter
      if(isBlank(value)) {
        result
      } else {
        key match {
          case "Pysäkin nimi" => result.copy(_2 = SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue(value))) :: result._2)
          case "Pysäkin tyyppi" =>
            val (malformedParameters, properties) = assetTypeToProperty(value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          case _ => result
        }
      }
    }
  }

  private def rowToString(csvRowWithHeaders: Map[String, String]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'"} mkString ", "
  }

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    val mandatoryParameters: Set[String] = Set("Valtakunnallinen ID", "Pysäkin nimi")
    csvRowWithHeaders.keys.foldLeft(mandatoryParameters) { (mandatoryParameters, key) => mandatoryParameters - key }.toList
  }

  def importAssets(inputStream: InputStream, assetProvider: AssetProvider): ImportResult = {
    val streamReader = new InputStreamReader(inputStream)
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult(List(), List(), List())) { (result, row) =>
      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = assetRowToProperties(row)
      if(missingParameters.isEmpty && malformedParameters.isEmpty) {
        val parsedRow = CsvAssetRow(externalId = row("Valtakunnallinen ID").toLong, properties = properties)
        try {
          assetProvider.updateAssetByExternalId(parsedRow.externalId, parsedRow.properties)
          result
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingAssets = NonExistingAsset(externalId = parsedRow.externalId, csvRow = rowToString(row)) :: result.nonExistingAssets)
        }
      } else {
        result.copy(
          incompleteAssets = missingParameters match {
            case List() => result.incompleteAssets
            case xs => IncompleteAsset(missingParameters = xs, csvRow = rowToString(row)) :: result.incompleteAssets
          },
          malformedAssets = malformedParameters match {
            case List() => result.malformedAssets
            case xs => MalformedAsset(malformedParameters = xs, csvRow = rowToString(row)) :: result.malformedAssets
          }
        )
      }
    }
  }
}
