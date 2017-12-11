package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrafficSignDao, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User

case class IncomingTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleProperty], validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

sealed trait TrafficSignTypeGroup {
  def value: Int
}
object TrafficSignTypeGroup {
  val values = Set(Unknown, SpeedLimits, PedestrianCrossing, MaximumRestrictions, GeneralWarningSigns, ProhibitionsAndRestrictions)

  def apply(intValue: Int): TrafficSignTypeGroup = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object SpeedLimits extends TrafficSignTypeGroup { def value = 1  }
  case object PedestrianCrossing extends TrafficSignTypeGroup { def value = 2 }
  case object MaximumRestrictions extends TrafficSignTypeGroup { def value = 3 }
  case object GeneralWarningSigns extends TrafficSignTypeGroup { def value = 4 }
  case object ProhibitionsAndRestrictions extends TrafficSignTypeGroup { def value = 5 }
  case object Unknown extends TrafficSignTypeGroup { def value = 99 }
}

sealed trait TrafficSignType {
  def value: Int
  def group: TrafficSignTypeGroup
}
object TrafficSignType {
  val values = Set(Unknown, SpeedLimit, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea, PedestrianCrossing, MaximumLength, Warning, NoLeftTurn,
    NoRightTurn, NoUTurn, ClosedToAllVehicles, NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges,
    NoVehiclesWithDangerGoods, NoBuses, NoMopeds, NoCyclesOrMopeds, NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, NoEntry, OvertakingProhibited,
    EndProhibitionOfOvertaking, NoWidthExceeding, MaxHeightExceeding, MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding,
    WRightBend, WLeftBend, WSeveralBendsRight, WSeveralBendsLeft, WDangerousDescent, WSteepAscent, WUnevenRoad, WChildren)

  def apply(intValue: Int): TrafficSignType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object SpeedLimit extends TrafficSignType { def value = 1;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndSpeedLimit extends TrafficSignType { def value = 2;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object SpeedLimitZone extends TrafficSignType { def value = 3;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndSpeedLimitZone extends TrafficSignType { def value = 4;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object UrbanArea extends TrafficSignType { def value = 5;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndUrbanArea extends TrafficSignType { def value = 6;  def group = TrafficSignTypeGroup.SpeedLimits; }
  case object PedestrianCrossing extends TrafficSignType { def value = 7;  def group = TrafficSignTypeGroup.PedestrianCrossing; }
  case object MaximumLength extends TrafficSignType { def value = 8;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object Warning extends TrafficSignType { def value = 9;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object NoLeftTurn extends TrafficSignType { def value = 10;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoRightTurn extends TrafficSignType { def value = 11;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoUTurn extends TrafficSignType { def value = 12;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object ClosedToAllVehicles extends TrafficSignType { def value = 13;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPowerDrivenVehicles extends TrafficSignType { def value = 14;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoLorriesAndVans extends TrafficSignType { def value = 15;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoVehicleCombinations extends TrafficSignType { def value = 16;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoAgriculturalVehicles extends TrafficSignType { def value = 17;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMotorCycles extends TrafficSignType { def value = 18;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMotorSledges extends TrafficSignType { def value = 19;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoVehiclesWithDangerGoods extends TrafficSignType { def value = 20;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoBuses extends TrafficSignType { def value = 21;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMopeds extends TrafficSignType { def value = 22;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoCyclesOrMopeds extends TrafficSignType { def value = 23;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPedestrians extends TrafficSignType { def value = 24;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPedestriansCyclesMopeds extends TrafficSignType { def value = 25;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoRidersOnHorseback extends TrafficSignType { def value = 26;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoEntry extends TrafficSignType { def value = 27;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object OvertakingProhibited extends TrafficSignType { def value = 28;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object EndProhibitionOfOvertaking extends TrafficSignType { def value = 29;  def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoWidthExceeding extends TrafficSignType { def value = 30;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxHeightExceeding extends TrafficSignType { def value = 31;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxLadenExceeding extends TrafficSignType { def value = 32;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxMassCombineVehiclesExceeding extends TrafficSignType { def value = 33;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxTonsOneAxleExceeding extends TrafficSignType { def value = 34;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object MaxTonsOnBogieExceeding extends TrafficSignType { def value = 35;  def group = TrafficSignTypeGroup.MaximumRestrictions; }
  case object WRightBend extends TrafficSignType { def value = 36;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WLeftBend extends TrafficSignType { def value = 37;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSeveralBendsRight extends TrafficSignType { def value = 38;  def group = TrafficSignTypeGroup.GeneralWarningSigns ; }
  case object WSeveralBendsLeft extends TrafficSignType { def value = 39;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WDangerousDescent extends TrafficSignType { def value = 40;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSteepAscent extends TrafficSignType { def value = 41;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WUnevenRoad extends TrafficSignType { def value = 42;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WChildren extends TrafficSignType { def value = 43;  def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object Unknown extends TrafficSignType { def value = 99;  def group = TrafficSignTypeGroup.Unknown; }
}

class TrafficSignService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficSign
  type PersistedAsset = PersistedTrafficSign

  override def typeId: Int = 300

  private def setAssetPosition(asset: IncomingAsset, geometry: Seq[Point], mValue: Double): IncomingAsset = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = OracleTrafficSignDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: PersistedTrafficSign, floating: Boolean): PersistedTrafficSign = {
    persistedAsset.copy(floating = floating)
  }

//  override def create(asset: IncomingTrafficSign, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass] = None, linkSource: LinkGeomSource): Long = {
  override def create(asset: IncomingTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), roadLink.geometry)
    withDynTransaction {
      OracleTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficSignDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality, username, Some(VVHClient.createVVHTimeStamp()), linkSource)
    }
    id
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment): Long = {
    val updated = IncomingTrafficSign(adjustment.lon, adjustment.lat, adjustment.linkId,
      persistedAsset.propertyData.map(prop => SimpleProperty(prop.publicId, prop.values)).toSet,
      persistedAsset.validityDirection, persistedAsset.bearing)

    OracleTrafficSignDao.update(adjustment.assetId, updated, adjustment.mValue, persistedAsset.municipalityCode,
      "vvh_generated", Some(adjustment.vvhTimeStamp), persistedAsset.linkSource)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {
    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.propertyData, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.validityDirection, persistedStop.bearing,
      persistedStop.linkSource)
  }

}
