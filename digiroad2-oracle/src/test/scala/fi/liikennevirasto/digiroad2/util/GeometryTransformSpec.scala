package fi.liikennevirasto.digiroad2.util

import java.net.ConnectException
import java.util.Properties

import fi.liikennevirasto.digiroad2.{Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.impl.client.HttpClientBuilder
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries


class GeometryTransformSpec extends FunSuite with Matchers {

  val transform = new GeometryTransform()
  val connectedToVKM = testConnection
  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  private def testConnection: Boolean = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    val url = properties.getProperty("digiroad2.VKMUrl")
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

  test("URL params should be well-formed") {
    def urlParamMatch(urlParams: String, key: String, value: String) = {
      urlParams.matches("(^|.*&)"+key+"="+value+"(&.*|$)")
    }

    transform.urlParams(Map("tie"-> Option(50), "tieosa" -> None)) should be ("tie=50")
    val allParams = transform.urlParams(Map("tie" -> Option(50), "osa" -> Option(1), "etaisyys" -> Option(234),
      "ajorata" -> Option(Track.Combined.value), "x" -> Option(3874744.2331), "y" -> Option(339484.1234)))

    urlParamMatch(allParams, "tie", "50") should be (true)
    urlParamMatch(allParams, "osa", "1") should be (true)
    urlParamMatch(allParams, "etaisyys", "234") should be (true)
    urlParamMatch(allParams, "ajorata", "0") should be (true)
    urlParamMatch(allParams, "x", "3874744\\.2331") should be (true)
    urlParamMatch(allParams, "y", "339484\\.1234") should be (true)
  }

  test("json should be well-formed") {
    transform.jsonParams(List(Map("tie"-> Option(50), "tieosa" -> None))) should be ("{\"koordinaatit\":[{\"tie\":50}]}")
    transform.jsonParams(List(Map("x" -> Option(3874744.2331), "y" -> Option(339484.1234)))) should be ("{\"koordinaatit\":[{\"x\":3874744.2331,\"y\":339484.1234}]}")
  }

  test("VKM request") {
    assume(connectedToVKM)
    val coord = Point(358813,6684163)
    val roadAddress = transform.coordToAddress(coord, Option(110))
    roadAddress.road should be (110)
    roadAddress.roadPart should be >0
  }

  test("Multiple VKM requests") {
    assume(connectedToVKM)
    val coords = Seq(Point(358813,6684163), Point(358832,6684148), Point(358770,6684181))
    val roadAddresses = transform.coordsToAddresses(coords, Option(110))
    roadAddresses.foreach(_.road should be (110))
    roadAddresses.foreach(_.roadPart should be >0)
    roadAddresses.foreach(_.municipalityCode.isEmpty should be (false))
    roadAddresses.foreach(_.deviation.getOrElse(0.0) should be >20.0)
  }

  test("missing results for query") {
    assume(connectedToVKM)
    val coord = Point(385879,6671604)
    val thrown = intercept[VKMClientException] {
      transform.coordToAddress(coord, Option(1), Option(0))
    }
    thrown.getMessage should be ("VKM error: Kohdetta ei löytynyt.")
  }

  test("missing results for multiple query") {
    assume(connectedToVKM)
    val coords = Seq(Point(385879,6671604), Point(385878,6671604), Point(385880,6671604))
    val thrown = intercept[VKMClientException] {
      transform.coordsToAddresses(coords, Option(1), Option(0))
    }
    thrown.getMessage should be ("VKM error: Kohdetta ei löytynyt.")
  }

  test("Resolve location on left") {
    assume(connectedToVKM)
    val coord = Point(358627.87728143384,6684191.235849902)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 104, Option(110))
    roadAddress.road should be (110)
    roadAddress.track should be (Track.LeftSide)
    roadSide should be (RoadSide.Left)
  }

  test("Resolve location on right") {
    assume(connectedToVKM)
    val coord = Point(358637.366678646,6684210.86903845)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 292, Option(110))
    roadAddress.road should be (110)
    roadAddress.track should be (Track.RightSide)
    roadSide should be (RoadSide.Right)
  }

  test("Resolve location on two-way road") {
    assume(connectedToVKM)
    val coord = Point(358345,6684305)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 110, Option(110))
    roadAddress.road should be (110)
    roadAddress.track should be (Track.Combined)
    roadSide should be (RoadSide.Left)
  }

  // The VKM result has changed
  ignore("end of road part") {
    assume(connectedToVKM)
    val coord = Point(385879,6671604)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 190, Option(1))
    roadAddress.road should be (1)
    roadAddress.track should be (Track.LeftSide)
    roadSide should be (RoadSide.Unknown)
  }

  test("missing results for location resolve") {
    assume(connectedToVKM)
    val coord = Point(385879,6671604)
    val thrown = intercept[VKMClientException] {
      transform.resolveAddressAndLocation(coord, 190, Option(1), Option(0))
    }
    thrown.getMessage should be ("VKM error: Kohdetta ei löytynyt.")

  }

  test("Resolve location close to pedestrian walkway") {
    assume(connectedToVKM)
    val coord = Point(378847,6677884)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 270)
    roadAddress.road should be (110)
  }

  test("Resolve location close to pedestrian walkway, allow pedestrian as result") {
    assume(connectedToVKM)
    val coord = Point(378847,6677884)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 270, includePedestrian = Option(true))
    roadAddress.road should be >= (69999)
  }

  test("Resolve location far from pedestrian walkway, allow pedestrian as result") {
    assume(connectedToVKM)
    val coord = Point(378817,6677914)
    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(coord, 270, includePedestrian = Option(true))
    roadAddress.road should be (110)
  }

  test("Resolve road address -> coordinate") {
    assume(connectedToVKM)
    val address = RoadAddress(None, 110, 1, Track.Combined, 3697, None)
    val (coord) = transform.addressToCoords(address)
    coord.size should be (1)
    coord.head.x shouldNot be (0.0)
    coord.head.y shouldNot be (0.0)
    val (newAddress, side) = transform.resolveAddressAndLocation(coord.head, 270, includePedestrian = Option(true))
    newAddress.road should be (address.road)
    newAddress.roadPart should be (address.roadPart)
    newAddress.track.value should be (address.track.value)
    Math.abs(newAddress.mValue - address.mValue) < 5 should be (true)
  }

  test("Resolve address and location from Viite"){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(4)

      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 2, 6000, null, 0.000, 100.000)""".execute
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 2, 6000, null, 100.000, 200.000)""".execute
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(2)}, 2, 6000, null, 200.000, 350.000)""".execute
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(3)}, 2, 6000, null, 350.000, 500.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (600, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                      start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
              values (601, 101, 1, 0, 5, 150, 250, ${lrmPositionsIds(1)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                      , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                      , NULL)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                      start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (602, 102, 1, 0, 5, 250, 400, ${lrmPositionsIds(2)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                     , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                     , NULL)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                      start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (603, 103, 1, 0, 5, 400, 550, ${lrmPositionsIds(3)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                     , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                     , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.BothDirections)
      roadAddress.length should be(4)
      roadAddress.map( _.id ).sorted should be (Seq (600, 601, 602, 603))

    }
  }

  test("Resolve address and location from Viite with road address with exceeding lrm_position bounds "){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)

      sqlu"""insert into lrm_position(id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 6000, null, 100.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (600, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.Unknown)
      roadAddress.length should be(1)
      roadAddress.map( _.id ).sorted should be (Seq (600))

    }
  }

  test("Resolve address and location from Viite with road address with lrm_position measure bigger"){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)

      sqlu"""insert into lrm_position(id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 6000, null, 0.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (600, 100, 1, 0, 5, 150, 500, ${lrmPositionsIds(0)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.Unknown)
      roadAddress.length should be(1)
      roadAddress.map( _.id ).sorted should be (Seq (600))

    }
  }

  test("Resolve address and location from Viite with no matching side code "){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)
     //Side Code TowardsDigitizing
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 2, 6000, null, 100.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (600, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.AgainstDigitizing)
      roadAddress.length should be(0)

    }
  }


  test("Resolve address and location from Viite with matching side code "){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)
      //Side Code TowardsDigitizing
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 2, 6000, null, 100.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values (600, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, '2015-01-01', NULL, 'test', '2015-01-01', 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.TowardsDigitizing)
      roadAddress.length should be(1)
      roadAddress.map( _.id ).sorted should be (Seq (600))

    }
  }
}
