package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimeSimplifiedFormat
import fi.liikennevirasto.digiroad2.asset.{Property, _}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, MunicipalityDao, Queries, ServicePoint}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.model.LRMPosition
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.joda.time.{DateTime, LocalDate}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

case class NewMassTransitStop(lon: Double, lat: Double, linkId: Long, bearing: Int, properties: Seq[SimplePointAssetProperty]) extends IncomingPointAsset

case class MassTransitStop(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Option[Int],
                           validityDirection: Int, municipalityNumber: Int,
                           validityPeriod: String, floating: Boolean, stopTypes: Seq[Int]) extends FloatingAsset

case class MassTransitStopWithProperties(id: Long, nationalId: Long, stopTypes: Seq[Int], lon: Double, lat: Double,
                                         validityDirection: Option[Int], bearing: Option[Int],
                                         validityPeriod: Option[String], floating: Boolean,
                                         propertyData: Seq[Property], municipalityName: Option[String] = None) extends FloatingAsset

case class PersistedMassTransitStop(id: Long, nationalId: Long, linkId: Long, stopTypes: Seq[Int],
                                    municipalityCode: Int, lon: Double, lat: Double, mValue: Double,
                                    validityDirection: Option[Int], bearing: Option[Int],
                                    validityPeriod: Option[String], floating: Boolean, vvhTimeStamp: Long,
                                    created: Modification, modified: Modification,
                                    propertyData: Seq[Property], linkSource: LinkGeomSource, terminalId: Option[Long] = None) extends PersistedPointAsset with TimeStamps

case class MassTransitStopRow(id: Long, externalId: Long, assetTypeId: Long, point: Option[Point], linkId: Long, bearing: Option[Int],
                              validityDirection: Int, validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                              created: Modification, modified: Modification, wgsPoint: Option[Point], lrmPosition: LRMPosition,
                              roadLinkType: AdministrativeClass = Unknown, municipalityCode: Int, persistedFloating: Boolean, terminalId: Option[Long])

case class LightGeometryMassTransitStop(lon: Double, lat: Double, validityPeriod: Option[String]) extends LightGeometry

trait AbstractPublishInfo {
  val asset: Option[PersistedMassTransitStop]
}

case class PublishInfo(asset: Option[PersistedMassTransitStop]) extends AbstractPublishInfo

trait AbstractBusStopStrategy {
  val typeId: Int
  val roadLinkService: RoadLinkService
  val massTransitStopDao: MassTransitStopDao

  def is(newProperties: Set[SimplePointAssetProperty], roadLink: Option[RoadLink], existingAsset: Option[PersistedMassTransitStop]): Boolean = {false}
  def is(newProperties: Set[SimplePointAssetProperty], roadLink: Option[RoadLink], existingAsset: Option[PersistedMassTransitStop], saveOption: Option[Boolean]): Boolean = {false}
  def was(existingAsset: PersistedMassTransitStop): Boolean = {false}
  def undo(existingAsset: PersistedMassTransitStop, newProperties: Set[SimplePointAssetProperty], username: String): Unit = {}
  def enrichBusStop(persistedStop: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike] = None): (PersistedMassTransitStop, Boolean)
  def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = { (false, None) }
  def create(newAsset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo)

  def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit
  def publishExpiringEvent(publishInfo: AbstractPublishInfo): Unit
  def publishDeleteEvent(publishInfo: AbstractPublishInfo): Unit

  def update(persistedStop: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo)
  def delete(asset: PersistedMassTransitStop): Option[AbstractPublishInfo]
  def pickRoadLink(optRoadLink: Option[RoadLink], optHistoric: Option[RoadLink]): RoadLink = {optRoadLink.getOrElse(throw new NoSuchElementException)}

  protected def updateAdministrativeClassValue(assetId: Long, administrativeClass: AdministrativeClass): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "linkin_hallinnollinen_luokka", administrativeClass.value)
  }

  protected def updatePropertiesForAsset(id: Long, properties: Seq[SimplePointAssetProperty], administrativeClass: AdministrativeClass, nationalId: Long) = {
    massTransitStopDao.updateAssetProperties(id, properties)
    updateAdministrativeClassValue(id, administrativeClass)
  }

  protected def updatePosition(id: Long, roadLink: RoadLink)(position: Position) = {
    val point = Point(position.lon, position.lat)
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)
    val newPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(point)
    massTransitStopDao.updateLrmPosition(id, mValue, roadLink.linkId, roadLink.linkSource)
    massTransitStopDao.updateMunicipality(id, roadLink.municipalityCode)
    updateAssetGeometry(id, newPoint)
  }

  protected def updatePositionWithBearing(id: Long, roadLink: RoadLink)(position: Position) = {
    updatePosition(id, roadLink)(position)
    massTransitStopDao.updateBearing(id, position)
  }

  protected def fetchAsset(id: Long): PersistedMassTransitStop = {
    massTransitStopDao.fetchPointAssets(massTransitStopDao.withId(id)).headOption.getOrElse(throw new NoSuchElementException)
  }

  protected def validateBusStopDirections(properties: Seq[SimplePointAssetProperty], roadLink: RoadLink) = {
    if(!properties.exists(prop => prop.publicId == "vaikutussuunta") ||
      !MassTransitStopOperations.isValidBusStopDirections(properties, Some(roadLink)))

      throw new MassTransitStopException("Invalid Mass Transit Stop direction")
  }
}

trait MassTransitStopService extends PointAssetOperations {
  type IncomingAsset = NewMassTransitStop
  type PersistedAsset = PersistedMassTransitStop

  lazy val logger = LoggerFactory.getLogger(getClass)
  val massTransitStopDao: MassTransitStopDao
  val municipalityDao: MunicipalityDao
  val roadLinkService: RoadLinkService
  val geometryTransform: GeometryTransform

  val tierekisteriClient: TierekisteriMassTransitStopClient

  override def typeId: Int = 10
  override val idField = "external_id"

  def withDynSession[T](f: => T): T
  def withDynTransaction[T](f: => T): T

  def eventbus: DigiroadEventBus

  lazy val massTransitStopEnumeratedPropertyValues = {
    withDynSession{
      val properties = Queries.getEnumeratedPropertyValues(typeId)
      properties.map(epv => epv.publicId -> epv.values).toMap
    }
  }
  lazy val defaultBusStopStrategy = new BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
  lazy val tierekisteriBusStopStrategy = new TierekisteriBusStopStrategy(typeId, massTransitStopDao, roadLinkService, tierekisteriClient, eventbus, geometryTransform)
  lazy val terminalBusStopStrategy = new TerminalBusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
  lazy val terminatedBusStopStrategy = new TerminatedBusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedMassTransitStop] = {
    getByMunicipality(municipalityCode, true)
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedMassTransitStop] = massTransitStopDao.fetchPointAssets(queryFilter)
  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedMassTransitStop] = { throw new UnsupportedOperationException("Not Supported Method") }
  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PersistedMassTransitStop] = massTransitStopDao.fetchPointAssetsWithExpiredLimited(queryFilter, token)

  override def getChanged(sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method") }

  override def fetchLightGeometry(queryFilter: String => String): Seq[LightGeometry] = massTransitStopDao.fetchLightGeometry(queryFilter)

  override def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedAsset] = {
    withDynSession {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
      fetchPointAssets(withFilter(filter))
    }
  }

  override def setAssetPosition(asset: NewMassTransitStop, geometry: Seq[Point], mValue: Double): NewMassTransitStop = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  def getPersistedAssetsByIdsEnriched(ids: Set[Long]): Seq[PersistedAsset] = {
    val idsStr = ids.toSeq.mkString(",")
    val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
    fetchPointAssets(withFilter(filter)).map { asset =>
      val strategy = getStrategy(asset)
      val (enrichedStop, _) = strategy.enrichBusStop(asset)
      enrichedStop
    }
  }

  override def update(id: Long, updatedAsset: NewMassTransitStop, roadLink: RoadLink, username: String): Long = {
    throw new NotImplementedError("Use updateExisting instead. Mass transit is legacy.")
  }

  override def updateFloating(id: Long, floating: Boolean, floatingReason: Option[FloatingReason]) = {
    super.updateFloating(id, floating, floatingReason)

    floatingReason match {
      case None =>
        deleteFloatingReasonValue(id)
      case Some(reason) =>
        updateFloatingReasonValue(id, reason)
    }
  }

  override def isFloating(persistedAsset: PersistedPointAsset, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    val persistedMassTransitStop = persistedAsset.asInstanceOf[PersistedMassTransitStop]

    roadLinkOption match {
      case Some(roadLink) =>
        val administrationClass = MassTransitStopOperations.getAdministrationClass(persistedMassTransitStop.propertyData)
        val(floating , floatingReason) = MassTransitStopOperations.isFloating(administrationClass.getOrElse(Unknown), Some(roadLink))
        if (floating) {
          return (floating, floatingReason)
        }
      case _ => //Do nothing
    }

    val strategy = getStrategy(persistedMassTransitStop)
    val (floating, floatingReason) = strategy.isFloating(persistedMassTransitStop, roadLinkOption)
    if(floating){
      (floating, floatingReason)
    }else{
      super.isFloating(persistedAsset, roadLinkOption)
    }
  }

  override def setFloating(persistedStop: PersistedMassTransitStop, floating: Boolean): PersistedMassTransitStop = {
    persistedStop.copy(floating = floating)
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedMassTransitStop] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createPersistedAssetObject))
  }

  override def getNormalAndComplementaryById(id: Long, roadLink: RoadLink): Option[PersistedAsset] = {
    val persistedAsset = getPersistedAssetsByIds(Set(id)).headOption
    val roadLinks: Option[RoadLinkLike] = Some(roadLink)

    def findRoadlink(linkId: Long): Option[RoadLinkLike] =
      roadLinks.find(_.linkId == linkId)

    withDynSession {
      persistedAsset.map{
        asset =>
          val strategy = getStrategy(asset)
          val (enrichedStop, _) = strategy.enrichBusStop(asset, roadLinks)
          withFloatingUpdate(convertPersistedAsset(setFloating, findRoadlink))(enrichedStop)
      }
    }
  }

  override def create(asset: NewMassTransitStop, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val (persistedAsset, publishInfo, strategy) =
      if (newTransaction) {
        withDynTransaction {
          createWithUpdateFloating(asset, username, roadLink)
        }
      } else {
        createWithUpdateFloating(asset, username, roadLink)
      }
    strategy.publishSaveEvent(publishInfo)
    persistedAsset.id
  }

  protected override def floatingReason(persistedAsset: PersistedAsset, roadLinkOption: Option[RoadLinkLike]) : String = {

    roadLinkOption match {
      case None => return super.floatingReason(persistedAsset, roadLinkOption) //This is just because the warning
      case Some(roadLink) =>
        val administrationClass = MassTransitStopOperations.getAdministrationClass(persistedAsset.asInstanceOf[PersistedMassTransitStop].propertyData)
        val floatingReason = MassTransitStopOperations.floatingReason(administrationClass.getOrElse(Unknown), roadLink)
        if (floatingReason.nonEmpty) {
          return floatingReason.get
        }
    }

    super.floatingReason(persistedAsset, roadLinkOption)
  }

  protected override def fetchFloatingAssets(addQueryFilter: String => String, isOperator: Option[Boolean]): Seq[(Long, String, Long, Option[Long])] ={
    val query = s"""
          select a.$idField, m.name_fi, lrm.link_id, np.value
          from asset a
          join municipality m on a.municipality_code = m.id
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'kellumisen_syy'
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
          where a.asset_type_id = $typeId and a.floating = '1' and (a.valid_to is null or a.valid_to > current_timestamp)"""

    val queryFilter = isOperator match {
      case Some(false) =>
        (q: String) => {
          addQueryFilter(q + s""" and np.value <> ${FloatingReason.RoadOwnerChanged.value}""")
        }
      case _ =>
        addQueryFilter
    }

    StaticQuery.queryNA[(Long, String, Long, Option[Long])](queryFilter(query)).list
  }

  def createWithUpdateFloating(asset: NewMassTransitStop, username: String, roadLink: RoadLink) = {
    val point = Point(asset.lon, asset.lat)
    val strategy = getStrategy(asset.properties.toSet, roadLink)
    val newAsset = asset.copy(properties = excludeProperties(asset.properties).toSeq)

    val (persistedAsset, publishInfo) = strategy.create(newAsset, username, point, roadLink)
    withFloatingUpdate(persistedStopToMassTransitStopWithProperties(_ => Some(roadLink)))(persistedAsset)
    (persistedAsset, publishInfo, strategy)

  }

  def updateExistingById(assetId: Long, optionalPosition: Option[Position], properties: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, newTransaction: Boolean = true): MassTransitStopWithProperties = {
    def updateExistingById() = {
      val asset = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption.getOrElse(throw new NoSuchElementException)

      val linkId = optionalPosition match {
        case Some(position) => position.linkId
        case _ => asset.linkId
      }

      val (optRoadLink, optHistoric) = (roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, false), roadLinkService.getHistoryDataLinkFromVVH(linkId, false))

      val (previousStrategy, currentStrategy) = getStrategy(properties, asset, optRoadLink)
      val roadLink = currentStrategy.pickRoadLink(optRoadLink, optHistoric)
      val newProperties = excludeProperties(properties.toSeq)

      if (previousStrategy != currentStrategy)
        previousStrategy.undo(asset, properties, username)

      val (persistedAsset, publishInfo) = currentStrategy.update(asset, optionalPosition, newProperties, username, municipalityValidation, roadLink)

      val (enrichPersistedAsset, error) = currentStrategy.enrichBusStop(persistedAsset)
      (currentStrategy, publishInfo, withFloatingUpdate(persistedStopToMassTransitStopWithProperties(_ => Some(roadLink)))(enrichPersistedAsset))
    }

    val (currentStrategy, publishInfo, persistedAsset ) =
      if (newTransaction)
        withDynTransaction {
          updateExistingById()
        } else
        updateExistingById()

    currentStrategy.publishSaveEvent(publishInfo)
    persistedAsset
  }

  def getByNationalId[T <: FloatingAsset](nationalId: Long, municipalityValidation: Int => Unit, persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason]), newTransaction: Boolean = true): Option[T] = {
    def getByNationalId : Option[T] = {
      val persistedStop = fetchPointAssets(massTransitStopDao.withNationalId(nationalId)).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      persistedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
    }
    if(newTransaction)
      withDynTransaction {
        getByNationalId
      }
    else
      getByNationalId
  }

  def getMassTransitStopByNationalId(nationalId: Long, municipalityValidation: Int => Unit, newTransaction: Boolean = true): Option[MassTransitStopWithProperties] = {
    getByNationalId(nationalId, municipalityValidation, persistedStopToMassTransitStopWithProperties(fetchRoadLink), newTransaction)
  }

  def getMassTransitStopByNationalIdWithTRWarnings(nationalId: Long): (Option[MassTransitStopWithProperties], Boolean, Option[Int]) = {
    withDynTransaction {
      val persistedStopOption = fetchPointAssets(massTransitStopDao.withNationalId(nationalId)).headOption
      persistedStopOption match {
        case Some(persistedStop) =>
          val roadLink = fetchRoadLink(persistedStop.linkId)

          val strategy = getStrategy(persistedStop)
          val (enrichedStop, error) = strategy.enrichBusStop(persistedStop, roadLink)

          (Some(withFloatingUpdate(persistedStopToMassTransitStopWithProperties(_ => roadLink))(enrichedStop)), error, Some(persistedStop.municipalityCode))
        case _ =>
          (None, false, None)
      }
    }
  }

  def getMassTransitStopByIdWithTRWarnings(id: Long): (Option[MassTransitStopWithProperties], Boolean, Option[Int]) = {
    withDynTransaction {
      val persistedStopOption = fetchPointAssets(massTransitStopDao.withId(id)).headOption
      persistedStopOption match {
        case Some(persistedStop) =>
          val roadLink = fetchRoadLink(persistedStop.linkId)

          val strategy = getStrategy(persistedStop)
          val (enrichedStop, error) = strategy.enrichBusStop(persistedStop, roadLink)

          (Some(withFloatingUpdate(persistedStopToMassTransitStopWithProperties(_ => roadLink))(enrichedStop)), error, Some(persistedStop.municipalityCode))
        case _ =>
          (None, false, None)
      }
    }
  }

  def getByLiviId[T <: FloatingAsset](liviId: String, municipalityValidation: Int => Unit, persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason])): Option[T] = {
    withDynTransaction {
      val nationalId = massTransitStopDao.getNationalIdByLiviId(liviId).headOption
      nationalId match {
        case Some(id) =>
          val persistedStop = fetchPointAssets(massTransitStopDao.withNationalId(id)).headOption
          persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
          persistedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
        case None => None
      }
    }
  }

  def getMassTransitStopByPassengerId(passengerId: String, municipalityValidation: Int => Unit): Seq[MassTransitStopWithProperties] = {
    def getMassTransitsStops(busStopIds: Seq[Long]) : List[MassTransitStopWithProperties] = {
      val persistedStops = fetchPointAssets(massTransitStopDao.withNationalIds(busStopIds)).toList
      persistedStops.map(_.municipalityCode).foreach(municipalityValidation)
      val municipalities = municipalityDao.getMunicipalitiesNameAndIdByCode(persistedStops.map(_.municipalityCode).toSet)

      persistedStops.map { persistedStop =>
        withFloatingUpdate(persistedStopToMassTransitStopWithProperties(fetchRoadLink, id => municipalities.find(_.id == id).map(_.name)))(persistedStop)
      }
    }

    withDynTransaction {
      val busStopIds = massTransitStopDao.getNationalIdsByPassengerId(passengerId)
      if (busStopIds.nonEmpty) getMassTransitsStops(busStopIds) else Seq()
    }
  }

  /**
    * This method returns mass transit stop by livi-id. It's utilized by livi-id search functionality.
    *
    * @param liviId
    * @param municipalityValidation
    * @return
    */
  def getMassTransitStopByLiviId(liviId: String, municipalityValidation: Int => Unit): Option[MassTransitStopWithProperties] = {
    getByLiviId(liviId, municipalityValidation, persistedStopToMassTransitStopWithProperties(fetchRoadLink))
  }

  def getByMunicipality(municipalityCode: Int, withEnrich: Boolean): Seq[PersistedAsset] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(roadLink => roadLink.linkId -> roadLink).toMap
    val assets = super.getByMunicipality(mapRoadLinks, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createPersistedAssetObject), withMunicipality(municipalityCode))

    if(withEnrich)
      withDynSession{
        assets.map{a =>
          val strategy = getStrategy(a)
          strategy.enrichBusStop(a)._1
        }
      }
    else
      assets
  }

  def getFloatingAssetsWithReason(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Map[String, Map[String, Seq[Map[String, Long]]]] = {

    val result = getFloatingPointAssets(includedMunicipalities, isOperator)

    val floatingTerminals = getTerminalFloatingPointAssets(includedMunicipalities, isOperator)

    Map("Terminaalipysäkit" -> floatingTerminals.groupBy(_._2).mapValues(_.map {
      case (id, _, floatingReason) =>
        Map("id" -> id, "floatingReason" -> floatingReason.value.toLong)
    })) ++ result.filterNot(r => floatingTerminals.exists(_._1 == r.id)).groupBy(_.municipality)
      .mapValues { municipalityAssets =>
        municipalityAssets
          .groupBy(_.administrativeClass)
          .mapValues(_.map(asset =>
            Map("id" -> asset.id, "floatingReason" -> asset.floatingReason.getOrElse(0L))
          ))
      }
  }

  def getMetadata(point: Option[Point]): Seq[Property] = {
    AssetPropertyConfiguration.commonAssetProperties.values.map(_.propertyDescriptor).toSeq ++ withDynSession {
      val properties = Queries.availableProperties(typeId)
      point match {
        case Some(point) =>
          val childFilters = massTransitStopDao.fetchByRadius(point, 200)
            .filter(a =>  a.terminalId.isEmpty)
            .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.Terminal))
            .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.ServicePoint))
          val newProperty = Property(0, "liitetyt_pysakit", PropertyTypes.MultipleChoice, required = true, values = childFilters.map{ a =>
            val stopName = MassTransitStopOperations.extractStopName(a.propertyData)
            PropertyValue(a.id.toString, Some(s"""${a.nationalId} $stopName"""), checked = false)
          })
          Seq(newProperty) ++ properties
        case _ => properties
      }
    }
  }

  def mandatoryProperties(properties: Seq[SimplePointAssetProperty]): Map[String, String] = {
    //TODO use the strategies to get the mandatory fields
    if(MassTransitStopOperations.extractStopTypes(properties).contains(BusStopType.Terminal)){
      Map[String, String]("liitetyt_pysakit" -> PropertyTypes.MultipleChoice)
    } else {
      val requiredProperties = withDynSession {
        sql"""select public_id, property_type from property where asset_type_id = $typeId and required = '1' """.as[(String, String)].iterator.toMap
      }
      val validityDirection = AssetPropertyConfiguration.commonAssetProperties(AssetPropertyConfiguration.ValidityDirectionId)
      requiredProperties + (validityDirection.publicId -> validityDirection.propertyType)
    }
  }

  def deleteMassTransitStopData(assetId: Long) = {
    val (strategy, publishInfo) = withDynTransaction {
      val persistedStop = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption.getOrElse(throw new NoSuchElementException)

      val strategy = getStrategy(persistedStop)
      strategy.publishDeleteEvent(PublishInfo(Option(persistedStop)))
      (strategy, strategy.delete(persistedStop))
    }
    (strategy, publishInfo) match {
      case (strategy, Some(info)) => strategy.publishSaveEvent(info)
      case _ =>
    }
  }

  /**
    * Update properties for asset.
    *
    * @param id
    * @param properties
    * @return
    */
  def updatePropertiesForAsset(id: Long, properties: Seq[SimplePointAssetProperty]) = {
    withDynTransaction {
      massTransitStopDao.updateAssetProperties(id, properties)
    }
  }

  def getPropertiesWithMaxSize(): Map[String, Int] = {
    withDynSession {
      massTransitStopDao.getPropertiesWithMaxSize(typeId)
    }
  }

  protected def getTerminalFloatingPointAssets(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Seq[(Long, String, FloatingReason)] = {
    withDynTransaction {
      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))

      val municipalityFilter = optionalMunicipalities match {
        case Some(municipalities) => s" and municipality_code in ($municipalities)"
        case _ => ""
      }

      val result = massTransitStopDao.fetchTerminalFloatingAssets(query => query + municipalityFilter, isOperator)
      val administrativeClasses = roadLinkService.getRoadLinksByLinkIdsFromVVH(result.map(_._2).toSet, newTransaction = false).groupBy(_.linkId).mapValues(_.head.administrativeClass)
      result
        .map { case (id, linkId) =>
          (id, administrativeClasses.getOrElse(linkId, Unknown).toString, FloatingReason.TerminalChildless)
        }
    }
  }

  private def updateFloatingReasonValue(assetId: Long, floatingReason: FloatingReason): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "kellumisen_syy", floatingReason.value)
  }

  private def createPersistedAssetObject(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    new PersistedAsset(adjustment.assetId, asset.nationalId, adjustment.linkId, asset.stopTypes, asset.municipalityCode, adjustment.lon, adjustment.lat,
      adjustment.mValue, asset.validityDirection, asset.bearing, asset.validityPeriod, asset.floating, asset.vvhTimeStamp,
      asset.created, asset.modified, asset.propertyData, asset.linkSource, asset.terminalId)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    updateAjustedGeometry(adjustment, persistedAsset.linkSource)
    persistedAsset.id
  }

  private def persistedStopToMassTransitStopWithProperties(roadLinkByLinkId: Long => Option[RoadLinkLike], getMunicipalityName: Int => Option[String] = _ => None)
                                                          (persistedStop: PersistedMassTransitStop): (MassTransitStopWithProperties, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    (MassTransitStopWithProperties(id = persistedStop.id, nationalId = persistedStop.nationalId, stopTypes = persistedStop.stopTypes,
      lon = persistedStop.lon, lat = persistedStop.lat, validityDirection = persistedStop.validityDirection,
      bearing = persistedStop.bearing, validityPeriod = persistedStop.validityPeriod, floating = floating,
      propertyData = persistedStop.propertyData, municipalityName = getMunicipalityName(persistedStop.municipalityCode)), floatingReason)
  }

  private def eventBusMassTransitStop(stop: PersistedMassTransitStop, municipalityName: String) = {
    EventBusMassTransitStop(municipalityNumber = stop.municipalityCode, municipalityName = municipalityName,
      nationalId = stop.nationalId, lon = stop.lon, lat = stop.lat, bearing = stop.bearing,
      validityDirection = stop.validityDirection, created = stop.created, modified = stop.modified,
      propertyData = stop.propertyData)
  }

  private def deleteFloatingReasonValue(assetId: Long): Unit = {
    massTransitStopDao.deleteNumberPropertyValue(assetId, "kellumisen_syy")
  }

  private def fetchRoadLink(linkId: Long): Option[RoadLinkLike] = {
    roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, newTransaction = false)
  }

  private def getStrategies(): (Seq[AbstractBusStopStrategy], AbstractBusStopStrategy) ={
    (Seq(terminalBusStopStrategy, tierekisteriBusStopStrategy, terminatedBusStopStrategy), defaultBusStopStrategy)
  }

  private def getStrategy(asset: PersistedMassTransitStop): AbstractBusStopStrategy ={
    val (strategies, defaultStrategy) = getStrategies()
    strategies.find(strategy => strategy.is(Set(), None, Some(asset))).getOrElse(defaultStrategy)
  }

  private def getStrategy(newProperties: Set[SimplePointAssetProperty], roadLink: RoadLink): AbstractBusStopStrategy ={
    val (strategies, defaultStrategy) = getStrategies()
    strategies.find(strategy => strategy.is(newProperties, Some(roadLink), None)).getOrElse(defaultStrategy)
  }

  private def getStrategy(newProperties: Set[SimplePointAssetProperty], asset: PersistedMassTransitStop, roadLink: Option[RoadLink]): (AbstractBusStopStrategy, AbstractBusStopStrategy) ={
    val (strategies, defaultStrategy) = getStrategies()
    val previousStrategy = strategies.find(v => v.was(asset)).getOrElse(defaultStrategy)
    val currentStrategy = strategies.find(strategy => strategy.is(newProperties, roadLink, Some(asset))).getOrElse(defaultStrategy)
    (previousStrategy, currentStrategy)
  }

  /**
    * Update asset ajusted geometry
    *
    * @param adjustment
    * @param linkSource
    * @return
    */
  private def updateAjustedGeometry(adjustment: AssetAdjustment, linkSource: LinkGeomSource) = {
    massTransitStopDao.updateAssetLastModified(adjustment.assetId, "vvh_generated")
    massTransitStopDao.updateLrmPosition(adjustment.assetId, adjustment.mValue, adjustment.linkId, linkSource, Some(adjustment.vvhTimeStamp))
    updateAssetGeometry(adjustment.assetId, Point(adjustment.lon, adjustment.lat))
  }

  private def excludeProperties(properties: Seq[SimplePointAssetProperty]): Set[SimplePointAssetProperty] = {
    properties.filterNot(prop => AssetPropertyConfiguration.ExcludedProperties.contains(prop.publicId)).toSet
  }

  def saveIdPrintedOnValluLog(id: Long): Unit = {
    massTransitStopDao.insertValluXmlIds(id)
  }

  def getPublishedOnXml(sinceDate: DateTime, untilDate: DateTime, token: Option[String]): Seq[ChangedPointAsset] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"

    val filter = s"WHERE a.asset_type_id = $typeId AND floating = 0 and (" +
      s"(a.valid_to > $querySinceDate and a.valid_to <= $queryUntilDate) or " +
      s"(a.modified_date > $querySinceDate and a.modified_date <= $queryUntilDate) or " +
      s"(a.created_date > $querySinceDate and a.created_date <= $queryUntilDate)) "+
      s"AND a.id in ( SELECT asset_id FROM vallu_xml_ids ) "

    val assets = withDynSession {
      fetchPointAssetsWithExpiredLimited(withFilter(filter), token)
    }

    val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIdsFromVVH(assets.map(_.linkId).toSet)

    assets.map { asset =>
      ChangedPointAsset(asset, roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new IllegalStateException(s"Road link no longer available: ${asset.linkId}")))    }
  }

  def getAbstractProperty(propertyData: Seq[AbstractProperty], property: String) : Seq[String] = {
    propertyData.find(p => p.publicId == property) match {
      case Some(prop) => prop.values.map(_.asInstanceOf[PropertyValue].propertyValue)
      case _ => Seq()
    }
  }

  def checkDuplicates(incomingMassTransitStop: NewMassTransitStop): Option[PersistedMassTransitStop] = {
    val position = Point(incomingMassTransitStop.lon, incomingMassTransitStop.lat)
    val direction = getAbstractProperty(incomingMassTransitStop.properties, "vaikutussuunta")
    val busTypes = getAbstractProperty(incomingMassTransitStop.properties, "pysakin_tyyppi")

    val assetsInRadius = fetchPointAssets(withBoundingBoxFilter(position, TwoMeters))
      .filter(asset => GeometryUtils.geometryLength(Seq(position, Point(asset.lon, asset.lat))) <= TwoMeters &&
        direction == getAbstractProperty(asset.propertyData, "vaikutussuunta") &&
        busTypes == getAbstractProperty(asset.propertyData, "pysakin_tyyppi"))

    if(assetsInRadius.nonEmpty)
      return Some(getLatestModifiedAsset(assetsInRadius))
    None
  }

  def getLatestModifiedAsset(assets: Seq[PersistedMassTransitStop]): PersistedMassTransitStop = {
    assets.maxBy(asset => asset.modified.modificationTime.getOrElse(asset.created.modificationTime.get).getMillis)
  }
}

class MassTransitStopException(string: String) extends RuntimeException {
  override def getMessage: String = string
}
