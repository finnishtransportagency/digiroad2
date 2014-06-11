package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStreamReader, InputStream}
import com.github.tototoshi.csv._
import org.apache.commons.lang3.StringUtils.isBlank
import fi.liikennevirasto.digiroad2.asset.{AssetProvider, PropertyValue, SimpleProperty}

object CsvImporter {
  case class ImportResult()
  case class CsvAssetRow(externalId: Long, properties: Seq[SimpleProperty])

  def assetRowToProperties(stopName: String): Seq[SimpleProperty] = {
    if(isBlank(stopName)) Seq()
    else Seq(SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue(stopName))))
  }

  def importAssets(inputStream: InputStream, assetProvider: AssetProvider): ImportResult = {
    val streamReader = new InputStreamReader(inputStream)
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    val rows = csvReader.allWithHeaders().map { row => CsvAssetRow(externalId = row("Valtakunnallinen ID").toLong, properties = assetRowToProperties(row("Pysäkin nimi"))) }
    rows.map(row => assetProvider.updateAssetByExternalId(row.externalId, row.properties))
    ImportResult()
  }
}
