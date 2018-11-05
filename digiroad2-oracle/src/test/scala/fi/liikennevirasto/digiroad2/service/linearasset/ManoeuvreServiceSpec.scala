package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DummyEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Saturday
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriod, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignCreateAsset, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}


class ManoeuvreServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  private def vvhRoadLink(linkId: Long, municipalityCode: Int, geometry: Seq[Point] = Seq(Point(0, 0), Point(10, 0))) = {
    RoadLink(linkId, geometry, 10.0, Municipality, 5, TrafficDirection.UnknownDirection, SingleCarriageway, None, None)
  }
  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]]))
    .thenReturn(Seq(vvhRoadLink(1611419, 235), vvhRoadLink(1611412, 235), vvhRoadLink(1611410, 235)))
  when(mockRoadLinkService.getRoadLinksFromVVH(municipality = 235))
    .thenReturn(Seq(vvhRoadLink(123, 235), vvhRoadLink(125, 235), vvhRoadLink(124, 235), vvhRoadLink(233, 235), vvhRoadLink(234, 235, Seq(Point(15, 0), Point(20, 0)))))
  when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean]))
    .thenReturn(Seq(vvhRoadLink(1611420, 235), vvhRoadLink(1611411, 235)))

  val manoeuvreService = new ManoeuvreService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val trafficSignService = new TrafficSignService(mockRoadLinkService, mockUserProvider, new DummyEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  after {
    OracleDatabase.withDynTransaction {
      sqlu"""delete from manoeuvre_element where manoeuvre_id in (select id from manoeuvre where modified_by = 'unittest')""".execute
      sqlu"""delete from manoeuvre_validity_period where manoeuvre_id in (select id from manoeuvre where modified_by = 'unittest')""".execute
      sqlu"""delete from manoeuvre where modified_by = 'unittest'""".execute
    }
  }

  test("Get all manoeuvres partially or completely in bounding box") {
    runWithRollback {
      val bounds = BoundingRectangle(Point(373880.25, 6677085), Point(374133, 6677382))
      val manoeuvres = manoeuvreService.getByBoundingBox(bounds, Set(235))
      manoeuvres.length should equal(3)
      val partiallyContainedManoeuvre = manoeuvres.find(_.id == 39561).get
      partiallyContainedManoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(1611419)
      partiallyContainedManoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(1611420)
      partiallyContainedManoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(1611420)
      val completelyContainedManoeuvre = manoeuvres.find(_.id == 97666).get
      completelyContainedManoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(1611412)
      completelyContainedManoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(1611410)
    }
  }

  test("Filters out manoeuvres with non-adjacent source and destination links") {
    runWithRollback {
      val roadLink1 = Seq(
        RoadLink(123, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(124, List(Point(10.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(125, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val roadLink2 = Seq(
        RoadLink(233, List(Point(0.0, 0.0), Point(50.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(234, List(Point(10.0, 0.0), Point(50.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(123, 125, 124), None), roadLink1)
      val manoeuvreId2 = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(233, 234), None),roadLink2)

      val manoeuvres = manoeuvreService.getByMunicipality(235)

      manoeuvres.exists(_.id == manoeuvreId) should be(true)
      manoeuvres.exists(_.id == manoeuvreId2) should be(false)
    }
  }

  test("Filter manoeuvre by source road link in the specified municipality") {
    runWithRollback {
      val roadLink1 = Seq(
        RoadLink(123, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(124, List(Point(10.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(125, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val roadLink2 = Seq(
        RoadLink(1611420, List(Point(5.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(125, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(123, 125, 124), None),roadLink1)
      val manoeuvreId2 = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(1611420, 125), None),roadLink2)

      val manoeuvres = manoeuvreService.getByMunicipality(235)

      manoeuvres.exists(_.id == manoeuvreId) should be(true)
      manoeuvres.exists(_.id == manoeuvreId2) should be(false)
    }
  }

  def createManouvre: Manoeuvre = {
    val roadLink = Seq(
      RoadLink(123, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
      RoadLink(124, List(Point(10.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
    )

    val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, Seq(123, 124), None),roadLink)

    manoeuvreService.getByMunicipality(235)
      .find(_.id == manoeuvreId)
      .get
  }

  test("Create manoeuvre") {
    runWithRollback {
      val manoeuvre: Manoeuvre = createManouvre

      manoeuvre.elements.head.sourceLinkId should equal(123)
      manoeuvre.elements.head.destLinkId should equal(124)
      manoeuvre.validityPeriods should equal(Set(ValidityPeriod(0, 21, Saturday, 30, 45)))
    }
  }

  test("Delete manoeuvre") {
    runWithRollback {
      val manoeuvre: Manoeuvre = createManouvre

      manoeuvreService.deleteManoeuvre("unittest", manoeuvre.id)

      val deletedManouver = manoeuvreService.getByMunicipality(235).find { m =>
        m.id == manoeuvre.id
      }
      deletedManouver should equal(None)
    }
  }

  test("Get source road link id with manoeuvre id") {
    runWithRollback {
      val manoeuvre: Manoeuvre = createManouvre

      val roadLinkId = manoeuvreService.getSourceRoadLinkIdById(manoeuvre.id)

      roadLinkId should equal(123)
    }
  }

  test("Cleanup should remove extra elements") {
    runWithRollback {
      val start = ManoeuvreElement(1, 1, 2, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 4, 0, ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, 2, 3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 3, 8, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 3, 4, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(start, end, intermediates)
      result should have size 4
      result.exists(_.destLinkId == 8) should be(false)
    }
  }

  test("Cleanup should remove loops") {
    runWithRollback {
      val start = ManoeuvreElement(1, 1, 2, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 5, 0, ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, 2, 3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 3, 4, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 4, 2, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(start, end, intermediates)
      result should have size 0
    }
  }

  test("Cleanup should remove forks") {
    runWithRollback {
      val start = ManoeuvreElement(1, 1, 2, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 5, 0, ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, 3, 5, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 2, 3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 2, 5, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(start, end, intermediates)
      result.size > 2 should be(true)
    }
  }

  test("Cleanup should not change working sequence") {
    runWithRollback {
      val start = ManoeuvreElement(1, 1, 2, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 5, 0, ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, 2, 3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 3, 4, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 4, 5, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(start, end, intermediates)
      result should have size 5
      result.filter(_.elementType == ElementTypes.IntermediateElement) should be(intermediates)
    }
  }

  test("Cleanup should produce correct order") {
    runWithRollback {
      val start = ManoeuvreElement(1, 1, 2, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 5, 0, ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, 2, 3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 4, 5, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, 3, 4, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(start, end, intermediates)
      result should have size 5
      result.filter(_.elementType == ElementTypes.IntermediateElement) shouldNot be(intermediates)
      val newIntermediates = result.filter(_.elementType == ElementTypes.IntermediateElement)
      newIntermediates.zip(newIntermediates.tail).forall(e => e._1.destLinkId == e._2.sourceLinkId) should be(true)
      newIntermediates.head.sourceLinkId should be(start.destLinkId)
      newIntermediates.last.destLinkId should be(end.sourceLinkId)
    }
  }

  test("Validate a New Manoeuvre") {
    runWithRollback {
      val roadLinksSeq: Seq[Long] = Seq(123, 124)
      val roadLinksSeq2: Seq[Long] = Seq(123, 124, 125, 126)
      var result: Boolean = false

      val roadLinksNoValid = Seq(vvhRoadLink(123, 235), vvhRoadLink(124, 235, Seq(Point(25, 0), Point(30, 0))))
      val manoeuvreNoValid = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq, None)
      result = manoeuvreService.isValid(manoeuvreNoValid, roadLinksNoValid)
      result should be(false)

      val roadLinksValid = Seq(vvhRoadLink(123, 235), vvhRoadLink(124, 235))
      val manoeuvreValid = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq, None)
      result = manoeuvreService.isValid(manoeuvreValid, roadLinksValid)
      result should be(true)

      val roadLinksValid2 = Seq(vvhRoadLink(123, 235), vvhRoadLink(124, 235), vvhRoadLink(125, 235), vvhRoadLink(126, 235))
      val manoeuvreValid2 = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq2, None)
      result = manoeuvreService.isValid(manoeuvreValid2, roadLinksValid2)
      result should be(true)

      val roadLinksNoValid2 = Seq(vvhRoadLink(123, 235), vvhRoadLink(124, 235, Seq(Point(25, 0), Point(30, 0))), vvhRoadLink(125, 235), vvhRoadLink(126, 235))
      val manoeuvreNoValid2 = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq2, None)
      result = manoeuvreService.isValid(manoeuvreNoValid2, roadLinksNoValid2)
      result should be(false)
    }
  }

  test("Validate an updated Manoeuvre") {
    runWithRollback {
      val bounds = BoundingRectangle(Point(373880.25, 6677085), Point(374133, 6677382))
      val manoeuvres = manoeuvreService.getByBoundingBox(bounds, Set(235))
      val oldId = 39561
      val berforeUpdate = manoeuvres.find(_.id == oldId).get
      berforeUpdate.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(1611419)
      berforeUpdate.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(1611420)
      berforeUpdate.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(1611420)
      berforeUpdate.exceptions should be(Seq(19, 21))
      berforeUpdate.validityPeriods should be(Set(ValidityPeriod(22, 8, ValidityPeriodDayOfWeek.apply(3))))

      val validityPeriod = Set(ValidityPeriod(12, 13, ValidityPeriodDayOfWeek("Sunday"), 30, 15), ValidityPeriod(8, 12, ValidityPeriodDayOfWeek("Saturday"), 0, 10))
      val exceptions = Seq(2, 5)
      val additionalInfo = "Additional Info"

      val manoeuvreUpdate = ManoeuvreUpdates(Option(validityPeriod), Option(exceptions), Option(additionalInfo))

      val newId = manoeuvreService.updateManoeuvre("updater", oldId, manoeuvreUpdate, None)
      val manoeuvresNew = manoeuvreService.getByBoundingBox(bounds, Set(235))
      val manoeuvreUpdated = manoeuvresNew.find(_.id == newId).get
      manoeuvreUpdated.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(1611419)
      manoeuvreUpdated.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(1611420)
      manoeuvreUpdated.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(1611420)
      manoeuvreUpdated.exceptions should be(exceptions)
      manoeuvreUpdated.validityPeriods should be(validityPeriod)
      manoeuvreUpdated.additionalInfo should be (additionalInfo)
    }
  }

  test("create manoeuvre where traffic sign is not turn left"){
    runWithRollback{
      val roadLink = RoadLink(1001, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink1 =  RoadLink(1002, Seq(Point(0.0, 0.0), Point(0.0, 500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 500))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val sourceRoadLink =  RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val properties = Set(
        SimpleProperty("trafficSigns_type", List(PropertyValue("10"))))

      when(mockRoadLinkService.getAdjacent(any[Long], any[Seq[Point]], any[Boolean])).thenReturn(Seq(roadLink, roadLink1))
      when(mockRoadLinkService.pickLeftMost(any[RoadLink], any[Seq[RoadLink]])).thenReturn(roadLink1)
      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))
      val id = trafficSignService.create(IncomingTrafficSign(0, 50, 1000, properties, 2, None), testUser.username, sourceRoadLink)
      val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head

      val manoeuvreId = manoeuvreService.createBasedOnTrafficSign(TrafficSignCreateAsset(assets, sourceRoadLink), false).head
      val manoeuvre = manoeuvreService.find(manoeuvreId).get

      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(1000)
      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(1002)
      manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(1002)
      manoeuvre.createdBy should be ("traffic_sign_generated")
    }
  }

  test("Should throw exception for empty adjacents return"){
    runWithRollback{
      intercept[AssetCreationException] {
        val sourceRoadLink = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
        val properties = Set(
          SimpleProperty("trafficSigns_type", List(PropertyValue("10"))))

        when(mockRoadLinkService.getAdjacent(any[Long], any[Seq[Point]], any[Boolean])).thenReturn(Seq())
        when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))

        val id = trafficSignService.create(IncomingTrafficSign(0, 50, 1000, properties, 3, None), testUser.username, sourceRoadLink)
        val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head
        manoeuvreService.createBasedOnTrafficSign(TrafficSignCreateAsset(assets, sourceRoadLink), false).head
      }
    }
  }

  test("Should throw exception for traffic sign with both directions"){
    runWithRollback{
      intercept[AssetCreationException] {
        val sourceRoadLink = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
        val properties = Set(
          SimpleProperty("trafficSigns_type", List(PropertyValue("10"))))

        when(mockRoadLinkService.getAdjacent(any[Long], any[Seq[Point]], any[Boolean])).thenReturn(Seq())
        when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))

        val id = trafficSignService.create(IncomingTrafficSign(0, 50, 1000, properties, 1, None), testUser.username, sourceRoadLink)
        val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head
        manoeuvreService.createBasedOnTrafficSign(TrafficSignCreateAsset(assets, sourceRoadLink), false).head
      }
    }
  }

  test("create manoeuvre turning right with intermediates"){
    runWithRollback{
      val roadLink = RoadLink(1001, Seq(Point(0.0, 100), Point(0.0, 150)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink1 =  RoadLink(1002, Seq(Point(0.0, 150), Point(0.0, 500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 500))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 =  RoadLink(1003, Seq(Point(0.0, 500), Point(0.0, 1500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 1500))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val sourceRoadLink =  RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val properties = Set( SimpleProperty("trafficSigns_type", List(PropertyValue("11"))))

      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(sourceRoadLink, Some(2))).thenReturn(Seq(Point(0.0, 100)))
      when(mockRoadLinkService.getAdjacent(1000, Seq(Point(0.0, 100)), false)).thenReturn(Seq(roadLink))

      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(roadLink, None)).thenReturn(Seq(Point(0.0, 150)))
      when(mockRoadLinkService.getAdjacent(1001, Seq(Point(0.0, 150)), false)).thenReturn(Seq(roadLink1, roadLink2))

      when(mockRoadLinkService.pickRightMost(roadLink, Seq(roadLink1, roadLink2))).thenReturn(roadLink1)

      val id = trafficSignService.create(IncomingTrafficSign(0, 50, 1000, properties, 2, None), testUser.username, sourceRoadLink)
      val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head

      val manoeuvreId = manoeuvreService.createBasedOnTrafficSign(TrafficSignCreateAsset(assets, sourceRoadLink), false).head
      val manoeuvre = manoeuvreService.find(manoeuvreId).get

      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(1000)
      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(1001)
      manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).get.sourceLinkId should equal(1001)
      manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).get.destLinkId should equal(1002)
      manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(1002)
      manoeuvre.createdBy should be ("traffic_sign_generated")
    }
  }
}
