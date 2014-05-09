package fi.liikennevirasto.digiroad2.util

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, AssetWithProperties}
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, OracleSpatialAssetProvider}
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime

class AssetValluCsvFormatterSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)
  var assetsByMunicipality: Iterable[AssetWithProperties] = null
  before {
    assetsByMunicipality = provider.getAssetsByMunicipality(235)
  }

  it must "return correct csv entries from test data" in {
    val csvAll = AssetValluCsvFormatter.formatAssetsWithProperties(assetsByMunicipality)
    csvAll.size must be > 3
    val csv = csvAll.find(_.startsWith("5")).get

    val propertyValue = extractPropertyValue(assetsByMunicipality.find(_.externalId.get == 5).get, _: String)
    val created = inOutputDateFormat(parseCreationDateTime(propertyValue("lisatty_jarjestelmaan")))

    val validFrom = inOutputDateFormat(parseDate(propertyValue("ensimmainen_voimassaolopaiva")))
    val validTo = inOutputDateFormat(parseDate(propertyValue("viimeinen_voimassaolopaiva")))

    csv must equal("5;;;;;374792.096855508;6677566.77442972;;;210;Etelään;;1;1;1;0;;;;" + created + ";dr1conversion;" + validFrom + ";" + validTo + ";Liikennevirasto;235;Kauniainen;;")
  }

  def createStop(stopType: Seq[Long]): AssetWithProperties = {
    def typeToPropertyValue(typeCode: Long): PropertyValue = { PropertyValue(typeCode.toString, Some(typeCode.toString)) }

    val sourceAsset = assetsByMunicipality.find(_.externalId.get == 5).get
    val properties = sourceAsset.propertyData.map { property =>
      property.publicId match {
        case "pysakin_tyyppi" => property.copy(values = stopType.map(typeToPropertyValue))
        case _ => property
      }
    }
    sourceAsset.copy(propertyData = properties)
  }

  it must "filter tram stops from test data" in {
    val tramStopType = List(1L)
    val localBusStopType = List(2L)
    val tramStop = createStop(tramStopType)
    val localBusStop = createStop(localBusStopType)
    val tramAndLocalBusStop = createStop(tramStopType ++ localBusStopType)
    val csvRows = AssetValluCsvFormatter.valluCsvRowsFromAssets(List(tramStop, localBusStop, tramAndLocalBusStop), Map())
    csvRows must have size 2
  }

  val testasset = AssetWithProperties(1, None, 1, 2.1, 2.2, 1, bearing = Some(3), validityDirection = None, wgslon = 2.2, wgslat = 0.56)
  it must "recalculate bearings in validity direction" in {
    AssetValluCsvFormatter.addBearing(testasset, List())._2 must equal (List("3"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(3)), List())._2 must equal (List("183"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(2)), List())._2 must equal (List("3"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(2), bearing = Some(195)), List())._2 must equal (List("195"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(3), bearing = Some(195)), List())._2 must equal (List("15"))
  }

  it must "describe bearing correctly" in {
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(316)), List())._2 must equal (List("Pohjoiseen"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(45)), List())._2 must equal (List("Pohjoiseen"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(46)), List())._2 must equal (List("Itään"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(135)), List())._2 must equal (List("Itään"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(136)), List())._2 must equal (List("Etelään"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(225)), List())._2 must equal (List("Etelään"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(226)), List())._2 must equal (List("Länteen"))
    AssetValluCsvFormatter.addBearingDescription(testasset.copy(bearing = Some(315)), List())._2 must equal (List("Länteen"))
  }

  it must "filter out newlines from text fields" in {
    val sourceAsset = assetsByMunicipality.find(_.externalId.get == 5).get
    val propertyValue = extractPropertyValue(sourceAsset, _: String)
    val created = inOutputDateFormat(parseCreationDateTime(propertyValue("lisatty_jarjestelmaan")))
    val validFrom = inOutputDateFormat(parseDate(propertyValue("ensimmainen_voimassaolopaiva")))
    val validTo = inOutputDateFormat(parseDate(propertyValue("viimeinen_voimassaolopaiva")))

    val testProperties = sourceAsset.propertyData.map { property =>
      property.publicId match {
        case "yllapitajan_tunnus" => property.copy(values = List(textPropertyValue("id\n")))
        case "matkustajatunnus" => property.copy(values = List(textPropertyValue("matkustaja\ntunnus")))
        case "nimi_suomeksi" => property.copy(values = List(textPropertyValue("n\nimi\nsuomeksi")))
        case "nimi_ruotsiksi" => property.copy(values = List(textPropertyValue("\nnimi ruotsiksi\n")))
        case "liikennointisuunta" => property.copy(values = List(textPropertyValue("\nliikennointisuunta\n")))
        case "esteettomyys_liikuntarajoitteiselle" => property.copy(values = List(textPropertyValue("\nesteettomyys\nliikuntarajoitteiselle\n")))
        case "lisatiedot" => property.copy(values = List(textPropertyValue("\nlisatiedot")))
        case "palauteosoite" => property.copy(values = List(textPropertyValue("palauteosoite\n")))
        case _ => property
      }
    }
    val asset = sourceAsset.copy(propertyData = testProperties)
    val csv = AssetValluCsvFormatter.formatFromAssetWithPropertiesValluCsv(asset)
    csv must equal("5;id ;matkustaja tunnus;n imi suomeksi; nimi ruotsiksi ;374792.096855508;6677566.77442972;;;210;Etelään; liikennointisuunta ;1;1;1;0;; esteettomyys liikuntarajoitteiselle ;;"
      + created
      + ";dr1conversion;" + validFrom + ";" + validTo + ";Liikennevirasto;235;Kauniainen; lisatiedot;palauteosoite ")
  }

  private def parseDate(date: String): DateTime = {
    val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
    dateFormat.parseDateTime(date)
  }

  private def textPropertyValue(value: String): PropertyValue = {
    PropertyValue(propertyValue = value, propertyDisplayValue = Some(value))
  }

  private def extractPropertyValue(asset: AssetWithProperties, propertyPublicId: String): String = {
    asset.propertyData.find(_.publicId == propertyPublicId).get.values.head.propertyDisplayValue.getOrElse("")
  }

  private def parseCreationDateTime(s: String): DateTime = {
    AssetPropertyConfiguration.Format.parseDateTime(s.split(" ").drop(1).mkString(" "))
  }

  private def inOutputDateFormat(date: DateTime): String = {
    AssetValluCsvFormatter.OutputDateTimeFormat.print(date)
  }
}
