//package fi.liikennevirasto.digiroad2.client
//
//import fi.liikennevirasto.digiroad2.Track
//import fi.liikennevirasto.digiroad2.util.RoadAddressRange
//import org.scalatest.{FunSuite, Matchers}
//
//class VKMClientSpec extends FunSuite with Matchers {
//  val client = new VKMClient
//  test("Testi") {
//    val linkIds = Seq("99852c1b-004b-4efe-bdb2-5f80e3ba18c3:2", "732df7e1-ec64-4017-82fe-fa559a9031f7:2")
//    val result = client.fetchRoadAddressesByLinkIds(linkIds)
//    println(result)
//  }
//
//  test("Testi2") {
//    val linkId = "732df7e1-ec64-4017-82fe-fa559a9031f7:2"
//    val result = client.fetchRoadAddressByLrmPosition(linkId, 50.756)
//    println(result)
//  }
//
//  test("Testi3") {
//    val road = 18
//    val startPart = 1
//    val endpart  = 3
//    val range = RoadAddressRange(18, Some(Track(0)), 1, 3, 0, 5904)
//    val startAndEndLinkIds = client.fetchStartAndEndLinkIdForAddrRange(range)
//    val allAddresses = client.fetchLinkIdsBetweenTwoRoadLinks(startAndEndLinkIds.head.linkId, startAndEndLinkIds.last.linkId, 18)
//    println(allAddresses)
//  }
//}
