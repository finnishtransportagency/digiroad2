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
import fi.liikennevirasto.digiroad2.user.{Role, Configuration, User}
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
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))
  val espooUser = User(
    id = 2,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityEspoo)))
  val espooKauniainenUser = User(
    id = 3,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityEspoo, MunicipalityKauniainen)))
  val unauthorizedUser =
    user.copy(configuration = Configuration(authorizedMunicipalities = Set(666999)))
  val creatingUser = user.copy(username = AssetCreator)
  val operatorUser = user.copy(configuration = user.configuration.copy(authorizedMunicipalities = Set(1), roles = Set(Role.Operator)))

  implicit def Asset2ListedAsset(asset: AssetWithProperties) = new Asset(asset.id, asset.externalId, asset.assetTypeId, asset.lon, asset.lat, asset.roadLinkId,
    asset.propertyData.flatMap(prop => prop.values.map(value => value.imageId)), asset.bearing, None, asset.status, asset.readOnly, asset.municipalityNumber)

  before {
    userProvider.setCurrentUser(user)
  }

  test("load assets by municipality number", Tag("db")) {
    val assets = provider.getAssets(TestAssetTypeId, userProvider.getCurrentUser())
    assets shouldBe 'nonEmpty
    assets.foreach(asset => asset.municipalityNumber shouldBe(Some(MunicipalityKauniainen)))
  }

  test("load assets with spatial bounds", Tag("db")) {
    val assets = provider.getAssets(TestAssetTypeId, userProvider.getCurrentUser(), Some(BoundingRectangle(Point(374700, 6677595), Point(374750, 6677560))),
        validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    assets.size shouldBe(1)
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
      newAsset.externalId.get should (be >= 300000L)
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
          List(SimpleProperty("viimeinen_voimassaolopaiva", List(PropertyValue("2045-12-10")))))
      newAsset.id should (be > 100L)
      Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
      Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
      newAsset.roadLinkId shouldBe(existingAsset.roadLinkId)
      newAsset.propertyData should contain (Property(0, "viimeinen_voimassaolopaiva", "date", 80, false, List(PropertyValue("2045-12-10 00:00:00.0", Some("2045-12-10 00:00:00.0")))))
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
          List(SimpleProperty(AssetPropertyConfiguration.ValidFromId, List(PropertyValue("2001-12-10"))),
               SimpleProperty(AssetPropertyConfiguration.ValidToId, List(PropertyValue("1995-12-10")))))
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
    provider.updateAsset(assetId = asset.id, position = Some(new Position(roadLinkId = asset.roadLinkId, lon = lon2, lat = lat2, bearing = Some(b2))))
    val updated = provider.getAssetById(asset.id).get
    provider.updateAsset(assetId = asset.id, position = Some(new Position(roadLinkId = asset.roadLinkId, lon = asset.lon, lat = asset.lat, bearing = asset.bearing)))
    Math.abs(updated.lat - lat1) should (be > 0.5)
    Math.abs(updated.lon - lon1) should (be > 0.5)
    updated.bearing.get should be (b2)
  }

  test("update the position of an asset, changing road links", Tag("db")) {
    val assets = provider.getAssets(TestAssetTypeId, userProvider.getCurrentUser(),
      validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    val origAsset = assets(0)
    val refAsset = assets(1)
    origAsset.roadLinkId shouldNot be (refAsset.roadLinkId)
    val bsMoved = origAsset.copy(roadLinkId = refAsset.roadLinkId, lon = refAsset.lon, lat = refAsset.lat)
    provider.updateAsset(assetId = bsMoved.id, position = Some(new Position(roadLinkId = bsMoved.roadLinkId, lon = bsMoved.lon, lat = bsMoved.lat, bearing = bsMoved.bearing)))
    val bsUpdated = provider.getAssetById(bsMoved.id).get
    provider.updateAsset(assetId = origAsset.id, position = Some(new Position(roadLinkId = origAsset.roadLinkId, lon = origAsset.lon, lat = origAsset.lat, bearing = origAsset.bearing)))
    Math.abs(bsUpdated.lat - refAsset.lat) should (be < 0.05)
    Math.abs(bsUpdated.lon - refAsset.lon) should (be < 0.05)
    bsUpdated.roadLinkId should be (refAsset.roadLinkId)
  }

  test("update the position of an asset, changing road links, fails without write access", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val assets = provider.getAssets(TestAssetTypeId, user,
      validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    val origAsset = assets(0)
    val refAsset = assets(1)
    origAsset.roadLinkId shouldNot be (refAsset.roadLinkId)
    val bsMoved = origAsset.copy(roadLinkId = refAsset.roadLinkId, lon = refAsset.lon, lat = refAsset.lat)
    intercept[IllegalArgumentException] {
      provider.updateAsset(assetId = bsMoved.id, position = Some(new Position(roadLinkId = bsMoved.roadLinkId, lon = bsMoved.lon, lat = bsMoved.lat, bearing = bsMoved.bearing)))
    }
  }

  test("update a common asset property value (single choice)", Tag("db")) {
    userProvider.setCurrentUser(operatorUser)
    val asset = provider.getAssetById(TestAssetId).get
    asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be (AssetPropertyConfiguration.ValidityDirectionSame)
    provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "3"))
    val updatedAsset = provider.getAssetById(TestAssetId).get
    updatedAsset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be ("3")
    provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "2"))
  }

  test("update validity date throws exception if validFrom after validTo", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidToId, "2045-12-10"))
    an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2065-12-15"))
  }


  test("update validity date throws exception if validTo before validFrom", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2010-12-15"))
    an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidToId, "2009-12-10"))
  }

  test("update a common asset without write access fails", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val asset = provider.getAssetById(TestAssetId).get
    asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be (AssetPropertyConfiguration.ValidityDirectionSame)
    intercept[IllegalArgumentException] {
      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "1"))
    }
  }

  test("update a common asset property value (text i.e. timestamp)", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidFromId).get.values shouldBe 'nonEmpty
    provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2013-12-31"))

    val updatedAsset = provider.getAssetById(TestAssetId).get
    updatedAsset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidFromId).get.values.head.propertyDisplayValue.get should startWith ("2013-12-31")

    provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId))
    val assetWithoutExpirationTime = provider.getAssetById(TestAssetId).get
    assetWithoutExpirationTime.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidFromId).get.values should equal (List(PropertyValue("")))

    provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2013-12-15"))
  }

  test("validate date property values", Tag("db")) {
    val asset = provider.getAssetById(TestAssetId).get
    an[IllegalArgumentException] should be thrownBy provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "INVALID DATE"))
  }

  test("asset on non-expired link is not marked as floating", Tag("db")) {
    val assets = provider.getAssets(assetTypeId = 10,
      user = espooUser,
      validFrom = Some(new LocalDate(2013, 6, 1)),
      validTo = Some(new LocalDate(2013, 6, 1)))
    assets should have length(1)
    assets.head.status should be(None)
  }

  test("asset on expired link is marked as floating", Tag("db")) {
    val assets = provider.getAssets(assetTypeId = 10,
      user = espooUser,
      validFrom = Some(new LocalDate(2014, 6, 1)),
      validTo = Some(new LocalDate(2014, 6, 1)))
    assets should have length(1)
    assets.head.status should be(Some(Floating))
  }

  test("provide road link geometry by municipality", Tag("db")) {
    provider.getRoadLinks(unauthorizedUser).size should be (0)
    val rls = provider.getRoadLinks(user, Some(BoundingRectangle(Point(372794, 6679569), Point(376794, 6675569))))
    rls.size should be (23)
    rls.foreach { rl =>
      rl.id should (be > 1l)
      rl.lonLat.foreach { pt =>
        pt._1 should (be > 60000.0 and be < 734000.0)
        pt._2 should (be > 6600000.0 and be < 77800000.0)
      }
    }
    provider.getRoadLinks(espooKauniainenUser, Some(BoundingRectangle(Point(373794, 6678569), Point(375794, 6676569)))).size should be (24)
  }

  test("Load image by id", Tag("db")) {
    val provider = new OracleSpatialAssetProvider(new OracleUserProvider)
    val image = provider.getImage(2)
    image.size should (be > 1)
  }

  test("Loads all asset with properties in municipality find", Tag("db")) {
    val provider = new OracleSpatialAssetProvider(new OracleUserProvider)
    val municipalities = provider.getAssetsByMunicipality(235)
    municipalities.size should be(5)
  }

  test("returns correct amount of assets from test data", Tag("db")) {
    val assetsByMunicipality = provider.getAssetsByMunicipality(235)
    assetsByMunicipality.size should be (5)
    assetsByMunicipality.head.id should be (300000)
  }

  private[this] def asSimplePropertySeq(propertyId: String, value: String): Seq[SimpleProperty] = {
    Seq(new SimpleProperty(propertyId, Seq(new PropertyValue(value))))
  }

  private[this] def asSimplePropertySeq(propertyId: String): Seq[SimpleProperty] = {
    Seq(new SimpleProperty(propertyId, Seq()))
  }
}
