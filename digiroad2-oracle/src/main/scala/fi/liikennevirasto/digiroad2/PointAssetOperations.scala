package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimeSimplifiedFormat
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.RoadLinkInfo
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation

sealed trait FloatingReason {
  def value: Int
}

case class ChangedPointAsset(pointAsset: PersistedPointAsset, link: RoadLink)

object FloatingReason{
  val values = Set(Unknown, RoadOwnerChanged, NoRoadLinkFound, DifferentMunicipalityCode, DistanceToRoad,
    NoReferencePointForMValue, TrafficDirectionNotMatch, TerminalChildless, TerminatedRoad)

  def apply(intValue: Int): FloatingReason = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Unknown extends FloatingReason { def value = 0 }
  case object RoadOwnerChanged extends FloatingReason { def value = 1 }
  case object NoRoadLinkFound extends FloatingReason { def value = 2 }
  case object DifferentMunicipalityCode extends FloatingReason { def value = 3 }
  case object DistanceToRoad extends FloatingReason { def value = 4 }
  case object NoReferencePointForMValue extends FloatingReason { def value = 5 }
  case object TrafficDirectionNotMatch extends FloatingReason { def value = 6 }
  case object TerminalChildless extends FloatingReason { def value = 7 }
  case object TerminatedRoad extends FloatingReason { def value = 8 }
}

trait IncomingPointAsset {
  val lon: Double
  val lat: Double
  val linkId: String
}

trait IncomePointAsset {
  val mValue: Long
  val linkId: String
}

trait PointAsset extends FloatingAsset {
  val municipalityCode: Int
}

trait PersistedPointAsset extends PointAsset with IncomingPointAsset {
  val id: Long
  val lon: Double
  val lat: Double
  val municipalityCode: Int
  val linkId: String
  val mValue: Double
  val floating: Boolean
  val timeStamp: Long
  val linkSource: LinkGeomSource
  val propertyData: Seq[Property]
  val externalId: Option[String]

  def getValidityDirection: Option[Int] = None
  def getBearing: Option[Int] = None
  def getProperty(property: String) : Option[PropertyValue] =
    this.propertyData.find(_.publicId == property).getOrElse(throw new NoSuchElementException("Property not found"))
      .values.map(_.asInstanceOf[PropertyValue]).headOption
}

trait PersistedPoint extends PersistedPointAsset with IncomingPointAsset {
  val id: Long
  val lon: Double
  val lat: Double
  val municipalityCode: Int
  val linkId: String
  val mValue: Double
  val floating: Boolean
  val timeStamp: Long
  val createdBy: Option[String]
  val createdAt: Option[DateTime]
  val modifiedBy: Option[String]
  val modifiedAt: Option[DateTime]
  val expired: Boolean
  val linkSource: LinkGeomSource
  val propertyData: Seq[Property]
  val externalId: Option[String]
}


trait LightGeometry {
  val lon: Double
  val lat: Double
}

case class AssetUpdate(assetId: Long, lon: Double, lat: Double, linkId: String, mValue: Double,
                       validityDirection: Option[Int], bearing: Option[Int], timeStamp: Long,
                       floating: Boolean, floatingReason: Option[FloatingReason] = None, externalId: Option[String] = None)

trait  PointAssetOperations{
  private val logger = LoggerFactory.getLogger(getClass)

  type IncomingAsset <: IncomingPointAsset
  type PersistedAsset <: PersistedPointAsset

  case class FloatingPointAsset(id: Long, municipality: String, administrativeClass: String, floatingReason: Option[Long])
  case class AssetBeforeUpdate(asset: PersistedAsset, persistedFloating: Boolean, floatingReason: Option[FloatingReason])

  def roadLinkService: RoadLinkService
  val idField = "id"

  final val TwoMeters = 2
  final val BearingLimit = 25
  final val defaultMultiChoiceValue = 0
  final val defaultSingleChoiceValue = 99

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def typeId: Int
  def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike] = Nil): Seq[PersistedAsset]
  def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike] = Nil): Seq[PersistedAsset]
  def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PersistedAsset]
  def setFloating(persistedAsset: PersistedAsset, floating: Boolean): PersistedAsset
  def create(asset: IncomingAsset, username: String, roadLink: RoadLink, newTransaction: Boolean = true): Long
  def update(id:Long, updatedAsset: IncomingAsset, roadLink: RoadLink, username: String): Long
  def setAssetPosition(asset: IncomingAsset, geometry: Seq[Point], mValue: Double): IncomingAsset
  def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingAsset] = { throw new UnsupportedOperationException()}
  def fetchLightGeometry(queryFilter: String => String): Seq[LightGeometry] = {throw new UnsupportedOperationException()}
  def createTimeStamp(offsetHours:Int=5): Long = LinearAssetUtils.createTimeStamp(offsetHours)
  def createOperation(asset: PersistedAsset, adjustment: AssetUpdate): PersistedAsset = {throw new UnsupportedOperationException()}
  def adjustmentOperation(asset: PersistedAsset, adjustment: AssetUpdate, link: RoadLinkInfo): Long = {throw new UnsupportedOperationException()}
  def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val roadLinks: Seq[RoadLink] = roadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(bounds,asyncMode=false)
    getByBoundingBox(user, bounds, roadLinks)
  }

  def assetProperties(pointAsset: PersistedPointAsset, since: DateTime) : Map[String, Any] = { throw new UnsupportedOperationException("Not Supported Method") }

  def getChanged(sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"

    val filter = s"where a.asset_type_id = $typeId and floating = '0' and (" +
      s"(a.valid_to > $querySinceDate and a.valid_to <= $queryUntilDate) or " +
      s"(a.modified_date > $querySinceDate and a.modified_date <= $queryUntilDate) or "+
      s"(a.created_date > $querySinceDate and a.created_date <= $queryUntilDate)) "

    val assets = withDynSession {
      fetchPointAssetsWithExpiredLimited(withFilter(filter), token)
    }

    val roadLinks = roadLinkService.getRoadLinksByLinkIds(assets.map(_.linkId).toSet)
    val historyRoadLinks = fetchMissingLinksFromHistory(assets, roadLinks)

    mapPersistedAssetChanges(assets, roadLinks, historyRoadLinks)
  }

  protected def fetchMissingLinksFromHistory(assets: Seq[PersistedAsset], roadLinks: Seq[RoadLink]) = {
    val missingOrDeletedLinks = assets.map(_.linkId).toSet.diff(roadLinks.map(_.linkId).toSet)
    roadLinkService.getHistoryDataLinks(missingOrDeletedLinks)
  }

  /**
   * Maps PersistedAssets with responding RoadLinks. If no RoadLink is found for an asset, it is skipped with log warning
   * @param assets
   * @param roadLinks
   * @param historyRoadLinks
   * @return
   */
  def mapPersistedAssetChanges(assets: Seq[PersistedAsset],
                               roadLinks: Seq[RoadLink],
                               historyRoadLinks: Seq[RoadLink]): Seq[ChangedPointAsset] = {
    assets.flatMap { asset =>
      val roadLinkFetched = roadLinks
        .find(_.linkId == asset.linkId)
        .orElse(historyRoadLinks.find(_.linkId == asset.linkId))

      roadLinkFetched match {
        case Some(roadLink: RoadLink) => Some(ChangedPointAsset(asset, roadLink))
        case _ =>
          logger.warn(s"Road link no longer available ${asset.linkId}. Skipping asset ${asset.id}")
          None
      }
    }
  }

  protected def getByBoundingBox(user: User, bounds: BoundingRectangle, roadLinks: Seq[RoadLink]): Seq[PersistedAsset] = {

    withDynTransaction {
      val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      fetchPointAssets(withFilter(filter), roadLinks)
    }
  }

  def getLightGeometryByBoundingBox(bounds: BoundingRectangle): Seq[LightGeometry] = {
    withDynSession {
      val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      fetchLightGeometry(withValidAssets(filter))
    }
  }

  protected def fetchFloatingAssets(addQueryFilter: String => String, isOperator: Option[Boolean]): Seq[(Long, String, String, Option[Long])] ={
    var query = s"""
          select a.$idField, m.name_fi, lrm.link_id, null
          from asset a
          join municipality m on a.municipality_code = m.id
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          where asset_type_id = $typeId and floating = '1' and (valid_to is null or valid_to > current_timestamp)"""

    StaticQuery.queryNA[(Long, String, String, Option[Long])](addQueryFilter(query)).list
  }

  protected def getFloatingPointAssets(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Seq[FloatingPointAsset] = {
    withDynTransaction {
      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))

      val municipalityFilter = optionalMunicipalities match {
        case Some(municipalities) => s" and municipality_code in ($municipalities)"
        case _ => ""
      }

      val result = fetchFloatingAssets(query => query + municipalityFilter, isOperator)
      val administrativeClasses = roadLinkService.getRoadLinksByLinkIds(result.map(_._3).toSet, newTransaction = false).groupBy(_.linkId).mapValues(_.head.administrativeClass)

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
    getByMunicipality(withMunicipality(municipalityCode) _)
  }

  protected def getByMunicipality(withFilter: String => String): Seq[PersistedAsset] = {
    withDynTransaction {
      fetchPointAssets(withFilter).toList
    }
  }

  protected def getByMunicipality(withFilter: String => String, newTransaction: Boolean = true): Seq[PersistedAsset] = {
    if (newTransaction)
      withDynTransaction {
        fetchPointAssets(withFilter).toList
      }
    else fetchPointAssets(withFilter).toList

  }

  def getById(id: Long): Option[PersistedAsset] = {
    val persistedAsset = getPersistedAssetsByIds(Set(id)).headOption
    val roadLinks: Option[RoadLinkLike] = persistedAsset.flatMap { x => roadLinkService.getRoadLinkByLinkId(x.linkId) }

    def findRoadlink(linkId: String): Option[RoadLinkLike] =
      roadLinks.find(_.linkId == linkId)

    withDynTransaction {
      persistedAsset.map(withFloatingUpdate(convertPersistedAsset(setFloating, findRoadlink)))
    }
  }

  def getNormalAndComplementaryById(id: Long, roadLink: RoadLink): Option[PersistedAsset] = {
    val persistedAsset = getPersistedAssetsByIds(Set(id)).headOption
    val roadLinks: Option[RoadLinkLike] = Some(roadLink)

    def findRoadlink(linkId: String): Option[RoadLinkLike] =
      roadLinks.find(_.linkId == linkId)

    withDynTransaction {
      persistedAsset.map(withFloatingUpdate(convertPersistedAsset(setFloating, findRoadlink)))
    }
  }

  def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedAsset] = {
    withDynSession {
      getPersistedAssetsByIdsWithoutTransaction(ids)
    }
  }

  def getPersistedAssetsByIdsWithExpire(ids: Set[Long]): Seq[PersistedAsset] = {
    withDynSession {
      getPersistedAssetsByIdsWithExpireWithoutTransaction(ids)
    }
  }

  def getPersistedAssetsByIdsWithoutTransaction(ids: Set[Long]): Seq[PersistedAsset] = {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
      fetchPointAssets(withFilter(filter))
  }

  def getPersistedAssetsByIdsWithExpireWithoutTransaction(ids: Set[Long]): Seq[PersistedAsset] = {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
    fetchPointAssetsWithExpired(withFilter(filter))
  }

  def getPersistedAssetsByLinkId(linkId: String): Seq[PersistedAsset] = {
    withDynSession {
      getPersistedAssetsByLinkIdWithoutTransaction(linkId)
    }
  }

  def getPersistedAssetsByLinkIdWithoutTransaction(linkId: String): Seq[PersistedAsset] = {
    val filter = s"where a.asset_type_id = $typeId and pos.link_Id = '$linkId'"
    fetchPointAssets(withFilter(filter))
  }

  def getPersistedAssetsByLinkIdsWithoutTransaction(linkIds: Set[String]): Seq[PersistedAsset] = {
    MassQuery.withStringIds(linkIds) { idTableName =>
      val filter = s"join $idTableName i on i.id = pos.link_id where a.asset_type_id = $typeId"
      fetchPointAssets(withFilter(filter))
    }
  }

  def getDefaultMultiChoiceValue: Int = defaultMultiChoiceValue
  def getDefaultSingleChoiceValue: Int = defaultSingleChoiceValue

  def expire(id: Long, username: String): Long = {
    withDynTransaction {
      expireWithoutTransaction(id, username)
    }
  }

  def expire(id: Long): Long = {
    withDynTransaction {
      expireWithoutTransaction(id)
    }
  }

  def expireWithoutTransaction(id: Long): Int = {
    sqlu"update asset set valid_to = current_timestamp where id = $id".first
  }

  def expireWithoutTransaction(id: Long, username: String): Int = {
    Queries.updateAssetModified(id, username).first
    sqlu"update asset set valid_to = current_timestamp where id = $id".first
  }

  def expireWithoutTransaction(ids: Seq[Long], username: String): Unit = {
    val expireAsset = dynamicSession.prepareStatement("update asset set valid_to = current_timestamp, modified_by = ? where id = ?")

    ids.foreach { id =>
      expireAsset.setString(1, username)
      expireAsset.setLong(2, id)
      expireAsset.executeUpdate()
      expireAsset.close()
    }
  }

  protected def convertPersistedAsset[T](setFloating: (PersistedAsset, Boolean) => T,
                                         roadLinkByLinkId: String => Option[RoadLinkLike])
                                        (persistedStop: PersistedAsset): (T, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    (setFloating(persistedStop, floating), floatingReason)
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  protected def withMunicipality(municipalityCode: Int)(query: String): String = {
    withFilter(s"where a.asset_type_id = $typeId and a.municipality_code = $municipalityCode")(query)
  }

  protected def withValidAssets(filter: String)(query: String): String = {
    withFilter(s"$filter and (a.valid_to is null or a.valid_to > current_timestamp) and a.valid_from < current_timestamp")(query)
  }

  protected def withFloatingUpdate[T <: FloatingAsset](toPointAsset: PersistedAsset => (T, Option[FloatingReason]))
                                                      (persistedAsset: PersistedAsset): T = {
    val (pointAsset, floatingReason) = toPointAsset(persistedAsset)
    if (persistedAsset.floating != pointAsset.floating) updateFloating(pointAsset.id, pointAsset.floating, floatingReason)
    pointAsset
  }

  def withBoundingBoxFilter(position : Point, meters: Int)(query: String): String = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    withFilter(s"Where a.asset_type_id = $typeId and $boundingBoxFilter")(query)
  }

  def floatingUpdate(assetId: Long, floating: Boolean, floatingReason: Option[FloatingReason]): Unit = {
    updateFloating(assetId, floating, floatingReason)
  }

  protected def updateFloating(id: Long, floating: Boolean, floatingReason: Option[FloatingReason]): Unit = sqlu"""update asset set floating = $floating where id = $id""".execute

  protected def floatingReason(persistedAsset: PersistedAsset, roadLinkOption: Option[RoadLinkLike]) : String = {
    roadLinkOption match {
      case None => s"No road link found with id ${persistedAsset.linkId}"
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

  def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = Map()


  def getProperty(properties: Seq[Property], property: String) : Option[PropertyValue] = {
    properties.find(p => p.publicId == property).get.values.map(_.asInstanceOf[PropertyValue]).headOption
  }

  def recalculateBearing(bearing: Option[Int]): (Option[Int], Option[Int]) = {
    bearing match {
      case Some(assetBearing) =>
        val validityDirection = getAssetValidityDirection(assetBearing)
        val readjustedBearing = if(validityDirection == SideCode.AgainstDigitizing.value) {
          if(assetBearing > 90 && assetBearing < 180)
            assetBearing + 180
          else
            Math.abs(assetBearing - 180)
        } else assetBearing

        (Some(readjustedBearing), Some(validityDirection))
      case _ =>
        (None, None)
    }
  }

  def getAssetValidityDirection(bearing: Int): Int = {
    bearing > 270 || bearing <= 90 match {
      case true => TowardsDigitizing.value
      case false => AgainstDigitizing.value
    }
  }

  def getAssetSideCodeByBearing(assetBearing: Int, roadLinkBearing: Int): Int = {
    val toleranceInDegrees = 25

    def getAngle(b1: Int, b2: Int): Int = {
      180 - Math.abs(Math.abs(b1 - b2) - 180)
    }

    val reverseRoadLinkBearing =
      if (roadLinkBearing - 180 < 0) {
        roadLinkBearing + 180
      } else {
        roadLinkBearing - 180
      }

    if(getAngle(assetBearing, roadLinkBearing) <= toleranceInDegrees) SideCode.TowardsDigitizing.value
    else if(Math.abs(assetBearing - reverseRoadLinkBearing) <= toleranceInDegrees) SideCode.AgainstDigitizing.value
    else SideCode.Unknown.value
  }

  def getSideCode(point: Point, roadLink: RoadLink, optBearing: Option[Int], twoSided: Boolean = false) : Int = {
    if (twoSided)
      BothDirections.value
    else {
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(point.x, point.y, 0), roadLink.geometry)
      val roadLinkPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue)
      val linkBearing = GeometryUtils.calculateBearing(roadLink.geometry, Some(mValue))
      SideCode.apply(optBearing match {
        case Some(bearing) => getAssetSideCodeByBearing(bearing, linkBearing)
        case _ => getSideCodeByGeometry(point, roadLinkPoint, linkBearing)
      }).value
    }

  }

  def getSideCodeByGeometry(assetLocation: Point, roadLinkPoint: Option[Point], linkBearing: Int): Int = {
    val lonDifference = assetLocation.x - roadLinkPoint.get.x
    val latDifference = assetLocation.y - roadLinkPoint.get.y

    (latDifference <= 0 && linkBearing <= 90) || (latDifference >= 0 && linkBearing > 270) match {
      case true => TowardsDigitizing.value
      case false => AgainstDigitizing.value
    }
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
              case Some(_) => return (true, Some(FloatingReason.DistanceToRoad))
              case _ => return (true, Some(FloatingReason.NoReferencePointForMValue))
            }
          }
        }
    }
    (false, None)
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