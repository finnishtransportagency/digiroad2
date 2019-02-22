package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.Asset.DateTimeSimplifiedFormat
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.SideCode._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrafficSignDao, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.{ProhibitionValue, RoadLink, RoadLinkLike, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import org.slf4j.LoggerFactory
import org.joda.time.DateTime

case class IncomingTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleTrafficSignProperty], validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset
case class AdditionalPanelInfo(mValue: Double, linkId: Long, propertyData: Set[SimpleTrafficSignProperty], validityDirection: Int, position: Option[Point] = None, id: Option[Long] = None)
case class TrafficSignInfo(id: Long, linkId: Long, validityDirection: Int, signType: Int, mValue: Double, roadLink: RoadLink, additionalPanel: Seq[AdditionalPanel])

class TrafficSignService(val roadLinkService: RoadLinkService, val userProvider: UserProvider, eventBusImpl: DigiroadEventBus) extends PointAssetOperations {
  def eventBus: DigiroadEventBus = eventBusImpl
  type IncomingAsset = IncomingTrafficSign
  type PersistedAsset = PersistedTrafficSign
  val logger = LoggerFactory.getLogger(getClass)

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _ )

  override def typeId: Int = TrafficSigns.typeId

  override def setAssetPosition(asset: IncomingTrafficSign, geometry: Seq[Point], mValue: Double): IncomingTrafficSign = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  val typePublicId = "trafficSigns_type"
  val valuePublicId = "trafficSigns_value"
  val infoPublicId = "trafficSigns_info"
  val additionalPublicId = "additional_panel"
  private val counterPublicId = "counter"
  private val counterDisplayValue = "Merkkien määrä"
  private val batchProcessName = "batch_process_trafficSigns"
  private val GroupingDistance = 2
  private val AdditionalPanelDistance = 2

  private val additionalInfoTypeGroups =
    Set(
      TrafficSignTypeGroup.GeneralWarningSigns,
      TrafficSignTypeGroup.ProhibitionsAndRestrictions,
      TrafficSignTypeGroup.AdditionalPanels
    )

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = OracleTrafficSignDao.fetchByFilter(queryFilter)

  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = OracleTrafficSignDao.fetchByFilterWithExpired(queryFilter)

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

    eventBus.publish("trafficSign:create", TrafficSignInfo(id, asset.linkId, asset.validityDirection, getProperty(asset, typePublicId).get.propertyValue.toInt, mValue, roadLink, Seq()))
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

  override def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method, Try to used") }

  def getChanged(trafficSignTypes: Set[Int], sinceDate: DateTime, untilDate: DateTime): Seq[ChangedPointAsset] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"

    val filter = s"where a.asset_type_id = $typeId and floating = 0 and " +
      s"exists (select * from single_choice_value scv2, enumerated_value ev2 where a.id = scv2.asset_id and scv2.enumerated_value_id = ev2.id and ev2.value in (${trafficSignTypes.mkString(",")})) and (" +
      s"(a.valid_to > $querySinceDate and a.valid_to <= $queryUntilDate) or " +
      s"(a.modified_date > $querySinceDate and a.modified_date <= $queryUntilDate) or "+
      s"(a.created_date > $querySinceDate and a.created_date <= $queryUntilDate)) "

    val assets = withDynSession {
      fetchPointAssetsWithExpired(withFilter(filter))
    }

    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(assets.map(_.linkId).toSet)

    assets.map { asset =>
      ChangedPointAsset(asset, roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available")))    }
  }

  //TODO Review eventBus trafficSign:update on this update method, if it's to keep or not
  def updateWithoutTransaction(id: Long, updatedAsset: IncomingTrafficSign, roadLink: RoadLink, username: String, mValue: Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.bearing != updatedAsset.bearing || (old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) || old.validityDirection != updatedAsset.validityDirection =>
        expireWithoutTransaction(id)
        val newId = OracleTrafficSignDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
        eventBus.publish("trafficSign:update", ((newId, roadLink), id))
        newId
      case _ =>
        val updatedId = OracleTrafficSignDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, value), value, roadLink.municipalityCode, username, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
        eventBus.publish("trafficSign:update", (id, TrafficSignInfo(id, updatedAsset.linkId, updatedAsset.validityDirection, getProperty(updatedAsset, typePublicId).get.propertyValue.toInt, value, roadLink, Seq())))
        updatedId
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    val result = super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
    val (pc, others) = result.partition(asset => asset.createdBy.contains(batchProcessName) && asset.propertyData.find(_.publicId == typePublicId).get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt == PedestrianCrossingSign.OTHvalue)

    sortCrossings(pc, Seq()) ++ others
  }

  def sortCrossings(sorted: Seq[PersistedAsset], result: Seq[PersistedAsset]): Seq[PersistedAsset] = {
    val centerSignOpt = sorted.headOption
    if(centerSignOpt.nonEmpty) {
      val centerSign = centerSignOpt.get
      val (inProximity, outsiders) = sorted.tail.partition(sign => centerSign.linkId == sign.linkId && centerSign.validityDirection == sign.validityDirection && GeometryUtils.withinTolerance(Seq(Point(centerSign.lon, centerSign.lat)), Seq(Point(sign.lon, sign.lat)), tolerance = GroupingDistance))
      val counterProp = TrafficSignProperty(0, counterPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(TextPropertyValue((1 + inProximity.size).toString, Some(counterDisplayValue))))
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
      persistedAsset.propertyData.map(prop => SimpleTrafficSignProperty(prop.publicId, prop.values)).toSet,
      persistedAsset.validityDirection, persistedAsset.bearing)

    updateWithoutTransaction(adjustment.assetId, updated, roadLink,
      "vvh_generated", Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipality(municipalityCode))
  }

  def withMunicipalityAndGroup(municipalityCode: Int, trafficSignTypes: Set[Int])(query: String): String = {
    withFilter(s"""where a.asset_type_id = $typeId and a.municipality_code = $municipalityCode
                    and a.id in( select at.id
                        from asset at
                        join property p on at.asset_type_id = p.asset_type_id
                        join single_choice_value scv on scv.asset_id = at.id and scv.property_id = p.id and p.property_type = 'single_choice'
                        join enumerated_value ev on scv.enumerated_value_id = ev.id and ev.value in (${trafficSignTypes.mkString(",")}))""")(query)

  }

  def getByMunicipalityAndGroup(municipalityCode: Int, groupName: TrafficSignTypeGroup): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    val assetTypes = TrafficSignType.apply(groupName)
    getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipalityAndGroup(municipalityCode, assetTypes))
  }

  def getByMunicipalityExcludeByAdminClass(municipalityCode: Int, filterAdministrativeClass: AdministrativeClass): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    val assets = getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipality(municipalityCode))
    val filterRoadLinks = mapRoadLinks.filterNot(_._2.administrativeClass == filterAdministrativeClass)
    assets.filter{a => filterRoadLinks.get(a.linkId).nonEmpty}
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

  def createFromCoordinates(trafficSign: IncomingTrafficSign, closestLink: RoadLink, nearbyLinks: Seq[VVHRoadlink]): Long = {

    val (vvhRoad, municipality) = (nearbyLinks.filter(_.administrativeClass != State), closestLink.municipalityCode)
    if (vvhRoad.isEmpty || vvhRoad.size > 1)
      createFloatingWithoutTransaction(trafficSign.copy(linkId = 0, bearing = None), userProvider.getCurrentUser().username, municipality)
    else {
      checkDuplicates(trafficSign) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, trafficSign, closestLink, userProvider.getCurrentUser().username, None, None)
        case _ =>
          createWithoutTransaction(trafficSign, userProvider.getCurrentUser().username, closestLink)
      }
    }
  }

  def checkDuplicates(asset: IncomingTrafficSign): Option[PersistedTrafficSign] = {
    val signToCreateLinkId = asset.linkId
    val signToCreateType = getProperty(asset, typePublicId).get.propertyValue.toInt
    val signToCreateDirection = asset.validityDirection
    val groupType = Some(TrafficSignTypeGroup.apply(TrafficSignType.applyOTHValue(signToCreateType).group.value))

    val trafficSignsInRadius = getTrafficSignByRadius(Point(asset.lon, asset.lat), 10, groupType).filter(
      ts =>
        getProperty(ts, typePublicId).get.propertyValue.toInt == signToCreateType
          && ts.linkId == signToCreateLinkId && ts.validityDirection == signToCreateDirection
    )

    if (trafficSignsInRadius.nonEmpty) {
      return Some(getLatestModifiedAsset(trafficSignsInRadius))
    }
    None
  }

  def getTrafficSignByRadius(position: Point, meters: Int, optGroupType: Option[TrafficSignTypeGroup] = None): Seq[PersistedTrafficSign] = {
    val assets = OracleTrafficSignDao.fetchByRadius(position, meters)
    optGroupType match {
      case Some(groupType) => assets.filter(asset =>
        TrafficSignType.applyOTHValue(asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt).group == groupType)
      case _ => assets
    }
  }

  def getTrafficSign(linkIds : Seq[Long]): Seq[PersistedTrafficSign] = {
    OracleTrafficSignDao.fetchByLinkId(linkIds)
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
        OracleTrafficSignDao.fetchEnumeratedValueIds(Seq(NoLeftTurn, NoRightTurn, NoUTurn))
      }
    else {
      OracleTrafficSignDao.fetchEnumeratedValueIds(Seq(NoLeftTurn, NoRightTurn, NoUTurn))
    }
  }

  def getProhibitionsEnumeratedValues(newTransaction: Boolean = true): Seq[Long] = {
    val trafficSignValues = Seq(ClosedToAllVehicles, NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations,
      NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges, NoBuses, NoMopeds,
      NoCyclesOrMopeds, NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback)

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
    eventBus.publish("trafficSign:expire", id)
    id
  }

  def expireAssetWithoutTransaction(filter: String => String, username: Option[String]): Unit = {
    OracleTrafficSignDao.expireWithoutTransaction(filter, username)
  }

  def withIds(ids: Set[Long])(query: String): String = {
    query + s" and id in (${ids.mkString(",")})"
  }

  def withMunicipalities(municipalities: Set[Int])(query: String): String = {
    query + s" and created_by != 'batch_process_trafficSigns' and municipality_code in (${municipalities.mkString(",")})"
  }

  def expire(linkIds: Set[Long], username: String, newTransaction: Boolean = true) : Unit = {
    if(newTransaction)
    withDynSession {
      OracleTrafficSignDao.expire(linkIds, username)
    } else
      OracleTrafficSignDao.expire(linkIds, username)
  }

  def getTrafficType(id: Long) : Option[Int] = {
    withDynSession {
      OracleTrafficSignDao.getTrafficSignType(id)
    }
  }

  def getLatestModifiedAsset(trafficSigns: Seq[PersistedTrafficSign]): PersistedTrafficSign = {
    trafficSigns.maxBy { ts => ts.modifiedAt.getOrElse(ts.createdAt.get) }
  }

  def getProperty(trafficSign: PersistedTrafficSign, property: String) : Option[TextPropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[TextPropertyValue]).headOption
  }

  def getProperty(trafficSign: IncomingTrafficSign, property: String) : Option[TextPropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[TextPropertyValue]).headOption
  }

  def getProperty(propertyData: Set[SimpleTrafficSignProperty], property: String) : Option[TextPropertyValue] = {
    propertyData.find(p => p.publicId == property) match {
      case Some(additionalProperty) => additionalProperty.values.map(_.asInstanceOf[TextPropertyValue]).headOption
      case _ => None
    }
  }

  def getAllProperties(trafficSign: PersistedTrafficSign, property: String) : Seq[PointAssetValue] = {
    trafficSign.propertyData.find(p => p.publicId == property) match {
      case Some(additionalProperty) => additionalProperty.values
      case _ => Seq()
    }
  }

  def getTrafficSignTypeByGroup(trafficSignGroup: TrafficSignTypeGroup): Set[Int] = {
    TrafficSignType.values.filter(_.group == trafficSignGroup).map(_.OTHvalue)
  }

  def getTrafficSignsByDistance(sign: PersistedAsset, groupedAssets: Map[Long, Seq[PersistedAsset]], distance: Int): Seq[PersistedTrafficSign]={
    val sameLinkAssets = groupedAssets.getOrElse(sign.linkId, Seq())

    sameLinkAssets.filter{ ts =>
      (getProperty(ts, typePublicId).get.propertyValue.toInt == getProperty(sign, typePublicId).get.propertyValue.toInt) &&
        ts.validityDirection == sign.validityDirection &&
        GeometryUtils.geometryLength(Seq(Point(sign.lon, sign.lat), Point(ts.lon, ts.lat))) <= distance
    }
  }

  def expireAssetsByMunicipalities(municipalityCodes: Set[Int]) : Unit = {
    OracleTrafficSignDao.expireAssetsByMunicipality(municipalityCodes)
  }


  def getPointOfInterest(first: Point, last: Point, sideCode: SideCode): Seq[Point] = {
    sideCode match {
      case SideCode.TowardsDigitizing => Seq(last)
      case SideCode.AgainstDigitizing => Seq(first)
      case _ => Seq(first, last)
    }
  }

  def getValidityDirection(point: Point, roadLink: RoadLink, optBearing: Option[Int], twoSided: Boolean = false) : Int = {
    if (twoSided)
      BothDirections.value
    else
      SideCode.apply(optBearing match {
      case Some(bearing) => getAssetValidityDirection(bearing)
      case _ => getTrafficSignValidityDirection(point, roadLink.geometry)
    }).value
  }

  def additionalPanelProperties(additionalProperties: Set[AdditionalPanelInfo]) : Set[SimpleTrafficSignProperty] = {
    val orderedAdditionalPanels = additionalProperties.toSeq.sortBy(_.propertyData.find(_.publicId == typePublicId).get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt).toSet
    val additionalPanelsProperty = orderedAdditionalPanels.zipWithIndex.map{ case (panel, index) =>
      AdditionalPanel(getProperty(panel.propertyData, typePublicId).get.propertyValue.toInt,
        getProperty(panel.propertyData, infoPublicId).getOrElse(TextPropertyValue("")).propertyValue,
        getProperty(panel.propertyData, valuePublicId).getOrElse(TextPropertyValue("")).propertyValue,
        index)
    }.toSeq


    if(additionalPanelsProperty.nonEmpty)
      Set(SimpleTrafficSignProperty(additionalPublicId, additionalPanelsProperty))
    else
      Set()
  }

  def getAdditionalPanels(linkId: Long, mValue: Double, validityDirection: Int, signPropertyValue: Int, geometry: Seq[Point],
                          additionalPanels: Set[AdditionalPanelInfo], roadLinks: Seq[VVHRoadlink]) : Set[AdditionalPanelInfo] = {

    val trafficSign =  TrafficSignType.applyOTHValue(signPropertyValue)

    def panelWithSamePosition(panel: AdditionalPanelInfo) : Boolean = {
      Math.abs(panel.mValue - mValue) < 0.001 && linkId == panel.linkId
    }

    def allowedAdditionalPanels: Seq[AdditionalPanelInfo] = additionalPanels.toSeq.filter {panel =>
      panelWithSamePosition(panel) ||
        trafficSign.allowed(TrafficSignType.applyOTHValue(getProperty(panel.propertyData, typePublicId).
          get.propertyValue.toInt).asInstanceOf[AdditionalPanelsType])
    }

    val (first, last) = GeometryUtils.geometryEndpoints(geometry)
    Seq(first, last).flatMap { point =>
      val signDistance = if (GeometryUtils.areAdjacent(point, first)) mValue else GeometryUtils.geometryLength(geometry) - mValue
      if (signDistance > AdditionalPanelDistance) {
        allowedAdditionalPanels.filter { additionalPanel =>
          Math.abs(additionalPanel.mValue - mValue) <= AdditionalPanelDistance &&
            additionalPanel.validityDirection == validityDirection &&
            additionalPanel.linkId == linkId
        }
      } else {
        val adjacentRoadLinkInfo = getAdjacent(point, linkId, roadLinks)
        if (adjacentRoadLinkInfo.isEmpty) {
          allowedAdditionalPanels.filter { additionalPanel =>
            Math.abs(additionalPanel.mValue - mValue) <= AdditionalPanelDistance &&
              additionalPanel.validityDirection == validityDirection &&
              additionalPanel.linkId == linkId
          }
        } else if (adjacentRoadLinkInfo.size == 1) {
          allowedAdditionalPanels.filter(additionalSign => additionalSign.validityDirection == validityDirection && additionalSign.linkId == linkId) ++
            allowedAdditionalPanels.filter { additionalSign =>
              val additionalPanelDistance = if (GeometryUtils.areAdjacent(adjacentRoadLinkInfo.map(_._1).head, point)) additionalSign.mValue else GeometryUtils.geometryLength(adjacentRoadLinkInfo.map(_._4).head) - additionalSign.mValue
              additionalSign.validityDirection == validityDirection && additionalSign.linkId == adjacentRoadLinkInfo.map(_._3).head && (additionalPanelDistance + signDistance) <= AdditionalPanelDistance
            }
        } else
          Seq()
      }
    }.toSet
  }

  def getAdjacent(point: Point, linkId: Long, roadLinks : Seq[VVHRoadlink]): Seq[(Point, Point, Long, Seq[Point])] = {
    if (roadLinks.isEmpty) {
      roadLinkService.getAdjacent(linkId, Seq(point), false).map { adjacentRoadLink =>
        val (start, end) = GeometryUtils.geometryEndpoints(adjacentRoadLink.geometry)
        (start, end, adjacentRoadLink.linkId, adjacentRoadLink.geometry)
      }
    } else {
      roadLinks.flatMap { roadLink =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
        if (GeometryUtils.areAdjacent(point, first) || GeometryUtils.areAdjacent(point, last))
          Seq((first, last, roadLink.linkId, roadLink.geometry))
        else
          Seq()
      }
    }
  }

  def expireAssetsByLinkId(linkIds: Seq[Long], signsType: Set[Int] = Set(), username: Option[String] = None) : Unit = {
    OracleTrafficSignDao.expireAssetByLinkId(linkIds, signsType, username)
  }

  def getLastExecutionDate(createdBy: String, signTypes: Set[Int]) : Option[DateTime] = {
    OracleTrafficSignDao.getLastExecutionDate(createdBy, signTypes)
  }

  def getAfterDate(sinceDate: Option[DateTime]): Seq[PersistedTrafficSign] = {
    val filter =
      sinceDate match {
        case Some(date) =>
          val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(date)}', 'YYYYMMDDHH24MI')"
          s"where a.asset_type_id = $typeId and floating = 0 and (a.valid_to > $querySinceDate or a.modified_date > $querySinceDate or a.created_date > $querySinceDate)"
        case _ =>
          s"where a.asset_type_id = $typeId and floating = 0 and a.valid_to is null"
      }

    fetchPointAssetsWithExpired(withFilter(filter))
  }
}
