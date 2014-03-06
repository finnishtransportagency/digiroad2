package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest._
import scala.util.Random
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.asset.AssetStatus._
import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import scala.Some
import scala.language.implicitConversions
import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.util.DataFixture.{TestAssetId, TestAssetTypeId, MunicipalityEspoo, MunicipalityKauniainen}
import java.sql.SQLIntegrityConstraintViolationException

class OracleSpatialAssetProviderSpec extends FunSuite with Matchers with BeforeAndAfter {

  val AssetCreator = "integration_test_add_asset"
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)
  val user = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val unauthorizedUser =
    user.copy(configuration = Configuration(authorizedMunicipalities = Set(666999)))
  val creatingUser = user.copy(username = AssetCreator)

  implicit def Asset2ListedAsset(asset: AssetWithProperties) = new Asset(asset.id, asset.assetTypeId, asset.lon, asset.lat, asset.roadLinkId,
    asset.propertyData.flatMap(prop => prop.values.map(value => value.imageId)), asset.bearing, None, asset.status, asset.readOnly, asset.municipalityNumber)

  before {
    userProvider.setCurrentUser(user)
  }

  test("load assets by municipality number", Tag("db")) {
    val assets = provider.getAssets(TestAssetTypeId, List(MunicipalityKauniainen))
    assets shouldBe 'nonEmpty
    assets.foreach(asset => asset.municipalityNumber shouldBe(Some(MunicipalityKauniainen)))
  }

  test("load assets with spatial bounds", Tag("db")) {
    val assets = provider.getAssets(TestAssetTypeId, List(MunicipalityKauniainen), Some(BoundingRectangle(374700, 6677595, 374750, 6677560)),
        validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    assets.size shouldBe(1)
  }

  test("load assetTypes from Oracle", Tag("db")) {
    val assetTypes = provider.getAssetTypes
    assetTypes shouldBe 'nonEmpty
    assetTypes.size shouldBe(1)
  }

  test("load enumerated values for asset type", Tag("db")) {
    val values = provider.getEnumeratedPropertyValues(TestAssetTypeId)
    values shouldBe 'nonEmpty
    values.exists(_.propertyName == "Vaikutussuunta") should be (true)
  }

  test("add asset to database", Tag("db")) {
    userProvider.setCurrentUser(creatingUser)
    val existingAsset = provider.getAssetById(TestAssetId).get
    try {
      val newAsset = provider.createAsset(TestAssetTypeId, existingAsset.lon, existingAsset.lat, existingAsset.roadLinkId, 180, AssetCreator, Nil)
      newAsset.id should (be > 300000L)
      Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
      Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
      newAsset.roadLinkId shouldBe(existingAsset.roadLinkId)
    } finally {
      executeStatement("DELETE FROM asset WHERE created_by = '" + AssetCreator + "'");
    }
  }

  test("add asset with properties to database", Tag("db")) {
    val AssetCreator = "integration_test_add_asset"
    val existingAsset = provider.getAssetById(TestAssetId).get
    try {
      val newAsset = provider.createAsset(
          TestAssetTypeId,
          existingAsset.lon,
          existingAsset.lat,
          existingAsset.roadLinkId,
          180,
          AssetCreator,
          List(SimpleProperty("validTo", List(PropertyValue(0, "2045-12-10")))))
      newAsset.id should (be > 100L)
      Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
      Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
      newAsset.roadLinkId shouldBe(existingAsset.roadLinkId)
      newAsset.propertyData should contain (Property("validTo", "Käytössä päättyen", "date", false, List(PropertyValue(0, "2045-12-10 00:00:00.0", null))))
    } finally {
      executeStatement("DELETE FROM asset WHERE created_by = '" + AssetCreator + "'");
    }
  }

  test("add asset is transactional", Tag("db")) {
    import scala.slick.driver.JdbcDriver.backend.Database
    import scala.slick.jdbc.{GetResult, StaticQuery => Q}
    import Database.dynamicSession
    import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
    val AssetCreator = "integration_test_add_asset"
    val existingAsset = provider.getAssetById(TestAssetId).get
    val oldCount = Database.forDataSource(ds).withDynSession {
      Q.queryNA[Long]("""SELECT COUNT(*) FROM asset""").list.head
    }
    try {
      val newAsset = provider.createAsset(
          TestAssetTypeId,
          existingAsset.lon,
          existingAsset.lat,
          existingAsset.roadLinkId,
          180,
          AssetCreator,
          List(SimpleProperty("validFrom", List(PropertyValue(0, "2001-12-10"))),
               SimpleProperty("validTo", List(PropertyValue(0, "1995-12-10")))))
      fail("Should have thrown an exception")
    } catch {
      case e: SQLIntegrityConstraintViolationException => {
        Database.forDataSource(ds).withDynSession {
          oldCount should be (Q.queryNA[Long]("""SELECT COUNT(*) FROM asset""").list.head)
        }
      }
    } finally {
      executeStatement("DELETE FROM asset WHERE created_by = '" + AssetCreator + "'");
    }
  }

  test("add asset to database without write access fails", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val existingAsset = provider.getAssetById(TestAssetId).get
    try {
      intercept[IllegalArgumentException] {
        provider.createAsset(TestAssetTypeId, existingAsset.lon, existingAsset.lat, existingAsset.roadLinkId, 180, AssetCreator, Nil)
      }
    } finally {
      executeStatement("DELETE FROM asset WHERE created_by = '" + AssetCreator + "'");
    }
  }

  test("update the position of an asset within a road link", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    val lon1 = asset.lon
    val lat1 = asset.lat
    val lon2 = lon1 + 4.0
    val lat2 = lat1 + 4.0
    val b2 = Random.nextInt(360)
    val asset2 = asset.copy(lon = lon2, lat = lat2, bearing = Some(b2))
    provider.updateAssetLocation(asset2)
    val updated = provider.getAssetById(asset.id).get
    provider.updateAssetLocation(asset)
    Math.abs(updated.lat - lat1) should (be > 0.5)
    Math.abs(updated.lon - lon1) should (be > 0.5)
    updated.bearing.get should be (b2)
  }

  test("update the position of an asset, changing road links", Tag("db")) {
    val assets = provider.getAssets(TestAssetTypeId, List(MunicipalityKauniainen),
      validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    val origAsset = assets(0)
    val refAsset = assets(1)
    origAsset.roadLinkId shouldNot be (refAsset.roadLinkId)
    val bsMoved = origAsset.copy(roadLinkId = refAsset.roadLinkId, lon = refAsset.lon, lat = refAsset.lat)
    provider.updateAssetLocation(bsMoved)
    val bsUpdated = provider.getAssetById(bsMoved.id).get
    provider.updateAssetLocation(origAsset)
    Math.abs(bsUpdated.lat - refAsset.lat) should (be < 0.05)
    Math.abs(bsUpdated.lon - refAsset.lon) should (be < 0.05)
    bsUpdated.roadLinkId should be (refAsset.roadLinkId)
  }

  test("update the position of an asset, changing road links, fails without write access", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val assets = provider.getAssets(TestAssetTypeId, List(MunicipalityKauniainen),
      validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    val origAsset = assets(0)
    val refAsset = assets(1)
    origAsset.roadLinkId shouldNot be (refAsset.roadLinkId)
    val bsMoved = origAsset.copy(roadLinkId = refAsset.roadLinkId, lon = refAsset.lon, lat = refAsset.lat)
    intercept[IllegalArgumentException] {
      provider.updateAssetLocation(bsMoved)
    }
  }

  test("update a common asset property value (single choice)", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    asset.propertyData.find(_.propertyId == "validityDirection").get.values.head.propertyValue should be (AssetPropertyConfiguration.ValidityDirectionSame)
    provider.updateAssetProperty(TestAssetId, "validityDirection", List(PropertyValue(3, "")))
    val updatedAsset = provider.getAssetById(TestAssetId).get
    updatedAsset.propertyData.find(_.propertyId == "validityDirection").get.values.head.propertyValue should be (3)
    provider.updateAssetProperty(TestAssetId, "validityDirection", List(PropertyValue(2, "")))
  }

  test("update validity date throws exception if validFrom after validTo", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    provider.updateAssetProperty(asset.id, "validTo", List(PropertyValue(0, "2045-12-10")))
    an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAssetProperty(asset.id, "validFrom", List(PropertyValue(0, "2065-12-15")))
  }


  test("update validity date throws exception if validTo before validFrom", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    provider.updateAssetProperty(asset.id, "validFrom", List(PropertyValue(0, "2010-12-15")))
    an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAssetProperty(asset.id, "validTo", List(PropertyValue(0, "2009-12-10")))
  }

  test("update a common asset without write access fails", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val asset = provider.getAssetById(TestAssetId).get
    asset.propertyData.find(_.propertyId == "validityDirection").get.values.head.propertyValue should be (AssetPropertyConfiguration.ValidityDirectionSame)
    intercept[IllegalArgumentException] {
      provider.updateAssetProperty(TestAssetId, "validityDirection", List(PropertyValue(1, "")))
    }
  }

  test("update a common asset property value (text i.e. timestamp)", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    asset.propertyData.find(_.propertyId == "validFrom").get.values shouldBe 'nonEmpty
    provider.updateAssetProperty(TestAssetId, "validFrom", List(PropertyValue(0, "2013-12-31")))

    val updatedAsset = provider.getAssetById(TestAssetId).get
    updatedAsset.propertyData.find(_.propertyId == "validFrom").get.values.head.propertyDisplayValue should startWith ("2013-12-31")

    provider.updateAssetProperty(TestAssetId, "validFrom", List())
    val assetWithoutExpirationTime = provider.getAssetById(TestAssetId).get
    assetWithoutExpirationTime.propertyData.find(_.propertyId == "validFrom").get.values should equal (List(PropertyValue(0, null, null)))

    provider.updateAssetProperty(TestAssetId, "validFrom", List(PropertyValue(0, "2013-12-15")))
  }

  test("validate date property values", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    an[IllegalArgumentException] should be thrownBy provider.updateAssetProperty(TestAssetId, "validFrom", List(PropertyValue(0, "INVALID DATE")))
  }

  test("asset on non-expired link is not marked as floating", Tag("db")) {
    val assets = provider.getAssets(assetTypeId = 10,
      municipalityNumbers = List(MunicipalityEspoo),
      validFrom = Some(new LocalDate(2013, 6, 1)),
      validTo = Some(new LocalDate(2013, 6, 1)))
    assets should have length(1)
    assets.head.status should be(None)
  }

  test("asset on expired link is marked as floating", Tag("db")) {
    val assets = provider.getAssets(assetTypeId = 10,
      municipalityNumbers = List(MunicipalityEspoo),
      validFrom = Some(new LocalDate(2014, 6, 1)),
      validTo = Some(new LocalDate(2014, 6, 1)))
    assets should have length(1)
    assets.head.status should be(Some(Floating))
  }

  test("provide road link geometry by municipality", Tag("db")) {
    provider.getRoadLinks(Seq(0)).size should be (0)
    val rls = provider.getRoadLinks(Seq(MunicipalityKauniainen), Some(BoundingRectangle(372794, 6679569, 376794, 6675569)))
    rls.size should be (23)
    rls.foreach { rl =>
      rl.id should (be > 1l)
      rl.lonLat.foreach { pt =>
        pt._1 should (be > 60000.0 and be < 734000.0)
        pt._2 should (be > 6600000.0 and be < 77800000.0)
      }
    }
    provider.getRoadLinks(Seq(MunicipalityKauniainen, MunicipalityEspoo), Some(BoundingRectangle(373794, 6678569, 375794, 6676569))).size should be (24)
  }

  test("Load image by id", Tag("db")) {
    val provider = new OracleSpatialAssetProvider(new OracleUserProvider)
    val image = provider.getImage(2)
    image.size should (be > 1)
  }

}
