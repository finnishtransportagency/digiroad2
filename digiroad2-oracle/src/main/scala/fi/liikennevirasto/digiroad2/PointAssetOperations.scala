package fi.liikennevirasto.digiroad2

import com.google.common.base.Optional
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, FloatingAsset, Unknown}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

sealed trait FloatingReason {
  def value: Int
}

object FloatingReason{
  val values = Set(Unknown, RoadOwnerChanged, NoRoadLinkFound, DifferentMunicipalityCode, DistanceToRoad, NoReferencePointForMValue)

  def apply(intValue: Int): FloatingReason = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Unknown extends FloatingReason { def value = 0 }
  case object RoadOwnerChanged extends FloatingReason { def value = 1 }
  case object NoRoadLinkFound extends FloatingReason { def value = 2 }
  case object DifferentMunicipalityCode extends FloatingReason { def value = 3 }
  case object DistanceToRoad extends FloatingReason { def value = 4 }
  case object NoReferencePointForMValue extends FloatingReason { def value = 5 }
}

trait IncomingPointAsset {
  val lon: Double
  val lat: Double
  val linkId: Long
}

trait PointAsset extends FloatingAsset {
  val municipalityCode: Int
}

trait PersistedPointAsset extends PointAsset with IncomingPointAsset {
  val id: Long
  val lon: Double
  val lat: Double
  val municipalityCode: Int
  val linkId: Long
  val mValue: Double
  val floating: Boolean
  val vvhTimeStamp: Long
}

trait PointAssetOperations {
  type IncomingAsset <: IncomingPointAsset
  type PersistedAsset <: PersistedPointAsset

  case class FloatingPointAsset(id: Long, municipality: String, administrativeClass: String, floatingReason: Option[Long])
  case class AssetBeforeUpdate(asset: PersistedAsset, persistedFloating: Boolean, floatingReason: Option[FloatingReason])

  def roadLinkService: RoadLinkService
  val idField = "id"

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def typeId: Int
  def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike] = Nil): Seq[PersistedAsset]
  def setFloating(persistedAsset: PersistedAsset, floating: Boolean): PersistedAsset
  def create(asset: IncomingAsset, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass]): Long
  def update(id:Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String): Long

  def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val roadLinks: Seq[RoadLink] = roadLinkService.getRoadLinksFromVVH(bounds)
    getByBoundingBox(user, bounds, roadLinks, Seq(), floatingTreatment)
  }

  def getByBoundingBox(user: User, bounds: BoundingRectangle, roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo],
                       adjustment: (Seq[RoadLink], Seq[ChangeInfo], AssetBeforeUpdate) => AssetBeforeUpdate): Seq[PersistedAsset] = {

    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedAssets: Seq[PersistedAsset] = fetchPointAssets(withFilter(filter), roadLinks)

      val assetsBeforeUpdate: Seq[AssetBeforeUpdate] = persistedAssets.filter { persistedAsset =>
        user.isAuthorizedToRead(persistedAsset.municipalityCode)
      }.map { (persistedAsset: PersistedAsset) =>
        val (floating, assetFloatingReason) = isFloating(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
        adjustment(roadLinks, changeInfo, AssetBeforeUpdate(persistedAsset, floating, assetFloatingReason))
      }
      assetsBeforeUpdate.foreach { asset =>
        if (asset.asset.floating != asset.persistedFloating) {
          updateFloating(asset.asset.id, asset.asset.floating, asset.floatingReason)
        }
      }
      assetsBeforeUpdate.map(_.asset)
    }
  }

  private def floatingTreatment(roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], assetBeforeUpdate: AssetBeforeUpdate) = {
    if (assetBeforeUpdate.persistedFloating && !assetBeforeUpdate.asset.floating) {
      val logger = LoggerFactory.getLogger(getClass)
      val floatingReasonMessage = floatingReason(assetBeforeUpdate.asset, roadLinks.find(_.linkId == assetBeforeUpdate.asset.linkId))
      logger.info("Floating asset %d, reason: %s".format(assetBeforeUpdate.asset.id, floatingReasonMessage))
    }
    AssetBeforeUpdate(setFloating(assetBeforeUpdate.asset, assetBeforeUpdate.persistedFloating), assetBeforeUpdate.asset.floating, assetBeforeUpdate.floatingReason)
  }

  protected def fetchFloatingAssets(addQueryFilter: String => String, isOperator: Option[Boolean]): Seq[(Long, String, Long, Option[Long])] ={
    var query = s"""
          select a.$idField, m.name_fi, lrm.link_id, null
          from asset a
          join municipality m on a.municipality_code = m.id
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          where asset_type_id = $typeId and floating = '1' and (valid_to is null or valid_to > sysdate)"""

    StaticQuery.queryNA[(Long, String, Long, Option[Long])](addQueryFilter(query)).list
  }

  protected def getFloatingPointAssets(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Seq[FloatingPointAsset] = {
    withDynSession {
      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))

      val municipalityFilter = optionalMunicipalities match {
        case Some(municipalities) => s" and municipality_code in ($municipalities)"
        case _ => ""
      }

      val result = fetchFloatingAssets(query => query + municipalityFilter, isOperator)
      val administrativeClasses = roadLinkService.getRoadLinksByLinkIdsFromVVH(result.map(_._3).toSet, newTransaction = false).groupBy(_.linkId).mapValues(_.head.administrativeClass)

      result
        .map { case (id, municipality, administrativeClass, floatingReason) =>
          FloatingPointAsset(id, municipality, administrativeClasses.getOrElse(administrativeClass, Unknown).toString, floatingReason)
        }
    }
  }

  def getFloatingAssets(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Map[String, Map[String, Seq[Long]]] = {

    val result = getFloatingPointAssets(includedMunicipalities, isOperator)

    result.groupBy(_.municipality)
      .mapValues { municipalityAssets =>
        municipalityAssets
          .groupBy(_.administrativeClass)
          .mapValues(_.map(_.id))
      }
  }

  def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityCode)
     val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, Seq(), floatingCorrection)
  }

  def getByMunicipality[T](municipalityCode: Int, mapRoadLinks: Map[Long, RoadLink], roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo],
                           floatingCorrection: (Seq[ChangeInfo], Seq[RoadLink], PersistedAsset, Boolean, Option[FloatingReason], (PersistedAsset, Boolean) => T) =>
                          (PersistedAsset, Option[FloatingReason])): Seq[PersistedAsset] = {

    def linkIdToRoadLink(linkId: Long): Option[RoadLinkLike] =
      mapRoadLinks.get(linkId)

    withDynSession {
      fetchPointAssets(withMunicipality(municipalityCode))
        .map(withFloatingUpdate(convertPersistedAsset(setFloating, linkIdToRoadLink, changeInfo, roadLinks)))
        .toList
    }
  }

  private def floatingCorrection[T](changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink],
                                    persistedStop: PersistedAsset, floating: Boolean, floatingReason: Option[FloatingReason],
                                    conversion: (PersistedAsset, Boolean) => T) = {
    (conversion(persistedStop, floating), floatingReason)
  }

  def getById(id: Long): Option[PersistedAsset] = {
    val persistedAsset = getPersistedAssetsByIds(Set(id)).headOption
    val roadLinks: Option[RoadLinkLike] = persistedAsset.flatMap { x => roadLinkService.getRoadLinkFromVVH(x.linkId) }

    def findRoadlink(linkId: Long): Option[RoadLinkLike] =
      roadLinks.find(_.linkId == linkId)

    withDynSession {
      persistedAsset.map(withFloatingUpdate(convertPersistedAsset(setFloating, findRoadlink, Seq(), Seq())))
    }
  }

  def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedAsset] = {
    withDynSession {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
      fetchPointAssets(withFilter(filter))
    }
  }

  def expire(id: Long, username: String): Long = {
    withDynSession {
      Queries.updateAssetModified(id, username).first
      sqlu"update asset set valid_to = sysdate where id = $id".first
    }
  }

  protected def convertPersistedAsset[T](conversion: (PersistedAsset, Boolean) => T,
                                         roadLinkByLinkId: Long => Option[RoadLinkLike],
                                         changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink])
                                        (persistedStop: PersistedAsset): (T, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    floatingCorrection(changeInfo, roadLinks, persistedStop, floating, floatingReason, conversion)
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  protected def withMunicipality(municipalityCode: Int)(query: String): String = {
    withFilter(s"where a.asset_type_id = $typeId and a.municipality_code = $municipalityCode")(query)
  }

  protected def withFloatingUpdate[T <: FloatingAsset](toPointAsset: PersistedAsset => (T, Option[FloatingReason]))
                                                      (persistedAsset: PersistedAsset): T = {
    val (pointAsset, floatingReason) = toPointAsset(persistedAsset)
    if (persistedAsset.floating != pointAsset.floating) updateFloating(pointAsset.id, pointAsset.floating, floatingReason)
    pointAsset
  }

  protected def updateFloating(id: Long, floating: Boolean, floatingReason: Option[FloatingReason]) = sqlu"""update asset set floating = $floating where id = $id""".execute

  protected def floatingReason(persistedAsset: PersistedAsset, roadLinkOption: Option[RoadLinkLike]) : String = {
    roadLinkOption match {
      case None => "No road link found with id %d".format(persistedAsset.linkId)
      case Some(roadLink) =>
        if (roadLink.municipalityCode != persistedAsset.municipalityCode) {
          "Road link and asset have differing municipality codes (%d vs %d)".format(roadLink.municipalityCode, persistedAsset.municipalityCode)
        } else {
          val point = Point(persistedAsset.lon, persistedAsset.lat)
          val roadOption = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, persistedAsset.mValue)
          roadOption match {
            case Some(value) => "Distance to road link is %.3f".format(value.distance2DTo(point))
            case _ => "Road link has no reference point for mValue %.3f".format(persistedAsset.mValue)
          }
        }
    }
  }

  def isFloating(persistedAsset: PersistedPointAsset, roadLink: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    PointAssetOperations.isFloating(municipalityCode = persistedAsset.municipalityCode, lon = persistedAsset.lon,
      lat = persistedAsset.lat, mValue = persistedAsset.mValue, roadLink = roadLink)
  }
}

object PointAssetOperations {
  def calculateBearing(persistedPointAsset: PersistedPointAsset, roadLinkOption: Option[RoadLinkLike]): Option[Int] = {
    roadLinkOption match {
      case None => None
      case Some(roadLink) =>
        Some(calculateBearing(persistedPointAsset, roadLink.geometry))
    }
  }

  def isFloating(persistedAsset: PersistedPointAsset, roadLink: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) =
    isFloating(municipalityCode = persistedAsset.municipalityCode, lon = persistedAsset.lon,
      lat = persistedAsset.lat, mValue = persistedAsset.mValue, roadLink = roadLink)

  def isFloating(municipalityCode: Int, lon: Double, lat: Double, mValue: Double, roadLink: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    roadLink match {
      case None => return (true, Some(FloatingReason.NoRoadLinkFound))
      case Some(roadLink) =>
        if (roadLink.municipalityCode != municipalityCode) {
          return (true, Some(FloatingReason.DifferentMunicipalityCode))
        } else {
          val point = Point(lon, lat)
          val roadOption = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue)
          if(!coordinatesWithinThreshold(Some(point), roadOption)){
            roadOption match {
              case Some(value) => return (true, Some(FloatingReason.DistanceToRoad))
              case _ => return (true, Some(FloatingReason.NoReferencePointForMValue))
            }
          }
        }
    }
    return (false, None)
  }

  def calculateBearing(persistedPointAsset: PersistedPointAsset, geometry: Seq[Point]): Int = {
    calculateBearing(Point(persistedPointAsset.lon, persistedPointAsset.lat), geometry)
  }

  def calculateBearing(point: Point, geometry: Seq[Point]): Int = {
    def direction(p1: Point, p2: Point): Long = {
      val dLon = p2.x - p1.x
      val dLat = p2.y - p1.y
      Math.round(Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360
    }
    val closest = GeometryUtils.segmentByMinimumDistance(point, geometry)
    direction(closest._1, closest._2).toInt
  }

  private val FLOAT_THRESHOLD_IN_METERS = 3

  def coordinatesWithinThreshold(pt1: Option[Point], pt2: Option[Point]): Boolean = {
    (pt1, pt2) match {
      case (Some(point1), Some(point2)) => point1.distance2DTo(point2) <= FLOAT_THRESHOLD_IN_METERS
      case _ => false
    }
  }
}
