package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.{AgainstLinkChain, TowardsLinkChain}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.bonecpToInternalConnection
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, RoadLinkType}
import fi.liikennevirasto.digiroad2.linearasset.LinearAsset
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import scala.collection.JavaConversions._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{StaticQuery => Q}
import org.joda.time.DateTime
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.{DateTimePropertyFormat => DateTimeFormat}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao

case class WeightLimitLink(id: Long, roadLinkId: Long, sideCode: Int, value: Int, points: Seq[Point], position: Option[Int] = None, towardsLinkChain: Option[Boolean] = None, expired: Boolean = false)
case class WeightLimit(id: Long, value: Int, expired: Boolean, endpoints: Set[Point],
                       modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String],
                       weightLimitLinks: Seq[WeightLimitLink], typeId: Int)

trait WeightLimitOperations {
  val valuePropertyId: String = "kokonaispainorajoitus"

  def withDynTransaction[T](f: => T): T

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  private def getLinksWithPositions(links: Seq[WeightLimitLink]): Seq[WeightLimitLink] = {
    def getLinkEndpoints(link: WeightLimitLink): (Point, Point) = GeometryUtils.geometryEndpoints(link.points)
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val rawLink = chainedLink.rawLink
      val towardsLinkChain = chainedLink.geometryDirection match {
        case TowardsLinkChain => true
        case AgainstLinkChain => false
      }
      rawLink.copy(position = Some(chainedLink.linkPosition), towardsLinkChain = Some(towardsLinkChain))
    }
  }

  private def weightLimitLinksById(id: Long): Seq[(Long, Long, Int, Int, Seq[Point], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)] = {
    val weightLimits = sql"""
      select a.id, pos.road_link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
             a.modified_by, a.modified_date, a.created_by, a.created_date, case when a.valid_to <= sysdate then 1 else 0 end as expired,
             a.asset_type_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on p.public_id = $valuePropertyId
        join number_property_value s on s.asset_id = a.id and s.property_id = p.id
        where a.id = $id
      """.as[(Long, Long, Int, Int, Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)].list

    weightLimits.map { case (segmentId, roadLinkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (segmentId, roadLinkId, sideCode, value, points, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId)
    }
  }

  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[WeightLimitLink] = {
    withDynTransaction {
      val roadLinks = RoadLinkService.getRoadLinks(bounds, false, municipalities)
      val roadLinkIds = roadLinks.map(_._1).toList

      val weightLimits = OracleArray.fetchWeightLimitsByRoadLinkIds(roadLinkIds, typeId, valuePropertyId, bonecpToInternalConnection(dynamicSession.conn))

      val linkGeometries: Map[Long, (Seq[Point], Double, RoadLinkType, Int)] =
      roadLinks.foldLeft(Map.empty[Long, (Seq[Point], Double, RoadLinkType, Int)]) { (acc, roadLink) =>
          acc + (roadLink._1 -> (roadLink._2, roadLink._3, roadLink._4, roadLink._5))
        }

      val weightLimitsWithGeometry: Seq[WeightLimitLink] = weightLimits.map { link =>
        val (assetId, roadLinkId, sideCode, value, startMeasure, endMeasure) = link
        val geometry = GeometryUtils.truncateGeometry(linkGeometries(roadLinkId)._1, startMeasure, endMeasure)
        WeightLimitLink(assetId, roadLinkId, sideCode, value, geometry)
      }

      weightLimitsWithGeometry.groupBy(_.id).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  private def getByIdWithoutTransaction(id: Long): Option[WeightLimit] =  {
    val links = weightLimitLinksById(id)
    if (links.isEmpty) None
    else {
      val linkEndpoints: List[(Point, Point)] = links.map { link => GeometryUtils.geometryEndpoints(link._5) }.toList
      val limitEndpoints = LinearAsset.calculateEndPoints(linkEndpoints)
      val head = links.head
      val (_, _, _, value, _, modifiedBy, modifiedAt, createdBy, createdAt, expired, typeId) = head
      val weightLimitLinks = links.map { case (_, roadLinkId, sideCode, _, points, _, _, _, _, _, typeId) =>
        WeightLimitLink(id, roadLinkId, sideCode, value, points, None, None, expired)
      }
      Some(WeightLimit(
            id, value, expired, limitEndpoints,
            modifiedBy, modifiedAt.map(DateTimeFormat.print),
            createdBy, createdAt.map(DateTimeFormat.print),
            getLinksWithPositions(weightLimitLinks), typeId))
    }
  }

  def getById(id: Long): Option[WeightLimit] = {
    withDynTransaction {
      getByIdWithoutTransaction(id)
    }
  }

  private def updateNumberProperty(assetId: Long, propertyId: Long, value: Int): Int =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId".first

  private def updateWeightLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption(valuePropertyId).get
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = updateNumberProperty(id, propertyId, value)
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  private def updateWeightLimitExpiration(id: Long, expired: Boolean, username: String) = {
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = expired match {
      case true => sqlu"update asset set valid_to = sysdate where id = $id".first
      case false => sqlu"update asset set valid_to = null where id = $id".first
    }
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  def updateWeightLimit(id: Long, value: Option[Int], expired: Boolean, username: String): Option[Long] = {
    withDynTransaction {
      val valueUpdate: Option[Long] = value.flatMap(updateWeightLimitValue(id, _, username))
      val expirationUpdate: Option[Long] = updateWeightLimitExpiration(id, expired, username)
      val updatedId = valueUpdate.orElse(expirationUpdate)
      if (updatedId.isEmpty) dynamicSession.rollback()
      updatedId
    }
  }

  private def createWeightLimitWithoutTransaction(typeId: Int, roadLinkId: Long, value: Int, expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): WeightLimit = {
    val id = OracleSpatialAssetDao.nextPrimaryKeySeqValue
    val numberPropertyValueId = OracleSpatialAssetDao.nextPrimaryKeySeqValue
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).first(valuePropertyId)
    val lrmPositionId = OracleSpatialAssetDao.nextLrmPositionPrimaryKeySeqValue
    val validTo = if(expired) "sysdate" else "null"
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to)
        values ($id, $typeId, $username, sysdate, #$validTo)

        into lrm_position(id, start_measure, end_measure, road_link_id, side_code)
        values ($lrmPositionId, $startMeasure, $endMeasure, $roadLinkId, $sideCode)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

        into number_property_value(id, asset_id, property_id, value)
        values ($numberPropertyValueId, $id, $propertyId, $value)
      select * from dual
    """.execute
    getByIdWithoutTransaction(id).get
  }

  def createWeightLimit(typeId: Int, roadLinkId: Long, value: Int, username: String): WeightLimit = {
    val sideCode = 1
    val startMeasure = 0
    val endMeasure = RoadLinkService.getRoadLinkLength(roadLinkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
    withDynTransaction {
      createWeightLimitWithoutTransaction(typeId, roadLinkId, value, false, sideCode, startMeasure, endMeasure, username)
    }
  }

  def split(id: Long, roadLinkId: Long, splitMeasure: Double, value: Int, expired: Boolean, username: String): Seq[WeightLimit] = {
    withDynTransaction {
      val typeId = getByIdWithoutTransaction(id).get.typeId
      Queries.updateAssetModified(id, username).execute()
      val (startMeasure, endMeasure, sideCode) = OracleLinearAssetDao.getLinkGeometryData(id, roadLinkId)
      val links: Seq[(Long, Double, (Point, Point))] = OracleLinearAssetDao.getLinksWithLength(typeId, id).map { link =>
        val (roadLinkId, length, geometry) = link
        (roadLinkId, length, GeometryUtils.geometryEndpoints(geometry))
      }
      val (existingLinkMeasures, createdLinkMeasures, linksToMove) = GeometryUtils.createSplit(splitMeasure, (roadLinkId, startMeasure, endMeasure), links)

      OracleLinearAssetDao.updateLinkStartAndEndMeasures(id, roadLinkId, existingLinkMeasures)
      val createdId = createWeightLimitWithoutTransaction(typeId, roadLinkId, value, expired, sideCode, createdLinkMeasures._1, createdLinkMeasures._2, username).id
      if (linksToMove.nonEmpty) OracleLinearAssetDao.moveLinks(id, createdId, linksToMove.map(_._1))
      Seq(getByIdWithoutTransaction(id).get, getByIdWithoutTransaction(createdId).get)
    }
  }
}

object WeightLimitService extends WeightLimitOperations {
  def withDynTransaction[T](f: => T): T = Database.forDataSource(dataSource).withDynTransaction(f)
}
