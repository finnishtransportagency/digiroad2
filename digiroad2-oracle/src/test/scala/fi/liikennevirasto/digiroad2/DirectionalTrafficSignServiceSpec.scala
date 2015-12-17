package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class DirectionalTrafficSignServiceSpec extends FunSuite with Matchers {
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)))

  val service = new DirectionalTrafficSignService(mockVVHClient) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

    test("Can fetch by bounding box") {
      runWithRollback {
        val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
        result.id should equal(600049)
        result.mmlId should equal(388553074)
        result.lon should equal(374467)
        result.lat should equal(6677347)
        result.mValue should equal(103)
      }
    }


  test("Create new") {
    runWithRollback {
      val now = DateTime.now()
      val id = service.create(IncomingDirectionalTrafficSign(2, 0.0, 388553075, 3, Some("'HELSINKI:HELSINGFORS;;;;1;1;'") ), "jakke", Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235)
      val assets = service.getPersistedAssetsByIds(Set(id))


      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.mmlId should be(388553075)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.validityDirection should be(3)
      asset.text should be (Some("'HELSINKI:HELSINGFORS;;;;1;1;'"))
      asset.createdBy should be(Some("jakke"))
      asset.createdDateTime shouldBe defined

    }
  }


}
