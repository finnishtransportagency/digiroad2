package fi.liikennevirasto.digiroad2.asset.oracle

import java.sql.SQLIntegrityConstraintViolationException

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, Role, User}
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, EventBusMassTransitStop, Point}
import org.joda.time.LocalDate
import org.mockito.Mockito.verify
import org.scalatest._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

import scala.language.implicitConversions
import scala.slick.jdbc.{StaticQuery => Q}
import scala.util.Random
import scala.slick.jdbc.StaticQuery.interpolation


class OracleSpatialAssetProviderSpec extends FunSuite with Matchers with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val MunicipalityEspoo = 49
  val TestAssetTypeId = 10
  val AssetCreator = "integration_test_add_asset"
  val userProvider = new OracleUserProvider

  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val provider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider, passThroughTransaction)
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

  private def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  before {
    userProvider.setCurrentUser(user)
  }

  test("load assets with spatial bounds", Tag("db")) {
    runWithCleanup {
      val assets = provider.getAssets(userProvider.getCurrentUser(), BoundingRectangle(Point(374443, 6677245), Point(374444, 6677246)),
        validFrom = Some(LocalDate.now), validTo = Some(LocalDate.now))
      assets.size shouldBe 1
    }
  }

  test("load enumerated values for asset type", Tag("db")) {
    runWithCleanup {
      val values = provider.getEnumeratedPropertyValues(TestAssetTypeId)
      values shouldBe 'nonEmpty
      values.map(_.propertyName) should contain("Vaikutussuunta")
    }
  }

  test("adding asset to database without mandatory properties fails", Tag("db")) {
    runWithCleanup {
      val exception = intercept[IllegalArgumentException] {
        provider.createAsset(
          TestAssetTypeId,
          0, 0, 5771, 180,
          "Tosi sika",
          Nil)
      }
      exception.getMessage should equal("Missing required properties: vaikutussuunta, pysakin_tyyppi")
    }
 }

  test("add asset to database", Tag("db")) {
    runWithCleanup {
      val eventBus = mock.MockitoSugar.mock[DigiroadEventBus]
      val providerWithMockedEventBus = new OracleSpatialAssetProvider(eventBus, userProvider, passThroughTransaction)
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
      val eventBusStop = EventBusMassTransitStop(municipalityNumber = 235, municipalityName = "Kauniainen",
        nationalId = newAsset.nationalId, lon = existingAsset.lon, lat = existingAsset.lat, bearing = Some(180),
        validityDirection = Some(2), created = newAsset.created, modified = newAsset.modified,
        propertyData = newAsset.propertyData)
        newAsset.id should (be > 300000L)
        Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
        Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
        verify(eventBus).publish("asset:saved", eventBusStop)
        newAsset.nationalId should (be >= 300000L)
    }
  }

  test("add asset to database sets default values for properties", Tag("db")) {
    runWithCleanup {
      val eventBus = mock.MockitoSugar.mock[DigiroadEventBus]
      val providerWithMockedEventBus = new OracleSpatialAssetProvider(eventBus, userProvider, passThroughTransaction)
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
        newAsset.propertyData.find(prop => prop.publicId == "katos").get.values.head.propertyValue shouldBe "99"
    }
  }

  test("add asset with properties to database", Tag("db")) {
    runWithCleanup {
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
        newAsset.id should (be > 100L)
        Math.abs(newAsset.lon - existingAsset.lon) should (be < 0.1)
        Math.abs(newAsset.lat - existingAsset.lat) should (be < 0.1)
        newAsset.propertyData should contain (Property(0, "viimeinen_voimassaolopaiva", "date", 80, required = false, List(PropertyValue("2045-12-10", Some("2045-12-10")))))
    }
  }

  test("add asset is transactional", Tag("db")) {
    runWithCleanup {
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
            val assetCount = Q.queryNA[Long]( """SELECT COUNT(*) FROM asset where created_by = '""" + assetCreator + "'").list.head
            assetCount should be(0)
          }
      }
    }
  }

  test("add asset to database without write access fails", Tag("db")) {
    runWithCleanup {
      userProvider.setCurrentUser(unauthorizedUser)
      val existingAsset = provider.getAssetById(TestAssetId).get
      try {
        intercept[IllegalArgumentException] {
          val asset = provider.createAsset(TestAssetTypeId, existingAsset.lon, existingAsset.lat, 6928, 180, AssetCreator, Nil)
        }
      }
    }
  }

  private def mandatoryBusStopProperties: Seq[SimpleProperty] = {
    Seq(
      SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2"))),
      SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue("nimi"))),
      SimpleProperty(publicId = "pysakin_tyyppi", values = Seq(PropertyValue("2"))))
  }

  private def amountOfSingleChoiceValues(assetId: Long): Int = {
    sql"""select count(*) from single_choice_value where asset_id = $assetId""".as[Int].first
  }

  private def amountOfLrmPositions(lrmPositionId: Long): Int = {
    sql"""select count(*) from lrm_position where id = $lrmPositionId""".as[Int].first
  }

  private def amountOfAssets(assetId: Long): Int = {
    sql"""select count(*) from asset where id = $assetId""".as[Int].first
  }

  test("update the position of an asset within a road link", Tag("db")) {
    runWithCleanup {
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
      val eventBusStop = EventBusMassTransitStop(municipalityNumber = 235, municipalityName = "Kauniainen",
        nationalId = updated.nationalId, lon = updated.lon, lat = updated.lat, bearing = updated.bearing,
        validityDirection = Some(2), created = updated.created, modified = updated.modified,
        propertyData = updated.propertyData)
      verify(eventBus).publish("asset:saved", eventBusStop)
      Math.abs(updated.lat - lat1) should (be > 3.0)
      Math.abs(updated.lon - lon1) should (be > 20.0)
      updated.bearing.get should be(b2)
    }
  }

  test("update the position of an asset, changing road links, fails without write access", Tag("db")) {
    runWithCleanup {
      userProvider.setCurrentUser(unauthorizedUser)
      val origAsset = provider.getAssetById(300000).get
      val refAsset = provider.getAssetById(300004).get
      val bsMoved = origAsset.copy(lon = refAsset.lon, lat = refAsset.lat)
      intercept[IllegalArgumentException] {
        provider.updateAsset(assetId = bsMoved.id, position = Some(Position(roadLinkId = 2499861l, lon = bsMoved.lon, lat = bsMoved.lat, bearing = bsMoved.bearing)))
      }
    }
  }

  test("update a common asset property value (single choice)", Tag("db")) {
    runWithCleanup {
      userProvider.setCurrentUser(operatorUser)
      val asset = provider.getAssetById(TestAssetId).get
      asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be(AssetPropertyConfiguration.ValidityDirectionSame)
      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "3"))
      val updatedAsset = provider.getAssetById(TestAssetId).get
      updatedAsset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be("3")
      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "2"))
    }
  }

  test("update validity date throws exception if validFrom after validTo", Tag("db")) {
    runWithCleanup {
      val asset = provider.getAssetById(TestAssetId).get
      provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidToId, "2045-12-10"))
      an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2065-12-15"))
    }
  }


  test("update validity date throws exception if validTo before validFrom", Tag("db")) {
    runWithCleanup {
      val asset = provider.getAssetById(TestAssetId).get
      provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2010-12-15"))
      an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidToId, "2009-12-10"))
    }
  }

  test("update a common asset without write access fails", Tag("db")) {
    runWithCleanup {
      userProvider.setCurrentUser(unauthorizedUser)
      val asset = provider.getAssetById(TestAssetId).get
      asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be(AssetPropertyConfiguration.ValidityDirectionSame)
      intercept[IllegalArgumentException] {
        provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "1"))
      }
    }
  }

  test("update a common asset property value (text i.e. timestamp)", Tag("db")) {
    runWithCleanup {

      val asset = provider.getAssetById(TestAssetId).get
      asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidFromId).get.values shouldBe 'nonEmpty
      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2013-12-31"))

      val updatedAsset = provider.getAssetById(TestAssetId).get
      updatedAsset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidFromId).get.values.head.propertyDisplayValue.get should startWith("2013-12-31")

      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId))
      val assetWithoutExpirationTime = provider.getAssetById(TestAssetId).get
      assetWithoutExpirationTime.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidFromId).get.values should equal(List(PropertyValue("")))

      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2013-12-15"))
    }
  }

  test("validate date property values", Tag("db")) {
    runWithCleanup {
      val asset = provider.getAssetById(TestAssetId).get
      an[IllegalArgumentException] should be thrownBy provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "INVALID DATE"))
    }
  }

  private[this] def asSimplePropertySeq(propertyId: String, value: String): Seq[SimpleProperty] = {
    Seq(new SimpleProperty(propertyId, Seq(new PropertyValue(value))))
  }

  private[this] def asSimplePropertySeq(propertyId: String): Seq[SimpleProperty] = {
    Seq(new SimpleProperty(propertyId, Seq()))
  }
}
