package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{PointAssetForConversion, RoadAddressBoundToAsset}
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LogUtils, RoadSide}
import org.joda.time.LocalDate

import scala.util.Try

class BusStopStrategy(val typeId : Int, val massTransitStopDao: MassTransitStopDao, val roadLinkService: RoadLinkService, val eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends AbstractBusStopStrategy {

  private val roadNumberPublicId = "tie"          // Tienumero
  private val roadPartNumberPublicId = "osa"      // Tieosanumero
  private val startMeasurePublicId = "aet"        // Etaisyys
  private val trackCodePublicId = "ajr"           // Ajorata
  private val sideCodePublicId = "puoli"

  def getRoadAddressPropertiesByLinkId(persistedStop: PersistedMassTransitStop, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    val road = extractRoadNumber(roadLink)

    val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get, road = road)

    val newRoadAddressProperties = extractRoadAddress(address,roadSide)

    oldProperties.filterNot(op => newRoadAddressProperties.map(_.publicId).contains(op.publicId)) ++ newRoadAddressProperties
  }

  private def extractRoadAddress(address: RoadAddress, roadSide: RoadSide): Seq[Property] = {
    Seq(
      Property(0, roadNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.road.toString, Some(address.road.toString)))),
      Property(0, roadPartNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.roadPart.toString, Some(address.roadPart.toString)))),
      Property(0, startMeasurePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.addrM.toString, Some(address.addrM.toString)))),
      Property(0, trackCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.track.value.toString, Some(address.track.value.toString)))),
      Property(0, sideCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(roadSide.value.toString, Some(roadSide.value.toString))))
    )
  }

 private def extractTerminal(terminalId: Long, terminalAssetOption: Option[PersistedMassTransitStop]): (Property, Property) = {
    val displayValue = terminalAssetOption.map { terminalAsset =>
      val name = MassTransitStopOperations.extractStopName(terminalAsset.propertyData)
      s"${terminalAsset.nationalId} $name"
    }
    val newProperty = Property(0, "liitetty_terminaaliin", PropertyTypes.ReadOnlyText, values = Seq(PropertyValue(terminalId.toString, displayValue)))

    val terminalNationalId = terminalAssetOption.map(_.nationalId.toString) match {
      case Some(extId) => Seq(PropertyValue(extId))
      case _ => Seq()
    }

    val newPropertyExtId = Property(0, "liitetty_terminaaliin_ulkoinen_tunnus", PropertyTypes.ReadOnlyText, values = terminalNationalId)
    (newProperty, newPropertyExtId)
  }
  
  override def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit = {
    publishInfo.asset match {
      case Some(asset) =>
        asset.propertyData.find(_.publicId == "suggest_box") match {
          case Some(property) if property.values.headOption.nonEmpty =>
            if (property.values.head.asInstanceOf[PropertyValue].propertyValue == "0")
              eventbus.publish("asset:saved", asset)
            else
              None
          case _ => eventbus.publish("asset:saved", asset)
        }
      case _ => None
    }
  }
  override def publishExpiringEvent(publishInfo: AbstractPublishInfo):Unit ={
    publishInfo.asset match {
      case Some(asset) =>
        val expiredAsset=massTransitStopDao.
        fetchPointAssets(massTransitStopDao.withId(asset.id))
        eventbus.publish("asset:expired", expiredAsset.head)
      case _ => None
    }
  }
  override def publishDeleteEvent(publishInfo: AbstractPublishInfo): Unit = {
    publishInfo.asset match {
      case Some(asset) =>
        val updateProperties:Seq[Property] = asset.propertyData.map(property =>
            if (property.publicId == "viimeinen_voimassaolopaiva") {
              property.copy(values = Seq(PropertyValue(propertyValue = LocalDate.now().toString)))
            }else{
              property
            }
        )
        val updateAsset = asset.copy(propertyData = updateProperties)
        eventbus.publish("asset:expired", (updateAsset,true))
      case _ => None
    }
  }
  /**
    *  enrich with additional data 
    * @param asset Mass Transit Stop
    * @param roadLinkOption provide road link when MassTransitStop need road address
    * @return
    */
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
          val (newProperty: Property, newPropertyExtId: Property) = extractTerminal(terminalId, terminalAssetOption)
          (asset.copy(propertyData = addRoadAddressProperties(asset.propertyData ++ Seq(newProperty, newPropertyExtId))), false)
      case _ =>
        (asset.copy(propertyData = addRoadAddressProperties(asset.propertyData)), false)
    }
  }

  override def enrichBusStopsOperation(persistedStops: Seq[PersistedMassTransitStop], links: Seq[RoadLink]): Seq[PersistedMassTransitStop] = {
    val roadAddressAdded = addRoadAddress(persistedStops, links)
    addTerminals(roadAddressAdded)
  }

  private def addRoadAddress(assets: Seq[PersistedMassTransitStop], links: Seq[RoadLink]): Seq[PersistedMassTransitStop] = {
    def mapToQuery(links: Seq[RoadLink], stop: PersistedMassTransitStop): Option[PointAssetForConversion] = {
      links.find(_.linkId == stop.linkId) match {
        case Some(link) =>
          val road = extractRoadNumber(link)
          Some(PointAssetForConversion(stop.id, Point(stop.lon, stop.lat), stop.bearing, stop.mValue, stop.linkId, stop.validityDirection, road = road))
        case None => None
      }
    }

    def mapAssetToRoadAddress(roadAddress: Seq[RoadAddressBoundToAsset], stop: PersistedMassTransitStop): PersistedMassTransitStop = {
      roadAddress.find(_.asset == stop.id) match {
        case Some(found) => stop.copy(propertyData = stop.propertyData ++ extractRoadAddress(found.address, found.side))
        case None => stop
      }
    }

    val query = assets.map(mapToQuery(links, _)).filter(_.isDefined).map(_.get)
    val roadAddress = geometryTransform.resolveMultipleAddressAndLocations(query)
    assets.map(mapAssetToRoadAddress(roadAddress, _))
  }

  private def extractRoadNumber(link: RoadLinkLike) = {
    link.attributes.find(_._1 == "ROADNUMBER") match {
      case Some((key, value)) => Try(value.toString.toInt).toOption
      case _ => None
    }
  }
  private def addTerminals(allStops: Seq[PersistedMassTransitStop]) = {
    LogUtils.time(logger, s"TEST LOG addTerminals") {
      allStops.map(addTerminal(allStops, _))
    }
  }

  private def addTerminal(allStops: Seq[PersistedMassTransitStop], stop: PersistedMassTransitStop): PersistedMassTransitStop = {
    stop.terminalId match {
      case Some(terminalId) =>
        val findTerminal = allStops.find(_.id == terminalId)
        val (newProperty: Property, newPropertyExtId: Property) = extractTerminal(terminalId, findTerminal)
        stop.copy(propertyData = stop.propertyData ++ Seq(newProperty, newPropertyExtId))
      case None => stop
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

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {

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

