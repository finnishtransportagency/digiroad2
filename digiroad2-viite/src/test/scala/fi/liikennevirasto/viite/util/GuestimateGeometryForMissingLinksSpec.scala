package fi.liikennevirasto.viite.util


import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.viite.RoadType
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

/**
  * Created by alapeijario on 14.9.2017.
  */
class GuestimateGeometryForMissingLinksSpec extends FunSuite with Matchers {
  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
  private val guessGeom= new GuestimateGeometryForMissingLinks
  private val roadA1= RoadAddress(1, 19438455, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 0L, 10L, Some(DateTime.parse("1901-01-01")),
    None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0,0),Point(0,10)), LinkGeomSource.NormalLinkInterface, 8)
  private val roadA2 = RoadAddress(2, 19438455, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 10L, 20L, Some(DateTime.parse("1901-01-01")),
    None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0,10),Point(0,20)), LinkGeomSource.NormalLinkInterface, 8)
  private val roadA3=RoadAddress(3, 19438455, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 20L, 30L, Some(DateTime.parse("1901-01-01")),
    None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0,20),Point(0,30)), LinkGeomSource.NormalLinkInterface, 8)
  private val roadA4=RoadAddress(4, 19438455, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 30L, 40L, Some(DateTime.parse("1901-01-01")),
    None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0,30),Point(0,40)), LinkGeomSource.NormalLinkInterface, 8)
  private val roadA5=RoadAddress(5, 19438455, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 40L, 50L, Some(DateTime.parse("1901-01-01")),
    None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0,40),Point(0,50)), LinkGeomSource.NormalLinkInterface, 8)


  test("Missing one link midle") {
    val missingGeom = Seq(roadA2).map(x=>x.copy(geometry=Seq.empty[Point]))
    val addressWithGeom = Seq(roadA1,roadA3)
    val links = guessGeom.guestimateGeometry(missingGeom, addressWithGeom)
    val link1= links.filter(x=>x.id==1).head
    val link2= links.filter(x=>x.id==2).head
    val link3= links.filter(x=>x.id==3).head
    link1.geometry should be (Seq(Point(0,0),Point(0,10)))
    link2.geometry should be (Seq(Point(0,10),Point(0,20)))
    link3.geometry should be (Seq(Point(0,20),Point(0,30)))
  }

  test("Missing 3 adjacent middle links") {
    val missingGeom = Seq(roadA2,roadA3,roadA4).map(x=>x.copy(geometry=Seq.empty[Point]))
    val addressWithGeom = Seq(roadA1,roadA5)
    val links = guessGeom.guestimateGeometry(missingGeom, addressWithGeom)
    val link1= links.filter(x=>x.id==1).head
    val link2= links.filter(x=>x.id==2).head
    val link3= links.filter(x=>x.id==3).head
    val link4= links.filter(x=>x.id==4).head
    val link5= links.filter(x=>x.id==5).head
    link1.geometry should be (Seq(Point(0,0),Point(0,10)))
    link2.geometry should be (Seq(Point(0,10),Point(0,20)))
    link3.geometry should be (Seq(Point(0,20),Point(0,30)))
    link4.geometry should be (Seq(Point(0,30),Point(0,40)))
    link5.geometry should be (Seq(Point(0,40),Point(0,50)))
  }


  test("Missing 3 adjacent links @ end") {
    val missingGeom = Seq(roadA4,roadA2,roadA3).map(x=>x.copy(geometry=Seq.empty[Point]))
    val addressWithGeom = Seq(roadA1)
    val links = guessGeom.guestimateGeometry(missingGeom, addressWithGeom)
    val link1= links.filter(x=>x.id==1).head
    val link2= links.filter(x=>x.id==2).head
    val link3= links.filter(x=>x.id==3).head
    val link4= links.filter(x=>x.id==4).head
    link1.geometry should be (Seq(Point(0,0),Point(0,10)))
    link2.geometry should be (Seq(Point(0,10),Point(0,20)))
    link3.geometry should be (Seq(Point(0,20),Point(0,30)))
    link4.geometry should be (Seq(Point(0,30),Point(0,40)))
  }

  test("Missing 3 adjacent links @ start") {
    val missingGeom = Seq(roadA1,roadA2,roadA3).map(x=>x.copy(geometry=Seq.empty[Point]))
    val addressWithGeom = Seq(roadA4)
    val links = guessGeom.guestimateGeometry(missingGeom, addressWithGeom)
    val link1= links.filter(x=>x.id==1).head
    val link2= links.filter(x=>x.id==2).head
    val link3= links.filter(x=>x.id==3).head
    val link4= links.filter(x=>x.id==4).head
    link1.geometry should be (Seq(Point(0,0),Point(0,10)))
    link2.geometry should be (Seq(Point(0,10),Point(0,20)))
    link3.geometry should be (Seq(Point(0,20),Point(0,30)))
    link4.geometry should be (Seq(Point(0,30),Point(0,40)))
  }


  test("force geometry forming to recursion") {
    val missingGeom = Seq(roadA1,roadA2,roadA3,roadA5).map(x=>x.copy(geometry=Seq.empty[Point]))
    val addressWithGeom = Seq(roadA4)
    val links = guessGeom.guestimateGeometry(missingGeom, addressWithGeom)
    val link1= links.filter(x=>x.id==1).head
    val link2= links.filter(x=>x.id==2).head
    val link3= links.filter(x=>x.id==3).head
    val link4= links.filter(x=>x.id==4).head
    val link5= links.filter(x=>x.id==5).head
    link1.geometry should be (Seq(Point(0,0),Point(0,10)))
    link2.geometry should be (Seq(Point(0,10),Point(0,20)))
    link3.geometry should be (Seq(Point(0,20),Point(0,30)))
    link4.geometry should be (Seq(Point(0,30),Point(0,40)))
    link5.geometry should be (Seq(Point(0,40),Point(0,50)))
  }
}
