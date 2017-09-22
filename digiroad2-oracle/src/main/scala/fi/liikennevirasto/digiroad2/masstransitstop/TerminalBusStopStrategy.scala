package fi.liikennevirasto.digiroad2.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

class TerminalBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService)
{
  private val radiusMeters = 200
  private val terminalChildrenPublicId = "liitetyt_pysakit"

  override def is(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    //If the stop have the property stop type with the terminal value
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimpleProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }

    properties.exists(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId &&
      p.values.exists(v => v.propertyValue == BusStopType.Terminal.value.toString))
  }

  override def enrichBusStop(asset: PersistedMassTransitStop): (PersistedMassTransitStop, Boolean) = {
    val childFilters = fetchByRadius(Point(asset.lon, asset.lat), radiusMeters, asset.id)
      .filter(a =>  a.terminalId.isEmpty || a.terminalId.contains(asset.id))
      .filter(a => !extractStopType(a).contains(BusStopType.Terminal))
    val newProperty = Property(0, terminalChildrenPublicId, PropertyTypes.MultipleChoice, required = true, values = childFilters.map{ a =>
      val stopName = extractStopName(a.propertyData)
      PropertyValue(a.id.toString, Some(s"""${a.nationalId} $stopName"""), checked = a.terminalId.contains(asset.id))
    })
    (asset.copy(propertyData = asset.propertyData.filterNot(p => p.publicId == terminalChildrenPublicId) ++ Seq(newProperty)), false)
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource, roadLink: RoadLink): PersistedMassTransitStop = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, municipality, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)

    val children = MassTransitStopOperations.getTerminalMassTransitStopChildren(asset.properties)
    //TODO: fetch by Seq[Long] id to check if the children exist and if they are at 200m

    massTransitStopDao.insertChildren(assetId, children)

    val properties = setPropertiesDefaultValues(asset.properties)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    if (MassTransitStopOperations.mixedStoptypes(properties.toSet))
      throw new IllegalArgumentException

    massTransitStopDao.updateAssetProperties(assetId, properties.filterNot(_.publicId == terminalChildrenPublicId) ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, administrativeClass.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))
    fetchAsset(assetId)
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int) => Unit, roadLink: RoadLink): PersistedMassTransitStop = {

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode)

    // Enrich properties with old administrator, if administrator value is empty in CSV import
    //TODO: Change propertyValue
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData) ++ Seq(SimpleProperty(MassTransitStopOperations.MassTransitStopTypePublicId, Seq(PropertyValue("5"))))

    val id = asset.id
    massTransitStopDao.updateAssetLastModified(id, username)
    //TODO check this better
    massTransitStopDao.updateAssetProperties(id, verifiedProperties.toSeq)
    updateAdministrativeClassValue(id, roadLink.administrativeClass)

    optionalPosition.map(updatePosition(id, roadLink))

    val children = MassTransitStopOperations.getTerminalMassTransitStopChildren(properties.toSeq)

    massTransitStopDao.deleteChildren(id, children)
    massTransitStopDao.insertChildren(id, children)

    enrichBusStop(fetchAsset(id))._1
  }

  override def delete(asset: PersistedMassTransitStop): Unit = {
    massTransitStopDao.deleteTerminalMassTransitStopData(asset.id)
  }

  //TODO move this
  private def extractStopName(properties: Seq[Property]): String = {
    properties
      .filter { property => property.publicId.equals("nimi_ruotsiksi") }
      .filterNot { property => property.values.isEmpty }
      .map(_.values.head)
      .map(_.propertyValue)
      .headOption
      .getOrElse("")
  }

  private def fetchByRadius(position : Point, meters: Int, terminalId: Long): Seq[PersistedMassTransitStop] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = OracleDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"where a.asset_type_id = $typeId and (($boundingBoxFilter )or tbs.terminal_asset_id = $terminalId)"
    massTransitStopDao.fetchPointAssets(massTransitStopDao.withFilter(filter))
      .filter(r => GeometryUtils.geometryLength(Seq(position, Point(r.lon, r.lat))) <= meters)
  }

  private def extractStopType(asset: PersistedMassTransitStop): Option[BusStopType] ={
    asset.propertyData.find(p=> p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId) match {
      case Some(property) =>
        property.values.map(p => BusStopType.apply(p.propertyValue.toInt)).headOption
      case _ =>
        None
    }
  }
}
