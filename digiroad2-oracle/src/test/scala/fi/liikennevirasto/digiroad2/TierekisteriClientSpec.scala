package fi.liikennevirasto.digiroad2

import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.Properties

import fi.liikennevirasto.digiroad2.util.DataFixture._
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.impl.client.HttpClientBuilder
import org.scalatest.{Matchers, FunSuite}

class TierekisteriClientSpec extends FunSuite with Matchers  {

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val tierekisteriClient: TierekisteriClient = {
    new TierekisteriClient(dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint"))
  }

  val connectedToTierekisteri = testConnection

  private def testConnection: Boolean = {
    val url = dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint")
    val request = new HttpGet(url)
    request.setConfig(RequestConfig.custom().setConnectTimeout(2500).build())
    val client = HttpClientBuilder.create().build()
    try {
      val response = client.execute(request)
      try {
        response.getStatusLine.getStatusCode >= 200
      } finally {
        response.close()
      }
    } catch {
      case e: HttpHostConnectException =>
        false
      case e: ConnectTimeoutException =>
        false
      case e: ConnectException =>
        false
    }
  }

  test("fetch from tierekisteri all active mass transit stop") {
    assume(testConnection)
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val assets = tierekisteriClient.fetchActiveMassTransitStops()

    assets.size should be (1)

    val asset = assets.head
    asset.nationalId should be (208914)
    asset.liViId should be ("OTHJ208914")
    asset.roadAddress.road should be (25823)
    asset.roadAddress.roadPart should be (104)
    asset.roadSide should be (RoadSide.Right)
    asset.stopType should be (StopType.Commuter)
    asset.stopCode should be ("681")
    asset.express should be (false)
    asset.equipments.get(Equipment.Timetable) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.TrashBin) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.BikeStand) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.Lighting) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.Seat) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.Roof) should be (Some(Existence.Yes))
    asset.equipments.get(Equipment.RoofMaintainedByAdvertiser) should be (Some(Existence.No))
    asset.equipments.get(Equipment.ElectronicTimetables) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.CarParkForTakingPassengers) should be (Some(Existence.No))
    asset.equipments.get(Equipment.RaisedBusStop) should be (Some(Existence.Yes))
    asset.nameFi should be ("Raisionjoki")
    asset.nameSe should be ("Reso å")
    asset.modifiedBy should be ("KX123456")
    dateFormat.format(asset.operatingFrom) should be("2016-01-01")
    dateFormat.format(asset.operatingTo) should be("2016-01-02")
    dateFormat.format(asset.removalDate) should be("2016-01-03")

  }

  test("fetch from tierekisteri mass transit stop") {
    assume(testConnection)
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val asset = tierekisteriClient.fetchMassTransitStop("OTHJ208914")

    asset.nationalId should be (208914)
    asset.liViId should be ("OTHJ208914")
    asset.roadAddress.road should be (25823)
    asset.roadAddress.roadPart should be (104)
    asset.roadSide should be (RoadSide.Right)
    asset.stopType should be (StopType.Commuter)
    asset.stopCode should be ("681")
    asset.express should be (false)
    asset.equipments.get(Equipment.Timetable) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.TrashBin) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.BikeStand) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.Lighting) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.Seat) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.Roof) should be (Some(Existence.Yes))
    asset.equipments.get(Equipment.RoofMaintainedByAdvertiser) should be (Some(Existence.No))
    asset.equipments.get(Equipment.ElectronicTimetables) should be (Some(Existence.Unknown))
    asset.equipments.get(Equipment.CarParkForTakingPassengers) should be (Some(Existence.No))
    asset.equipments.get(Equipment.RaisedBusStop) should be (Some(Existence.Yes))
    asset.nameFi should be ("Raisionjoki")
    asset.nameSe should be ("Reso å")
    asset.modifiedBy should be ("KX123456")
    dateFormat.format(asset.operatingFrom) should be("2016-01-01")
    dateFormat.format(asset.operatingTo) should be("2016-01-02")
    dateFormat.format(asset.removalDate) should be("2016-01-03")

  }

  test("fetch should throw exception when mass transit stop doesn't exist"){
    assume(testConnection)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriClient.fetchMassTransitStop("")
    }
    thrown.getMessage should be ("Tierekisteri error: Request returned HTTP Error 404")
  }

  test("delete tierekisteri mass transit stop"){
    assume(testConnection)
    tierekisteriClient.deleteMassTransitStop("OTHJ208914")
  }

  test("delete should throw exception when mass transit stop doesn't exist"){
    assume(testConnection)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriClient.deleteMassTransitStop("")
    }
    thrown.getMessage should be ("Tierekisteri error: Request returned HTTP Error 404")
  }

}
