package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
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
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation

sealed trait FloatingReason {
  def value: Int
}

case class ChangedPointAsset(pointAsset: PersistedPointAsset, link: RoadLink)

object FloatingReason{
  val values = Set(Unknown, RoadOwnerChanged, NoRoadLinkFound, DifferentMunicipalityCode, DistanceToRoad, NoReferencePointForMValue, TrafficDirectionNotMatch, TerminalChildless)

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
  val linkId: Long
}

trait IncomePointAsset {
  val mValue: Long
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
  val linkSource: LinkGeomSource
  val propertyData: Seq[Property]
}

trait PersistedPoint extends PersistedPointAsset with IncomingPointAsset {
  val id: Long
  val lon: Double
  val lat: Double
  val municipalityCode: Int
  val linkId: Long
  val mValue: Double
  val floating: Boolean
  val vvhTimeStamp: Long
  val createdBy: Option[String]
  val createdAt: Option[DateTime]
  val modifiedBy: Option[String]
  val modifiedAt: Option[DateTime]
  val expired: Boolean
  val linkSource: LinkGeomSource
  val propertyData: Seq[Property]
}


trait LightGeometry {
  val lon: Double
  val lat: Double
}

trait  PointAssetOperations{
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

  def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val roadLinks: Seq[RoadLink] = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    getByBoundingBox(user, bounds, roadLinks, Seq(), floatingTreatment)
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

    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(assets.map(_.linkId).toSet)
    val historicRoadLink = roadLinkService.getHistoryDataLinksFromVVH(assets.map(_.linkId).toSet.diff(roadLinks.map(_.linkId).toSet))

    assets.map { asset =>
      ChangedPointAsset(asset, roadLinks.find(_.linkId == asset.linkId) match {
        case Some(roadLink) => roadLink
        case _ => historicRoadLink.filter(_.linkId == asset.linkId).sortBy(_.vvhTimeStamp)(Ordering.Long.reverse).headOption
          .getOrElse(throw new IllegalStateException(s"Road link no longer available ${asset.linkId}"))
      })
    }
  }

  protected def getByBoundingBox(user: User, bounds: BoundingRectangle, roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo],
                       adjustment: (Seq[RoadLink], Seq[ChangeInfo], PersistedAsset, Boolean, Option[FloatingReason]) => Option[AssetBeforeUpdate]): Seq[PersistedAsset] = {

    withDynTransaction {
      val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedAssets: Seq[PersistedAsset] = fetchPointAssets(withFilter(filter), roadLinks)

      val assetsBeforeUpdate: Seq[AssetBeforeUpdate] = persistedAssets.map { persistedAsset: PersistedAsset =>
        val (floating, assetFloatingReason) = isFloating(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
        adjustment(roadLinks, changeInfo, persistedAsset, floating, assetFloatingReason)  match {
          case Some(adjustment) =>
            adjustment
          case _ =>
            if (floating && !persistedAsset.floating) {
              val logger = LoggerFactory.getLogger(getClass)
              val floatingReasonMessage = floatingReason(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
              logger.info("Floating asset %d, reason: %s".format(persistedAsset.id, floatingReasonMessage))
            }
            AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, assetFloatingReason)
        }
      }
      assetsBeforeUpdate.foreach { asset =>
        if (asset.asset.floating != asset.persistedFloating) {
          updateFloating(asset.asset.id, asset.asset.floating, asset.floatingReason)
        }
      }
      assetsBeforeUpdate.map(_.asset)
    }
  }

  def getLightGeometryByBoundingBox(bounds: BoundingRectangle): Seq[LightGeometry] = {
    withDynSession {
      val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      fetchLightGeometry(withValidAssets(filter))
    }
  }

  private def floatingTreatment(roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], persistedAsset: PersistedAsset, floating: Boolean, reason: Option[FloatingReason]) = {
    if (floating && !persistedAsset.floating) {
      val logger = LoggerFactory.getLogger(getClass)
      val floatingReasonMessage = floatingReason(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
      logger.info("Floating asset %d, reason: %s".format(persistedAsset.id, floatingReasonMessage))
    }
    Some(AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, reason))
  }

  protected def floatingAdjustment(adjustmentOperation: (PersistedAsset, AssetAdjustment, RoadLink) => Any, createOperation: (PersistedAsset, AssetAdjustment) => PersistedAsset)
                                      (roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], persistedAsset: PersistedAsset, floating: Boolean, floatingReason: Option[FloatingReason]
  ): Option[AssetBeforeUpdate]= {

    roadLinks.find(_.linkId == persistedAsset.linkId) match {
      case Some(roadLink) =>
        PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match {
          case Some(adjustment) =>
            adjustmentOperation(persistedAsset, adjustment, roadLink)
            val persitedAsset = createOperation(persistedAsset, adjustment)
            Some(AssetBeforeUpdate(persitedAsset, adjustment.floating, Some(FloatingReason.Unknown)))
          case None if floating =>  None
          case _ =>
            PointAssetFiller.snapPersistedAssetToRoadLink(persistedAsset, roadLink) match {
              case Some(adjustment) =>
                adjustmentOperation(persistedAsset, adjustment, roadLink)
                Some(AssetBeforeUpdate(createOperation(persistedAsset, adjustment), adjustment.floating, Some(FloatingReason.Unknown)))
              case _ => None
            }
        }
      case _ => None
    }
  }

  protected def fetchFloatingAssets(addQueryFilter: String => String, isOperator: Option[Boolean]): Seq[(Long, String, Long, Option[Long])] ={
    var query = s"""
          select a.$idField, m.name_fi, lrm.link_id, null
          from asset a
          join municipality m on a.municipality_code = m.id
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          where asset_type_id = $typeId and floating = '1' and (valid_to is null or valid_to > current_timestamp)"""

    StaticQuery.queryNA[(Long, String, Long, Option[Long])](addQueryFilter(query)).list
  }

  protected def getFloatingPointAssets(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Seq[FloatingPointAsset] = {
    withDynTransaction {
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
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(mapRoadLinks, roadLinks, Seq(), (_, _, _, _, _) => None, withMunicipality(municipalityCode))
  }

  protected def getByMunicipality[T](mapRoadLinks: Map[Long, RoadLink], roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo],
            adjustment: (Seq[RoadLink], Seq[ChangeInfo], PersistedAsset, Boolean, Option[FloatingReason]) => Option[AssetBeforeUpdate], withFilter: String => String): Seq[PersistedAsset] = {

    def linkIdToRoadLink(linkId: Long): Option[RoadLinkLike] =
      mapRoadLinks.get(linkId)

    withDynTransaction {
      fetchPointAssets(withFilter)
        .map(withFloatingUpdate(adjustPersistedAsset(setFloating, linkIdToRoadLink, changeInfo, roadLinks, adjustment)))
        .toList
    }
  }

  def getById(id: Long): Option[PersistedAsset] = {
    val persistedAsset = getPersistedAssetsByIds(Set(id)).headOption
    val roadLinks: Option[RoadLinkLike] = persistedAsset.flatMap { x => roadLinkService.getRoadLinkByLinkIdFromVVH(x.linkId) }

    def findRoadlink(linkId: Long): Option[RoadLinkLike] =
      roadLinks.find(_.linkId == linkId)

    withDynTransaction {
      persistedAsset.map(withFloatingUpdate(convertPersistedAsset(setFloating, findRoadlink)))
    }
  }

  def getNormalAndComplementaryById(id: Long, roadLink: RoadLink): Option[PersistedAsset] = {
    val persistedAsset = getPersistedAssetsByIds(Set(id)).headOption
    val roadLinks: Option[RoadLinkLike] = Some(roadLink)

    def findRoadlink(linkId: Long): Option[RoadLinkLike] =
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

  def getPersistedAssetsByLinkId(linkId: Long): Seq[PersistedAsset] = {
    withDynSession {
      getPersistedAssetsByLinkIdWithoutTransaction(linkId)
    }
  }

  def getPersistedAssetsByLinkIdWithoutTransaction(linkId: Long): Seq[PersistedAsset] = {
    val filter = s"where a.asset_type_id = $typeId and lp.link_Id = $linkId"
    fetchPointAssets(withFilter(filter))
  }

  def getPersistedAssetsByLinkIdsWithoutTransaction(linkIds: Set[Long]): Seq[PersistedAsset] = {
    MassQuery.withIds(linkIds) { idTableName =>
      val filter = s"join $idTableName i on i.id = lp.link_id where a.asset_type_id = $typeId"
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
                                         roadLinkByLinkId: Long => Option[RoadLinkLike])
                                        (persistedStop: PersistedAsset): (T, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    (setFloating(persistedStop, floating), floatingReason)
  }

  protected def adjustPersistedAsset[T](setFloating: (PersistedAsset, Boolean) => T,
                                        roadLinkByLinkId: Long => Option[RoadLinkLike],
                                        changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink],
                                        adjustment: (Seq[RoadLink], Seq[ChangeInfo], PersistedAsset, Boolean, Option[FloatingReason]) => Option[AssetBeforeUpdate])
                                       (persistedAsset: PersistedAsset): (T, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedAsset, roadLinkByLinkId(persistedAsset.linkId))

    adjustment(roadLinks, changeInfo, persistedAsset, floating, floatingReason)  match {
      case Some(adjustment) =>
        (setFloating(adjustment.asset, adjustment.persistedFloating), adjustment.floatingReason)
      case _ =>
        (setFloating(persistedAsset, floating), floatingReason)
    }
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

  protected def updateFloating(id: Long, floating: Boolean, floatingReason: Option[FloatingReason]): Unit = sqlu"""update asset set floating = $floating where id = $id""".execute

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

  def getValidityDirection(point: Point, roadLink: RoadLink, optBearing: Option[Int], twoSided: Boolean = false) : Int = {
    if (twoSided)
      BothDirections.value
    else
      SideCode.apply(optBearing match {
        case Some(bearing) => getAssetValidityDirection(bearing)
        case _ => getValidityDirectionByGeometry(point, roadLink.geometry)
      }).value
  }

  def getValidityDirectionByGeometry(assetLocation: Point, geometry: Seq[Point]): Int = {

    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(assetLocation.x, assetLocation.y, 0), geometry)
    val roadLinkPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue)
    val linkBearing = GeometryUtils.calculateBearing(geometry, Some(mValue))

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