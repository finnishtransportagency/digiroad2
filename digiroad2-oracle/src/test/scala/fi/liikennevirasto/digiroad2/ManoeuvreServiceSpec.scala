package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}


class ManoeuvreServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  private def vvhRoadLink(mmlId: Long, municipalityCode: Int, geometry: Seq[Point] = Seq(Point(0, 0), Point(10, 0))) = {
    RoadLink(mmlId, geometry, 10.0, Municipality, 5, TrafficDirection.UnknownDirection, SingleCarriageway, None, None)
  }

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]]))
    .thenReturn(Seq(vvhRoadLink(388562342, 235), vvhRoadLink(388569430, 235), vvhRoadLink(388569418, 235)) )
  when(mockRoadLinkService.getRoadLinksFromVVH(municipality = 235))
    .thenReturn(Seq(vvhRoadLink(123, 235), vvhRoadLink(124, 235), vvhRoadLink(233, 235), vvhRoadLink(234, 235,  Seq(Point(15, 0), Point(20, 0)))))
  when(mockRoadLinkService.getRoadLinkFromVVH(mmlId = 388569406))
    .thenReturn(Some(vvhRoadLink(388569406, 235)))
  when(mockRoadLinkService.getRoadLinkFromVVH(mmlId = 388570174))
    .thenReturn(Some(vvhRoadLink(388570174, 235)))


  val manoeuvreService = new ManoeuvreService(mockRoadLinkService)

  after {
    OracleDatabase.withDynTransaction {
      sqlu"""delete from manoeuvre_element where manoeuvre_id in (select id from manoeuvre where modified_by = 'unittest')""".execute
      sqlu"""delete from manoeuvre where modified_by = 'unittest'""".execute
    }
  }

  test("Get all manoeuvres partially or completely in bounding box") {
    val bounds = BoundingRectangle(Point(373880.25, 6677085), Point(374133, 6677382))
    val manoeuvres = manoeuvreService.getByBoundingBox(bounds, Set(235))
    manoeuvres.length should equal(3)
    val partiallyContainedManoeuvre = manoeuvres.find(_.id == 39561).get
    partiallyContainedManoeuvre.sourceMmlId should equal(388562342)
    partiallyContainedManoeuvre.destMmlId should equal(388569406)
    val completelyContainedManoeuvre = manoeuvres.find(_.id == 97666).get
    completelyContainedManoeuvre.sourceMmlId should equal(388569430)
    completelyContainedManoeuvre.destMmlId should equal(388569418)
  }

  test("Filters out manoeuvres with non-adjacent source and destination links") {
    val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, 123, 124))
    val manoeuvreId2 = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, 233, 234))

    val manoeuvres = manoeuvreService.getByMunicipality(235)

    manoeuvres.exists(_.id == manoeuvreId) should be(true)
    manoeuvres.exists(_.id == manoeuvreId2) should be(false)
  }

  def createManouvre: Manoeuvre = {
    val manoeuvreId = manoeuvreService.createManoeuvre("unittest", NewManoeuvre(Set.empty, Nil, None, 123, 124))

    manoeuvreService.getByMunicipality(235)
      .find(_.id == manoeuvreId)
      .get
  }

  test("Create manoeuvre") {
    val manoeuvre: Manoeuvre = createManouvre

    manoeuvre.sourceMmlId should equal(123)
    manoeuvre.destMmlId should equal(124)
  }

  test("Delete manoeuvre") {
    val manoeuvre: Manoeuvre = createManouvre

    manoeuvreService.deleteManoeuvre("unittest", manoeuvre.id)

    val deletedManouver = manoeuvreService.getByMunicipality(235).find { m =>
      m.id == manoeuvre.id
    }
    deletedManouver should equal(None)
  }

  test("Get source road link id with manoeuvre id") {
    val manoeuvre: Manoeuvre = createManouvre

    val roadLinkId = manoeuvreService.getSourceRoadLinkMmlIdById(manoeuvre.id)

    roadLinkId should equal(123)
  }
}
