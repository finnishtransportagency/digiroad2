package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.Track
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.mockito.ArgumentMatchers.any
import org.scalatest.{FunSuite, Matchers}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

class RoadAddressServiceSpec extends FunSuite with Matchers{
  val mockViiteClient = MockitoSugar.mock[SearchViiteClient]
  val testRoadAddressService = new RoadAddressService(mockViiteClient)

  test("LRM calculation on Road Address") {
    val linkId = LinkIdGenerator.generateRandom()
    val towards = RoadAddressForLink(1L, 1L, 1L, Track.RightSide, 100, 110, None, None, linkId, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    val against = RoadAddressForLink(1L, 1L, 1L, Track.RightSide, 100, 110, None, None, linkId, 1.5, 11.4, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    towards.addressMValueToLRM(100L).get should be (1.5 +- .001)
    against.addressMValueToLRM(100L).get should be (11.4 +- .001)
    towards.addressMValueToLRM(110L).get should be (11.4 +- .001)
    against.addressMValueToLRM(110L).get should be (1.5 +- .001)
    towards.addressMValueToLRM(103L).get should be (4.470 +- .001)
    against.addressMValueToLRM(103L).get should be (8.430 +- .001)
    towards.addressMValueToLRM(99L)  should be (None)
    towards.addressMValueToLRM(111L) should be (None)
    against.addressMValueToLRM(99L)  should be (None)
    against.addressMValueToLRM(111L) should be (None)
  }

  test("When fetching RoadAddress info with more than 1000 linkIds, then returned batches should be combined into one sequence") {
    val mockRoadAddresses: Seq[RoadAddressForLink] = List.fill(1000)(RoadAddressForLink(1L, 1L, 1L, Track.RightSide, 100, 110, None, None, LinkIdGenerator.generateRandom(), 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None))
    val linkIds: Seq[String] = List.fill(2000)(LinkIdGenerator.generateRandom())
    when(mockViiteClient.fetchAllByLinkIds(any[Seq[String]])).thenReturn(mockRoadAddresses)
    val roadAddressCollection = testRoadAddressService.getAllByLinkIds(linkIds)
    roadAddressCollection.size should be(2000)
  }
}
