package fi.liikennevirasto.digiroad2.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}

class BusStopStrategy(val typeId : Int, val massTransitStopDao: MassTransitStopDao, val roadLinkService: RoadLinkService) extends AbstractBusStopStrategy
{
  //TODO remove duplicated code
  override def enrichBusStop(asset: PersistedMassTransitStop): (PersistedMassTransitStop, Boolean) = {
    def extractStopName(properties: Seq[Property]): String = {
      properties
        .filter { property => property.publicId.equals("nimi_suomeksi") }
        .filterNot { property => property.values.isEmpty }
        .map(_.values.head)
        .map(_.propertyValue)
        .headOption
        .getOrElse("")
    }

    asset.terminalId match {
      case Some(terminalId) =>
        val terminalAssetOption = massTransitStopDao.fetchPointAssets(massTransitStopDao.withId(terminalId)).headOption
        val displayValue = terminalAssetOption.map{ terminalAsset =>
          val name = extractStopName(terminalAsset.propertyData)
          s"${terminalAsset.nationalId} $name"
        }
        val newProperty = Property(0, "liitetty_terminaaliin", PropertyTypes.ReadOnlyText, values = Seq(PropertyValue(terminalId.toString, displayValue)))
        (asset.copy(propertyData = asset.propertyData ++ Seq(newProperty)), false)
      case _ =>
        (asset, false)
    }
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource, roadLink: RoadLink): PersistedMassTransitStop = {

    val properties = setPropertiesDefaultValues(asset.properties)

    if (MassTransitStopOperations.mixedStoptypes(properties.toSet))
      throw new IllegalArgumentException

    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, municipality, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, administrativeClass.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))

    fetchAsset(assetId)
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int) => Unit, roadLink: RoadLink): PersistedMassTransitStop = {

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode)

    massTransitStopDao.updateAssetLastModified(asset.id, username)

    optionalPosition.map(updatePosition(asset.id, roadLink))

    //Remove from common assets the side code property
    val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
      filterNot(_._1 == AssetPropertyConfiguration.ValidityDirectionId)

    val props = setPropertiesDefaultValues(properties.toSeq)
    updatePropertiesForAsset(asset.id, props, roadLink.administrativeClass, asset.nationalId)

    fetchAsset(asset.id)
  }

  override def delete(asset: PersistedMassTransitStop): Unit = {
    massTransitStopDao.deleteAllMassTransitStopData(asset.id)
  }
}

