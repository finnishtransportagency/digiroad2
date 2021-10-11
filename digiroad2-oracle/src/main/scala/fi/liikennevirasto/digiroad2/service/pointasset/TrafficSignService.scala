package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimeSimplifiedFormat
import fi.liikennevirasto.digiroad2.asset.SideCode._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{PostGISTrafficSignDao, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.lane.LaneType
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.slf4j.LoggerFactory
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat


case class IncomingTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty], validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset
case class AdditionalPanelInfo(mValue: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty], validityDirection: Int, position: Option[Point] = None, id: Option[Long] = None)
case class TrafficSignInfo(id: Long, linkId: Long, validityDirection: Int, signType: Int, roadLink: RoadLink)
case class TrafficSignInfoUpdate(newSign: TrafficSignInfo, oldSign: PersistedTrafficSign)

class TrafficSignService(val roadLinkService: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetOperations {
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
  val mainSignText="main_sign_text"
  val additionalPublicId = "additional_panel"
  val additionalPanelSizePublicId = "size"
  val additionalPanelTextPublicId = "text"
  val additionalPanelCoatingTypePublicId = "coating_type"
  val additionalPanelColorPublicId = "additional_panel_color"
  val suggestedPublicId = "suggest_box"
  val oldTrafficCodePublicId = "old_traffic_code"
  val lifeCyclePublicId = "life_cycle"
  val trafficSignStartDatePublicId = "trafficSign_start_date"
  val trafficSignEndDatePublicId = "trafficSign_end_date"
  val newTrafficCodeStartDate = DateTime.parse("1.6.2020", DateTimeFormat.forPattern("dd.MM.yyyy"))
  private val counterPublicId = "counter"
  private val counterDisplayValue = "Merkkien määrä"
  private val batchProcessName = "batch_process_trafficSigns"
  private val GroupingDistance = 2
  private val AdditionalPanelDistance = 2

  private val getSingleChoiceDefaultValueByPublicId = Map(
    "structure" -> PointAssetStructure.getDefault.value,
    "condition" -> Condition.getDefault.value,
    "size" -> Size.getDefault.value,
    "coating_type" -> CoatingType.getDefault.value,
    "sign_material" -> SignMaterial.getDefault.value,
    "location_specifier" -> LocationSpecifier.getDefault.value,
    "lane_type" -> LaneType.getDefault.value,
    "life_cycle" -> PointAssetState.getDefault.value,
    "type_of_damage" -> TypeOfDamage.getDefault.value,
    "urgency_of_repair" -> UrgencyOfRepair.getDefault.value
  )

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = PostGISTrafficSignDao.fetchByFilter(queryFilter)

  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedTrafficSign] = PostGISTrafficSignDao.fetchByFilterWithExpired(queryFilter)

  def fetchByFilterWithExpiredByIds(ids: Set[Long]): Seq[PersistedTrafficSign] = PostGISTrafficSignDao.fetchByFilterWithExpiredByIds(ids)

  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PersistedTrafficSign] = PostGISTrafficSignDao.fetchByFilterWithExpiredLimited(queryFilter, token)

  override def setFloating(persistedAsset: PersistedTrafficSign, floating: Boolean): PersistedTrafficSign = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingTrafficSign, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    if(newTransaction) {
      withDynTransaction {
        createWithoutTransaction(asset, username, roadLink)
      }
    } else {
      createWithoutTransaction(asset, username, roadLink)
    }
  }

  def createWithoutTransaction(asset: IncomingTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    val id = PostGISTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)

    eventBus.publish("trafficSign:create", TrafficSignInfo(id, asset.linkId, asset.validityDirection,  getProperty(asset, typePublicId).get.propertyValue.toInt, roadLink))
    id
  }

  def createFloating(asset: IncomingTrafficSign, username: String, municipality: Int): Long = {
    withDynTransaction {
      createFloatingWithoutTransaction(asset, username, municipality)
    }
  }

  def createFloatingWithoutTransaction(asset: IncomingTrafficSign, username: String, municipality: Int): Long = {
    PostGISTrafficSignDao.createFloating(asset, 0, username, municipality, VVHClient.createVVHTimeStamp(), LinkGeomSource.Unknown, floating = true)
  }

  override def update(id: Long, updatedAsset: IncomingTrafficSign, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    }
  }

  override def getChanged(sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method, Try to used") }

  def getChangedByType(trafficSignTypes: Set[Int], sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"


    val filter = s"where a.asset_type_id = $typeId and floating = '0' and " +
      s"exists (select * from single_choice_value scv2, enumerated_value ev2 where a.id = scv2.asset_id " +
      s"and scv2.property_id = (select p.id from property p where public_id = 'trafficSigns_type') " +
      s"and scv2.enumerated_value_id = ev2.id and ev2.value in (${trafficSignTypes.mkString(",")})) " +
      s"and (" +
      s"(a.valid_to > $querySinceDate and a.valid_to <= $queryUntilDate) or " +
      s"(a.modified_date > $querySinceDate and a.modified_date <= $queryUntilDate) or "+
      s"(a.created_date > $querySinceDate and a.created_date <= $queryUntilDate))"

    val assets = withDynSession {
      fetchPointAssetsWithExpiredLimited(withFilter(filter), token)
    }

    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(assets.map(_.linkId).toSet)
    val historicRoadLink = roadLinkService.getHistoryDataLinksFromVVH(assets.map(_.linkId).toSet.diff(roadLinks.map(_.linkId).toSet))

    assets.map { asset =>
      ChangedPointAsset(asset,
        roadLinks.find(_.linkId == asset.linkId) match {
          case Some(roadLink) => roadLink
          case _ =>
            historicRoadLink.filter(_.linkId == asset.linkId).sortBy(_.vvhTimeStamp)(Ordering.Long.reverse).headOption
            .getOrElse(throw new IllegalStateException(s"Road link no longer available ${asset.linkId}"))
        })
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingTrafficSign, roadLink: RoadLink, username: String, mValue: Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    val oldAsset = getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException(s"Asset not found: $id"))
    val updatedId = oldAsset match {
      case old if old.bearing != updatedAsset.bearing || !GeometryUtils.areAdjacent(Point(old.lon, old.lat), Point(updatedAsset.lon, updatedAsset.lat)) || old.validityDirection != updatedAsset.validityDirection =>
        expireWithoutTransaction(id)
        PostGISTrafficSignDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
      case _ =>
        PostGISTrafficSignDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, value), value, roadLink.municipalityCode, username, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
    }
    eventBus.publish("trafficSign:update", TrafficSignInfoUpdate(TrafficSignInfo(updatedId, updatedAsset.linkId, updatedAsset.validityDirection, getProperty(updatedAsset, typePublicId).get.propertyValue.toInt, roadLink), oldAsset))
    updatedId
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    val result = super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
    val (pc, others) = result.partition(asset => asset.createdBy.contains(batchProcessName) && asset.propertyData.find(_.publicId == typePublicId).get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt == PedestrianCrossingSign.OTHvalue)

    sortCrossings(pc, Seq()) ++ others
  }

  def sortCrossings(sorted: Seq[PersistedAsset], result: Seq[PersistedAsset]): Seq[PersistedAsset] = {
    val centerSignOpt = sorted.headOption
    if(centerSignOpt.nonEmpty) {
      val centerSign = centerSignOpt.get
      val (inProximity, outsiders) = sorted.tail.partition(sign => centerSign.linkId == sign.linkId && centerSign.validityDirection == sign.validityDirection && GeometryUtils.withinTolerance(Seq(Point(centerSign.lon, centerSign.lat)), Seq(Point(sign.lon, sign.lat)), tolerance = GroupingDistance))
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
    val propertyData = persistedAsset.propertyData.map(prop =>
      prop.propertyType match {
        case "single_choice" if prop.values.head.asInstanceOf[PropertyValue].propertyValue.isEmpty =>
          SimplePointAssetProperty(prop.publicId, Seq(PropertyValue(getSingleChoiceDefaultValueByPublicId(prop.publicId).toString)))

        case "checkbox" if prop.values.head.asInstanceOf[PropertyValue].propertyValue.isEmpty =>
          if (prop.publicId == "old_traffic_code" && persistedAsset.createdAt.get.isBefore(newTrafficCodeStartDate))
            SimplePointAssetProperty(prop.publicId, Seq(PropertyValue("1")))
          else
            SimplePointAssetProperty(prop.publicId, Seq(PropertyValue(getDefaultMultiChoiceValue.toString)))

        case "additional_panel_type" if prop.values.nonEmpty =>
          val additionalPanelValues = prop.values.head.asInstanceOf[AdditionalPanel]
          val pValues = additionalPanelValues.copy(
            size = if (additionalPanelValues.size != 0) additionalPanelValues.size else AdditionalPanelSize.getDefault.value,
            coating_type = if (additionalPanelValues.coating_type != 0) additionalPanelValues.coating_type else AdditionalPanelCoatingType.getDefault.value,
            additional_panel_color = if (additionalPanelValues.additional_panel_color != 0) additionalPanelValues.additional_panel_color else AdditionalPanelColor.getDefault.value
          )
          SimplePointAssetProperty(prop.publicId, Seq(pValues))

        case _ => SimplePointAssetProperty(prop.publicId, prop.values)
      }).toSet

    val updated = IncomingTrafficSign(adjustment.lon, adjustment.lat, adjustment.linkId, propertyData,
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

  def getAssetBearing(validityDirection: Int, geometry: Seq[Point], assetMValue: Option[Double] = None, geometryLength: Option[Double] = None): Int = {
    val linkBearing = GeometryUtils.calculateBearing(geometry, assetMValue)
    GeometryUtils.calculateActualBearing(validityDirection, Some(linkBearing)).get
  }

  def createFromCoordinates(trafficSign: IncomingTrafficSign, roadLink: RoadLink, username: String, isFloating: Boolean = false): Long = {
    if (isFloating)
      createFloatingWithoutTransaction(trafficSign.copy(linkId = 0), username, roadLink.municipalityCode)
    else {
      checkDuplicates(trafficSign) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, trafficSign, roadLink, username, None, None)
        case _ =>
          createWithoutTransaction(trafficSign, username, roadLink)
      }
    }
  }


  def checkDuplicates(asset: IncomingTrafficSign): Option[PersistedTrafficSign] = {
    val signToCreateLinkId = asset.linkId
    val signToCreateType = getProperty(asset, typePublicId).get.propertyValue.toInt
    val signToMainSignText = asset.propertyData.find(_.publicId == mainSignText)
    val signToCreateDirection = asset.validityDirection
    val groupType = Some(TrafficSignTypeGroup.apply(TrafficSignType.applyOTHValue(signToCreateType).group.value))

    def getPropertyValue(property: Option[AbstractProperty]): String = {
      property.get.values.map(_.asInstanceOf[PropertyValue]).head.propertyValue
    }

    val trafficSignsInRadius = getTrafficSignByRadius(Point(asset.lon, asset.lat), 10, groupType).filter(
      ts => {
        val oldSignText = ts.propertyData.find(_.publicId == mainSignText)
        if (signToMainSignText.isDefined && oldSignText.isDefined) {
          getPropertyValue(oldSignText) == getPropertyValue(signToMainSignText) &&
            getProperty(ts, typePublicId).get.propertyValue.toInt == signToCreateType &&
            ts.linkId == signToCreateLinkId &&
            ts.validityDirection == signToCreateDirection
        } else {
          getProperty(ts, typePublicId).get.propertyValue.toInt == signToCreateType &&
            ts.linkId == signToCreateLinkId &&
            ts.validityDirection == signToCreateDirection
        }
      }
    )
    if (trafficSignsInRadius.nonEmpty) Some(getLatestModifiedAsset(trafficSignsInRadius)) else None
  }

  def getTrafficSignByRadius(position: Point, meters: Int, optGroupType: Option[TrafficSignTypeGroup] = None): Seq[PersistedTrafficSign] = {
    val assets = PostGISTrafficSignDao.fetchByRadius(position, meters)
    optGroupType match {
      case Some(groupType) => assets.filter(asset =>
        TrafficSignType.applyOTHValue(asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt).group == groupType)
      case _ => assets
    }
  }

  def getTrafficSign(linkIds : Seq[Long]): Seq[PersistedTrafficSign] = {
    PostGISTrafficSignDao.fetchByLinkId(linkIds)
  }

  def getTrafficSigns( municipality: Int, enumeratedValueIds: Boolean => Seq[Long], newTransaction: Boolean = true): Seq[PersistedTrafficSign] = {
    val enumeratedValues = enumeratedValueIds(newTransaction)
    if(newTransaction)
        withDynSession {
          PostGISTrafficSignDao.fetchByTypeValues(enumeratedValues, municipality)
        }
    else {
      PostGISTrafficSignDao.fetchByTypeValues(enumeratedValues, municipality)
    }
  }

  def getRestrictionsEnumeratedValues(trafficType: Seq[TrafficSignType])( newTransaction: Boolean = true): Seq[Long] = {
    if(newTransaction)
      withDynSession {
        PostGISTrafficSignDao.fetchEnumeratedValueIds(trafficType)
      }
    else {
      PostGISTrafficSignDao.fetchEnumeratedValueIds(trafficType)
    }
  }

  override def expire(id: Long, username: String): Long = {
    withDynSession {
      expireWithoutTransaction(id, username)
    }
    eventBus.publish("trafficSign:expire", id)
    id
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
      PostGISTrafficSignDao.expire(linkIds, username)
    } else
      PostGISTrafficSignDao.expire(linkIds, username)
  }

  def getLatestModifiedAsset(trafficSigns: Seq[PersistedTrafficSign]): PersistedTrafficSign = {
    trafficSigns.maxBy { ts => ts.modifiedAt.getOrElse(ts.createdAt.get) }
  }

  def getProperty(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[PropertyValue]).headOption
  }

  def getAdditionalPanelProperty(trafficSign: PersistedTrafficSign, property: String): Option[AdditionalPanel] = {
    trafficSign.propertyData.find(p => p.publicId == property) match {
      case Some(additionalPanel) =>
        additionalPanel.values.map(_.asInstanceOf[AdditionalPanel]).headOption
      case _ => None
    }
  }

  def getProperty(trafficSign: IncomingTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[PropertyValue]).headOption
  }

  def getProperty(propertyData: Set[SimplePointAssetProperty], property: String) : Option[PropertyValue] = {
    propertyData.find(p => p.publicId == property) match {
      case Some(additionalProperty) => additionalProperty.values.map(_.asInstanceOf[PropertyValue]).headOption
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
    PostGISTrafficSignDao.expireAssetsByMunicipality(municipalityCodes)
  }

  def verifyDatesOnTemporarySigns(asset: IncomingTrafficSign): Boolean = {
    val temporaryTrafficSigns = Seq(4, 5)
    val trafficSignLifeCycle = getProperty(asset, lifeCyclePublicId).get.propertyValue

    val trafficSignStartDateValue = getProperty(asset, trafficSignStartDatePublicId).getOrElse(PropertyValue("")).propertyValue
    val trafficSignEndDateValue = getProperty(asset, trafficSignEndDatePublicId).getOrElse(PropertyValue("")).propertyValue

    if (temporaryTrafficSigns.contains(trafficSignLifeCycle.toInt) && (trafficSignStartDateValue.isEmpty || trafficSignEndDateValue.isEmpty))
      return false

    if (!trafficSignStartDateValue.isEmpty && !trafficSignEndDateValue.isEmpty){
      val startDateAsDate = DateParser.DatePropertyFormat.parseDateTime(trafficSignStartDateValue)
      val endDateAsDate = DateParser.DatePropertyFormat.parseDateTime(trafficSignEndDateValue)

      return !startDateAsDate.isAfter(endDateAsDate)
    }

    true
  }

  def isOldCodeBeingUsed(asset: IncomingTrafficSign): Boolean = {
    val checkedValue = "1"
    val oldCode = getProperty(asset, oldTrafficCodePublicId)
    oldCode.get.propertyValue == checkedValue
  }

  def additionalPanelProperties(additionalProperties: Set[AdditionalPanelInfo]) : Set[SimplePointAssetProperty] = {

    def getSingleChoiceValue(additionalPanelInfo: AdditionalPanelInfo, target: String): Int = {
      val targetValue = getProperty(additionalPanelInfo.propertyData, target).get.propertyValue
      if (targetValue.nonEmpty) targetValue.toInt
      else getDefaultSingleChoiceValue
    }

    val orderedAdditionalPanels = additionalProperties.toSeq.sortBy(_.propertyData.find(_.publicId == typePublicId).get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt).toSet
    val additionalPanelsProperty = orderedAdditionalPanels.zipWithIndex.map{ case (panel, index) =>

      AdditionalPanel(
        getProperty(panel.propertyData, typePublicId).get.propertyValue.toInt,
        getProperty(panel.propertyData, infoPublicId).getOrElse(PropertyValue("")).propertyValue,
        getProperty(panel.propertyData, valuePublicId).getOrElse(PropertyValue("")).propertyValue,
        index,
        getProperty(panel.propertyData, additionalPanelTextPublicId).getOrElse(PropertyValue("")).propertyValue,
        getSingleChoiceValue(panel, additionalPanelSizePublicId),
        getSingleChoiceValue(panel, additionalPanelCoatingTypePublicId),
        getSingleChoiceValue(panel, additionalPanelColorPublicId)
      )
    }.toSeq

    if (additionalPanelsProperty.nonEmpty)
      Set(SimplePointAssetProperty(additionalPublicId, additionalPanelsProperty))
    else
      Set()
  }

  def getAdditionalPanels(linkId: Long, mValue: Double, validityDirection: Int, signPropertyValue: Int, geometry: Seq[Point],
                          additionalPanels: Set[AdditionalPanelInfo], roadLinks: Seq[RoadLinkLike] = Seq()) : Set[AdditionalPanelInfo] = {

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

              val (additionalPanelDistance, direction) =
                if (GeometryUtils.areAdjacent(adjacentRoadLinkInfo.map(_._1).head, first))
                   (additionalSign.mValue, SideCode.switch(SideCode.apply(additionalSign.validityDirection)))
                else if (GeometryUtils.areAdjacent(adjacentRoadLinkInfo.map(_._2).head, last))
                   (GeometryUtils.geometryLength(adjacentRoadLinkInfo.map(_._4).head) - additionalSign.mValue, SideCode.switch(SideCode.apply(additionalSign.validityDirection)))
                else if (GeometryUtils.areAdjacent(adjacentRoadLinkInfo.map(_._1).head, last))
                  (additionalSign.mValue, additionalSign.validityDirection)
                else
                   (GeometryUtils.geometryLength(adjacentRoadLinkInfo.map(_._4).head) - additionalSign.mValue, additionalSign.validityDirection)

              direction == validityDirection && additionalSign.linkId == adjacentRoadLinkInfo.map(_._3).head && (additionalPanelDistance + signDistance) <= AdditionalPanelDistance
            }
        } else
          Seq()
      }
    }.toSet
  }

  def getAdjacent(point: Point, linkId: Long, roadLinks : Seq[RoadLinkLike]): Seq[(Point, Point, Long, Seq[Point])] = {
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
    PostGISTrafficSignDao.expireAssetByLinkId(linkIds, signsType, username)
  }

  def getLastExecutionDate(createdBy: String, signTypes: Set[Int]) : Option[DateTime] = {
    PostGISTrafficSignDao.getLastExecutionDate(createdBy, signTypes)
  }

  def distinctPanels(panels: Set[AdditionalPanelInfo]): Set[AdditionalPanelInfo] = {
    panels.groupBy{ panel =>
      val props = panel.propertyData
      (getProperty(props, typePublicId), getProperty(props, valuePublicId), getProperty(props, infoPublicId))
    }.map(_._2.head).toSet
  }
}
