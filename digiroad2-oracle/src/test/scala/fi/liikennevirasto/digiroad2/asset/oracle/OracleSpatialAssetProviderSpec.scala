package fi.liikennevirasto.digiroad2.asset.oracle

import java.sql.SQLIntegrityConstraintViolationException

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, Role, User}
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2._
import org.joda.time.LocalDate
import org.mockito.Mockito.verify
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

import scala.language.implicitConversions
import slick.jdbc.{StaticQuery => Q}
import scala.util.Random
import slick.jdbc.StaticQuery.interpolation


class OracleSpatialAssetProviderSpec extends FunSuite with Matchers with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val MunicipalityEspoo = 49
  val TestAssetTypeId = 10
  val AssetCreator = "integration_test_add_asset"
  val userProvider = new OracleUserProvider
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val spatialAssetDao = new OracleSpatialAssetDao(mockRoadLinkService)

  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val provider = new OracleSpatialAssetProvider(spatialAssetDao, new DummyEventBus, userProvider, passThroughTransaction)
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

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  before {
    userProvider.setCurrentUser(user)
  }

  test("load enumerated values for asset type", Tag("db")) {
    runWithRollback {
      val values = provider.getEnumeratedPropertyValues(TestAssetTypeId)
      values shouldBe 'nonEmpty
      values.map(_.propertyName) should contain("Vaikutussuunta")
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

  ignore("update the position of an asset within a road link", Tag("db")) {
    runWithRollback {
      val eventBus = mock.MockitoSugar.mock[DigiroadEventBus]
      val providerWithMockedEventBus = new OracleSpatialAssetProvider(spatialAssetDao, eventBus, userProvider)
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

  ignore("update the position of an asset, changing road links, fails without write access", Tag("db")) {
    runWithRollback {
      userProvider.setCurrentUser(unauthorizedUser)
      val origAsset = provider.getAssetById(300000).get
      val refAsset = provider.getAssetById(300004).get
      val bsMoved = origAsset.copy(lon = refAsset.lon, lat = refAsset.lat)
      intercept[IllegalArgumentException] {
        provider.updateAsset(assetId = bsMoved.id, position = Some(Position(roadLinkId = 2499861l, lon = bsMoved.lon, lat = bsMoved.lat, bearing = bsMoved.bearing)))
      }
    }
  }

  ignore("update a common asset property value (single choice)", Tag("db")) {
    runWithRollback {
      userProvider.setCurrentUser(operatorUser)
      val asset = provider.getAssetById(TestAssetId).get
      asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be(AssetPropertyConfiguration.ValidityDirectionSame)
      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "3"))
      val updatedAsset = provider.getAssetById(TestAssetId).get
      updatedAsset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be("3")
      provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "2"))
    }
  }

  ignore("update validity date throws exception if validFrom after validTo", Tag("db")) {
    runWithRollback {
      val asset = provider.getAssetById(TestAssetId).get
      provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidToId, "2045-12-10"))
      an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2065-12-15"))
    }
  }


  ignore("update validity date throws exception if validTo before validFrom", Tag("db")) {
    runWithRollback {
      val asset = provider.getAssetById(TestAssetId).get
      provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidFromId, "2010-12-15"))
      an[SQLIntegrityConstraintViolationException] should be thrownBy provider.updateAsset(asset.id, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidToId, "2009-12-10"))
    }
  }

  ignore("update a common asset without write access fails", Tag("db")) {
    runWithRollback {
      userProvider.setCurrentUser(unauthorizedUser)
      val asset = provider.getAssetById(TestAssetId).get
      asset.propertyData.find(_.publicId == AssetPropertyConfiguration.ValidityDirectionId).get.values.head.propertyValue should be(AssetPropertyConfiguration.ValidityDirectionSame)
      intercept[IllegalArgumentException] {
        provider.updateAsset(TestAssetId, None, asSimplePropertySeq(AssetPropertyConfiguration.ValidityDirectionId, "1"))
      }
    }
  }

  ignore("update a common asset property value (text i.e. timestamp)", Tag("db")) {
    runWithRollback {

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

  ignore("validate date property values", Tag("db")) {
    runWithRollback {
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
