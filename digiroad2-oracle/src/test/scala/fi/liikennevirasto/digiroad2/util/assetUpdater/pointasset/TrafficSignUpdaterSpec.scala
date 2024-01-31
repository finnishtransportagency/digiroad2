package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, PersistedPointAsset}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TrafficSignUpdaterSpec extends FunSuite with Matchers {

  case class testTrafficSign(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: String,
                                       mValue: Double, floating: Boolean, timeStamp: Long, validityDirection: Int,
                                       bearing: Option[Int], linkSource: LinkGeomSource,
                                       propertyData: Seq[Property] = Seq()) extends PersistedPointAsset {
    override def getValidityDirection: Option[Int] = Some(validityDirection)

    override def getBearing: Option[Int] = bearing
  }

  val roadLinkChangeClient = new RoadLinkChangeClient
  val source: scala.io.BufferedSource = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json")
  val changes: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(source.mkString)

  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]

  val trafficSignService: TrafficSignService = new TrafficSignService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  val updater: TrafficSignUpdater = new TrafficSignUpdater(trafficSignService) {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
  }

  test("Traffic sign outside the road network: validity direction should be set to zero and bearing remain the same") {
    val oldLinkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.TowardsDigitizing,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val asset = testTrafficSign(1, 391936.8899081593, 6672840.334351871, 91, oldLinkId,
      4.773532861864595, false, 0, SideCode.AgainstDigitizing.value, Some(23), NormalLinkInterface,
      Seq(Property(1, "location_specifier", "single_choice", false, Seq(PropertyValue("6", Some("Tie tai katuverkon ulkopuolella, esimerkiksi parkkialueella tai piha-alueella"))))))
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.validityDirection should be(Some(SideCode.DoesNotAffectRoadLink.value))
    corrected.bearing should be(asset.getBearing)
  }

  test("Traffic sign within the road network: validity direction and bearing should be processed like other directional point assets") {
    val oldLinkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.TowardsDigitizing,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val asset = testTrafficSign(1, 391936.8899081593, 6672840.334351871, 91, oldLinkId,
      4.773532861864595, false, 0, SideCode.AgainstDigitizing.value, Some(23), NormalLinkInterface,
      Seq(Property(1, "location_specifier", "single_choice", false, Seq(PropertyValue("1", Some("Väylän oikea puoli"))))))
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.validityDirection should be(Some(SideCode.TowardsDigitizing.value))
    corrected.bearing should not be(asset.bearing) // the validity direction has changed, so bearing should change as well
  }
}
