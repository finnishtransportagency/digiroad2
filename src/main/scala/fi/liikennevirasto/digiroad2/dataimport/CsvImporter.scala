package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStreamReader, InputStream}
import com.github.tototoshi.csv._
import org.apache.commons.lang3.StringUtils.isBlank
import fi.liikennevirasto.digiroad2.asset.{AssetNotFoundException, AssetProvider, PropertyValue, SimpleProperty}

object CsvImporter {
  case class NonExistingAsset(externalId: Long, csvRow: String)
  case class ImportResult(nonExistingAssets: List[NonExistingAsset])
  case class CsvAssetRow(externalId: Long, properties: Seq[SimpleProperty])

  def assetRowToProperties(stopName: String): Seq[SimpleProperty] = {
    if(isBlank(stopName)) Seq()
    else Seq(SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue(stopName))))
  }

  def rowToString(csvRowWithHeaders: Map[String, String]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'"} mkString ", "
  }

  def importAssets(inputStream: InputStream, assetProvider: AssetProvider): ImportResult = {
    val streamReader = new InputStreamReader(inputStream)
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    val nonExistingAssets = csvReader.allWithHeaders().foldLeft(List(): List[NonExistingAsset]) { (nonExistingAssets, row) =>
      val parsedRow = CsvAssetRow(externalId = row("Valtakunnallinen ID").toLong, properties = assetRowToProperties(row("Pysäkin nimi")))
      try {
        assetProvider.updateAssetByExternalId(parsedRow.externalId, parsedRow.properties)
        nonExistingAssets
      } catch {
        case e: AssetNotFoundException => NonExistingAsset(externalId = parsedRow.externalId, csvRow = rowToString(row)) :: nonExistingAssets
      }
    }
    ImportResult(nonExistingAssets = nonExistingAssets)
  }
}
