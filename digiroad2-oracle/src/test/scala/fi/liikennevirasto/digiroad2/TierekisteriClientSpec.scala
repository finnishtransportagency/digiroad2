package fi.liikennevirasto.digiroad2

import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.{Date, Properties}

import fi.liikennevirasto.digiroad2.util.DataFixture._
import fi.liikennevirasto.digiroad2.util.{RoadAddress, Track}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.impl.client.HttpClientBuilder
import org.scalatest.{FunSuite, Matchers}

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
    asset.liviId should be ("OTHJ208914")
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
    asset.liviId should be ("OTHJ208914")
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

  test("post should throw nothing if the create TierekisteriMassTransitStop was successful"){
    assume(testConnection)

    val thrown = intercept[TierekisteriClientException] {

    val equipments : Map[Equipment, Existence] = Map(
      Equipment.Timetable -> Existence.Unknown,
      Equipment.Seat -> Existence.Unknown,
      Equipment.BikeStand -> Existence.Unknown,
      Equipment.ElectronicTimetables -> Existence.Unknown,
      Equipment.TrashBin -> Existence.Unknown,
      Equipment.RoofMaintainedByAdvertiser -> Existence.No,
      Equipment.CarParkForTakingPassengers -> Existence.No,
      Equipment.RaisedBusStop -> Existence.Yes,
      Equipment.Lighting -> Existence.Unknown)


      val dateFormat = "yyyy-MM-dd"

      val tkMassTransitStop = TierekisteriMassTransitStop(208914,"OTHJ208914",RoadAddress(None,25823,104,Track.Combined,150,None),RoadSide.Right,StopType.Combined, false,
        equipments,"681","Raisionjoki", "Reso å", "KX123456", new Date, new Date,new Date)


      tierekisteriClient.createMassTransitStop(tkMassTransitStop)
    }
  }

  test("post should throw exception when mass transit data has errors") {
    assume(testConnection)

    val thrown = intercept[TierekisteriClientException] {

      val equipments: Map[Equipment, Existence] = Map(
        Equipment.Timetable -> Existence.Unknown,
        Equipment.Seat -> Existence.Unknown,
        Equipment.BikeStand -> Existence.Unknown,
        Equipment.ElectronicTimetables -> Existence.Unknown,
        Equipment.TrashBin -> Existence.Unknown,
        Equipment.RoofMaintainedByAdvertiser -> Existence.No,
        Equipment.CarParkForTakingPassengers -> Existence.No,
        Equipment.RaisedBusStop -> Existence.Yes,
        Equipment.Lighting -> Existence.Unknown)

      val tkMassTransitStop = TierekisteriMassTransitStop(208914, "OTHJ208914eeeeeeeeeeeee", RoadAddress(None, 25823, 104, Track.Combined, 150, None), RoadSide.Right, StopType.Combined, false,
        equipments, "681", "Raisionjoki", "Reso å", "KX123456", new Date, new Date, new Date)

      tierekisteriClient.createMassTransitStop(tkMassTransitStop)

    }
    thrown.getMessage should be("Tierekisteri error: Request returned HTTP Error 400")
  }

  test("post should throw exception when mass transit have one id that already exists"){
    assume(testConnection)

    val thrown = intercept[TierekisteriClientException] {

      val equipments : Map[Equipment, Existence] = Map(
        Equipment.Timetable -> Existence.Unknown,
        Equipment.Seat -> Existence.Unknown,
        Equipment.BikeStand -> Existence.Unknown,
        Equipment.ElectronicTimetables -> Existence.Unknown,
        Equipment.TrashBin -> Existence.Unknown,
        Equipment.RoofMaintainedByAdvertiser -> Existence.No,
        Equipment.CarParkForTakingPassengers -> Existence.No,
        Equipment.RaisedBusStop -> Existence.Yes,
        Equipment.Lighting -> Existence.Unknown)

      val tkMassTransitStop = TierekisteriMassTransitStop(208913,"OTHJ208914",RoadAddress(None,25823,104,Track.Combined,150,None),RoadSide.Right,StopType.Combined, false,
        equipments,"681","Raisionjoki", "Reso å", "KX123456", new Date, new Date,new Date)

      tierekisteriClient.createMassTransitStop(tkMassTransitStop)

    }
    thrown.getMessage should be("Tierekisteri error: Request returned HTTP Error 409")
  }

  test("updating bus stop information in Tierekisteri using PUT method (204 on successful update)") {
    val equipmentsMap: Map[Equipment, Existence] = Map(
      Equipment.Timetable -> Existence.Unknown,
      Equipment.Seat -> Existence.Unknown,
      Equipment.BikeStand -> Existence.Unknown,
      Equipment.ElectronicTimetables -> Existence.Unknown,
      Equipment.TrashBin -> Existence.Unknown,
      Equipment.Roof -> Existence.Yes,
      Equipment.RoofMaintainedByAdvertiser -> Existence.No,
      Equipment.CarParkForTakingPassengers -> Existence.No,
      Equipment.RaisedBusStop -> Existence.Yes,
      Equipment.Lighting -> Existence.Unknown
    )

    assume(testConnection)

    val objTierekisteriMassTransitStop = TierekisteriMassTransitStop(208914, "OTHJ208914",
      RoadAddress(None, 1, 1, Track.Combined, 150, None), RoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      "681", "Raisionjoki", "Reso å", "KX123456", new Date, new Date, new Date)

    val asset = tierekisteriClient.updateMassTransitStop(objTierekisteriMassTransitStop)
  }

  test("updating bus stop information in Tierekisteri using PUT method (400 (BAD REQUEST) malformed)") {
    val equipmentsMap: Map[Equipment, Existence] = Map(
      Equipment.Timetable -> Existence.Unknown,
      Equipment.Seat -> Existence.Unknown,
      Equipment.BikeStand -> Existence.Unknown,
      Equipment.ElectronicTimetables -> Existence.Unknown,
      Equipment.TrashBin -> Existence.Unknown,
      Equipment.Roof -> Existence.Yes,
      Equipment.RoofMaintainedByAdvertiser -> Existence.No,
      Equipment.CarParkForTakingPassengers -> Existence.No,
      Equipment.RaisedBusStop -> Existence.Yes,
      Equipment.Lighting -> Existence.Unknown
    )

    assume(testConnection)

    val objTierekisteriMassTransitStop = TierekisteriMassTransitStop(208914, "OTHJ20891499999999",
      RoadAddress(None, 1, 1, Track.Combined, 150, None), RoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      "681", "Raisionjoki", "Reso å", "KX123456", new Date, new Date, new Date)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriClient.updateMassTransitStop(objTierekisteriMassTransitStop)
    }
    thrown.getMessage should be ("Tierekisteri error: Request returned HTTP Error 400")
  }

  test("updating bus stop information in Tierekisteri using PUT method (500 (INTERNAL SERVER ERROR) error in Tierekisteri)") {
    val equipmentsMap: Map[Equipment, Existence] = Map(
      Equipment.Timetable -> Existence.Unknown,
      Equipment.Seat -> Existence.Unknown,
      Equipment.BikeStand -> Existence.Unknown,
      Equipment.ElectronicTimetables -> Existence.Unknown,
      Equipment.TrashBin -> Existence.Unknown,
      Equipment.Roof -> Existence.Yes,
      Equipment.RoofMaintainedByAdvertiser -> Existence.No,
      Equipment.CarParkForTakingPassengers -> Existence.No,
      Equipment.RaisedBusStop -> Existence.Yes,
      Equipment.Lighting -> Existence.Unknown
    )

    assume(testConnection)

    val objTierekisteriMassTransitStop = TierekisteriMassTransitStop(208914, "OTHJ20891499Err",
      RoadAddress(None, 1, 1, Track.Combined, 150, None), RoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      "681", "Raisionjoki", "Reso å", "KX123456", new Date, new Date, new Date)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriClient.updateMassTransitStop(objTierekisteriMassTransitStop)
    }
    thrown.getMessage should be ("Tierekisteri error: Request returned HTTP Error 500")
  }

  test ("Convert MassTransitStopWithProperties into TierekisteriMassTransitStop") {
    val MTSWithPropertiesToConvert = MassTransitStopWithProperties(2, 2, Nil, 373915.2020468317, 6677177.581940852, None, Some(73), None, false, Nil)

    val tierekisteriMassTransitStopConverted = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(MTSWithPropertiesToConvert)

    tierekisteriMassTransitStopConverted.nationalId should be(2)
    tierekisteriMassTransitStopConverted.roadAddress.track should be(Track.Combined)
    tierekisteriMassTransitStopConverted.roadSide.value should be("oikea")
    tierekisteriMassTransitStopConverted.express should be(false)
  }

}
