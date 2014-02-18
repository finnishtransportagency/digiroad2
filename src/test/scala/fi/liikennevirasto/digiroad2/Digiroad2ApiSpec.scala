package fi.liikennevirasto.digiroad2

import org.scalatra.test.scalatest._
import org.scalatest.{FunSuite, Tag}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.AssetStatus._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.asset.EnumeratedPropertyValue
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import scala.Some
import fi.liikennevirasto.digiroad2.asset.AssetType
import fi.liikennevirasto.digiroad2.asset.PropertyValue

class Digiroad2ApiSpec extends FunSuite with ScalatraSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats
  val TestAssetId = 100
  val TestPropertyId = "1"
  val TestPropertyId2 = "2"

  addServlet(classOf[Digiroad2Api], "/*")
  addServlet(classOf[SessionApi], "/auth/*")

  test("require authentication", Tag("db")) {
    get("/assets?assetTypeId=10&municipalityNumber=235") {
      status should equal(401)
    }
    getWithUserAuth("/assets?assetTypeId=10&municipalityNumber=235", username = "nonexistent") {
      status should equal(401)
    }
    getWithUserAuth("/assets?assetTypeId=10&municipalityNumber=235", username = "test") {
      status should equal(200)
    }
  }

  test("get assets", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&bbox=374702,6677462,374870,6677780&validityPeriod=current") {
      status should equal(200)
      parse(body).extract[List[AssetWithProperties]].size should be(1)
    }
  }

  test("get assets without bounding box", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&validityPeriod=current") {
      status should equal(200)
      parse(body).extract[List[AssetWithProperties]].size should be(3)
    }
  }

  test("get assets without bounding box for multiple municipalities", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&validityPeriod=current", "test2") {
      status should equal(200)
      parse(body).extract[List[AssetWithProperties]].size should be(4)
    }
  }

  test("get asset by id", Tag("db")) {
    getWithUserAuth("/assets/" + TestAssetId) {
      status should equal(200)
      parse(body).extract[AssetWithProperties].id should be (TestAssetId)
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

  test("get map configuration", Tag("db")) {
    getWithUserAuth("/config") {
      status should equal(200)
      val responseJson = parse(body)
      (responseJson \ "mapfull" \ "state" \ "zoom").values should equal(8)
      (responseJson \ "mapfull" \ "state" \ "east").values should equal("373560")
      (responseJson \ "mapfull" \ "state" \ "north").values should equal("6677676")
    }
  }

  test("get road links", Tag("db")) {
    getWithUserAuth("/roadlinks?bbox=374702,6677462,374870,6677780") {
      status should equal(200)
      val roadLinksJson = parse(body)
      (roadLinksJson \ "features" \ "geometry" \ "coordinates").children.size should be (6)
    }
  }

  test("update asset property", Tag("db")) {
    val body1 = write(List(PropertyValue(3, "Linja-autojen kaukoliikenne")))
    val body2 = write(List(PropertyValue(2, "Linja-autojen paikallisliikenne")))
    putWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId2 + "/values", body1.getBytes, Map("Content-type" -> "application/json")) {
      status should equal(200)
      getWithUserAuth("/assets/" + TestAssetId) {
        val asset = parse(body).extract[AssetWithProperties]
        val prop = asset.propertyData.find(_.propertyId == TestPropertyId2).get
        prop.values.size should be (1)
        prop.values.head.propertyValue should be (3)
        putWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId2 + "/values", body2.getBytes, Map("Content-type" -> "application/json")) {
          status should equal(200)
          getWithUserAuth("/assets/" + TestAssetId) {
            parse(body).extract[AssetWithProperties].propertyData.find(_.propertyId == TestPropertyId2).get.values.head.propertyValue should be (2)
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
        val asset = parse(body).extract[AssetWithProperties]
        asset.propertyData.find(_.propertyId == TestPropertyId).get.values.size should be (0)
        putWithUserAuth("/assets/" + TestAssetId + "/properties/" + TestPropertyId + "/values", propBody.getBytes, Map("Content-type" -> "application/json")) {
          status should equal(200)
          getWithUserAuth("/assets/" + TestAssetId) {
            parse(body).extract[AssetWithProperties].propertyData.find(_.propertyId == TestPropertyId).get.values.head.propertyValue should be (2)
          }
        }
      }
    }
  }

  test("get past assets", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&validityPeriod=past") {
      status should equal(200)
      parse(body).extract[List[AssetWithProperties]].size should be(1)
    }
  }

  test("get future assets", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&validityPeriod=future") {
      status should equal(200)
      parse(body).extract[List[AssetWithProperties]].size should be(1)
    }
  }

  test("mark asset on expired link as floating", Tag("db")) {
    getWithUserAuth("/assets?assetTypeId=10&validityDate=2014-06-01&validityPeriod=current", "test49") {
      status should equal(200)
      val assets = parse(body).extract[List[AssetWithProperties]]
      assets should have length(1)
      assets.head.status should be(Some(Floating))
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

  test("get available properties for asset type", Tag("db")) {
    getWithUserAuth("/assetTypeProperties/10") {
      status should equal(200)
      val ps = parse(body).extract[List[Property]]
      ps.size should equal(11)
      val p1 = ps.find(_.propertyId == "1").get
      p1.propertyName should be ("Pysäkin katos")
      p1.propertyType should be ("single_choice")
      p1.required should be (true)
      ps.find(_.propertyName == "Vaikutussuunta") should be ('defined)
    }
  }

  private[this] def getWithUserAuth[A](uri: String, username: String = "test")(f: => A): A = {
    val authHeader = authenticateAndGetHeader(username)
    get(uri, headers = authHeader)(f)
  }

  private[this] def putWithUserAuth[A](uri: String, body: Array[Byte], headers: Map[String, String] = Map(), username: String = "test")(f: => A): A = {
    put(uri, body, headers = headers ++ authenticateAndGetHeader(username))(f)
  }

  private[this] def deleteWithUserAuth[A](uri: String, username: String = "test")(f: => A): A = {
    delete(uri, headers = authenticateAndGetHeader(username))(f)
  }

  private[this] def authenticateAndGetHeader(username: String): Map[String, String] = {
    Map("OAM_REMOTE_USER" -> username)
  }
}
