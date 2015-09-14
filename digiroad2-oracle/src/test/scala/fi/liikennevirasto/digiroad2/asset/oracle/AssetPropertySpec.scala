package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{SimpleProperty, PropertyValue, AssetWithProperties}
import java.sql.SQLException
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import fi.liikennevirasto.digiroad2.DummyEventBus
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class AssetPropertySpec extends FunSuite with Matchers with BeforeAndAfter {
  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider, passThroughTransaction)
  val user = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))

  before {
    userProvider.setCurrentUser(user)
  }

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Update a text property value", Tag("db")) {
    runWithRollback {
      val asset = getTestAsset
      val property = asset.propertyData.find(_.publicId == "nimi_suomeksi").get
      val modifiedProperty = property.copy(values = List(PropertyValue("NEW TEXT")))
      updateAssetProperties(asset.id, modifiedProperty.publicId, modifiedProperty.values)
      val updatedProperty = getTestAsset.propertyData.find(_.id == modifiedProperty.id).get
      updatedProperty.values.size should be(1)
      updatedProperty.values.head.propertyValue should be("NEW TEXT")
      updatedProperty.values.head.propertyDisplayValue should be(Some("NEW TEXT"))
      an[IllegalArgumentException] should be thrownBy {
        updateAssetProperties(asset.id, property.publicId, List(PropertyValue("A", Some("A")), PropertyValue("B")))
      }
      updateAssetProperties(asset.id, property.publicId, property.values)
    }
  }

  test("Delete and create a text property value", Tag("db")) {
    runWithRollback {
      val asset = getTestAsset
      val property = asset.propertyData.find(_.publicId == "esteettomyys_liikuntarajoitteiselle").get
      updateAssetProperties(asset.id, property.publicId)
      val updatedAsset = getTestAsset
      updatedAsset.propertyData.find(_.id == property.id).get.values shouldBe empty
      updateAssetProperties(asset.id, property.publicId, property.values)
      val restoredAsset = getTestAsset
      restoredAsset.propertyData.find(_.id == property.id).get.values should not be empty
    }
  }

  test("Update a single-choice property value", Tag("db")) {
    runWithRollback {
      val asset = getTestAsset
      val property = asset.propertyData.find(_.publicId == "katos").get
      val modifiedProperty = property.copy(values = List(PropertyValue("2", Some("Kyllä"))))
      updateAssetProperties(asset.id, modifiedProperty.publicId, modifiedProperty.values)
      val updatedProperty = getTestAsset.propertyData.find(_.id == modifiedProperty.id).get
      updatedProperty.values.size should be(1)
      updatedProperty.values.head.propertyDisplayValue.get should be("Kyllä")
      an[IllegalArgumentException] should be thrownBy {
        updateAssetProperties(asset.id, property.publicId, List(PropertyValue("0", Some("A")), PropertyValue("0", Some("B"))))
      }
      an[SQLException] should be thrownBy {
        updateAssetProperties(asset.id, property.publicId, List(PropertyValue("333", Some("A"))))
      }
      updateAssetProperties(asset.id, property.publicId, property.values)
    }
  }

  test("Update multiple-choice property values", Tag("db")) {
    runWithRollback {
      val asset = getTestAsset
      val property = asset.propertyData.find(_.propertyType == "multiple_choice").get
      val modifiedProperty = property.copy(values = List(PropertyValue("2", Some("Linja-autojen paikallisliikenne")), PropertyValue("3", Some("Linja-autojen kaukoliikenne"))))
      updateAssetProperties(asset.id, modifiedProperty.publicId, modifiedProperty.values)
      val updatedProperty = getTestAsset.propertyData.find(_.publicId == modifiedProperty.publicId).get
      updatedProperty.values.size should be(2)
      updatedProperty.values.map(_.propertyValue) should contain allOf("2", "3")
      updateAssetProperties(asset.id, property.publicId, property.values)
      val restoredProperty = getTestAsset.propertyData.find(_.id == modifiedProperty.id).get
      restoredProperty.values.size should be(1)
    }
  }

  test("Delete and create a multiple-choice property value", Tag("db")) {
    runWithRollback {
      val asset = getTestAsset
      val property = asset.propertyData.find(_.publicId == "pysakin_tyyppi").get
      updateAssetProperties(asset.id, property.publicId)
      val updatedAsset = getTestAsset
      updatedAsset.propertyData.find(_.id == property.id).get.values shouldBe empty
      updateAssetProperties(asset.id, property.publicId, List(PropertyValue("2", Some("Linja-autojen paikallisliikenne"))))
      val restoredAsset = getTestAsset
      restoredAsset.propertyData.find(_.id == property.id).get.values should not be empty
    }
  }

  private[this] def updateAssetProperties(assetId: Long, propertyId: String, values: Seq[PropertyValue] = Seq()) {
    provider.updateAsset(assetId, None, Seq(new SimpleProperty(propertyId, values)))
  }

  private def getTestAsset: AssetWithProperties = provider.getAssetById(TestAssetId).get
}
