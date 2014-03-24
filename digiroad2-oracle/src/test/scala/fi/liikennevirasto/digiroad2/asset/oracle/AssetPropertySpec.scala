package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, AssetWithProperties}
import java.sql.SQLException
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId

class AssetPropertySpec extends FunSuite with Matchers with BeforeAndAfter {
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)
  val user = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))

  before {
    userProvider.setCurrentUser(user)
  }

  test("Update a text property value", Tag("db")) {
    val asset = getTestAsset
    val property = asset.propertyData.find(_.propertyType == "text").get
    val modifiedProperty = property.copy(values = List(PropertyValue(0, "NEW TEXT")))
    provider.updateAssetProperty(asset.id, modifiedProperty.propertyId, modifiedProperty.values)
    val updatedProperty = getTestAsset.propertyData.find(_.propertyId == modifiedProperty.propertyId).get
    updatedProperty.values.size should be(1)
    updatedProperty.values.head.propertyDisplayValue should be("NEW TEXT")
    an[IllegalArgumentException] should be thrownBy {
      provider.updateAssetProperty(asset.id, property.propertyId, List(PropertyValue(0, "A"), PropertyValue(0, "B")))
    }
    provider.updateAssetProperty(asset.id, property.propertyId, property.values)
  }

  test("Delete and create a text property value", Tag("db")) {
    val asset = getTestAsset
    val property = asset.propertyData.find(_.propertyName == "Esteettömyys liikuntarajoitteiselle").get
    provider.deleteAssetProperty(asset.id, property.propertyId)
    val updatedAsset = getTestAsset
    updatedAsset.propertyData.find(_.propertyId == property.propertyId).get.values shouldBe empty
    provider.updateAssetProperty(asset.id, property.propertyId, property.values)
    val restoredAsset = getTestAsset
    restoredAsset.propertyData.find(_.propertyId == property.propertyId).get.values should not be empty
  }

  test("Update a single-choice property value", Tag("db")) {
    val asset = getTestAsset
    val property = asset.propertyData.find(_.propertyName == "Katos").get
    val modifiedProperty = property.copy(values = List(PropertyValue(2, "Kyllä")))
    provider.updateAssetProperty(asset.id, modifiedProperty.propertyId, modifiedProperty.values)
    val updatedProperty = getTestAsset.propertyData.find(_.propertyId == modifiedProperty.propertyId).get
    updatedProperty.values.size should be (1)
    updatedProperty.values.head.propertyDisplayValue should be ("Kyllä")
    an [IllegalArgumentException] should be thrownBy {
      provider.updateAssetProperty(asset.id, property.propertyId, List(PropertyValue(0, "A"), PropertyValue(0, "B")))
    }
    an [SQLException] should be thrownBy {
      provider.updateAssetProperty(asset.id, property.propertyId, List(PropertyValue(333, "A")))
    }
    provider.updateAssetProperty(asset.id, property.propertyId, property.values)
  }

  test("Delete and create a single-choice property value", Tag("db")) {
    val asset = getTestAsset
    val property = asset.propertyData.find(_.propertyName == "Katos").get
    provider.deleteAssetProperty(asset.id, property.propertyId)
    val updatedAsset = getTestAsset
    updatedAsset.propertyData.find(_.propertyId == property.propertyId).get.values shouldBe empty
    provider.updateAssetProperty(asset.id, property.propertyId, property.values)
    val restoredAsset = getTestAsset
    restoredAsset.propertyData.find(_.propertyId == property.propertyId) should not be empty
  }

  test("Update multiple-choice property values", Tag("db")) {
    val asset = getTestAsset
    val property = asset.propertyData.find(_.propertyType == "multiple_choice").get
    val modifiedProperty = property.copy(values = List(PropertyValue(2, "Linja-autojen paikallisliikenne"), PropertyValue(3, "Linja-autojen kaukoliikenne")))
    provider.updateAssetProperty(asset.id, modifiedProperty.propertyId, modifiedProperty.values)
    val updatedProperty = getTestAsset.propertyData.find(_.propertyId == modifiedProperty.propertyId).get
    updatedProperty.values.size should be (2)
    updatedProperty.values.map(_.propertyValue) should contain allOf (2, 3)
    provider.updateAssetProperty(asset.id, property.propertyId, property.values)
    val restoredProperty = getTestAsset.propertyData.find(_.propertyId == modifiedProperty.propertyId).get
    restoredProperty.values.size should be (1)
  }

  test("Delete and create a multiple-choice property value", Tag("db")) {
    val asset = getTestAsset
    val property = asset.propertyData.find(_.propertyName == "Pysäkin tyyppi").get
    provider.deleteAssetProperty(asset.id, property.propertyId)
    val updatedAsset = getTestAsset
    updatedAsset.propertyData.find(_.propertyId == property.propertyId).get.values shouldBe empty
    provider.updateAssetProperty(asset.id, property.propertyId,  List(PropertyValue(2, "Linja-autojen paikallisliikenne")))
    val restoredAsset = getTestAsset
    restoredAsset.propertyData.find(_.propertyId == property.propertyId).get.values should not be empty
  }

  private def getTestAsset: AssetWithProperties = provider.getAssetById(TestAssetId).get
}
