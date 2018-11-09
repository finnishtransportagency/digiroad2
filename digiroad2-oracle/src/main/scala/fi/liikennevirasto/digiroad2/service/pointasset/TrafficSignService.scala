package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.{asset, _}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.SideCode._
import fi.liikennevirasto.digiroad2.client.tierekisteri.TRTrafficSignType
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrafficSignDao, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import org.slf4j.LoggerFactory
import org.joda.time.DateTime

case class TrafficSignProvider(id: Long, roadLink: RoadLink)
case class TrafficSignInfo(id: Long, linkId: Long, validityDirection: Int, signType: Int, mValue: Double, roadLink: RoadLink)
case class IncomingTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleProperty], validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

class TrafficSignService(val roadLinkService: RoadLinkService, val userProvider: UserProvider, eventBusImpl: DigiroadEventBus, manoeuvreService: ManoeuvreService, prohibitionService: ProhibitionService ) extends PointAssetOperations {

  def eventBus: DigiroadEventBus = eventBusImpl
  type IncomingAsset = IncomingTrafficSign
  type PersistedAsset = PersistedTrafficSign
  val logger = LoggerFactory.getLogger(getClass)

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _ )

  override def typeId: Int = 300

  override def setAssetPosition(asset: IncomingTrafficSign, geometry: Seq[Point], mValue: Double): IncomingTrafficSign = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"
  private val counterPublicId = "counter"
  private val counterDisplayValue = "Merkkien määrä"
  private val batchProcessName = "batch_process_trafficSigns"
  private val groupingDistance = 2

  private val additionalInfoTypeGroups =
    Set(
      TrafficSignTypeGroup.GeneralWarningSigns,
      TrafficSignTypeGroup.ProhibitionsAndRestrictions,
      TrafficSignTypeGroup.AdditionalPanels
    )

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = OracleTrafficSignDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: PersistedTrafficSign, floating: Boolean): PersistedTrafficSign = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingTrafficSign, username: String, roadLink: RoadLink): Long = {
    withDynTransaction {
      createWithoutTransaction(asset, username, roadLink)
    }
  }

  def createWithoutTransaction(asset: IncomingTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    val id = OracleTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)

    eventBus.publish("trafficSignCreate", TrafficSignProvider(id, roadLink))
    id
  }

  def createFloating(asset: IncomingTrafficSign, username: String, municipality: Int): Long = {
    withDynTransaction {
      createFloatingWithoutTransaction(asset, username, municipality)
    }
  }

  def createFloatingWithoutTransaction(asset: IncomingTrafficSign, username: String, municipality: Int): Long = {
    OracleTrafficSignDao.createFloating(asset, 0, username, municipality, VVHClient.createVVHTimeStamp(), LinkGeomSource.Unknown, floating = true)
  }

  override def update(id: Long, updatedAsset: IncomingTrafficSign, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingTrafficSign, roadLink: RoadLink, username: String, mValue: Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.bearing != updatedAsset.bearing || (old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) || old.validityDirection != updatedAsset.validityDirection =>
        expireWithoutTransaction(id)
        val newId = OracleTrafficSignDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
        eventBus.publish("trafficSignUpdate", ((newId, roadLink), id))
        newId
      case _ =>
        OracleTrafficSignDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, value), value, roadLink.municipalityCode, username, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    val result = super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
    val (pc, others) = result.partition(asset => asset.createdBy.contains(batchProcessName) && asset.propertyData.find(_.publicId == typePublicId).get.values.head.propertyValue.toInt == TrafficSignType.PedestrianCrossing.value)

    sortCrossings(pc, Seq()) ++ others
  }

  def sortCrossings(sorted: Seq[PersistedAsset], result: Seq[PersistedAsset]): Seq[PersistedAsset] = {
    val centerSignOpt = sorted.headOption
    if(centerSignOpt.nonEmpty) {
      val centerSign = centerSignOpt.get
      val (inProximity, outsiders) = sorted.tail.partition(sign => centerSign.linkId == sign.linkId && centerSign.validityDirection == sign.validityDirection && GeometryUtils.withinTolerance(Seq(Point(centerSign.lon, centerSign.lat)), Seq(Point(sign.lon, sign.lat)), tolerance = groupingDistance))
      val counterProp = Property(0, counterPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue((1 + inProximity.size).toString, Some(counterDisplayValue))))
      val withCounter = centerSign.copy(propertyData = centerSign.propertyData ++ Seq(counterProp))
      sortCrossings(outsiders, result ++ Seq(withCounter))
    } else {
      result
    }
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingTrafficSign(adjustment.lon, adjustment.lat, adjustment.linkId,
      persistedAsset.propertyData.map(prop => SimpleProperty(prop.publicId, prop.values)).toSet,
      persistedAsset.validityDirection, persistedAsset.bearing)

    updateWithoutTransaction(adjustment.assetId, updated, roadLink,
      "vvh_generated", Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
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

  def getAssetValidityDirection(bearing: Int): Int = {
    bearing >= 270 || bearing < 90 match {
      case true => AgainstDigitizing.value
      case false => TowardsDigitizing.value
    }
  }

  def getTrafficSignValidityDirection(assetLocation: Point, geometry: Seq[Point]): Int = {

    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(assetLocation.x, assetLocation.y, 0), geometry)
    val roadLinkPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue)
    val linkBearing = GeometryUtils.calculateBearing(geometry)

    val lonDifference = assetLocation.x - roadLinkPoint.get.x
    val latDifference = assetLocation.y - roadLinkPoint.get.y

    (latDifference <= 0 && linkBearing <= 90) || (latDifference >= 0 && linkBearing > 270) match {
      case true => TowardsDigitizing.value
      case false => AgainstDigitizing.value
    }
  }

  def getAssetBearing(validityDirection: Int, geometry: Seq[Point]): Int = {
    val linkBearing = GeometryUtils.calculateBearing(geometry)
    GeometryUtils.calculateActualBearing(validityDirection, Some(linkBearing)).get
  }

  private def generateProperties(tRTrafficSignType: TRTrafficSignType, value: Any, additionalInfo: String) = {
    val signValue = value.toString
    val signAdditionalInfo = additionalInfo
    val trafficType = tRTrafficSignType.trafficSignType
    val typeProperty = SimpleProperty(typePublicId, Seq(PropertyValue(trafficType.value.toString)))
    val valueProperty = additionalInfoTypeGroups.exists(group => group == trafficType.group) match {
      case true => SimpleProperty(infoPublicId, Seq(PropertyValue(signAdditionalInfo)))
      case _ => SimpleProperty(valuePublicId, Seq(PropertyValue(signValue)))
    }

    Set(typeProperty, valueProperty)
  }

  def createFromCoordinates(lon: Long, lat: Long, trafficSignType: TRTrafficSignType, value: Option[Int],
                            twoSided: Option[Boolean], trafficDirection: TrafficDirection, bearing: Option[Int],
                            additionalInfo: Option[String], roadLinks: Seq[VVHRoadlink]): Long = {

      val closestLink: VVHRoadlink = roadLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon, lat), r.geometry))
      val (vvhRoad, municipality) = (roadLinks.filter(_.administrativeClass != State), closestLink.municipalityCode)

      if (vvhRoad.isEmpty || vvhRoad.size > 1) {
        val asset = IncomingTrafficSign(lon, lat, 0, generateProperties(trafficSignType, value.getOrElse(""), additionalInfo.getOrElse("")), SideCode.Unknown.value, None)
        createFloatingWithoutTransaction(asset, userProvider.getCurrentUser().username, municipality)
      }
      else {
        roadLinkService.enrichRoadLinksFromVVH(Seq(closestLink)).map { roadLink =>
          val validityDirection =
            bearing match {
              case Some(assetBearing) if twoSided.getOrElse(false) => BothDirections.value
              case Some(assetBearing) => getAssetValidityDirection(assetBearing)
              case None if twoSided.getOrElse(false) => BothDirections.value
              case _ => getTrafficSignValidityDirection(Point(lon, lat), roadLink.geometry)
            }
          val newAsset = IncomingTrafficSign(lon, lat, roadLink.linkId, generateProperties(trafficSignType, value.getOrElse(""), additionalInfo.getOrElse("")), validityDirection, Some(GeometryUtils.calculateBearing(roadLink.geometry)))
          checkDuplicates(newAsset) match {
            case Some(existingAsset) =>
              updateWithoutTransaction(existingAsset.id, newAsset, roadLink, userProvider.getCurrentUser().username, None, None)
            case _ =>
              createWithoutTransaction(newAsset, userProvider.getCurrentUser().username, roadLink)
          }
        }
      }.head
  }

  def checkDuplicates(asset: IncomingTrafficSign): Option[PersistedTrafficSign] = {
    val signToCreateLinkId = asset.linkId
    val signToCreateType = getTrafficSignsProperties(asset, typePublicId).get.propertyValue.toInt
    val signToCreateDirection = asset.validityDirection
    val groupType = Some(TrafficSignTypeGroup.apply(signToCreateType))

    val trafficSignsInRadius = getTrafficSignByRadius(Point(asset.lon, asset.lat), 10, groupType).filter(
      ts =>
        getTrafficSignsProperties(ts, typePublicId).get.propertyValue.toInt == signToCreateType
          && ts.linkId == signToCreateLinkId && ts.validityDirection == signToCreateDirection
    )

    if (trafficSignsInRadius.nonEmpty) {
      return Some(getLatestModifiedAsset(trafficSignsInRadius))
    }
    None
  }

  def getTrafficSignByRadius(position: Point, meters: Int, optGroupType: Option[TrafficSignTypeGroup]): Seq[PersistedTrafficSign] = {
    val assets = OracleTrafficSignDao.fetchByRadius(position, meters)
    optGroupType match {
      case Some(groupType) => assets.filter(asset => TrafficSignTypeGroup.apply(asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.propertyValue.toInt) == groupType)
      case _ => assets
    }
  }

  def getTrafficSignsWithTrafficRestrictions( municipality: Int, enumeratedValueIds: Boolean => Seq[Long], newTransaction: Boolean = true): Seq[PersistedTrafficSign] = {
    val enumeratedValues = enumeratedValueIds(newTransaction)
    if(newTransaction)
        withDynSession {
          OracleTrafficSignDao.fetchByTurningRestrictions(enumeratedValues, municipality)
        }
    else {
      OracleTrafficSignDao.fetchByTurningRestrictions(enumeratedValues, municipality)
    }
  }


  def getRestrictionsEnumeratedValues(newTransaction: Boolean = true): Seq[Long] = {
    if(newTransaction)
      withDynSession {
        OracleTrafficSignDao.fetchEnumeratedValueIds(Seq(TrafficSignType.NoLeftTurn, TrafficSignType.NoRightTurn, TrafficSignType.NoUTurn))
      }
    else {
      OracleTrafficSignDao.fetchEnumeratedValueIds(Seq(TrafficSignType.NoLeftTurn, TrafficSignType.NoRightTurn, TrafficSignType.NoUTurn))
    }
  }

  def getProhibitionsEnumeratedValues(newTransaction: Boolean = true): Seq[Long] = {
    val trafficSignValues = Seq(TrafficSignType.ClosedToAllVehicles, TrafficSignType.NoPowerDrivenVehicles, TrafficSignType.NoLorriesAndVans, TrafficSignType.NoVehicleCombinations,
      TrafficSignType.NoAgriculturalVehicles, TrafficSignType.NoMotorCycles, TrafficSignType.NoMotorSledges, TrafficSignType.NoBuses, TrafficSignType.NoMopeds,
      TrafficSignType.NoCyclesOrMopeds, TrafficSignType.NoPedestrians, TrafficSignType.NoPedestriansCyclesMopeds, TrafficSignType.NoRidersOnHorseback)

    if(newTransaction)
      withDynSession {
        OracleTrafficSignDao.fetchEnumeratedValueIds(trafficSignValues)
      }
    else {
      OracleTrafficSignDao.fetchEnumeratedValueIds(trafficSignValues)
    }
  }

  override def expire(id: Long, username: String): Long = {
    withDynSession {
      expireWithoutTransaction(id, username)
    }
    eventBus.publish("trafficSignExpire", id)
    id
  }

  def expireAssetsByMunicipality(municipality: Int) : Unit = {
    OracleTrafficSignDao.expireAssetsByMunicipality(municipality)
  }

  def expireAssetWithoutTransaction(id: Long, username: String): Long = {
    expireWithoutTransaction(id)
    id
  }

  def expire(linkIds: Set[Long], username: String, newTransaction: Boolean = true) = {
    if(newTransaction)
    withDynSession {
      OracleTrafficSignDao.expire(linkIds, username)
    } else
      OracleTrafficSignDao.expire(linkIds, username)
  }

//  def getTrafficType(id: Long) : Option[Int] = {
//    withDynSession {
//      OracleTrafficSignDao.getTrafficSignType(id)
//    }
//  }

  def getLatestModifiedAsset(trafficSigns: Seq[PersistedTrafficSign]): PersistedTrafficSign = {
    trafficSigns.maxBy { ts => ts.modifiedAt.getOrElse(ts.createdAt.get) }
  }

  def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }

  def getTrafficSignsProperties(trafficSign: IncomingTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }

  def getTrafficSignsByDistance(sign: PersistedAsset, groupedAssets: Map[Long, Seq[PersistedAsset]], distance: Int): Seq[PersistedTrafficSign]={
    val sameLinkAssets = groupedAssets.getOrElse(sign.linkId, Seq())

    sameLinkAssets.filter{ ts =>
      (getTrafficSignsProperties(ts, typePublicId).get.propertyValue.toInt == getTrafficSignsProperties(sign, typePublicId).get.propertyValue.toInt) &&
        ts.validityDirection == sign.validityDirection &&
        GeometryUtils.geometryLength(Seq(Point(sign.lon, sign.lat), Point(ts.lon, ts.lat))) <= distance
    }
  }

  def trafficSignsCreateAssets(trafficSignInfo: TrafficSignInfo): Unit = {

    if (TrafficSignType.belongsToManoeuvre(trafficSignInfo.signType)) {
      manoeuvreService.createBasedOnTrafficSign(trafficSignInfo)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignInfo.signType)) {
      prohibitionService.createBasedOnTrafficSign(trafficSignInfo)
    }
  }

  def trafficSignsDeleteAssets(id: Long, trafficSignType: Int): Unit = {
    if (TrafficSignType.belongsToManoeuvre(trafficSignType)) {
      manoeuvreService.deleteManoeuvreFromSign(id)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignType)) {
      prohibitionService.deleteAssetBasedOnSign(prohibitionService.withId(id))
    }
  }

  override def expireAssetsByMunicipalities(municipalityCodes: Set[Int]) : Unit = {
    OracleTrafficSignDao.expireAssetsByMunicipality(municipalityCodes)
  }
}
