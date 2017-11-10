package fi.liikennevirasto.digiroad2.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}

class BusStopStrategy(val typeId : Int, val massTransitStopDao: MassTransitStopDao, val roadLinkService: RoadLinkService) extends AbstractBusStopStrategy {

  override def enrichBusStop(asset: PersistedMassTransitStop): (PersistedMassTransitStop, Boolean) = {
    asset.terminalId match {
      case Some(terminalId) =>
        val terminalAssetOption = massTransitStopDao.fetchPointAssets(massTransitStopDao.withId(terminalId)).headOption
        val displayValue = terminalAssetOption.map { terminalAsset =>
          val name = MassTransitStopOperations.extractStopName(terminalAsset.propertyData)
          s"${terminalAsset.nationalId} $name"
        }
        val newProperty = Property(0, "liitetty_terminaaliin", PropertyTypes.ReadOnlyText, values = Seq(PropertyValue(terminalId.toString, displayValue)))
        (asset.copy(propertyData = asset.propertyData ++ Seq(newProperty)), false)
      case _ =>
        (asset, false)
    }
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): PersistedMassTransitStop = {

    validateBusStopDirections(asset.properties, roadLink)

    val properties = MassTransitStopOperations.setPropertiesDefaultValues(asset.properties, roadLink)

    if (MassTransitStopOperations.mixedStoptypes(properties.toSet))
      throw new IllegalArgumentException

    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, roadLink.linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, roadLink.municipalityCode, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, roadLink.administrativeClass) //.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))

    fetchAsset(assetId)
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int) => Unit, roadLink: RoadLink): PersistedMassTransitStop = {

    if (properties.exists(prop => prop.publicId == "vaikutussuunta")) {
      validateBusStopDirections(properties.toSeq, roadLink)
    }

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode)

    massTransitStopDao.updateAssetLastModified(asset.id, username)

    optionalPosition.map(updatePositionWithBearing(asset.id, roadLink))

    //Remove from common assets the side code property
    val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
      filterNot(_._1 == AssetPropertyConfiguration.ValidityDirectionId)

    val props = MassTransitStopOperations.setPropertiesDefaultValues(properties.toSeq, roadLink)
    updatePropertiesForAsset(asset.id, props, roadLink.administrativeClass, asset.nationalId)

    fetchAsset(asset.id)
  }

  override def delete(asset: PersistedMassTransitStop): Unit = {
    massTransitStopDao.deleteAllMassTransitStopData(asset.id)
  }

  override def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    roadLinkOption match {
      case Some(roadLink) =>
        val (floatingDir, floatingReasonDir) = MassTransitStopOperations.isFloating(persistedAsset, roadLinkOption)
          (floatingDir, floatingReasonDir)
      case _ => (false, None)
    }
  }
}

