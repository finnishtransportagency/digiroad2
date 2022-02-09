package fi.liikennevirasto.digiroad2.client

import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.{Date, Properties}

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.util._
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.{ProtocolVersion, StatusLine}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TierekisteriClientSpec extends FunSuite with Matchers  {

  lazy val tierekisteriMassTransitStopClient: TierekisteriMassTransitStopClient = {
    new TierekisteriMassTransitStopClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
      Digiroad2Properties.tierekisteriEnabled,
      HttpClientBuilder.create().build())
  }
  
  lazy val connectedToTierekisteri = testConnection

  private def testConnection: Boolean = {
    val url = Digiroad2Properties.tierekisteriRestApiEndPoint
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
    val assets = tierekisteriMassTransitStopClient.fetchActiveMassTransitStops()

    assets.size should be (2)
    assets.map(_.liviId) should contain ("OTHJ208914")

    val asset = assets.find(_.liviId == "OTHJ208914").get
    asset.nationalId should be (208914)
    asset.roadAddress.road should be (25823)
    asset.roadAddress.roadPart should be (104)
    asset.roadSide should be (TRRoadSide.Right)
    asset.stopType should be (StopType.Commuter)
    asset.stopCode should be (Some("681"))
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
    asset.nameFi should be (Some("Raisionjoki"))
    asset.nameSe should be (Some("Reso å"))
    asset.modifiedBy should be ("KX123456")
    dateFormat.format(asset.operatingFrom.get) should be("2016-01-01")
    dateFormat.format(asset.operatingTo.get) should be("2016-01-02")
    dateFormat.format(asset.removalDate.get) should be("2016-01-03")
    dateFormat.format(asset.inventoryDate) should be ("2016-09-01")

  }

  test("fetch from tierekisteri mass transit stop") {
    assume(testConnection)
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val asset = tierekisteriMassTransitStopClient.fetchMassTransitStop("OTHJ208914").get

    asset.nationalId should be (208914)
    asset.liviId should be ("OTHJ208914")
    asset.roadAddress.road should be (25823)
    asset.roadAddress.roadPart should be (104)
    asset.roadSide should be (TRRoadSide.Right)
    asset.stopType should be (StopType.Commuter)
    asset.stopCode should be (Some("681"))
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
    asset.nameFi should be (Some("Raisionjoki"))
    asset.nameSe should be (Some("Reso å"))
    asset.modifiedBy should be ("KX123456")
    dateFormat.format(asset.operatingFrom.get) should be("2016-01-01")
    dateFormat.format(asset.operatingTo.get) should be("2016-01-02")
    dateFormat.format(asset.removalDate.get) should be("2016-01-03")
    dateFormat.format(asset.inventoryDate) should be ("2016-09-01")

  }

  test("fetch should return None when mass transit stop doesn't exist"){
    assume(testConnection)

    val asset = tierekisteriMassTransitStopClient.fetchMassTransitStop("12345")
    asset should be(None)
  }

  test("delete tierekisteri mass transit stop"){
    assume(testConnection)
    tierekisteriMassTransitStopClient.deleteMassTransitStop("OTHJ208914")
  }

  test("delete should throw exception when mass transit stop doesn't exist"){
    assume(testConnection)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriMassTransitStopClient.deleteMassTransitStop("123475")
    }
    thrown.getMessage should be ("Tierekisteri error: 404: N/A")
  }

  test("post should throw nothing if the create TierekisteriMassTransitStop was successful"){
    assume(testConnection)

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

    val tkMassTransitStop = TierekisteriMassTransitStop(208914,"OTHJ208914",RoadAddress(None,25823,104,Track.Combined,150),TRRoadSide.Right,StopType.Combined, false,
      equipments,Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date)

    tierekisteriMassTransitStopClient.createMassTransitStop(tkMassTransitStop)
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

      val tkMassTransitStop = TierekisteriMassTransitStop(208914, "OTHJ208914eeeeeeeeeeeee", RoadAddress(None, 25823, 104, Track.Combined, 150), TRRoadSide.Right, StopType.Combined, false,
        equipments, Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9,1))

      tierekisteriMassTransitStopClient.createMassTransitStop(tkMassTransitStop)

    }
    thrown.getMessage should be ("Tierekisteri error: 400: N/A")
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

      val tkMassTransitStop = TierekisteriMassTransitStop(208913,"OTHJ208916",RoadAddress(None,25823,104,Track.Combined,150),TRRoadSide.Right,StopType.Combined, false,
        equipments,Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9, 1))

      tierekisteriMassTransitStopClient.createMassTransitStop(tkMassTransitStop)

    }
    thrown.getMessage should be ("Tierekisteri error: 409: N/A")
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
      RoadAddress(None, 1, 1, Track.Combined, 150), TRRoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date)

    val asset = tierekisteriMassTransitStopClient.updateMassTransitStop(objTierekisteriMassTransitStop, None)
  }

  test("updating bus stop information in Tierekisteri using override in PUT method") {
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
      RoadAddress(None, 1, 1, Track.Combined, 150), TRRoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date)

    val asset = tierekisteriMassTransitStopClient.updateMassTransitStop(objTierekisteriMassTransitStop, Some("Livi123456"))
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
      RoadAddress(None, 1, 1, Track.Combined, 150), TRRoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriMassTransitStopClient.updateMassTransitStop(objTierekisteriMassTransitStop, None)
    }
    thrown.getMessage should be ("Tierekisteri error: 400: N/A")
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
      RoadAddress(None, 1, 1, Track.Combined, 150), TRRoadSide.Right, StopType("paikallis"),
      false, equipmentsMap,
      Option("681"),Option("Raisionjoki"), Option("Reso å"), "KX123456", Option(new Date), Option(new Date), Option(new Date), new Date)

    val thrown = intercept[TierekisteriClientException] {
      val asset = tierekisteriMassTransitStopClient.updateMassTransitStop(objTierekisteriMassTransitStop, None)
    }
    thrown.getMessage should be ("Tierekisteri error: 500: N/A")
  }


  test("Returning only mandatory fields from Tierekisteri should be accepted") {
    val httpClient = MockitoSugar.mock[CloseableHttpClient]
    val trClient =  new TierekisteriMassTransitStopClient(
      Digiroad2Properties.tierekisteriRestApiEndPoint,
      Digiroad2Properties.tierekisteriEnabled,
      httpClient)
    val response = MockitoSugar.mock[CloseableHttpResponse]
    when(response.getStatusLine).thenReturn(new StatusLine {override def getStatusCode: Int = 200
      override def getReasonPhrase: String = "OK"
      override def getProtocolVersion: ProtocolVersion = new ProtocolVersion("HTTP", 1, 1)
    })
    val retval =  "{" +
      "\"valtakunnallinen_id\": 208910,"+
      "\"tie\": 25823,"+
      "\"aosa\": 104,"+
      "\"ajr\": 0,"+
      "\"aet\": 150,"+
      "\"puoli\": \"oikea\","+
      "\"pysakin_tyyppi\": \"kauko\","+
      "\"pikavuoro\": \"ei\","+
      "\"livitunnus\": \"OTHJ208910\","+
      "\"kayttajatunnus\": \"KX123456\","+
      "\"inventointipvm\": \"2013-01-01\""+
      "}"
    when (response.getEntity).thenReturn(new StringEntity(retval))
    when(httpClient.execute(any[HttpGet])).thenReturn(response)
    val stop = trClient.fetchMassTransitStop("OTHJ208910").get
    stop.liviId should be ("OTHJ208910")
    stop.modifiedBy should be ("KX123456")
    stop.roadAddress.road should be (25823)
    stop.roadAddress.track should be (Track.Combined)
    stop.roadAddress.roadPart should be (104)
    stop.roadAddress.addrM should be (150)
    stop.equipments.values.forall(_.value == "ei tietoa") should be (true)
    stop.express should be (false)
  }

  test("Stop type conversions") {
    TierekisteriBusStopMarshaller.findStopType(Seq(2)) should be (StopType.Commuter)
    TierekisteriBusStopMarshaller.findStopType(Seq(3)) should be (StopType.LongDistance)
    TierekisteriBusStopMarshaller.findStopType(Seq(3,2)) should be (StopType.Combined)
    TierekisteriBusStopMarshaller.findStopType(Seq(2,3)) should be (StopType.Combined)
    TierekisteriBusStopMarshaller.findStopType(Seq(1,2,3)) should be (StopType.Combined)
    TierekisteriBusStopMarshaller.findStopType(Seq(5,2,3)) should be (StopType.Unknown)
    TierekisteriBusStopMarshaller.findStopType(Seq(5)) should be (StopType.Virtual)
    TierekisteriBusStopMarshaller.findStopType(Seq(1,2)) should be (StopType.Commuter)
    TierekisteriBusStopMarshaller.findStopType(Seq(1,3)) should be (StopType.LongDistance)
    TierekisteriBusStopMarshaller.findStopType(Seq(1,3,4)) should be (StopType.LongDistance)
    TierekisteriBusStopMarshaller.findStopType(Seq(5,3,4)) should be (StopType.Unknown)
    TierekisteriBusStopMarshaller.findStopType(Seq(1,2,3,4)) should be (StopType.Combined)
    TierekisteriBusStopMarshaller.findStopType(Seq(1,2,3,5)) should be (StopType.Unknown)
    for (x <- 6 to 99) {
      TierekisteriBusStopMarshaller.findStopType(Seq(x)) should be(StopType.Unknown)
    }
  }
  
  def trSpeedLimitDataTest(speedLimitType: TrafficSignType, fldLIIKVAST: String = null, fldNOPRA506: String = null, fldLMTEKSTI: String = null ) = {
    Map("PUOLI" -> "1",
        "OSA" -> "1",
        "LIIKVAST" -> fldLIIKVAST,
        "NOPRA506" -> fldNOPRA506,
        "AJORATA" -> "0",
        "LMNUMERO" -> speedLimitType.TRvalue,
        "TIE" -> "11008",
        "ETAISYYS" -> "15",
        "LMTEKSTI" -> fldLMTEKSTI
      )
  }
}
