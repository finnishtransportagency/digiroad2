package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.{RoadAddressesService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, RoadAddressException}
import org.slf4j.LoggerFactory

import scala.util.Try

class BusStopStrategy(val typeId : Int, val massTransitStopDao: MassTransitStopDao, val roadLinkService: RoadLinkService, val eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends AbstractBusStopStrategy {

  private val roadNumberPublicId = "tie"          // Tienumero
  private val roadPartNumberPublicId = "osa"      // Tieosanumero
  private val startMeasurePublicId = "aet"        // Etaisyys
  private val trackCodePublicId = "ajr"           // Ajorata
  private val sideCodePublicId = "puoli"

  def getRoadAddressPropertiesByLinkId(persistedStop: PersistedMassTransitStop, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    val road =
      roadLink.attributes.find(_._1 == "ROADNUMBER") match {
        case Some((key, value)) => Try(value.toString.toInt).toOption
        case _ => None
      }

    val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get, road = road)

    val newRoadAddressProperties =
      Seq(
        Property(0, roadNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.road.toString, Some(address.road.toString)))),
        Property(0, roadPartNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.roadPart.toString, Some(address.roadPart.toString)))),
        Property(0, startMeasurePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.addrM.toString, Some(address.addrM.toString)))),
        Property(0, trackCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.track.value.toString, Some(address.track.value.toString)))),
        Property(0, sideCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(roadSide.value.toString, Some(roadSide.value.toString))))
      )

    oldProperties.filterNot(op => newRoadAddressProperties.map(_.publicId).contains(op.publicId)) ++ newRoadAddressProperties
  }

  override def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit = {
    publishInfo.asset match {
      case Some(asset) => eventbus.publish("asset:saved", asset)
      case _ => None
    }
  }

  override def enrichBusStop(asset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike] = None): (PersistedMassTransitStop, Boolean) = {
    def addRoadAddressProperties(oldProperties: Seq[Property]): Seq[Property] = {
      roadLinkOption match {
        case Some(roadLink) =>
          try {
            getRoadAddressPropertiesByLinkId(asset, roadLink, oldProperties)
          } catch {
            case e: RoadAddressException =>
              oldProperties
          }
        case _ => oldProperties
      }
    }

    asset.terminalId match {
      case Some(terminalId) =>
        val terminalAssetOption = massTransitStopDao.fetchPointAssets(massTransitStopDao.withId(terminalId)).headOption
        val displayValue = terminalAssetOption.map { terminalAsset =>
          val name = MassTransitStopOperations.extractStopName(terminalAsset.propertyData)
          s"${terminalAsset.nationalId} $name"
        }
        val newProperty = Property(0, "liitetty_terminaaliin", PropertyTypes.ReadOnlyText, values = Seq(PropertyValue(terminalId.toString, displayValue)))

        val terminalExternalId = terminalAssetOption.map(_.nationalId.toString) match {
          case Some(extId) => Seq(PropertyValue(extId))
          case _ => Seq()
        }

        val newPropertyExtId = Property(0, "liitetty_terminaaliin_ulkoinen_tunnus", PropertyTypes.ReadOnlyText, values = terminalExternalId)

        (asset.copy(propertyData = addRoadAddressProperties(asset.propertyData ++ Seq(newProperty, newPropertyExtId))), false)
      case _ =>
        (asset.copy(propertyData = addRoadAddressProperties(asset.propertyData)), false)
    }
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {

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
    updateAdministrativeClassValue(assetId, roadLink.administrativeClass)

    val resultAsset = fetchAsset(assetId)
    (resultAsset, PublishInfo(Some(resultAsset)))
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {

    if (properties.exists(prop => prop.publicId == "vaikutussuunta")) {
      validateBusStopDirections(properties.toSeq, roadLink)
    }

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode, roadLink.administrativeClass)

    massTransitStopDao.updateAssetLastModified(asset.id, username)

    optionalPosition.map(updatePositionWithBearing(asset.id, roadLink))

    //Remove from common assets the side code property
    val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
      filterNot(_._1 == AssetPropertyConfiguration.ValidityDirectionId)

    val props = MassTransitStopOperations.setPropertiesDefaultValues(properties.toSeq, roadLink)
    updatePropertiesForAsset(asset.id, props, roadLink.administrativeClass, asset.nationalId)

    val resultAsset = enrichBusStop(fetchAsset(asset.id))._1
    (resultAsset, PublishInfo(Some(resultAsset)))
  }

  override def delete(asset: PersistedMassTransitStop): Option[AbstractPublishInfo] = {
    massTransitStopDao.deleteAllMassTransitStopData(asset.id)
    None
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

