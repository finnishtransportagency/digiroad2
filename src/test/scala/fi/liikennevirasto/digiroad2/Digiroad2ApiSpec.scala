package fi.liikennevirasto.digiroad2

import org.scalatra.test.scalatest._
import org.scalatest.{FunSuite, Tag}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import fi.liikennevirasto.digiroad2.feature.{EnumeratedPropertyValue, AssetType, Asset, PropertyValue}
import org.json4s.JsonDSL._

class Digiroad2ApiSpec extends FunSuite with ScalatraSuite  {
  protected implicit val jsonFormats: Formats = DefaultFormats
  val TestAssetId = 105
  val TestPropertyId = "37"
  val TestPropertyId2 = "41"

  addServlet(classOf[Digiroad2Api], "/*")

  test("get assets", Tag("db")) {
    get("/assets?assetTypeId=10&municipalityNumber=235") {
      status should equal(200)
      parse(body).extract[List[Asset]].size should be(3)
    }
  }

  test("get asset by id", Tag("db")) {
    get("/assets/" + TestAssetId) {
      status should equal(200)
      parse(body).extract[Asset].id should be (TestAssetId)
    }
    get("/assets/9999999999999999") {
      status should equal(404)
    }
  }

  test("get asset types", Tag("db")) {
    get("/assetTypes") {
      status should equal(200)
      parse(body).extract[List[AssetType]].size should be(1)
    }
  }

  test("get enumerated property values", Tag("db")) {
    get("/enumeratedPropertyValues/10") {
      status should equal(200)
      parse(body).extract[List[EnumeratedPropertyValue]].size should be(4)
    }
  }

  test("ping") {
    get("/ping") {
      status should equal (200)
      body should equal ("pong")
    }
  }

  test("get map configuration") {
    get("/config") {
      status should equal(200)
      val responseJson = parse(body)
      (responseJson \ "mapfull" \ "state" \ "zoom").values should equal(8)
      (responseJson \ "mapfull" \ "state" \ "east").values should equal("373560")
      (responseJson \ "mapfull" \ "state" \ "north").values should equal("6677676")
    }
  }

  test("get road links", Tag("db")) {
    get("/roadlinks?municipalityNumber=235") {
      status should equal(200)
      val roadLinksJson = parse(body)
      (roadLinksJson \ "features" \ "geometry").children.size should (be > 20)
    }
  }

  test("update asset property", Tag("db")) {
    val body1 = write(List(PropertyValue(3, "Linja-autojen kaukoliikenne")))
    val body2 = write(List(PropertyValue(2, "Linja-autojen paikallisliikenne")))
    put("/assets/" + TestAssetId + "/properties/" + TestPropertyId2 + "/values", body1.getBytes, Map("Content-type" -> "application/json")) {
      status should equal(200)
      get("/assets/" + TestAssetId) {
        val asset = parse(body).extract[Asset]
        val prop = asset.propertyData.find(_.propertyId == TestPropertyId2).get
        prop.values.size should be (1)
        prop.values.head.propertyValue should be (3)
        put("/assets/" + TestAssetId + "/properties/" + TestPropertyId2 + "/values", body2.getBytes, Map("Content-type" -> "application/json")) {
          status should equal(200)
          get("/assets/" + TestAssetId) {
            parse(body).extract[Asset].propertyData.find(_.propertyId == TestPropertyId2).get.values.head.propertyValue should be (2)
          }
        }
      }
    }
  }

  test("delete and create asset property", Tag("db")) {
    val propBody = write(List(PropertyValue(2, "")))
    delete("/assets/" + TestAssetId + "/properties/" + TestPropertyId + "/values") {
      status should equal(200)
      get("/assets/" + TestAssetId) {
        val asset = parse(body).extract[Asset]
        asset.propertyData.find(_.propertyId == TestPropertyId).get.values.size should be (0)
        put("/assets/" + TestAssetId + "/properties/" + TestPropertyId + "/values", propBody.getBytes, Map("Content-type" -> "application/json")) {
          status should equal(200)
          get("/assets/" + TestAssetId) {
            parse(body).extract[Asset].propertyData.find(_.propertyId == TestPropertyId).get.values.head.propertyValue should be (2)
          }
        }
      }
    }
  }

  test("load image by id", Tag("db")) {
    get("/images/1476") {
      status should equal(200)
      body.length should(be > 0)
    }
  }

  test("load image by id and timestamp", Tag("db")) {
    get("/images/1476_123456789") {
      status should equal(200)
      body.length should(be > 0)
    }
  }
}
