package fi.liikennevirasto.digiroad2.util

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, AssetWithProperties}
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, OracleSpatialAssetProvider, OracleSpatialAssetDao}
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import scala.slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.asset.Modification

class AssetValluCsvFormatterSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  val userProvider = new OracleUserProvider
  var assetsByMunicipality: Iterable[AssetWithProperties] = null

  before {
    Database.forDataSource(ds).withDynSession {
      assetsByMunicipality = OracleSpatialAssetDao.getAssetsByMunicipality(235)
    }
  }

  it must "return correct csv entries from test data" in {
    val csvAll = AssetValluCsvFormatter.formatAssetsWithProperties(235, "Kauniainen", assetsByMunicipality)
    csvAll.size must be > 3
    val csv = csvAll.find(_.startsWith("5")).get

    val created = inOutputDateFormat(testAsset.created.modificationTime.get)
    val (validFrom: String, validTo: String) = assetValidityPeriod(testAsset)

    csv must equal("5;;;;;374780.259160265;6677546.84962279;;;210;Etelä;;1;1;1;0;;;Ei tiedossa;" + created + ";dr1conversion;" + validFrom + ";" + validTo + ";ELY-keskus;235;Kauniainen;;;;2")
  }

  it must "leave modification info empty if creation and modification info are unspecified" in {
    val asset = testAsset.copy(created = Modification(None, None), modified = Modification(None, None))
    val csv = AssetValluCsvFormatter.formatFromAssetWithPropertiesValluCsv(235, "Kauniainen", asset)

    val (validFrom: String, validTo: String) = assetValidityPeriod(testAsset)

    csv must equal("5;;;;;374780.259160265;6677546.84962279;;;210;Etelä;;1;1;1;0;;;Ei tiedossa;;;" + validFrom + ";" + validTo + ";ELY-keskus;235;Kauniainen;;;;2")
  }

  it must "specify creation date" in {
    val creationDate = new DateTime(2013, 11, 14, 14, 35)
    val asset = testAsset.copy(created = Modification(Some(creationDate), Some("testCreator")))
    val csv = AssetValluCsvFormatter.formatFromAssetWithPropertiesValluCsv(235, "Kauniainen", asset)

    val created = inOutputDateFormat(creationDate)
    val (validFrom: String, validTo: String) = assetValidityPeriod(testAsset)

    csv must equal("5;;;;;374780.259160265;6677546.84962279;;;210;Etelä;;1;1;1;0;;;Ei tiedossa;" + created + ";testCreator;" + validFrom + ";" + validTo + ";ELY-keskus;235;Kauniainen;;;;2")
  }

  it must "specify modification date" in {
    val creationInformation = Modification(Some(new DateTime(2013, 11, 14, 14, 35)), Some("testCreator"))
    val modificationInformation = Modification(Some(new DateTime(2014, 1, 3, 11, 12)), Some("testModifier"))
    val asset = testAsset.copy(created = creationInformation, modified = modificationInformation)
    val csv = AssetValluCsvFormatter.formatFromAssetWithPropertiesValluCsv(235, "Kauniainen", asset)

    val modified = inOutputDateFormat(modificationInformation.modificationTime.get)
    val (validFrom: String, validTo: String) = assetValidityPeriod(testAsset)

    csv must equal("5;;;;;374780.259160265;6677546.84962279;;;210;Etelä;;1;1;1;0;;;Ei tiedossa;" + modified + ";testModifier;" + validFrom + ";" + validTo + ";ELY-keskus;235;Kauniainen;;;;2")
  }

  it must "filter tram stops from test data" in {
    val tramStopType = List(1L)
    val localBusStopType = List(2L)
    val tramStop = createStop(tramStopType)
    val localBusStop = createStop(localBusStopType)
    val tramAndLocalBusStop = createStop(tramStopType ++ localBusStopType)
    val csvRows = AssetValluCsvFormatter.valluCsvRowsFromAssets(235, "Kauniainen", List(tramStop, localBusStop, tramAndLocalBusStop), Map())
    csvRows must have size 2
  }

  val testasset = AssetWithProperties(1, 1, 1, 2.1, 2.2, 1, bearing = Some(3), validityDirection = None, wgslon = 2.2, wgslat = 0.56,
      created = Modification(None, None), modified = Modification(None, None), floating = false)
  it must "recalculate bearings in validity direction" in {
    AssetValluCsvFormatter.addBearing(testasset, List())._2 must equal (List("3"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(3)), List())._2 must equal (List("183"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(2)), List())._2 must equal (List("3"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(2), bearing = Some(195)), List())._2 must equal (List("195"))
    AssetValluCsvFormatter.addBearing(testasset.copy(validityDirection = Some(3), bearing = Some(195)), List())._2 must equal (List("15"))
  }

  it must "filter out newlines and replace semicolons with commas in text fields" in {
    val testProperties = testAsset.propertyData.map { property =>
      property.publicId match {
        case "yllapitajan_tunnus" => property.copy(values = List(textPropertyValue("id\n")))
        case "matkustajatunnus" => property.copy(values = List(textPropertyValue("matkustaja\n;tunnus")))
        case "nimi_suomeksi" => property.copy(values = List(textPropertyValue("n\nimi\nsuomeksi")))
        case "nimi_ruotsiksi" => property.copy(values = List(textPropertyValue("\nnimi ruotsiksi\n")))
        case "liikennointisuunta" => property.copy(values = List(textPropertyValue("\nliikennointisuunta\n")))
        case "lisatiedot" => property.copy(values = List(textPropertyValue("\nlisatiedot")))
        case "palauteosoite" => property.copy(values = List(textPropertyValue("palauteosoite\n; teksti")))
        case _ => property
      }
    }
    val asset: AssetWithProperties = testAsset.copy(propertyData = testProperties)
    val created = inOutputDateFormat(asset.created.modificationTime.get)
    val (validFrom: String, validTo: String) = assetValidityPeriod(asset)

    val csv = AssetValluCsvFormatter.formatFromAssetWithPropertiesValluCsv(235, "Kauniainen", asset)
    csv must equal("5;id ;matkustaja ,tunnus;n imi suomeksi; nimi ruotsiksi ;374780.259160265;6677546.84962279;;;210;Etelä; liikennointisuunta ;1;1;1;0;;;Ei tiedossa;"
      + created
      + ";dr1conversion;" + validFrom + ";" + validTo + ";ELY-keskus;235;Kauniainen; lisatiedot;palauteosoite , teksti;;2")
  }

  private def createStop(stopType: Seq[Long]): AssetWithProperties = {
    def typeToPropertyValue(typeCode: Long): PropertyValue = { PropertyValue(typeCode.toString, Some(typeCode.toString)) }

    val properties = testAsset.propertyData.map { property =>
      property.publicId match {
        case "pysakin_tyyppi" => property.copy(values = stopType.map(typeToPropertyValue))
        case _ => property
      }
    }
    testAsset.copy(propertyData = properties)
  }

  private def assetValidityPeriod(asset: AssetWithProperties): (String, String) = {
    val validFrom = inOutputDateFormat(parseDate(extractPropertyValue(asset, "ensimmainen_voimassaolopaiva")))
    val validTo = inOutputDateFormat(parseDate(extractPropertyValue(asset, "viimeinen_voimassaolopaiva")))
    (validFrom, validTo)
  }

  private def testAsset(): AssetWithProperties = {
    assetsByMunicipality.find(_.externalId == 5).get
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

  private def inOutputDateFormat(date: DateTime): String = {
    AssetValluCsvFormatter.OutputDateTimeFormat.print(date)
  }
}
