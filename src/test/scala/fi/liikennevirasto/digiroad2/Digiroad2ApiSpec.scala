package fi.liikennevirasto.digiroad2

import org.scalatra.test.scalatest._
import org.scalatest.{FunSuite, Tag}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import fi.liikennevirasto.digiroad2.feature.{EnumeratedPropertyValue, AssetType, Asset, PropertyValue}
import org.json4s.JsonDSL._
import org.apache.commons.codec.binary.Base64

class Digiroad2ApiSpec extends FunSuite with ScalatraSuite  {
  protected implicit val jsonFormats: Formats = DefaultFormats
  val TestAssetId = 100
  val TestPropertyId = "1"
  val TestPropertyId2 = "2"

  addServlet(classOf[Digiroad2Api], "/*")

  test("require basic authentication") {
    val basicCredentials = Base64.encodeBase64("test:test".getBytes)
    val authHeader = Map("Authorization" -> ("Basic " + new String(basicCredentials)))
    val invalidCredentials = Base64.encodeBase64("test:INVALID".getBytes)
    val invalidAuthHeader = Map("Authorization" -> ("Basic " + new String(invalidCredentials)))
    get("/assets?assetTypeId=10&municipalityNumber=235") {
      status should equal(401)
    }
    get("/assets?assetTypeId=10&municipalityNumber=235", headers = authHeader) {
      status should equal(200)
    }
    get("/assets?assetTypeId=10&municipalityNumber=235", headers = invalidAuthHeader) {
      status should equal(401)
    }
  }

  test("get assets", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&municipalityNumber=235") {
      status should equal(200)
      parse(body).extract[List[Asset]].size should be(3)
    }
  }

  test("get asset by id", Tag("db")) {
    getWithUserAuth("/assets/" + TestAssetId) {
      status should equal(200)
      parse(body).extract[Asset].id should be (TestAssetId)
    }
    getWithUserAuth("/assets/9999999999999999") {
      status should equal(404)
    }
  }

  test("get asset types", Tag("db")) {
    getWithUserAuth("/assetTypes") {
      status should equal(200)
      parse(body).extract[List[AssetType]].size should be(1)
    }
  }

  test("get enumerated property values", Tag("db")) {
    getWithUserAuth("/enumeratedPropertyValues/10") {
      status should equal(200)
      parse(body).extract[List[EnumeratedPropertyValue]].size should be(4)
    }
  }

  test("get map configuration") {
    getWithUserAuth("/config") {
      status should equal(200)
      val responseJson = parse(body)
      (responseJson \ "mapfull" \ "state" \ "zoom").values should equal(8)
      (responseJson \ "mapfull" \ "state" \ "east").values should equal("373560")
      (responseJson \ "mapfull" \ "state" \ "north").values should equal("6677676")
    }
  }

  test("get road links", Tag("db")) {
    getWithUserAuth("/roadlinks?municipalityNumber=235") {
      status should equal(200)
      val roadLinksJson = parse(body)
      (roadLinksJson \ "features" \ "geometry").children.size should (be > 20)
    }
  }

  test("update asset property", Tag("db")) {
    val body1 = write(List(PropertyValue(3, "Linja-autojen kaukoliikenne")))
    val body2 = write(List(PropertyValue(2, "Linja-autojen paikallisliikenne")))
    putWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId2 + "/values", body1.getBytes, Map("Content-type" -> "application/json")) {
      status should equal(200)
      getWithUserAuth("/assets/" + TestAssetId) {
        val asset = parse(body).extract[Asset]
        val prop = asset.propertyData.find(_.propertyId == TestPropertyId2).get
        prop.values.size should be (1)
        prop.values.head.propertyValue should be (3)
        putWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId2 + "/values", body2.getBytes, Map("Content-type" -> "application/json")) {
          status should equal(200)
          getWithUserAuth("/assets/" + TestAssetId) {
            parse(body).extract[Asset].propertyData.find(_.propertyId == TestPropertyId2).get.values.head.propertyValue should be (2)
          }
        }
      }
    }
  }

  test("delete and create asset property", Tag("db")) {
    val propBody = write(List(PropertyValue(2, "")))
    deleteWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId + "/values") {
      status should equal(200)
      getWithUserAuth("/assets/" + TestAssetId) {
        val asset = parse(body).extract[Asset]
        asset.propertyData.find(_.propertyId == TestPropertyId).get.values.size should be (0)
        putWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId + "/values", propBody.getBytes, Map("Content-type" -> "application/json")) {
          status should equal(200)
          getWithUserAuth("/assets/" + TestAssetId) {
            parse(body).extract[Asset].propertyData.find(_.propertyId == TestPropertyId).get.values.head.propertyValue should be (2)
          }
        }
      }
    }
  }

  test("load image by id", Tag("db")) {
    getWithUserAuth("/images/2") {
      status should equal(200)
      body.length should(be > 0)
    }
  }

  test("load image by id and timestamp", Tag("db")) {
    getWithUserAuth("/images/1_123456789") {
      status should equal(200)
      body.length should(be > 0)
    }
  }

  private[this] def getWithUserAuth[A](uri: String, user: String = "test")(f: => A): A = {
    get(uri, headers = basicAuthHeader(user))(f)
  }

  private[this] def putWithUserAuth[A](uri: String, body: Array[Byte], headers: Map[String, String] = Map(), user: String = "test")(f: => A): A = {
    put(uri, body, headers = headers ++ basicAuthHeader(user))(f)
  }

  private[this] def deleteWithUserAuth[A](uri: String, user: String = "test")(f: => A): A = {
    delete(uri, headers = basicAuthHeader(user))(f)
  }

  private[this] def basicAuthHeader(user: String): Map[String, String] = {
    val basicCredentials = Base64.encodeBase64((user + ":" + user).getBytes)
    Map("Authorization" -> ("Basic " + new String(basicCredentials)))
  }
}
