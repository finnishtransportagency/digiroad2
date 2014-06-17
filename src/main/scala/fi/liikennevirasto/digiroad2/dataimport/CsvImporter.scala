package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStreamReader, InputStream}
import com.github.tototoshi.csv._
import org.apache.commons.lang3.StringUtils.isBlank
import fi.liikennevirasto.digiroad2.asset._

object CsvImporter {
  case class NonExistingAsset(externalId: Long, csvRow: String)
  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class MalformedAsset(malformedParameters: List[String], csvRow: String)
  case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)
  case class ImportResult(nonExistingAssets: List[NonExistingAsset],
                          incompleteAssets: List[IncompleteAsset],
                          malformedAssets: List[MalformedAsset],
                          excludedAssets: List[ExcludedAsset])
  case class CsvAssetRow(externalId: Long, properties: Seq[SimpleProperty])

  type MalformedParameters = List[String]
  type ParsedProperties = List[SimpleProperty]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)
  type ExcludedRoadLinkTypes = List[RoadLinkType]

  private def maybeInt(string: String): Option[Int] = {
    try {
      Some(string.toInt)
    } catch {
      case e: NumberFormatException => None
    }
  }

  private val isValidTypeEnumeration = Set(1, 2, 3, 4, 5, 99)

  private def resultWithType(result: (MalformedParameters, List[SimpleProperty]), assetType: Int): ParsedAssetRow = {
    result.copy(_2 = result._2 match {
      case List(SimpleProperty("pysakin_tyyppi", xs)) => List(SimpleProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.toList))
      case _ => List(SimpleProperty("pysakin_tyyppi", Seq(PropertyValue(assetType.toString))))
    })
  }

  private def assetTypeToProperty(assetTypes: String): ParsedAssetRow = {
    val invalidAssetType = (List("Pysäkin tyyppi"), Nil)
    val types = assetTypes.split(',')
    if(types.isEmpty) invalidAssetType
    else {
      types.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, assetType) =>
        maybeInt(assetType.trim)
          .filter(isValidTypeEnumeration)
          .map(typeEnumeration => resultWithType(result, typeEnumeration))
          .getOrElse(invalidAssetType)
      }
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
    csvRowWithHeaders.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, parameter) =>
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

  def updateAsset(externalId: Long, properties: Seq[SimpleProperty], limitImportToStreets: Boolean, assetProvider: AssetProvider): ExcludedRoadLinkTypes = {
    val excludedRoadLinkTypes: Set[RoadLinkType] = Set(PrivateRoad, Road, UnknownRoad)

    val exclusion = if(limitImportToStreets) {
      val roadLinkType: RoadLinkType = assetProvider.getAssetByExternalId(externalId)
        .flatMap(asset => assetProvider.getRoadLinkById(asset.roadLinkId))
        .map(_.roadLinkType)
        .getOrElse(UnknownRoad)
      (Set(roadLinkType) & excludedRoadLinkTypes).toList
    } else Nil

    if(exclusion.isEmpty) assetProvider.updateAssetByExternalId(externalId, properties)

    exclusion
  }

  def importAssets(inputStream: InputStream, assetProvider: AssetProvider, limitImportToStreets: Boolean = false): ImportResult = {
    val streamReader = new InputStreamReader(inputStream)
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult(Nil, Nil, Nil, Nil)) { (result, row) =>
      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = assetRowToProperties(row)
      if(missingParameters.isEmpty && malformedParameters.isEmpty) {
        val parsedRow = CsvAssetRow(externalId = row("Valtakunnallinen ID").toLong, properties = properties)
        try {
          result.copy(excludedAssets = updateAsset(parsedRow.externalId, parsedRow.properties, limitImportToStreets, assetProvider)
            .map(excludedRoadLinkType => ExcludedAsset(affectedRoadLinkType = excludedRoadLinkType.toString, csvRow = rowToString(row))) ::: result.excludedAssets)
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingAssets = NonExistingAsset(externalId = parsedRow.externalId, csvRow = rowToString(row)) :: result.nonExistingAssets)
        }
      } else {
        result.copy(
          incompleteAssets = missingParameters match {
            case Nil => result.incompleteAssets
            case parameters => IncompleteAsset(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteAssets
          },
          malformedAssets = malformedParameters match {
            case Nil => result.malformedAssets
            case parameters => MalformedAsset(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedAssets
          }
        )
      }
    }
  }
}
