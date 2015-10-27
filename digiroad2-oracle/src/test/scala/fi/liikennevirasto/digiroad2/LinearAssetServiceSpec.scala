package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.fetchVVHRoadlink(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
      VVHRoadLinkWithProperties(
        1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None)))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByMmlIds(30, Seq(1), "mittarajoitus"))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30)))

  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = mockRoadLinkService
  }

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
  }

  object ServiceWithDao extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

  test("Expire numerical limit") {
    runWithRollback {
      ServiceWithDao.update(Seq(11111l), None, true, "lol")
      val limit = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limit.value should be (Some(NumericValue(4000)))
      limit.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithRollback {
      ServiceWithDao.update(Seq(11111l), Some(2000), false, "lol")
      val limit = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limit.value should be (Some(NumericValue(2000)))
      limit.expired should be (false)
    }
  }

  test("Create new linear asset") {
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, Some(1000), 1)), 30, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchLinearAssetsByIds(Set(newAssets.head.id), "mittarajoitus").head
      asset.value should be (Some(NumericValue(1000)))
      asset.expired should be (false)
    }
  }

  test("adjust linear asset to cover whole link when the difference in asset length and link length is less than maximum allowed error") {
    val linearAssets = PassThroughService.getByBoundingBox(30, BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0))).head
    linearAssets should have size 1
    linearAssets.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    linearAssets.map(_.mmlId) should be(Seq(1))
    linearAssets.map(_.value) should be(Seq(Some(NumericValue(40000))))
    verify(mockEventBus, times(1))
      .publish("linearAssets:update", ChangeSet(Set.empty[Long], Seq(MValueAdjustment(1, 1, 0.0, 10.0)), Nil))
  }

  test("Municipality fetch dispatches to dao based on asset type id") {
    when(mockRoadLinkService.getRoadLinksFromVVH(235)).thenReturn(Seq(
      VVHRoadLinkWithProperties(
        1, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None)))

    when(mockLinearAssetDao.fetchProhibitionsByMmlIds(Seq(1l))).thenReturn(Nil)
    PassThroughService.getByMunicipality(190, 235)
    verify(mockLinearAssetDao).fetchProhibitionsByMmlIds(Seq(1l))

    when(mockLinearAssetDao.fetchLinearAssetsByMmlIds(100, Seq(1l), "mittarajoitus")).thenReturn(Nil)
    PassThroughService.getByMunicipality(100, 235)
    verify(mockLinearAssetDao).fetchLinearAssetsByMmlIds(100, Seq(1l), "mittarajoitus")
  }

  test("Separate linear asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Some(1), 1)
      val asset = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(asset.id, Some(2), Some(3), "unittest", (i) => Unit).filter(_ != asset.id).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(asset.id)).head

      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.mmlId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value towards digitization") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Some(1), 1)
      val asset = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(asset.id, None, Some(3), "unittest", (i) => Unit).filter(_ != asset.id).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(asset.id)).head

      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.expired should be (true)
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.mmlId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.expired should be (false)
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value against digitization") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Some(1), 1)
      val asset = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(asset.id, Some(2), None, "unittest", (i) => Unit).filter(_ != asset.id).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(asset.id)).head

      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.expired should be (false)
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.mmlId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.expired should be (true)
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separation should call municipalityValidation") {
    def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Some(1), 1)
      val asset = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      intercept[IllegalArgumentException] {
        ServiceWithDao.separate(asset.id, Some(1), Some(2), "unittest", failingMunicipalityValidation)
      }
    }
  }
}
