package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest._
import scala.util.Random
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import scala.language.implicitConversions
import fi.liikennevirasto.digiroad2.user.Role
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import java.sql.SQLIntegrityConstraintViolationException
import fi.liikennevirasto.digiroad2.{Point, DummyEventBus, DigiroadEventBus}
import org.mockito.Mockito.verify
import fi.liikennevirasto.digiroad2.asset._
import scala.Some
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.user.Configuration
import scala.slick.jdbc.{StaticQuery => Q}

class OracleSpatialAssetProviderSpec extends FunSuite with Matchers with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val MunicipalityEspoo = 49
  val TestAssetTypeId = 10
  val AssetCreator = "integration_test_add_asset"
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider)
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

  before {
    userProvider.setCurrentUser(user)
  }

  test("load assets by municipality number", Tag("db")) {
    val assets = provider.getAssets(userProvider.getCurrentUser())
    assets shouldBe 'nonEmpty
    assets.foreach(asset => asset.municipalityNumber shouldBe Some(MunicipalityKauniainen))
  }

  test("load assets with spatial bounds", Tag("db")) {
    val assets = provider.getAssets(userProvider.getCurrentUser(), Some(BoundingRectangle(Point(374443, 6677245), Point(374444, 6677246))),
        validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    assets.size shouldBe 1
  }

  test("load enumerated values for asset type", Tag("db")) {
    val values = provider.getEnumeratedPropertyValues(TestAssetTypeId)
    values shouldBe 'nonEmpty
    values.map(_.propertyName) should contain ("Vaikutussuunta")
  }

  test("adding asset to database without mandatory properties fails", Tag("db")) {
    val exception = intercept[IllegalArgumentException] {
      provider.createAsset(
        TestAssetTypeId,
        0, 0, 5771, 180,
        AssetCreator,
        Nil)
    }
    exception.getMessage should equal("Missing required properties: vaikutussuunta, pysakin_tyyppi")
 }

  test("add asset to database", Tag("db")) {
    val eventBus = mock.MockitoSugar.mock[DigiroadEventBus]
    val providerWithMockedEventBus = new OracleSpatialAssetProvider(eventBus, userProvider)
    userProvider.setCurrentUser(creatingUser)
    val existingAsset = providerWithMockedEventBus.getAssetById(TestAssetId).get
    val newAsset = providerWithMockedEventBus.createAsset(
      TestAssetTypeId,
      existingAsset.lon,
      existingAsset.lat,
      6928,
      180,
      AssetCreator,
      mandatoryBusStopProperties)
    try {
      newAsset.id should (be > 300000L)
      Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
      Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
      verify(eventBus).publish("asset:saved", ("Kauniainen", newAsset))
      newAsset.externalId should (be >= 300000L)
    } finally {
      deleteCreatedTestAsset(newAsset.id)
    }
  }

  test("add asset to database sets default values for properties", Tag("db")) {
    val eventBus = mock.MockitoSugar.mock[DigiroadEventBus]
    val providerWithMockedEventBus = new OracleSpatialAssetProvider(eventBus, userProvider)
    userProvider.setCurrentUser(creatingUser)
    val existingAsset = providerWithMockedEventBus.getAssetById(TestAssetId).get
    val newAsset = providerWithMockedEventBus.createAsset(
      TestAssetTypeId,
      existingAsset.lon,
      existingAsset.lat,
      6928,
      180,
      AssetCreator,
      mandatoryBusStopProperties)
    try {
      newAsset.propertyData.find( prop => prop.publicId == "katos" ).get.values.head.propertyValue shouldBe "99"
    } finally {
      deleteCreatedTestAsset(newAsset.id)
    }
  }

  test("add asset with properties to database", Tag("db")) {
    val AssetCreator = "integration_test_add_asset"
    val existingAsset = provider.getAssetById(TestAssetId).get
    val newAsset = provider.createAsset(
      TestAssetTypeId,
      existingAsset.lon,
      existingAsset.lat,
      6928,
      180,
      AssetCreator,
      mandatoryBusStopProperties ++ List(SimpleProperty("viimeinen_voimassaolopaiva", List(PropertyValue("2045-12-10")))))
    try {
      newAsset.id should (be > 100L)
      Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
      Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
      newAsset.propertyData should contain (Property(0, "viimeinen_voimassaolopaiva", "date", 80, required = false, List(PropertyValue("2045-12-10", Some("2045-12-10")))))
    } finally {
      deleteCreatedTestAsset(newAsset.id)
    }
  }

  test("add asset is transactional", Tag("db")) {
    import scala.slick.driver.JdbcDriver.backend.Database
    import scala.slick.jdbc.{StaticQuery => Q}
    import Database.dynamicSession
    import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
    val assetCreator = "spatial_asset_provider_spec_add_asset_is_transactional"
    val existingAsset = provider.getAssetById(TestAssetId).get
    try {
      val newAsset = provider.createAsset(
        TestAssetTypeId,
        existingAsset.lon,
        existingAsset.lat,
        6928,
        180,
        assetCreator,
        mandatoryBusStopProperties ++
          List(
            SimpleProperty(AssetPropertyConfiguration.ValidFromId, List(PropertyValue("2001-12-10"))),
            SimpleProperty(AssetPropertyConfiguration.ValidToId, List(PropertyValue("1995-12-10")))))
      fail("Should have thrown an exception")
    } catch {
      case e: SQLIntegrityConstraintViolationException =>
        Database.forDataSource(ds).withDynSession {
          val assetCount = Q.queryNA[Long]("""SELECT COUNT(*) FROM asset where created_by = '""" + assetCreator + "'").list.head
          assetCount should be (0)
        }
    }
  }

  test("add asset to database without write access fails", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val existingAsset = provider.getAssetById(TestAssetId).get
    try {
      intercept[IllegalArgumentException] {
        val asset = provider.createAsset(TestAssetTypeId, existingAsset.lon, existingAsset.lat, 6928, 180, AssetCreator, Nil)
      }
    }
  }

  test("remove asset from database", Tag("db")) {
    val asset = provider.createAsset(
      TestAssetTypeId,
      0, 0, 7445, 180,
      AssetCreator,
      mandatoryBusStopProperties)
    val assetId = asset.id
    val lrmPositionId = executeIntQuery(s"select position_id from asset_link where asset_id = $assetId")
    amountOfSingleChoiceValues(assetId) should be > 0
    amountOfLrmPositions(lrmPositionId) should equal(1)
    amountOfAssets(assetId) should be(1)

    deleteCreatedTestAsset(assetId)

    amountOfSingleChoiceValues(assetId) should be(0)
    amountOfLrmPositions(lrmPositionId) should equal(0)
    amountOfAssets(assetId) should be(0)
  }

  private def mandatoryBusStopProperties: Seq[SimpleProperty] = {
    Seq(
      SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2"))),
      SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue("nimi"))),
      SimpleProperty(publicId = "pysakin_tyyppi", values = Seq(PropertyValue("2"))))
  }

  private def amountOfSingleChoiceValues(assetId: Long): Int = {
    executeIntQuery(s"select count(*) from single_choice_value where asset_id = $assetId")
  }

  private def amountOfLrmPositions(lrmPositionId: Long): Int = {
    executeIntQuery(s"select count(*) from lrm_position where id = $lrmPositionId")
  }

  private def amountOfAssets(assetId: Long): Int = {
    executeIntQuery(s"select count(*) from asset where id = $assetId")
  }

  private def deleteCreatedTestAsset(assetId: Long) {
    try {
      provider.removeAsset(assetId)
    } catch {
      // TODO: Remove handling of this exception once LRM position removal does not fail in test runs
      case e: LRMPositionDeletionFailed => println("Removing LRM Position of asset " + assetId + " failed: " + e.reason)
    }
  }

  test("update the position of an asset within a road link", Tag("db")) {
    val eventBus = mock.MockitoSugar.mock[DigiroadEventBus]
    val providerWithMockedEventBus = new OracleSpatialAssetProvider(eventBus, userProvider)
    val asset = providerWithMockedEventBus.getAssetById(TestAssetId).get
    val lon1 = asset.lon
    val lat1 = asset.lat
    val lon2 = lon1 + 137.0
    val lat2 = lat1 + 7.0
    val b2 = Random.nextInt(360)
    providerWithMockedEventBus.updateAsset(assetId = asset.id, position = Some(Position(roadLinkId = 6928, lon = lon2, lat = lat2, bearing = Some(b2))))
    val updated = providerWithMockedEventBus.getAssetById(asset.id).get
    providerWithMockedEventBus.updateAsset(assetId = asset.id, position = Some(Position(roadLinkId = 6928, lon = asset.lon, lat = asset.lat, bearing = asset.bearing)))
    verify(eventBus).publish("asset:saved", ("Kauniainen", updated))
    Math.abs(updated.lat - lat1) should (be > 3.0)
    Math.abs(updated.lon - lon1) should (be > 20.0)
    updated.bearing.get should be (b2)
  }

  test("update the position of an asset, changing road links, fails without write access", Tag("db")) {
    userProvider.setCurrentUser(unauthorizedUser)
    val assets = provider.getAssets(user,
      validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
    val origAsset = assets(0)
    val refAsset = assets(1)
    origAsset.roadLinkId shouldNot be (refAsset.roadLinkId)
    val bsMoved = origAsset.copy(roadLinkId = refAsset.roadLinkId, lon = refAsset.lon, lat = refAsset.lat)
    intercept[IllegalArgumentException] {
      provider.updateAsset(assetId = bsMoved.id, position = Some(Position(roadLinkId = bsMoved.roadLinkId, lon = bsMoved.lon, lat = bsMoved.lat, bearing = bsMoved.bearing)))
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

  test("load image by id", Tag("db")) {
    val provider = new OracleSpatialAssetProvider(new DummyEventBus, new OracleUserProvider)
    val image = provider.getImage(2)
    image.size should (be > 1)
  }

  test("loads all asset with properties in municipality find", Tag("db")) {
    val provider = new OracleSpatialAssetProvider(new DummyEventBus, new OracleUserProvider)
    val assets = provider.getAssetsByMunicipality(235)
    assets.size should (be > 0)
    assets.map(_.municipalityNumber.get) should contain only (235)
  }

  test("returns correct assets from test data", Tag("db")) {
    val assetsByMunicipality = provider.getAssetsByMunicipality(235)
    assetsByMunicipality.map(_.id) should contain allOf(300000, 300004, 300008, 300003, 300001)
    assetsByMunicipality.map(_.id) should contain noneOf(300005, 307577) // stop with invalid road link and a stop at Rovaniemi
  }

  private[this] def asSimplePropertySeq(propertyId: String, value: String): Seq[SimpleProperty] = {
    Seq(new SimpleProperty(propertyId, Seq(new PropertyValue(value))))
  }

  private[this] def asSimplePropertySeq(propertyId: String): Seq[SimpleProperty] = {
    Seq(new SimpleProperty(propertyId, Seq()))
  }
}
