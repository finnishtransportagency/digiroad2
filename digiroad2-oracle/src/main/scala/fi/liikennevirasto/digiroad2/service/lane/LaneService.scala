package fi.liikennevirasto.digiroad2.service.lane

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.linearasset.{AssetFiller, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory


class LaneService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LaneOperations {
  override def roadLinkService: RoadLinkService =roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def dao: LaneDao = new LaneDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()

}

trait LaneOperations {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def dao: LaneDao
  def municipalityDao: MunicipalityDao
  def eventBus: DigiroadEventBus
  def polygonTools: PolygonTools
  def assetFiller: AssetFiller = new AssetFiller

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  val logger = LoggerFactory.getLogger(getClass)


  def getByZoomLevel( boundingRectangle: BoundingRectangle, linkGeomSource: Option[LinkGeomSource] = None) : Seq[Seq[LightLane]] = {
    withDynTransaction {
      val assets = dao.fetchLanes(  boundingRectangle, linkGeomSource)
      Seq(assets)
    }
  }

  /**
    * Returns linear assets for Digiroad2Api /lanes GET endpoint.
    *
    * @param bounds
    * @param municipalities
    * @return
    */
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLane]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getMainLanesByRoadLinks( roadLinks, change)

    LanePartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  protected def getMainLanesByRoadLinks( roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLane] = {

    val existingAssets = fetchExistingMainLanesByRoadLinks(roadLinks, Seq() )

    existingAssets.map { lane =>
      val roadLink = roadLinks.find( _.linkId == lane.linkId).getOrElse(throw new Exception (s"No roadLink found [${lane.linkId}]") )
      val geometry = GeometryUtils.truncateGeometry3D(  roadLink.geometry, lane.startMeasure, lane.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(geometry)

        PieceWiseLane(lane.id, lane.linkId, lane.sideCode, lane.expired, geometry,
          lane.startMeasure, lane.endMeasure,
          Set(endPoints._1, endPoints._2), lane.modifiedBy, lane.modifiedDateTime,
          lane.createdBy, lane.createdDateTime,
          roadLink.vvhTimeStamp, lane.geomModifiedDate, roadLink.administrativeClass, lane.attributes )
    }
    // TODO: in future the filltopology and changes/update will be include here
  }

  def fetchExistingMainLanesByRoadLinks( roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long]): Seq[PersistedLane] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
        dao.fetchMainLanesByLinkIds( linkIds ++ removedLinkIds)
      }.filterNot(_.expired)
    existingAssets
  }


  def fetchExistingLanesByRoadLinks( roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long] = Seq()): Seq[PersistedLane] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
        dao.fetchLanesByLinkIds( linkIds ++ removedLinkIds)
      }.filterNot(_.expired)
    existingAssets
  }


  def fetchExistingLanesByLinksIdAndSideCode(linkId: Long, sideCode: Int): Seq[PieceWiseLane] = {

    val roadLink = roadLinkService.getRoadLinkByLinkIdFromVVH(linkId).head

    val existingAssets =
      withDynTransaction {
        dao.fetchLanesByLinkIdAndSideCode( linkId, sideCode)
      }

    existingAssets.map { lane =>
    val geometry = GeometryUtils.truncateGeometry3D(  roadLink.geometry, lane.startMeasure, lane.endMeasure)
    val endPoints = GeometryUtils.geometryEndpoints(geometry)

    PieceWiseLane(lane.id, lane.linkId, lane.sideCode, lane.expired, geometry,
      lane.startMeasure, lane.endMeasure,
      Set(endPoints._1, endPoints._2), lane.modifiedBy, lane.modifiedDateTime,
      lane.createdBy, lane.createdDateTime,
      roadLink.vvhTimeStamp, lane.geomModifiedDate, roadLink.administrativeClass, lane.attributes )
    }

  }


  /**
    * Returns lanes ids. Used by Digiroad2Api /lane POST and /lane DELETE endpoints.
    */
  def getPersistedLanesByIds( ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLane] = {
    if(newTransaction)
      withDynTransaction {
        dao.fetchLanesByIds( ids )
      }
    else
      dao.fetchLanesByIds( ids )
  }


  def update ( newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long] ,sideCode: Int, username: String ): Seq[Long] = {
    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    withDynTransaction {
      val result = linkIds.map { linkId =>
        newIncomeLane.map { lane =>

          val laneCode = lane.attributes.properties.find( _.publicId == "lane_code").getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))

          val lameToUpdate = PersistedLane(lane.id, linkId, sideCode, laneCode.values.head.value.toString.toInt, lane.municipalityCode, lane.startMeasure, lane.endMeasure,
            Some(username), None, None, None, false, 0, None, lane.attributes )

          dao.updateEntryLane(lameToUpdate, username)
        }
      }

      if (result.isEmpty)
       Seq()
     else
       result.head
    }
  }



  def createWithoutTransaction( newLane: PersistedLane, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Long = {

    val laneId = dao.createLane( newLane, username )
    val lanePositionId = dao.createLanePosition(newLane, username)

    dao.createLanePositionRelation(laneId, lanePositionId)
    dao.createLanePosition(newLane, username)

    newLane.attributes match {
      case props: LanePropertiesValues =>
        props.properties.filterNot( _.publicId == "lane_code" )
                        .map( attr => dao.insertLaneAttributes(laneId, attr, username) )

      case _ => None
    }

    laneId
  }


  def validateMinDistance(measure1: Double, measure2: Double): Boolean = {
    val minDistanceAllow = 0.01
    val (maxMeasure, minMeasure) = (math.max(measure1, measure2), math.min(measure1, measure2))
    (maxMeasure - minMeasure) > minDistanceAllow
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def create(newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long] ,sideCode: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {

    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    withDynTransaction {

      val result = linkIds.map { linkId =>
        newIncomeLane.map { newLane =>

          val laneCode = newLane.attributes.properties.find( _.publicId == "lane_code").getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))

          val lameToInsert = PersistedLane(0, linkId, sideCode, laneCode.values.head.value.toString.toInt, newLane.municipalityCode, newLane.startMeasure, newLane.endMeasure,
            Some(username),  Some(DateTime.now()), None, None, expired = false, vvhTimeStamp, None, newLane.attributes )

          createWithoutTransaction(lameToInsert, username,  vvhTimeStamp)
        }
      }

      if (result.isEmpty)
        Seq()
      else
        result.head
    }

  }

  def deleteMultipleLanes(ids: Set[Long]) = {
    withDynTransaction {
      ids.map(id => dao.deleteEntryLane(id))
    }
  }

  def deleteEntryLane(id: Long ): Unit = {
    withDynTransaction {
      dao.deleteEntryLane(id)
    }
  }

  def multipleLanesToHistory (ids: Set[Long], username: String):  Set[Long] ={
    withDynTransaction {
      ids.map(id => dao.updateLaneExpiration(id, username) )
    }
    ids
  }

  def laneToHistory (id: Long, username: String): Long ={
    withDynTransaction {
      dao.updateLaneExpiration(id, username)
    }
    id
  }

}