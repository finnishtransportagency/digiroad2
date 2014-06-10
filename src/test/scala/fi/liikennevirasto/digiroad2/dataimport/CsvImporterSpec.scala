package fi.liikennevirasto.digiroad2.dataimport

import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, AuthenticatedApiSpec}
import java.io.ByteArrayInputStream

class CsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val userProvider = new OracleUserProvider
  val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider)

  before {
    userProvider.setCurrentUser(User(id = 1, username = "CsvImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("update defined values in CSV import", Tag("db")) {
    val asset = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2")))))
    val csv = s"Valtakunnallinen ID;Pysäkin nimi\n${asset.externalId};Härkikuja 4"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      CsvImporter.importAssets(inputStream, assetProvider)
      val updatedAsset = assetProvider.getAssetByExternalId(asset.externalId)
      val finnishName = updatedAsset.flatMap(
        asset => asset.propertyData.find(property => property.publicId.equals("nimi_suomeksi"))
          .flatMap(property => property.values.headOption.map(value => value.propertyValue)))
      finnishName should equal(Some("Härkikuja 4"))
    } finally {
      // TODO: Remove test asset
    }
  }

  // TODO: Test updating asset that does not exist
  // TODO: Test updating position
  // TODO: Test updating multiple rows
}
