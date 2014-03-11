package fi.liikennevirasto.digiroad2.asset.oracle

import org.scalatest._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import org.joda.time.{LocalDate, DateTime}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.mock.MockitoSugar
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import fi.liikennevirasto.digiroad2.mtk.Point
import fi.liikennevirasto.digiroad2.asset.RoadLink
import org.mockito.Mockito._
import org.mockito.Matchers._
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.DataFixture._
import fi.liikennevirasto.digiroad2.mtk.Point
import scala.Some
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import fi.liikennevirasto.digiroad2.user.Configuration
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.asset.RoadLink


class RoadlinkProviderSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll
  with MockitoSugar {

  val startDate: DateTime = new DateTime(2013, 10, 10, 0, 0, 0, 0)

  val roadlink1 = MtkRoadLink(666L, startDate, None, 99999,
                              List(Point(100001.001, 2999997.002, 3.003), Point(100002.0, 2999998.0, 4.0)))
  val roadlink2 = MtkRoadLink(667L, startDate, None, 99999,
                              List(Point(100002.001, 2999998.002, 4.003), Point(100003.0, 2999999.0, 5.0)))
  val roadlink3 = MtkRoadLink(668L, startDate, None, 99998,
                              List(Point(100003.001, 2999999.002, 3.003), Point(100012.0, 3000015.0, 48.0)))
  val roadlink4 = MtkRoadLink(669L, startDate, None, 99998,
                              List(Point(100004.001, 3000000.002, 3.003), Point(100022.0, 3000016.0, 64.0)))

  val user99999 = User(
    id = 1,
    username = "User",
    configuration = Configuration(authorizedMunicipalities = Set(99999)))
  val user99998 = User(
    id = 2,
    username = "User",
    configuration = Configuration(authorizedMunicipalities = Set(99998)))
  val user99995 = User(
    id = 3,
    username = "User",
    configuration = Configuration(authorizedMunicipalities = Set(99995)))
  val provider = new OracleSpatialAssetProvider(new OracleUserProvider)
  val ds = OracleDatabase.initDataSource
  val mockedProvider = mock[NodeProvider]
  val originalProvider = RoadlinkProvider.roadNodeProvider

  before {
    RoadlinkProvider.roadNodeProvider = mockedProvider
    reset(mockedProvider)
  }

  override def afterAll() {
    RoadlinkProvider.roadNodeProvider = originalProvider
  }

  def cleanDbForMunicipalities(municipality: String): Int = {
    Database.forDataSource(ds).withDynSession {
      (Q.u + s"delete from road_link where municipality_number in ($municipality)").first()
    }
  }

  it must "be created if nonexistent (insert)" in {
    cleanDbForMunicipalities("99999")
    provider.getRoadLinks(user99999) must equal(List())
    provider.updateRoadLinks(List(roadlink1, roadlink2))

    provider.getRoadLinks(user99999).sortBy(x => x.id) must equal(
      List(RoadLink(id = 666L, lonLat = Vector((100001.001, 2999997.002), (100002.0, 2999998.0)), municipalityNumber = 99999),
           RoadLink(id = 667L, lonLat = Vector((100002.001, 2999998.002), (100003.0, 2999999.0)), municipalityNumber = 99999)))
    verify(mockedProvider).createRoadNode(roadlink1.points.head)
    verify(mockedProvider).createRoadNode(roadlink1.points.last)
    verify(mockedProvider).createRoadNode(roadlink2.points.head)
    verify(mockedProvider).createRoadNode(roadlink2.points.head)
  }

  it must "be updated if exists" in {
    cleanDbForMunicipalities("99998")
    provider.getRoadLinks(user99998) must equal(List())
    when(mockedProvider.createRoadNode(roadlink3.points.head)).thenReturn(1000)
    when(mockedProvider.createRoadNode(roadlink3.points.last)).thenReturn(1001)
    when(mockedProvider.createRoadNode(roadlink4.points.head)).thenReturn(1002)
    when(mockedProvider.createRoadNode(roadlink4.points.last)).thenReturn(1003)
    provider.updateRoadLinks(List(roadlink3, roadlink4))

    provider.getRoadLinks(user99998).sortBy(x => x.id) must equal(
      List(RoadLink(id = 668L,
                    lonLat = Vector((100003.001, 2999999.002), (100012.0, 3000015.0)),
                    municipalityNumber = 99998),
           RoadLink(id = 669L,
                    lonLat = Vector((100004.001, 3000000.002), (100022.0, 3000016.0)),
                    municipalityNumber = 99998)))

    provider.updateRoadLinks(
      List(roadlink3.copy(
                    points = List(Point(200004.001, 4000000.002, 13.003), Point(200022.0, 4000016.0, 64.0)),
                    endDate = Some(new LocalDate(2013, 10, 11))),
           roadlink4.copy(points = List(Point(200014.001, 4000001.002, 13.003), Point(200222.0, 4000216.0, 64.0)))))

    provider.getRoadLinks(user99998).sortBy(x => x.id) must equal(
      List(RoadLink(id = 668L,
                    lonLat = Vector((200004.001, 4000000.002), (200022.0, 4000016.0)),
                    endDate = Some(new LocalDate(2013, 10, 11)),
                    municipalityNumber = 99998),
           RoadLink(id = 669L,
                    lonLat = Vector((200014.001, 4000001.002), (200222.0, 4000216.0)),
                    municipalityNumber = 99998)))
    verify(mockedProvider).updateRoadNode(1000, Point(200004.001, 4000000.002, 13.003))
    verify(mockedProvider).updateRoadNode(1001, Point(200022.0, 4000016.0, 64.0))
    verify(mockedProvider).updateRoadNode(1002, Point(200014.001, 4000001.002, 13.003))
    verify(mockedProvider).updateRoadNode(1003, Point(200222.0, 4000216.0, 64.0))
  }

  it must "not store data if enddate is given and no previous record" in {
    val roadlink = MtkRoadLink(700L, startDate, Some(new LocalDate(2013, 10, 10)), 99995,
        List(Point(100003.001, 2999999.002, 3.003), Point(100012.0, 3000015.0, 48.0)))
    cleanDbForMunicipalities("99995")
    provider.getRoadLinks(user99995) must equal(List())
    provider.updateRoadLinks(List(roadlink))
    provider.getRoadLinks(user99995) must equal(List())
    verify(mockedProvider, never()).updateRoadNode(anyObject(), anyObject())
    verify(mockedProvider, never()).createRoadNode(anyObject())
  }
}

