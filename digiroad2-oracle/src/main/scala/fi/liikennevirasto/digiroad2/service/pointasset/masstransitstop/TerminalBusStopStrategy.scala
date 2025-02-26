package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.GeometryTransform

case class TerminalPublishInfo(asset: Option[PersistedMassTransitStop], detachAsset: Seq[Long], attachedAsset: Seq[Long]) extends AbstractPublishInfo

class TerminalBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
{
  private val radiusMeters = 200
  private val terminalChildrenPublicId = "liitetyt_pysakit"
  private val validityDirectionPublicId = "vaikutussuunta"
  private val ignoredProperties = Seq(terminalChildrenPublicId, validityDirectionPublicId)

  def isTerminal(properties: Seq[AbstractProperty]): Boolean = {
    properties.exists(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId &&
      p.values.exists(v => v.asInstanceOf[PropertyValue].propertyValue == BusStopType.Terminal.value.toString))
  }

  override def is(newProperties: Set[SimplePointAssetProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    //If the stop have the property stop type with the terminal value
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimplePointAssetProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }
    isTerminal(properties)
  }

  def extractTerminalChilds(terminal: PersistedMassTransitStop, childFilters: Seq[PersistedMassTransitStop]): Property = {
     Property(0, terminalChildrenPublicId, PropertyTypes.MultipleChoice, required = true, values = childFilters.map { a =>
      val stopName = MassTransitStopOperations.extractStopName(a.propertyData)
      PropertyValue(a.id.toString, Some(s"""${a.nationalId} $stopName"""), checked = a.terminalId.contains(terminal.id))
    })
  }

  override def enrichBusStop(asset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike] = None): (PersistedMassTransitStop, Boolean) = {
      val childFilters = massTransitStopDao.fetchByRadius(Point(asset.lon, asset.lat), radiusMeters, Some(asset.id))
        .filter(a => a.terminalId.isEmpty || a.terminalId.contains(asset.id))
        .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.Terminal))
        .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.ServicePoint))
      val newProperty: Property = extractTerminalChilds(asset, childFilters)
      (asset.copy(propertyData = asset.propertyData.filterNot(p => p.publicId == terminalChildrenPublicId) ++ Seq(newProperty)), false)
  }

  override def enrichBusStopsOperation(persistedStops: Seq[PersistedMassTransitStop], links: Seq[RoadLink]): Seq[PersistedMassTransitStop] = {
    val selectTerminals = persistedStops.filter(a => isTerminal(a.propertyData))
    val selectTerminalsIds = selectTerminals.map(_.id)
    persistedStops.filterNot(a => selectTerminalsIds.contains(a.id)) ++ selectTerminals.map(addChild(persistedStops, _))
  }

  private def addChild(potentialChild: Seq[PersistedMassTransitStop], terminal: PersistedMassTransitStop): PersistedMassTransitStop = {
    val childFilters = potentialChild.filter(a => a.terminalId.contains(terminal.id))
      .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.Terminal))
      .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.ServicePoint))
    val newProperty: Property = extractTerminalChilds(terminal, childFilters)
    terminal.copy(propertyData = terminal.propertyData.filterNot(p => p.publicId == terminalChildrenPublicId) ++ Seq(newProperty))
  }
  
  override def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    massTransitStopDao.countTerminalChildBusStops(persistedAsset.id) match {
      case 0 => (true, Some(FloatingReason.TerminalChildless))
      case _ => (false, None)
    }
  }

  override def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit = {
    val terminalPublishInfo = publishInfo.asInstanceOf[TerminalPublishInfo]
    super.publishSaveEvent(terminalPublishInfo)
    if(terminalPublishInfo.attachedAsset.nonEmpty || terminalPublishInfo.detachAsset.nonEmpty){
      eventbus.publish("terminal:saved", terminalPublishInfo)
    }
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, roadLink.linkSource, SideCode.BothDirections)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, username, roadLink.municipalityCode, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)

    val children = MassTransitStopOperations.getTerminalMassTransitStopChildren(asset.properties)
    //TODO: fetch by Seq[Long] id to check if the children exist and if they are at 200m

    massTransitStopDao.insertChildren(assetId, children)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => asset.properties.exists(_.publicId == defaultValue.publicId))
    if (MassTransitStopOperations.mixedStoptypes(asset.properties.toSet))
      throw new IllegalArgumentException

    massTransitStopDao.updateAssetProperties(assetId, asset.properties.filterNot(p =>  ignoredProperties.contains(p.publicId)) ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, roadLink.administrativeClass)
    val resultAsset = fetchAsset(assetId)
    (resultAsset, TerminalPublishInfo(Some(resultAsset), children, Seq()))
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink, isCsvImported: Boolean = false): (PersistedMassTransitStop, AbstractPublishInfo) = {

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode, roadLink.administrativeClass)

    // Enrich properties with old administrator, if administrator value is empty in CSV import
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)

    val id = asset.id
    massTransitStopDao.updateAssetLastModified(id, username)
    massTransitStopDao.updateAssetProperties(id, verifiedProperties.filterNot(p =>  ignoredProperties.contains(p.publicId)).toSeq)
    updateAdministrativeClassValue(id, roadLink.administrativeClass)
    val oldChildren = massTransitStopDao.getAllChildren(id)

    optionalPosition.map(updatePosition(id, roadLink))

    if(properties.exists(p => p.publicId == terminalChildrenPublicId)){
      val children = MassTransitStopOperations.getTerminalMassTransitStopChildren(properties.toSeq)
      massTransitStopDao.deleteChildren(id)
      massTransitStopDao.insertChildren(id, children)

      val resultAsset = enrichBusStop(fetchAsset(id))._1
      (resultAsset, TerminalPublishInfo(Some(resultAsset), children.diff(oldChildren), oldChildren.diff(children)))

    } else {
      val resultAsset = enrichBusStop(fetchAsset(id))._1
      (resultAsset, TerminalPublishInfo(Some(resultAsset), Seq(), Seq()))
    }
  }

  override def delete(asset: PersistedMassTransitStop): Option[AbstractPublishInfo] = {
    val oldChildren = massTransitStopDao.getAllChildren(asset.id)
    massTransitStopDao.deleteAllMassTransitStopData(asset.id)

    Some(TerminalPublishInfo(None, Seq(), oldChildren))
  }
}
