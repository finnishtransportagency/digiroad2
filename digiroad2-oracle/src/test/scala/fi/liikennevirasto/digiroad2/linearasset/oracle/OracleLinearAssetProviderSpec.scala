package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.linearasset.NewLimit
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.asset.{UnknownLinkType, UnknownDirection, Municipality, BoundingRectangle}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import scala.language.implicitConversions

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Tag
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation

class OracleLinearAssetProviderSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val provider = new OracleLinearAssetProvider(new DummyEventBus, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val roadLink = VVHRoadLinkWithProperties(1105998302l, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, Municipality, 1, UnknownDirection, UnknownLinkType, None, None)
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(roadLink))
  when(mockRoadLinkService.getRoadLinksFromVVH(Set.empty[Long])).thenReturn(Seq.empty[VVHRoadLinkWithProperties])

  when(mockRoadLinkService.fetchVVHRoadlinks(Set(362964704l, 362955345l, 362955339l)))
    .thenReturn(Seq(VVHRoadlink(362964704l, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, UnknownDirection, FeatureClass.AllOthers),
                    VVHRoadlink(362955345l, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, UnknownDirection, FeatureClass.AllOthers),
                    VVHRoadlink(362955339l, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, UnknownDirection, FeatureClass.AllOthers)))

  private def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  test("create new speed limit") {
    runWithCleanup {
      val roadLink = VVHRoadlink(1l, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlink(1l)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(Set(1l))).thenReturn(Seq(roadLink))

      val id = provider.createSpeedLimits(Seq(NewLimit(1, 0.0, 150.0)), 30, "test", (_) => Unit)

      val createdLimit = provider.getSpeedLimit(id.head).get
      createdLimit.value should equal(Some(30))
      createdLimit.createdBy should equal(Some("test"))
    }
  }
}
