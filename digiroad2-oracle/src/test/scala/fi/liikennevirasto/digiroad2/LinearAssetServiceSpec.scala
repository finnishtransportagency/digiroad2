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
  val linearAssetDao = new OracleLinearAssetDao(mockRoadLinkService)

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
      ServiceWithDao.expire(Seq(11111l), "lol")
      val limit = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limit.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithRollback {
      ServiceWithDao.update(Seq(11111l), NumericValue(2000), "lol")
      val limit = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limit.value should be (Some(NumericValue(2000)))
      limit.expired should be (false)
    }
  }

  test("Update prohibition") {
    when(mockRoadLinkService.fetchVVHRoadlink(1621077551l)).thenReturn(Some(VVHRoadlink(1621077551l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

    runWithRollback {
      ServiceWithDao.update(Seq(600020l), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "lol")
      val limit = linearAssetDao.fetchProhibitionsByMmlIds(190, Seq(1621077551l)).head

      limit.value should be (Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty)))))
      limit.expired should be (false)
    }
  }

  test("Create new linear asset") {
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, NumericValue(1000), 1)), 30, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchLinearAssetsByIds(Set(newAssets.head), "mittarajoitus").head
      asset.value should be (Some(NumericValue(1000)))
      asset.expired should be (false)
    }
  }

  test("Create new prohibition") {
    val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty)))
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, prohibition, 1)), 190, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchProhibitionsByMmlIds(190, Seq(388562360l)).head
      asset.value should be (Some(prohibition))
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

    when(mockLinearAssetDao.fetchProhibitionsByMmlIds(190, Seq(1l), includeFloating = false)).thenReturn(Nil)
    PassThroughService.getByMunicipality(190, 235)
    verify(mockLinearAssetDao).fetchProhibitionsByMmlIds(190, Seq(1l), includeFloating = false)

    when(mockLinearAssetDao.fetchLinearAssetsByMmlIds(100, Seq(1l), "mittarajoitus")).thenReturn(Nil)
    PassThroughService.getByMunicipality(100, 235)
    verify(mockLinearAssetDao).fetchLinearAssetsByMmlIds(100, Seq(1l), "mittarajoitus")
  }

  test("Separate linear asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(assetId, Some(NumericValue(2)), Some(NumericValue(3)), "unittest", (i) => Unit).filter(_ != assetId).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(assetId)).head

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

  test("Separate prohibition asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1)
      val assetId = ServiceWithDao.create(Seq(newLimit), 190, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty)))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2))))

      ServiceWithDao.separate(assetId, Some(prohibitionA), Some(prohibitionB), "unittest", (i) => Unit)

      val limits = linearAssetDao.fetchProhibitionsByMmlIds(190, Seq(388562360))
      val oldLimit = limits.find(_.id == assetId).get
      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(prohibitionA))
      oldLimit.modifiedBy should be (Some("unittest"))

      val createdLimit = limits.find(_.id != assetId).get
      createdLimit.mmlId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(prohibitionB))
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value towards digitization") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(assetId, None, Some(NumericValue(3)), "unittest", (i) => Unit).filter(_ != assetId).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(assetId)).head

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
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      ServiceWithDao.separate(assetId, Some(NumericValue(2)), None, "unittest", (i) => Unit).filter(_ != assetId) shouldBe empty

      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(assetId)).head

      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.expired should be (false)
      oldLimit.modifiedBy should be (Some("unittest"))

    }
  }

  test("Split linear asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      val ids = ServiceWithDao.split(assetId, 2.0, Some(NumericValue(2)), Some(NumericValue(3)), "unittest", (i) => Unit)

      val createdId = ids.filter(_ != assetId).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(Set(assetId)).head

      oldLimit.mmlId should be (388562360)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.modifiedBy should be (Some("unittest"))
      oldLimit.startMeasure should be (2.0)
      oldLimit.endMeasure should be (10.0)

      createdLimit.mmlId should be (388562360)
      createdLimit.sideCode should be (SideCode.BothDirections.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.createdBy should be (Some("unittest"))
      createdLimit.startMeasure should be (0.0)
      createdLimit.endMeasure should be (2.0)
    }
  }

  test("Split prohibition") {
    runWithRollback {
      val newProhibition = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1)
      val assetId = ServiceWithDao.create(Seq(newProhibition), 190, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty)))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2))))

      ServiceWithDao.split(assetId, 6.0, Some(prohibitionA), Some(prohibitionB), "unittest", (i) => Unit)

      val prohibitions = linearAssetDao.fetchProhibitionsByMmlIds(190, Seq(388562360))
      val oldProhibition = prohibitions.find(_.id == assetId).get
      oldProhibition.mmlId should be (388562360)
      oldProhibition.sideCode should be (SideCode.BothDirections.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (Some("unittest"))
      oldProhibition.startMeasure should be (0.0)
      oldProhibition.endMeasure should be (6.0)

      val createdProhibition = prohibitions.find(_.id != assetId).get
      createdProhibition.mmlId should be (388562360)
      createdProhibition.sideCode should be (SideCode.BothDirections.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
      createdProhibition.startMeasure should be (6.0)
      createdProhibition.endMeasure should be (10.0)
    }
  }

  test("Separation should call municipalityValidation") {
    def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      intercept[IllegalArgumentException] {
        ServiceWithDao.separate(assetId, Some(NumericValue(1)), Some(NumericValue(2)), "unittest", failingMunicipalityValidation)
      }
    }
  }
}
