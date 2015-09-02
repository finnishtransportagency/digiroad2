package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{StaticQuery => Q}
import slick.jdbc.StaticQuery.interpolation

class ManoeuvreServiceSpec extends FunSuite with Matchers with BeforeAndAfter {

  after {
    OracleDatabase.withDynTransaction {
      sqlu"""delete from manoeuvre_element where manoeuvre_id in (select id from manoeuvre where modified_by = 'unittest')""".execute
      sqlu"""delete from manoeuvre where modified_by = 'unittest'""".execute
    }
  }

  test("Get all manoeuvres partially or completely in bounding box") {
    val bounds = BoundingRectangle(Point(373880.25, 6677085), Point(374133, 6677382))
    val manoeuvres = ManoeuvreService.getByBoundingBox(bounds, Set(235))
    manoeuvres.length should equal(5)
    val partiallyContainedManoeuvre = manoeuvres.find(_.id == 39561).get
    partiallyContainedManoeuvre.sourceMmlId should equal(388562342)
    partiallyContainedManoeuvre.destMmlId should equal(388569406)
    val completelyContainedManoeuvre = manoeuvres.find(_.id == 97666).get
    completelyContainedManoeuvre.sourceMmlId should equal(388569430)
    completelyContainedManoeuvre.destMmlId should equal(388569418)
  }


  def createManouvre: Manoeuvre = {
    val manoeuvreId = ManoeuvreService.createManoeuvre("unittest", NewManoeuvre(7482, 6677, Nil, None))

    val manoeuvre = ManoeuvreService.getByMunicipality(235).find { manoeuvre =>
      manoeuvre.id == manoeuvreId
    }.get
    manoeuvre
  }

  test("Create manoeuvre") {
    val manoeuvre: Manoeuvre = createManouvre

    manoeuvre.sourceRoadLinkId should equal(7482)
    manoeuvre.destRoadLinkId should equal(6677)
  }

  test("Delete manoeuvre") {
    val manoeuvre: Manoeuvre = createManouvre

    ManoeuvreService.deleteManoeuvre("unittest", manoeuvre.id)

    val deletedManouver = ManoeuvreService.getByMunicipality(235).find { m =>
      m.id == manoeuvre.id
    }
    deletedManouver should equal(None)
  }

  test("Get source road link id with manoeuvre id") {
    val manoeuvre: Manoeuvre = createManouvre

    val roadLinkId = ManoeuvreService.getSourceRoadLinkIdById(manoeuvre.id)

    roadLinkId should equal(7482)
  }

}
