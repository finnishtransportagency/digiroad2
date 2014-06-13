package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStreamReader, InputStream}
import com.github.tototoshi.csv._
import org.apache.commons.lang3.StringUtils.isBlank
import fi.liikennevirasto.digiroad2.asset.{AssetNotFoundException, AssetProvider, PropertyValue, SimpleProperty}

object CsvImporter {
  case class NonExistingAsset(externalId: Long, csvRow: String)
  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class ImportResult(nonExistingAssets: List[NonExistingAsset], incompleteAssets: List[IncompleteAsset])
  case class CsvAssetRow(externalId: Long, properties: Seq[SimpleProperty])

  private def assetRowToProperties(stopName: String): Seq[SimpleProperty] = {
    if(isBlank(stopName)) Seq()
    else Seq(SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue(stopName))))
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
    csvReader.allWithHeaders().foldLeft(ImportResult(List(), List())) { (result, row) =>
      val missingParameters = findMissingParameters(row)
      if(missingParameters.isEmpty) {
        val parsedRow = CsvAssetRow(externalId = row("Valtakunnallinen ID").toLong, properties = assetRowToProperties(row("Pysäkin nimi")))
        try {
          assetProvider.updateAssetByExternalId(parsedRow.externalId, parsedRow.properties)
          result
        } catch {
          case e: AssetNotFoundException => result.copy(nonExistingAssets = NonExistingAsset(externalId = parsedRow.externalId, csvRow = rowToString(row)) :: result.nonExistingAssets)
        }
      } else {
        result.copy(incompleteAssets = IncompleteAsset(missingParameters = missingParameters, csvRow = rowToString(row)) :: result.incompleteAssets)
      }
    }
  }
}
