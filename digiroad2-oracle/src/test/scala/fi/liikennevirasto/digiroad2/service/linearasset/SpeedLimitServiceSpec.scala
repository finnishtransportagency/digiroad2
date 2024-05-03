package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao, Sequences}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISSpeedLimitDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.language.implicitConversions

class SpeedLimitServiceSpec extends FunSuite with Matchers {
val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val provider = new SpeedLimitService(new DummyEventBus, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }

  val eventBus = MockitoSugar.mock[DigiroadEventBus]
  
  val (linkId1, linkId2, linkId3, linkId4) =
    (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(),
      LinkIdGenerator.generateRandom())
  val linkId5 = "12345678-abcd-abcd-abcd-123456789abc:1"

  val roadLink = RoadLink(
    linkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1,
    TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn((List(roadLink), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((List(roadLink), Nil))

  when(mockRoadLinkService.fetchRoadlinksAndComplementaries(Set(linkId2, linkId3, linkId4)))
    .thenReturn(Seq(RoadLinkFetched(linkId2, 91, List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
      RoadLinkFetched(linkId3, 91, List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
      RoadLinkFetched(linkId4, 91, List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def passingMunicipalityValidation(code: Int, administrativeClass: AdministrativeClass): Unit = {}

  def failingMunicipalityValidation(code: Int, administrativeClass: AdministrativeClass): Unit = {
    throw new IllegalArgumentException
  }

  val roadLinkForSeparation = RoadLink(linkId5, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.BothDirections, UnknownLinkType, None, None)
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId5)).thenReturn(Some(roadLinkForSeparation))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId5, true)).thenReturn(Some(roadLinkForSeparation))
  val roadLinkFetched = RoadLinkFetched(linkId5, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.BothDirections, AllOthers)

  private def daoWithRoadLinks(roadLinks: Seq[RoadLinkFetched]): PostGISSpeedLimitDao = {
    
    when(mockRoadLinkService.fetchRoadlinksByIds(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    when(mockRoadLinkService.fetchRoadlinksAndComplementaries(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    roadLinks.foreach { roadLink =>
      when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(roadLink.linkId)).thenReturn(Some(roadLink))
    }

    new PostGISSpeedLimitDao(mockRoadLinkService)
  }


  private def truncateLinkGeometry(linkId: String, startMeasure: Double, endMeasure: Double): Seq[Point] = {
    val geometry = mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(linkId).get.geometry
    GeometryUtils.truncateGeometry3D(geometry, startMeasure, endMeasure)
  }

  def assertSpeedLimitEndPointsOnLink(speedLimitId: Long, linkId: String, startMeasure: Double, endMeasure: Double, dao: PostGISSpeedLimitDao) = {
    val expectedEndPoints = GeometryUtils.geometryEndpoints(truncateLinkGeometry(linkId, startMeasure, endMeasure).toList)
    val limitEndPoints = GeometryUtils.geometryEndpoints(dao.getLinksWithLength(speedLimitId).find { link => link._1 == linkId }.get._3)
    expectedEndPoints._1.distance2DTo(limitEndPoints._1) should be(0.0 +- 0.01)
    expectedEndPoints._2.distance2DTo(limitEndPoints._2) should be(0.0 +- 0.01)
  }

  test("create new speed limit") {
    runWithRollback {
      val roadLink = RoadLinkFetched(linkId1, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId1)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.fetchRoadlinksAndComplementaries(Set(linkId1))).thenReturn(Seq(roadLink))

      val id = provider.create(Seq(NewLimit(linkId1, 0.0, 150.0)), SpeedLimitValue(30), "test", (_, _) => Unit)

      val createdLimit = provider.getSpeedLimitById(id.head).get
      createdLimit.value should equal(Some(SpeedLimitValue(30,false)))
      createdLimit.createdBy should equal(Some("test"))
    }
  }

  test("Split should fail when user is not authorized for municipality") {
    runWithRollback {
      intercept[IllegalArgumentException] {
        val linkId = linkId5
        val roadLink = RoadLinkFetched(linkId, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
        when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(Some(roadLink))

        val asset = provider.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(200097)).head
        provider.splitSpeedLimit(asset.id, 100, 120, 60, "test", failingMunicipalityValidation _)
      }
    }
  }

  test("splitting one link speed limit " +
    "where split measure is after link middle point " +
    "modifies end measure of existing speed limit " +
    "and creates new speed limit for second split") {
    runWithRollback {
      val asset = provider.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(200097)).head
      val (createdId1, createdId2) = provider.split(asset, roadLinkFetched, 100, 120, 60, "test")
      val created1 = provider.getPersistedSpeedLimitById(createdId1).get
      val created2 = provider.getPersistedSpeedLimitById(createdId2).get

      assertSpeedLimitEndPointsOnLink(createdId1, linkId5, 0, 100, daoWithRoadLinks(List(roadLinkFetched)))
      assertSpeedLimitEndPointsOnLink(createdId2, linkId5, 100, 136.788, daoWithRoadLinks(List(roadLinkFetched)))
      provider.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(asset.id)).head.expired should be (true)

      created1.modifiedBy shouldBe Some("test")
      created2.createdBy shouldBe Some("test")
    }
  }

  test("splitting one link speed limit " +
    "where split measure is before link middle point " +
    "modifies start measure of existing speed limit " +
    "and creates new speed limit for first split") {
    runWithRollback {
      val asset = provider.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(200097)).head
      val (createdId1, createdId2) = provider.split(asset, roadLinkFetched, 50, 120, 60, "test")
      val created1 = provider.getPersistedSpeedLimitById(createdId1).get
      val created2 = provider.getPersistedSpeedLimitById(createdId2).get

      assertSpeedLimitEndPointsOnLink(createdId1, linkId5, 50, 136.788, daoWithRoadLinks(List(roadLinkFetched)))
      assertSpeedLimitEndPointsOnLink(createdId2, linkId5, 0, 50, daoWithRoadLinks(List(roadLinkFetched)))

      provider.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(asset.id)).head.expired should be (true)

      created1.modifiedBy shouldBe Some("test")
      created2.createdBy shouldBe Some("test")
    }
  }

  test("split existing speed limit") {
    runWithRollback {
      val roadLink = RoadLinkFetched(linkId5, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId5)).thenReturn(Some(roadLink))
      val speedLimits = provider.splitSpeedLimit(200097, 100, 50, 60, "test", (_, _) => Unit).sortBy(_.id)

      val existing = speedLimits(0)
      val created = speedLimits(1)
      existing.value should not be(200097)
      created.value should not be(200097)
      existing.value should be(Some(SpeedLimitValue(50,false)))
      created.value should be(Some(SpeedLimitValue(60,false)))
    }
  }


  test("separate speed limit to two") {
    val municipalityCode = 235
    val linkId = linkId5
    val geometry = List(Point(0.0, 0.0), Point(424.557, 0.0))
    val roadLinkFetched = RoadLinkFetched(linkId, municipalityCode, geometry, AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map())

    runWithRollback {
      when(mockRoadLinkService.fetchRoadlinksAndComplementaries(any[Set[String]])).thenReturn(List(roadLinkFetched))

      val Seq(updatedLimit, createdLimit) = provider.separateSpeedLimit(200097, SpeedLimitValue(50), SpeedLimitValue(40), "test", passingMunicipalityValidation)

      updatedLimit.linkId should be (linkId)
      updatedLimit.sideCode should be (SideCode.TowardsDigitizing)
      updatedLimit.value should be (Some(SpeedLimitValue(50,false)))
      updatedLimit.createdBy should be (Some("test"))

      createdLimit.linkId should be (linkId)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing)
      createdLimit.value should be (Some(SpeedLimitValue(40,false)))
      createdLimit.createdBy should be (Some("test"))
    }
  }

  test("separation should call municipalityValidation") {
    val municipalityCode = 235
    val linkId = linkId5
    val geometry = List(Point(0.0, 0.0), Point(424.557, 0.0))
    val roadLinkFetched = RoadLinkFetched(linkId, municipalityCode, geometry, AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map())

    when(mockRoadLinkService.fetchRoadlinksAndComplementaries(any[Set[String]])).thenReturn(List(roadLinkFetched))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separateSpeedLimit(200097, SpeedLimitValue(50), SpeedLimitValue(40), "test", failingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if no speed limit is found") {
    runWithRollback {
      intercept[NoSuchElementException] {
        provider.separateSpeedLimit(0, SpeedLimitValue(50), SpeedLimitValue(40), "test", passingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if speed limit is one way") {
    val linkId = "6cd52876-449d-47b5-8536-e206a8fee17b:1"
    val roadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.BothDirections, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId)).thenReturn(Some(roadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, true)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separateSpeedLimit(300388, SpeedLimitValue(50), SpeedLimitValue(40), "test", passingMunicipalityValidation)
      }
    }
  }

  test("speed limit separation fails if road link is one way") {
    val linkId = "4d146477-876b-4ab5-ad11-f29d16a9b300:1"
    val roadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(0.0, 200.0)), 200.0, Municipality, 1, TrafficDirection.TowardsDigitizing, UnknownLinkType, None, None)
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId)).thenReturn(Some(roadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId, true)).thenReturn(Some(roadLink))

    runWithRollback {
      intercept[IllegalArgumentException] {
        provider.separateSpeedLimit(200299, SpeedLimitValue(50), SpeedLimitValue(40), "test", passingMunicipalityValidation)
      }
    }
  }

  test("Must be able to split one sided speedlimits and keep new speed limit") {
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val speedLimitAssetTypeId = 20
    val linkId = LinkIdGenerator.generateRandom()
    val geometry = List(Point(0.0, 0.0), Point(424.557, 0.0))
    val roadLinkFetched = RoadLinkFetched(linkId, municipalityCode, geometry, AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map())
    val newRoadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(424.557, 0.0)), 424.557, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

    val changeInfo = Seq()

    PostGISDatabase.withDynTransaction {
      sqlu"""Insert into ASSET (ID,NATIONAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050499',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.YYYY HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,NATIONAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050501',null,'20',to_timestamp('20.04.2016 13:16:01','DD.MM.YYYY HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute
      sqlu"""Insert into ASSET (ID,NATIONAL_ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,MODIFIED_DATE,MODIFIED_BY,BEARING,VALID_FROM,VALID_TO,GEOMETRY,MUNICIPALITY_CODE,FLOATING) values ('18050503',null,'20',to_timestamp('20.04.2016 13:16:02','DD.MM.YYYY HH24:MI:SS'),'k127773',null,null,null,null,null,null,235,'0')""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647958',null,'2','0','424',null,$linkId,'1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.YYYY HH24:MI:SS'))""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ('46647960',null,'3','0','424',null,$linkId,'1460044024000',to_timestamp('08.04.2016 16:17:11','DD.MM.YYYY HH24:MI:SS'))""".execute

      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050499','46647958')""".execute
      sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ('18050501','46647960')""".execute

      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) values ('18050499',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 100 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.YYYY HH24:MI:SS'),null)""".execute
      sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) values ('18050501',(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = 80 and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:12','DD.MM.YYYY HH24:MI:SS'),null)""".execute
      
      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn((List(newRoadLink), changeInfo))
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(any[String], any[Boolean])).thenReturn(Some(newRoadLink))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(any[String])).thenReturn(Some(roadLinkFetched))
      when(mockRoadLinkService.getRoadLinksByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(List(newRoadLink))

      val before = provider.getByBoundingBox(SpeedLimitAsset.typeId, boundingBox, Set(municipalityCode)).toList

      val split = provider.splitSpeedLimit(18050499, 419.599, 100, 80, "split", { (x: Int, y: AdministrativeClass) => })

      split should have size(2);

      val after = provider.getByBoundingBox(SpeedLimitAsset.typeId, boundingBox, Set(municipalityCode)).toList

      after.forall(_.forall(_.id != 0)) should be (true)

      dynamicSession.rollback()
    }
  }

  /**
    * Raw SQL table columns for asset (some removed), raw sql table columns for lrm position added with asset id
    *
    * @param speed
    * @param data
    */
  private def saveSpeedLimits(speed: Int, data: Seq[((String, String, String, String, String),
                              (String, String, String, String, String, String, String, String, String, String))]) = {
    val assetData = data.map(_._1)
    val lrmData = data.map(_._2)
    assetData.foreach {
      case (id, _, createdDate, createdBy, _) =>
        sqlu"""Insert into ASSET (ID,ASSET_TYPE_ID,CREATED_DATE,CREATED_BY,VALID_FROM,FLOATING) values ($id,'20',to_timestamp($createdDate,'DD.MM.YYYY HH24:MI:SS'),$createdBy, current_timestamp, '0')""".execute
        sqlu"""Insert into SINGLE_CHOICE_VALUE (ASSET_ID,ENUMERATED_VALUE_ID,PROPERTY_ID,MODIFIED_DATE,MODIFIED_BY) values ( $id,(select ev.id from enumerated_value ev join property p on (p.id = property_id) where value = $speed and public_id = 'rajoitus'),(select id from property where public_id = 'rajoitus'),to_timestamp('08.04.2016 16:17:11','DD.MM.YYYY HH24:MI:SS'),null)""".execute
    }
    lrmData.foreach {
      case (assetId, id, _, sideCode, startM, endM, mmlId, linkId, adjTimeStamp, modDate) =>
        sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE) values ($id,null,$sideCode,${startM.toDouble},${endM.toDouble},$mmlId,$linkId,$adjTimeStamp,to_timestamp($modDate,'DD.MM.YYYY HH24:MI:SS'))""".execute
        sqlu"""Insert into ASSET_LINK (ASSET_ID,POSITION_ID) values ($assetId,$id)""".execute
    }
  }


  test("Should filter out speed limits on walkways from TN-ITS message") {
    val (linkId1, linkId2, linkId3) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    val roadLink1 = RoadLink(linkId1, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink2 = RoadLink(linkId2, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink3 = RoadLink(linkId3, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 7, TrafficDirection.BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))

    PostGISDatabase.withDynTransaction {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm1, $linkId1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset1, 20, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1, $lrm1)""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id) values ($asset1,(SELECT ev.id FROM enumerated_value ev, PROPERTY p WHERE p.ASSET_TYPE_ID = 20 AND p.id = ev.property_id AND ev.value = 50),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm2, $linkId2)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset2, 20, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2, $lrm2)""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id) values ($asset2,(SELECT ev.id FROM enumerated_value ev, PROPERTY p WHERE p.ASSET_TYPE_ID = 20 AND p.id = ev.property_id AND ev.value = 50),(select id from property where public_id = 'rajoitus'))""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm3, $linkId3)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values ($asset3, 20, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3, $lrm3)""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id) values ($asset3,(SELECT ev.id FROM enumerated_value ev, PROPERTY p WHERE p.ASSET_TYPE_ID = 20 AND p.id = ev.property_id AND ev.value = 50),(select id from property where public_id = 'rajoitus'))""".execute

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

      val result = provider.getChanged(SpeedLimitAsset.typeId, DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-11-02T12:00Z"))
      result.length should be(1)
      result.head.link.linkType should not be (TractorRoad)
      result.head.link.linkType should not be (CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }

  test("Delete link id from unknown speed limit list when that link does not exist anymore"){
    
    when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq())

    val oldLinkId = LinkIdGenerator.generateRandom()
    val municipalityCode = 235

    PostGISDatabase.withDynTransaction {
      sqlu"""INSERT INTO UNKNOWN_SPEED_LIMIT (MUNICIPALITY_CODE, ADMINISTRATIVE_CLASS, LINK_ID) VALUES ($municipalityCode, ${Municipality.value}, $oldLinkId)""".execute

      provider.purgeUnknown(Set(), Seq(oldLinkId))
      val unknownSpeedLimits = provider.speedLimitDao.getMunicipalitiesWithUnknown(municipalityCode)

      unknownSpeedLimits.isEmpty should be (true)
      dynamicSession.rollback()
    }
  }
}