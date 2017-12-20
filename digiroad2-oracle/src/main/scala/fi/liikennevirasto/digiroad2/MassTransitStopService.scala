package fi.liikennevirasto.digiroad2

import java.util.Date

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{Property, _}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle._
import fi.liikennevirasto.digiroad2.oracle.{GenericQueries, OracleDatabase}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Interval, LocalDate}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import sun.reflect.generics.tree.BottomSignature

import scala.util.Try
import scala.xml.dtd.REQUIRED

case class NewMassTransitStop(lon: Double, lat: Double, linkId: Long, bearing: Int, properties: Seq[SimpleProperty]) extends IncomingPointAsset

case class MassTransitStop(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Option[Int],
                           validityDirection: Int, municipalityNumber: Int,
                           validityPeriod: String, floating: Boolean, stopTypes: Seq[Int]) extends FloatingAsset

case class MassTransitStopWithProperties(id: Long, nationalId: Long, stopTypes: Seq[Int], lon: Double, lat: Double,
                                         validityDirection: Option[Int], bearing: Option[Int],
                                         validityPeriod: Option[String], floating: Boolean,
                                         propertyData: Seq[Property]) extends FloatingAsset

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

trait AbstractPublishInfo {
  val asset: Option[PersistedMassTransitStop]
}

case class PublishInfo(asset: Option[PersistedMassTransitStop]) extends AbstractPublishInfo

trait AbstractBusStopStrategy {
  val typeId: Int
  val roadLinkService: RoadLinkService
  val massTransitStopDao: MassTransitStopDao

  def is(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAsset: Option[PersistedMassTransitStop]): Boolean = {false}
  def was(existingAsset: PersistedMassTransitStop): Boolean = {false}
  def undo(existingAsset: PersistedMassTransitStop, newProperties: Set[SimpleProperty], username: String): Unit = {}
  def enrichBusStop(persistedStop: PersistedMassTransitStop): (PersistedMassTransitStop, Boolean)
  def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = { (false, None) }
  def create(newAsset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo)

  def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit

  def update(persistedStop: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo)
  def delete(asset: PersistedMassTransitStop): Option[AbstractPublishInfo]

  protected def updateAdministrativeClassValue(assetId: Long, administrativeClass: AdministrativeClass): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "linkin_hallinnollinen_luokka", administrativeClass.value)
  }

  protected def updatePropertiesForAsset(id: Long, properties: Seq[SimpleProperty], administrativeClass: AdministrativeClass, nationalId: Long) = {
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

  protected def validateBusStopDirections(properties: Seq[SimpleProperty], roadLink: RoadLink) = {
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
  val roadLinkService: RoadLinkService

  val geometryTransform = new GeometryTransform
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
  lazy val defaultBusStopStrategy = new BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus)
  lazy val tierekisteriBusStopStrategy = new TierekisteriBusStopStrategy(typeId, massTransitStopDao, roadLinkService, tierekisteriClient, geometryTransform, eventbus)
  lazy val terminalBusStopStrategy = new TerminalBusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus)

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedMassTransitStop] = {
    getByMunicipality(municipalityCode, true)
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedMassTransitStop] = massTransitStopDao.fetchPointAssets(queryFilter)

  override def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedAsset] = {
    withDynSession {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
      fetchPointAssets(withFilter(filter))
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

  override def update(id: Long, updatedAsset: NewMassTransitStop, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
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
          val (enrichedStop, _) = strategy.enrichBusStop(asset)
          withFloatingUpdate(convertPersistedAsset(setFloating, findRoadlink))(enrichedStop)
      }
    }
  }

  override def create(asset: NewMassTransitStop, username: String, roadLink: RoadLink): Long = {
    val (persistedAsset, publishInfo, strategy) = withDynTransaction {
      val point = Point(asset.lon, asset.lat)
      val strategy = getStrategy(asset.properties.toSet, roadLink)
      val (persistedAsset, publishInfo) = strategy.create(asset, username, point, roadLink)
      withFloatingUpdate(persistedStopToMassTransitStopWithProperties(_ => Some(roadLink)))(persistedAsset)

      (persistedAsset, publishInfo, strategy)
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
          where a.asset_type_id = $typeId and a.floating = '1' and (a.valid_to is null or a.valid_to > sysdate)"""

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

  def updateExistingById(assetId: Long, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit): MassTransitStopWithProperties = {

   val (currentStrategy, publishInfo, persistedAsset ) =  withDynTransaction {
      val asset = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption.getOrElse(throw new NoSuchElementException)

      val linkId = optionalPosition match {
        case Some(position) => position.linkId
        case _ => asset.linkId
      }
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, false).getOrElse(throw new NoSuchElementException)

      val (previousStrategy, currentStrategy) = getStrategy(properties, asset, roadLink)

      if (previousStrategy != currentStrategy)
        previousStrategy.undo(asset, properties, username)

      val (persistedAsset, publishInfo) = currentStrategy.update(asset, optionalPosition, properties, username, municipalityValidation, roadLink)

      val (enrichPersistedAsset, error) = currentStrategy.enrichBusStop(persistedAsset)
      (currentStrategy, publishInfo, withFloatingUpdate(persistedStopToMassTransitStopWithProperties(_ => Some(roadLink)))(enrichPersistedAsset))
    }
    currentStrategy.publishSaveEvent(publishInfo)
    persistedAsset
  }

  def getByNationalId[T <: FloatingAsset](nationalId: Long, municipalityValidation: Int => Unit, persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason])): Option[T] = {
    withDynTransaction {
      val persistedStop = fetchPointAssets(massTransitStopDao.withNationalId(nationalId)).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      persistedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
    }
  }

  def getMassTransitStopByNationalId(nationalId: Long, municipalityValidation: Int => Unit): Option[MassTransitStopWithProperties] = {
    getByNationalId(nationalId, municipalityValidation, persistedStopToMassTransitStopWithProperties(fetchRoadLink))
  }

  def getMassTransitStopByNationalIdWithTRWarnings(nationalId: Long, municipalityValidation: Int => Unit): (Option[MassTransitStopWithProperties], Boolean) = {
    withDynTransaction {
      val persistedStopOption = fetchPointAssets(massTransitStopDao.withNationalId(nationalId)).headOption
      persistedStopOption match {
        case Some(persistedStop) =>
          municipalityValidation(persistedStop.municipalityCode)

          val strategy = getStrategy(persistedStop)
          val (enrichedStop, error) = strategy.enrichBusStop(persistedStop)
          (Some(withFloatingUpdate(persistedStopToMassTransitStopWithProperties(fetchRoadLink))(enrichedStop)), error)
        case _ =>
          (None, false)
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
    val assets = super.getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createPersistedAssetObject))

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
        case Some(point) => {
          val childFilters = massTransitStopDao.fetchByRadius(point, 200)
            .filter(a =>  a.terminalId.isEmpty)
            .filter(a => !MassTransitStopOperations.extractStopType(a).contains(BusStopType.Terminal))
          val newProperty = Property(0, "liitetyt_pysakit", PropertyTypes.MultipleChoice, required = true, values = childFilters.map{ a =>
            val stopName = MassTransitStopOperations.extractStopName(a.propertyData)
            PropertyValue(a.id.toString, Some(s"""${a.nationalId} $stopName"""), checked = false)
          })
          Seq(newProperty) ++ properties
        }
        case _ => properties
      }
    }
  }

  def mandatoryProperties(properties: Seq[SimpleProperty]): Map[String, String] = {
    //TODO use the strategies to get the mandatory fields
    if(MassTransitStopOperations.extractStopTypes(properties).contains(BusStopType.Terminal)){
      Map[String, String]("liitetyt_pysakit" -> PropertyTypes.MultipleChoice)
    } else {
      val requiredProperties = withDynSession {
        sql"""select public_id, property_type from property where asset_type_id = $typeId and required = 1""".as[(String, String)].iterator.toMap
      }
      val validityDirection = AssetPropertyConfiguration.commonAssetProperties(AssetPropertyConfiguration.ValidityDirectionId)
      requiredProperties + (validityDirection.publicId -> validityDirection.propertyType)
    }
  }

  def deleteMassTransitStopData(assetId: Long) = {
    val (strategy, publishInfo) = withDynTransaction {
      val persistedStop = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption.getOrElse(throw new NoSuchElementException)

      val strategy = getStrategy(persistedStop)

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
  def updatePropertiesForAsset(id: Long, properties: Seq[SimpleProperty]) = {
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

  private def persistedStopToMassTransitStopWithProperties(roadLinkByLinkId: Long => Option[RoadLinkLike])
                                                          (persistedStop: PersistedMassTransitStop): (MassTransitStopWithProperties, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    (MassTransitStopWithProperties(id = persistedStop.id, nationalId = persistedStop.nationalId, stopTypes = persistedStop.stopTypes,
      lon = persistedStop.lon, lat = persistedStop.lat, validityDirection = persistedStop.validityDirection,
      bearing = persistedStop.bearing, validityPeriod = persistedStop.validityPeriod, floating = floating,
      propertyData = persistedStop.propertyData), floatingReason)
  }

  private def deleteFloatingReasonValue(assetId: Long): Unit = {
    massTransitStopDao.deleteNumberPropertyValue(assetId, "kellumisen_syy")
  }

  private def fetchRoadLink(linkId: Long): Option[RoadLinkLike] = {
    roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, newTransaction = false)
  }

  private def getStrategies(): (Seq[AbstractBusStopStrategy], AbstractBusStopStrategy) ={
    (Seq(terminalBusStopStrategy, tierekisteriBusStopStrategy), defaultBusStopStrategy)
  }

  private def getStrategy(asset: PersistedMassTransitStop): AbstractBusStopStrategy ={
    val (strategies, defaultStrategy) = getStrategies()
    strategies.find(strategy => strategy.is(Set(), None, Some(asset))).getOrElse(defaultStrategy)
  }

  private def getStrategy(newProperties: Set[SimpleProperty], roadLink: RoadLink): AbstractBusStopStrategy ={
    val (strategies, defaultStrategy) = getStrategies()
    strategies.find(strategy => strategy.is(newProperties, Some(roadLink), None)).getOrElse(defaultStrategy)
  }

  private def getStrategy(newProperties: Set[SimpleProperty], asset: PersistedMassTransitStop, roadLink: RoadLink): (AbstractBusStopStrategy, AbstractBusStopStrategy) ={
    val (strategies, defaultStrategy) = getStrategies()
    val previousStrategy = strategies.find(v => v.was(asset)).getOrElse(defaultStrategy)
    val currentStrategy = strategies.find(strategy => strategy.is(newProperties, Some(roadLink), Some(asset))).getOrElse(defaultStrategy)
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
}

class MassTransitStopException(string: String) extends RuntimeException {
  override def getMessage: String = string
}
