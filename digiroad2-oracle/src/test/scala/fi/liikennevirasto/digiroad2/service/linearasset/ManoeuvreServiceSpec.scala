package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.PostGISUserProvider
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Saturday
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriod, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}


class ManoeuvreServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  private def roadLinkTemplate(linkId: String, municipalityCode: Int, geometry: Seq[Point] = Seq(Point(0, 0), Point(10, 0))) = {
    RoadLink(linkId, geometry, 10.0, Municipality, 5, TrafficDirection.UnknownDirection, SingleCarriageway, None, None)
  }
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))

  val linkId1 = "9d544f86-94f6-4b92-a5df-60d2086b0215:1"
  val linkId2 = "8c455954-16fe-4f99-93c4-41a25edc47ba:1"
  val linkId3 = "74a00083-c652-4f91-bb38-8a0bbd972d22:1"
  val linkId4 = "39a9cb55-4f30-43aa-b3d8-542613c668a3:1"
  val linkId5: String = LinkIdGenerator.generateRandom()
  val linkId6: String = LinkIdGenerator.generateRandom()
  val linkId7: String = LinkIdGenerator.generateRandom()
  val linkId8: String = LinkIdGenerator.generateRandom()
  val linkId9: String = LinkIdGenerator.generateRandom()
  val linkId10: String = LinkIdGenerator.generateRandom()

  val startLinkId: String = LinkIdGenerator.generateRandom()
  val intermediateLinkId1: String = LinkIdGenerator.generateRandom()
  val intermediateLinkId2: String = LinkIdGenerator.generateRandom()
  val intermediateLinkId3: String = LinkIdGenerator.generateRandom()
  val endLinkId: String = LinkIdGenerator.generateRandom()

  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]],any[Boolean]))
    .thenReturn(Seq(
      roadLinkTemplate(linkId1, 235),
      roadLinkTemplate(linkId2, 235),
      roadLinkTemplate(linkId3, 235)))
  when(mockRoadLinkService.getRoadLinksByMunicipalityUsingCache(municipality = 235))
    .thenReturn(Seq(
      roadLinkTemplate(linkId5, 235),
      roadLinkTemplate(linkId7, 235),
      roadLinkTemplate(linkId6, 235),
      roadLinkTemplate(linkId9, 235),
      roadLinkTemplate(linkId10, 235, Seq(Point(15, 0), Point(20, 0)))))
  when(mockRoadLinkService.getRoadLinksByLinkIds(any[Set[String]], any[Boolean]))
    .thenReturn(Seq(roadLinkTemplate(linkId4, 235), roadLinkTemplate("a1b6659b-41ac-4cc7-9367-e742d7f9216f:1", 235)))

  val manoeuvreService = new ManoeuvreService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val mockProhibitionService = MockitoSugar.mock[ProhibitionService]

  val trafficSignService = new TrafficSignService(mockRoadLinkService, new DummyEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  after {
    PostGISDatabase.withDynTransaction {
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
      partiallyContainedManoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(linkId1)
      partiallyContainedManoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(linkId4)
      partiallyContainedManoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(linkId4)
      val completelyContainedManoeuvre = manoeuvres.find(_.id == 97666).get
      completelyContainedManoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(linkId2)
      completelyContainedManoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(linkId3)
    }
  }

  test("Filters out manoeuvres with non-adjacent source and destination links") {
    runWithRollback {
      val roadLink1 = Seq(
        RoadLink(linkId5, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId6, List(Point(10.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val roadLink2 = Seq(
        RoadLink(linkId9, List(Point(0.0, 0.0), Point(50.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId10, List(Point(10.0, 0.0), Point(50.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(linkId5, linkId7, linkId6), None, false), roadLink1)
      val manoeuvreId2 = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(linkId9, linkId10), None, false),roadLink2)

      val manoeuvres = manoeuvreService.getByMunicipality(235)

      manoeuvres.exists(_.id == manoeuvreId) should be(true)
      manoeuvres.exists(_.id == manoeuvreId2) should be(false)
    }
  }

  test("Filter manoeuvre by source road link in the specified municipality") {
    runWithRollback {
      val roadLink1 = Seq(
        RoadLink(linkId5, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId6, List(Point(10.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val roadLink2 = Seq(
        RoadLink(linkId4, List(Point(5.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
        RoadLink(linkId7, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
      )

      val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(linkId5, linkId7, linkId6), None, false),roadLink1)
      val manoeuvreId2 = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(linkId4, linkId7), None, false),roadLink2)

      val manoeuvres = manoeuvreService.getByMunicipality(235)

      manoeuvres.exists(_.id == manoeuvreId) should be(true)
      manoeuvres.exists(_.id == manoeuvreId2) should be(false)
    }
  }

  def createManouvre: Manoeuvre = {
    val roadLink = Seq(
      RoadLink(linkId5, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None),
      RoadLink(linkId6, List(Point(10.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
    )

    val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, Seq(linkId5, linkId6), None, false),roadLink)

    manoeuvreService.getByMunicipality(235)
      .find(_.id == manoeuvreId)
      .get
  }

  test("Create manoeuvre") {
    runWithRollback {
      val manoeuvre: Manoeuvre = createManouvre

      manoeuvre.elements.head.sourceLinkId should equal(linkId5)
      manoeuvre.elements.head.destLinkId should equal(linkId6)
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

      roadLinkId should equal(linkId5)
    }
  }

  test("Cleanup should remove extra elements") {
    runWithRollback {
      val randomLinkId = LinkIdGenerator.generateRandom()
      val start = ManoeuvreElement(1, startLinkId, intermediateLinkId1, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, intermediateLinkId3, "", ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, intermediateLinkId1, intermediateLinkId2, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId2, randomLinkId, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId2, intermediateLinkId3, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(Some(start), Some(end), intermediates)
      result should have size 4
      result.exists(_.destLinkId == randomLinkId) should be(false)
    }
  }

  test("Cleanup should remove loops") {
    runWithRollback {
      val start = ManoeuvreElement(1, startLinkId, intermediateLinkId1, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, endLinkId, "", ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, intermediateLinkId1, intermediateLinkId2, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId2, intermediateLinkId3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId3, intermediateLinkId1, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(Some(start), Some(end), intermediates)
      result should have size 0
    }
  }

  test("Cleanup should remove forks") {
    runWithRollback {
      val start = ManoeuvreElement(1, startLinkId, intermediateLinkId1, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, endLinkId, "", ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, intermediateLinkId2, endLinkId, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId1, intermediateLinkId2, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId1, endLinkId, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(Some(start), Some(end), intermediates)
      result.size > 2 should be(true)
    }
  }

  test("Cleanup should not change working sequence") {
    runWithRollback {
      val start = ManoeuvreElement(1, startLinkId, intermediateLinkId1, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, endLinkId, "", ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, intermediateLinkId1, intermediateLinkId2, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId2, intermediateLinkId3, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId3, endLinkId, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(Some(start), Some(end), intermediates)
      result should have size 5
      result.filter(_.elementType == ElementTypes.IntermediateElement) should be(intermediates)
    }
  }

  test("Cleanup should produce correct order") {
    runWithRollback {
      val start = ManoeuvreElement(1, startLinkId, intermediateLinkId1, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, endLinkId, "", ElementTypes.LastElement)
      val intermediates = Seq(ManoeuvreElement(1, intermediateLinkId1, intermediateLinkId2, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId3, endLinkId, ElementTypes.IntermediateElement),
        ManoeuvreElement(1, intermediateLinkId2, intermediateLinkId3, ElementTypes.IntermediateElement))
      val result = manoeuvreService.cleanChain(Some(start), Some(end), intermediates)
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
      val roadLinksSeq: Seq[String] = Seq(linkId5, linkId6)
      val roadLinksSeq2: Seq[String] = Seq(linkId5, linkId6, linkId7, linkId8)
      var result: Boolean = false

      val roadLinksNoValid = Seq(roadLinkTemplate(linkId5, 235), roadLinkTemplate(linkId6, 235, Seq(Point(25, 0), Point(30, 0))))
      val manoeuvreNoValid = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq, None, false)
      result = manoeuvreService.isValid(manoeuvreNoValid, roadLinksNoValid)
      result should be(false)

      val roadLinksValid = Seq(roadLinkTemplate(linkId5, 235), roadLinkTemplate(linkId6, 235))
      val manoeuvreValid = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq, None, false)
      result = manoeuvreService.isValid(manoeuvreValid, roadLinksValid)
      result should be(true)

      val roadLinksValid2 = Seq(roadLinkTemplate(linkId5, 235), roadLinkTemplate(linkId6, 235), roadLinkTemplate(linkId7, 235), roadLinkTemplate(linkId8, 235))
      val manoeuvreValid2 = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq2, None, false)
      result = manoeuvreService.isValid(manoeuvreValid2, roadLinksValid2)
      result should be(true)

      val roadLinksNoValid2 = Seq(roadLinkTemplate(linkId5, 235), roadLinkTemplate(linkId6, 235, Seq(Point(25, 0), Point(30, 0))), roadLinkTemplate(linkId7, 235), roadLinkTemplate(linkId8, 235))
      val manoeuvreNoValid2 = NewManoeuvre(Set.empty, Nil, None, roadLinksSeq2, None, false)
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
      berforeUpdate.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(linkId1)
      berforeUpdate.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(linkId4)
      berforeUpdate.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(linkId4)
      berforeUpdate.exceptions.sorted should be(Seq(19, 21))
      berforeUpdate.validityPeriods should be(Set(ValidityPeriod(22, 8, ValidityPeriodDayOfWeek.apply(3))))

      val validityPeriod = Set(ValidityPeriod(12, 13, ValidityPeriodDayOfWeek("Sunday"), 30, 15), ValidityPeriod(8, 12, ValidityPeriodDayOfWeek("Saturday"), 0, 10))
      val exceptions = Seq(2, 5)
      val additionalInfo = "Additional Info"

      val manoeuvreUpdate = ManoeuvreUpdates(Option(validityPeriod), Option(exceptions), Option(additionalInfo), Option(false))

      val newId = manoeuvreService.updateManoeuvre("updater", oldId, manoeuvreUpdate, None)
      val manoeuvresNew = manoeuvreService.getByBoundingBox(bounds, Set(235))
      val manoeuvreUpdated = manoeuvresNew.find(_.id == newId).get
      manoeuvreUpdated.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(linkId1)
      manoeuvreUpdated.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(linkId4)
      manoeuvreUpdated.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(linkId4)
      manoeuvreUpdated.exceptions.sorted should be(exceptions)
      manoeuvreUpdated.validityPeriods should be(validityPeriod)
      manoeuvreUpdated.additionalInfo should be (additionalInfo)
    }
  }

  test("create manoeuvre where traffic sign is not turn left"){
    runWithRollback{
      val roadLink = RoadLink(linkId6, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink1 =  RoadLink(linkId7, Seq(Point(0.0, 0.0), Point(0.0, 500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 500))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val sourceRoadLink =  RoadLink(linkId5, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val properties = Set(
        SimplePointAssetProperty("trafficSigns_type", List(PropertyValue("10"))))

      when(mockRoadLinkService.getAdjacent(any[String], any[Seq[Point]], any[Boolean])).thenReturn(Seq(roadLink, roadLink1))
      when(mockRoadLinkService.pickLeftMost(any[RoadLink], any[Seq[RoadLink]])).thenReturn(roadLink1)
      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))
      val id = trafficSignService.create(IncomingTrafficSign(0, 50, linkId5, properties, 2, None), testUser.username, sourceRoadLink)
      val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head

      val manoeuvreId = manoeuvreService.createBasedOnTrafficSign(TrafficSignInfo(assets.id, assets.linkId, assets.validityDirection, NoLeftTurn.OTHvalue, sourceRoadLink), false).head
      val manoeuvre = manoeuvreService.find(manoeuvreId).get

      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(linkId5)
      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(linkId7)
      manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(linkId7)
      manoeuvre.createdBy should be ("traffic_sign_generated")
    }
  }

  test("Should throw exception for empty adjacents return"){
    runWithRollback{
      intercept[ManoeuvreCreationException] {
        val sourceRoadLink = RoadLink(linkId5, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
        val properties = Set(
          SimplePointAssetProperty("trafficSigns_type", List(PropertyValue("10"))))

        when(mockRoadLinkService.getAdjacent(any[String], any[Seq[Point]], any[Boolean])).thenReturn(Seq())
        when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))

        val id = trafficSignService.create(IncomingTrafficSign(0, 50, linkId5, properties, 3, None), testUser.username, sourceRoadLink)
        val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head
        manoeuvreService.createBasedOnTrafficSign(TrafficSignInfo(assets.id, assets.linkId, assets.validityDirection, NoLeftTurn.OTHvalue, sourceRoadLink), false).head
      }
    }
  }

  test("Should throw exception for traffic sign with both directions"){
    runWithRollback{
      intercept[ManoeuvreCreationException] {
        val sourceRoadLink = RoadLink(linkId5, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
        val properties = Set(
          SimplePointAssetProperty("trafficSigns_type", List(PropertyValue("10"))))

        when(mockRoadLinkService.getAdjacent(any[String], any[Seq[Point]], any[Boolean])).thenReturn(Seq())
        when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))

        val id = trafficSignService.create(IncomingTrafficSign(0, 50, linkId5, properties, 1, None), testUser.username, sourceRoadLink)
        val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head
        manoeuvreService.createBasedOnTrafficSign(TrafficSignInfo(assets.id, assets.linkId, assets.validityDirection, NoLeftTurn.OTHvalue, sourceRoadLink), false).head
      }
    }
  }

  test("create manoeuvre turning right with intermediates"){
    runWithRollback{
      val roadLink = RoadLink(linkId6, Seq(Point(0.0, 100), Point(0.0, 150)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink1 =  RoadLink(linkId7, Seq(Point(0.0, 150), Point(0.0, 500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 500))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 =  RoadLink(linkId8, Seq(Point(0.0, 500), Point(0.0, 1500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 1500))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val sourceRoadLink =  RoadLink(linkId5, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val properties = Set(SimplePointAssetProperty("trafficSigns_type", List(PropertyValue("11"))))

      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(sourceRoadLink, Some(2))).thenReturn(Seq(Point(0.0, 100)))
      when(mockRoadLinkService.getAdjacent(linkId5, Seq(Point(0.0, 100)), false)).thenReturn(Seq(roadLink))

      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(roadLink, None)).thenReturn(Seq(Point(0.0, 150)))
      when(mockRoadLinkService.getAdjacent(linkId6, Seq(Point(0.0, 150)), false)).thenReturn(Seq(roadLink1, roadLink2))

      when(mockRoadLinkService.pickRightMost(roadLink, Seq(roadLink1, roadLink2))).thenReturn(roadLink1)

      val id = trafficSignService.create(IncomingTrafficSign(0, 50, linkId5, properties, 2, None), testUser.username, sourceRoadLink)
      val assets = trafficSignService.getPersistedAssetsByIds(Set(id)).head

      val manoeuvreId = manoeuvreService.createBasedOnTrafficSign(TrafficSignInfo(assets.id, assets.linkId, assets.validityDirection, NoRightTurn.OTHvalue, sourceRoadLink), false).head
      val manoeuvre = manoeuvreService.find(manoeuvreId).get

      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.sourceLinkId should equal(linkId5)
      manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).get.destLinkId should equal(linkId6)
      manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).get.sourceLinkId should equal(linkId6)
      manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).get.destLinkId should equal(linkId7)
      manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).get.sourceLinkId should equal(linkId7)
      manoeuvre.createdBy should be ("traffic_sign_generated")
    }
  }

  test("Given a manoeuvre on worklist; When the manoeuvre is expired and removed from worklist; It should not be fetched or appear on worklist") {
    val roadLink1 = RoadLink(linkId5, Seq(Point(0.0, 0.0), Point(0.0, 100)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink2 = RoadLink(linkId6, Seq(Point(0.0, 0.0), Point(0.0, 150)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 500))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink3 = RoadLink(linkId7, Seq(Point(0.0, 0.0), Point(0.0, 500)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 500))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    RoadLink(linkId7, List(Point(20.0, 0.0), Point(25.0, 0.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None)
    runWithRollback {
      when(mockRoadLinkService.getAdjacent(any[String], any[Seq[Point]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickLeftMost(any[RoadLink], any[Seq[RoadLink]])).thenReturn(roadLink1)
      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0.0, 100)))

      val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, Seq(linkId5, linkId7, linkId6), None, false), Seq(roadLink1, roadLink2, roadLink3))
      val manoeuvre = manoeuvreService.find(manoeuvreId).get

      manoeuvreService.insertManoeuvresToWorkList(Seq(manoeuvre))
      val worklistimes = manoeuvreService.getManoeuvreWorkList()
      worklistimes.size should be(1)
      worklistimes.head.assetId should be(manoeuvreId)

      manoeuvreService.expireManoeuvreByIds(Set(manoeuvreId), "unittest")
      manoeuvreService.deleteManoeuvreWorkListItems(Set(manoeuvreId))

      val manoeuvreExpired = manoeuvreService.find(manoeuvreId)
      manoeuvreExpired should be(None)

      val worklistItemsDeleted = manoeuvreService.getManoeuvreWorkList()
      worklistItemsDeleted.size should be(0)
    }
  }
}
