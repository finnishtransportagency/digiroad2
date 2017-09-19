package fi.liikennevirasto.digiroad2

import java.util.Date

import fi.liikennevirasto.digiroad2.Operation._
import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{Property, _}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop.{BusStopType, MassTransitStopOperations}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
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

trait MassTransitStopService extends PointAssetOperations {
  type IncomingAsset = NewMassTransitStop
  type PersistedAsset = PersistedMassTransitStop

  lazy val logger = LoggerFactory.getLogger(getClass)
  val massTransitStopDao: MassTransitStopDao
  val tierekisteriEnabled: Boolean
  val roadLinkService: RoadLinkService
  val tierekisteriClient: TierekisteriMassTransitStopClient

  override def typeId: Int = 10
  override val idField = "external_id"

  val geometryTransform = new GeometryTransform

  lazy val defaultVariation = new DefaultBusStopVariation(typeId, massTransitStopDao, roadLinkService)
  lazy val tierekisteriVariation = new TierekisteriBusStopVariation(typeId, massTransitStopDao, roadLinkService, tierekisteriClient)
  lazy val terminalVariation = new TerminalBusStopVariation(typeId, massTransitStopDao, roadLinkService)

  def withDynSession[T](f: => T): T

  def withDynTransaction[T](f: => T): T

  def eventbus: DigiroadEventBus

  //TODO check if this is really needed here
  lazy val massTransitStopEnumeratedPropertyValues = {
    withDynSession{
      val properties = Queries.getEnumeratedPropertyValues(typeId)
      properties.map(epv => epv.publicId -> epv.values).toMap
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createPersistedAsset))
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
      val persistedStop = fetchPointAssets(massTransitStopDao.withNationalId(nationalId)).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      //TODO Add error
      val variation = getOnlyVariation(Seq(), None, persistedStop)
      //val (enrichedStop, trError) = enrichStopIfInTierekisteri(persistedStop)
      val (enrichedStop, trError) = (Some(variation.enrichBusStop(persistedStop.get)), false)
      (enrichedStop.map(withFloatingUpdate(persistedStopToMassTransitStopWithProperties(fetchRoadLink))), trError)
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

  def getByMunicipality(municipalityCode: Int, enrichWithTR: Boolean): Seq[PersistedAsset] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(roadLink => roadLink.linkId -> roadLink).toMap
    val assets = super.getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createPersistedAsset))

    //TODO do this better
    if(enrichWithTR)
      assets.flatMap{a =>
            val variation = getOnlyVariation(Seq(), None, Some(a))
            //enrichStopIfInTierekisteri(Some(a))._1
            Some(variation.enrichBusStop(a))
        }




    assets
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedMassTransitStop] = {
    getByMunicipality(municipalityCode, true)
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedMassTransitStop] = massTransitStopDao.fetchPointAssets(queryFilter)

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
    roadLinkOption match {
      case None => return super.isFloating(persistedAsset, roadLinkOption)
      case Some(roadLink) =>
        val administrationClass = MassTransitStopOperations.getAdministrationClass(persistedAsset.asInstanceOf[PersistedMassTransitStop].propertyData)
        val(floating , floatingReason) = MassTransitStopOperations.isFloating(administrationClass.getOrElse(Unknown), Some(roadLink))
        if (floating) {
          return (floating, floatingReason)
        }
    }

    super.isFloating(persistedAsset, roadLinkOption)
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

  override def setFloating(persistedStop: PersistedMassTransitStop, floating: Boolean): PersistedMassTransitStop = {
    persistedStop.copy(floating = floating)
  }

  private def updateFloatingReasonValue(assetId: Long, floatingReason: FloatingReason): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "kellumisen_syy", floatingReason.value)
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

  def getFloatingAssetsWithReason(includedMunicipalities: Option[Set[Int]], isOperator: Option[Boolean] = None): Map[String, Map[String, Seq[Map[String, Long]]]] = {

    val result = getFloatingPointAssets(includedMunicipalities, isOperator)

    result.groupBy(_.municipality)
      .mapValues { municipalityAssets =>
        municipalityAssets
          .groupBy(_.administrativeClass)
          .mapValues(_.map(asset =>
            Map("id" -> asset.id, "floatingReason" -> asset.floatingReason.getOrElse(0L))
          ))
      }
  }

  private def createPersistedAsset(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    new PersistedAsset(adjustment.assetId, asset.nationalId, adjustment.linkId, asset.stopTypes, asset.municipalityCode, adjustment.lon, adjustment.lat,
      adjustment.mValue, asset.validityDirection, asset.bearing, asset.validityPeriod, asset.floating, asset.vvhTimeStamp,
      asset.created, asset.modified, asset.propertyData, asset.linkSource, asset.terminalId)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment): Long = {
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

  private def extractStopTypes(rows: Seq[MassTransitStopRow]): Seq[Int] = {
    rows
      .filter { row => row.property.publicId.equals(MassTransitStopOperations.MassTransitStopTypePublicId) }
      .filterNot { row => row.property.propertyValue.isEmpty }
      .map { row => row.property.propertyValue.toInt }
  }

  private def eventBusMassTransitStop(stop: PersistedMassTransitStop, municipalityName: String) = {
    EventBusMassTransitStop(municipalityNumber = stop.municipalityCode, municipalityName = municipalityName,
      nationalId = stop.nationalId, lon = stop.lon, lat = stop.lat, bearing = stop.bearing,
      validityDirection = stop.validityDirection, created = stop.created, modified = stop.modified,
      propertyData = stop.propertyData)
  }

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  //TODO change set to seq
  def updateExistingById(assetId: Long, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit): MassTransitStopWithProperties = {
    withDynTransaction {
      val persistedStop = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption.getOrElse(throw new NoSuchElementException)
      val linkId = optionalPosition match {
        case Some(position) => position.linkId
        case _ => persistedStop.linkId
      }
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, false).getOrElse(throw new NoSuchElementException)
      val variation = getVariation(properties.toSeq, Some(roadLink), Some(persistedStop))
      val id = variation.update(persistedStop, optionalPosition, properties, username, municipalityValidation)

      getPersistedStopAndPublishEvent(id, _ => Some(roadLink))
    }
  }

  private def deleteFloatingReasonValue(assetId: Long): Unit ={
    massTransitStopDao.deleteNumberPropertyValue(assetId, "kellumisen_syy")
  }

  private def fetchRoadLink(linkId: Long): Option[RoadLinkLike] = {
    roadLinkService.getRoadLinkFromVVH(linkId, newTransaction = false)
  }

  override def create(asset: NewMassTransitStop, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): Long = {
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(asset.linkId).getOrElse(throw new NoSuchElementException)
    withDynTransaction {
      val point = Point(asset.lon, asset.lat)
      val variation = getVariation(asset.properties, Some(roadLink))
      val assetId = variation.create(asset, username, point, geometry, municipality, administrativeClass, linkSource)

      //TODO Check if this is really needed
      getPersistedStopAndPublishEvent(assetId, _ => Some(roadLink))

      assetId
    }
  }

  private def getVariation(newProperties: Seq[SimpleProperty], roadLink: Option[RoadLink] = None, existingAsset: Option[PersistedMassTransitStop] = None): BusStopVariation = {
    val variations = Seq(terminalVariation, tierekisteriVariation)
    val wasVariation = variations.find(v => v.was(newProperties.toSet, roadLink, existingAsset)).getOrElse(defaultVariation)
    val isVariation = variations.find(v => v.is(newProperties.toSet, roadLink, existingAsset)).getOrElse(defaultVariation)

    if(wasVariation != isVariation)
      wasVariation.revert(existingAsset.get, newProperties.toSet, roadLink.get)//Check this better

    isVariation
  }

  private def getOnlyVariation(newProperties: Seq[SimpleProperty], roadLink: Option[RoadLink] = None, existingAsset: Option[PersistedMassTransitStop] = None): BusStopVariation = {
    val variations = Seq(terminalVariation, tierekisteriVariation)
    variations.find(v => v.is(newProperties.toSet, roadLink, existingAsset)).getOrElse(defaultVariation)
  }

  private def getPersistedStopAndPublishEvent(assetId: Long, roadLinkByLinkId: Long => Option[RoadLinkLike]): MassTransitStopWithProperties = {
    val persistedStop = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption

    persistedStop.foreach { stop =>
      //executeTierekisteriOperation(operation, stop, roadLinkByLinkId, liviId, username)

      val municipalityName = massTransitStopDao.getMunicipalityNameByCode(stop.municipalityCode)
      eventbus.publish("asset:saved", eventBusMassTransitStop(stop, municipalityName))
    }

    //TODO check if it's needed to do a enrish here
    persistedStop
      .map(withFloatingUpdate(persistedStopToMassTransitStopWithProperties(roadLinkByLinkId)))
      .get
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

  def mandatoryProperties(): Map[String, String] = {
    val requiredProperties = withDynSession {
      sql"""select public_id, property_type from property where asset_type_id = $typeId and required = 1""".as[(String, String)].iterator.toMap
    }
    val validityDirection = AssetPropertyConfiguration.commonAssetProperties(AssetPropertyConfiguration.ValidityDirectionId)
    requiredProperties + (validityDirection.publicId -> validityDirection.propertyType)
  }

  def deleteMassTransitStopData(assetId: Long) = {
    withDynTransaction {
      val persistedStop = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption
      val variation = getOnlyVariation(Seq(), None, persistedStop)
      variation.delete(persistedStop.get)

      /*
      val persistedStop = fetchPointAssets(massTransitStopDao.withId(assetId)).headOption
      val relevantToTR = MassTransitStopOperations.isStoredInTierekisteri(Some(persistedStop.get))

      massTransitStopDao.deleteAllMassTransitStopData(assetId)

      if (relevantToTR && tierekisteriClient.isTREnabled) {
        val liviIdOption = getByLiviId(persistedStop.get.propertyData).map(_.propertyValue)

        liviIdOption match {
          case Some(liviId) => tierekisteriClient.deleteMassTransitStop(liviId)
          case _ => throw new RuntimeException(s"bus stop relevant to Tierekisteri doesn't have 'yllapitajan koodi' property")
        }
      }*/
    }
  }


}

trait BusStopVariation
{
  val typeId: Int
  val massTransitStopDao: MassTransitStopDao

  val roadLinkService: RoadLinkService

  val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  def is(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAsset: Option[PersistedMassTransitStop]): Boolean = {false}
  def was(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAsset: Option[PersistedMassTransitStop]): Boolean = {false}
  def revert(existingAsset: PersistedMassTransitStop, newProperties: Set[SimpleProperty], roadLink: RoadLink): Unit = {}
  def enrichBusStop(persistedStop: PersistedMassTransitStop): PersistedMassTransitStop
  def create(newAsset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): Long
  def update(persistedStop: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit): Long
  def delete(asset: PersistedMassTransitStop): Unit

  protected def updateAdministrativeClassValue(assetId: Long, administrativeClass: AdministrativeClass): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "linkin_hallinnollinen_luokka", administrativeClass.value)
  }

  def setPropertiesDefaultValues(properties: Seq[SimpleProperty]): Seq[SimpleProperty] = {
    val inventoryDate = properties.find(_.publicId == MassTransitStopOperations.InventoryDateId)
    val notInventoryDate = properties.filterNot(_.publicId == MassTransitStopOperations.InventoryDateId)
    if (inventoryDate.nonEmpty && inventoryDate.get.values.exists(_.propertyValue != "")) {
      properties
    } else {
      notInventoryDate ++ Seq(SimpleProperty(MassTransitStopOperations.InventoryDateId, Seq(PropertyValue(toIso8601.print(DateTime.now())))))
    }
  }

  def updatePosition(id: Long, roadLink: RoadLink)(position: Position) = {
    val point = Point(position.lon, position.lat)
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)
    val newPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(point)
    massTransitStopDao.updateLrmPosition(id, mValue, roadLink.linkId, roadLink.linkSource)
    massTransitStopDao.updateBearing(id, position)
    massTransitStopDao.updateMunicipality(id, roadLink.municipalityCode)
    updateAssetGeometry(id, newPoint)
  }

  //TODO I think i'm missing part of this in the another updates/creates
  /**
    * Update properties and administrative class for asset. Return optionally new LiviId
    *
    * @param assetId
    * @param properties
    * @param administrativeClass
    * @return
    */
  /*
  protected def updatePropertiesForAsset(assetId: Long, properties: Seq[SimpleProperty], administrativeClass: AdministrativeClass,
                                         nationalId: Long, assetLiviId: Option[PropertyValue]) = {
    massTransitStopDao.updateAssetProperties(id, properties)
    updateAdministrativeClassValue(id, administrativeClass)
    if (!assetLiviId.exists(_.propertyValue != ""))
      overWriteLiViIdentifierProperty(id, nationalId, properties, Some(administrativeClass))
    else
      None
  }*/
}

class DefaultBusStopVariation(val typeId : Int, val massTransitStopDao: MassTransitStopDao, val roadLinkService: RoadLinkService) extends BusStopVariation
{

  override def enrichBusStop(asset: PersistedMassTransitStop): PersistedMassTransitStop = {
    asset
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): Long = {

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

    assetId
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int) => Unit): Long = {

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode)

    val linkId = optionalPosition match {
      case Some(position) => position.linkId
      case _ => asset.linkId
    }
    //TODO move this outside this update
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, newTransaction = false).
      getOrElse(throw new NoSuchElementException)

    massTransitStopDao.updateAssetLastModified(asset.id, username)

    optionalPosition.map(updatePosition(asset.id, roadLink))

    //Remove from common assets the side code property
    val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
      filterNot(_._1 == AssetPropertyConfiguration.ValidityDirectionId)

    val props = setPropertiesDefaultValues(properties.toSeq)
    val mergedProperties = (asset.propertyData.
      filterNot(property => props.exists(_.publicId == property.publicId)).
      map(property => SimpleProperty(property.publicId, property.values)) ++ props).
      filterNot(property => commonAssetProperties.exists(_._1 == property.publicId))

    //TODO check what it was suppose to do after
    //update(asset, optionalPosition, username, mergedProperties, roadLink, Operation.Noop)

    asset.id
  }

  override def delete(asset: PersistedMassTransitStop): Unit = {
    massTransitStopDao.deleteAllMassTransitStopData(asset.id)
  }
}

//TODO missing the fetch on the massstransitdao
class TerminalBusStopVariation(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService) extends DefaultBusStopVariation(typeId, massTransitStopDao, roadLinkService)
{
  private val radiusMeters = 200
  private val terminalChildrenPublicId = "liitetyt_pysakit"

  override def is(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    //If the stop have the property stop type with the terminal value

    //TODO Check if this is really needed
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

  override def enrichBusStop(asset: PersistedMassTransitStop): PersistedMassTransitStop = {
    /*asset.propertyData.find(_.publicId == terminalChildrenPublicId) match{
      case Some(property) =>
        val childFilters = fetchByRadius(Point(asset.lon, asset.lat), radiusMeters)
        val newProperty = property.copy(values = property.values ++ childFilters.filterNot(a => property.values.exists(_.propertyValue.toLong == a.id)).
          map{a => PropertyValue(a.id.toString, Some(s"""${a.nationalId} """), checked = false)})
        asset.copy(propertyData = asset.propertyData.filterNot(p => p.publicId == terminalChildrenPublicId) ++ Seq(newProperty))
      case _ =>
        asset
    }*/
    //TODO get already added properties
    val childFilters = fetchByRadius(Point(asset.lon, asset.lat), radiusMeters, asset.id)
    val newProperty = Property(0, terminalChildrenPublicId, PropertyTypes.MultipleChoice, values = childFilters.map{a =>
      val stopName = extractStopName(a.propertyData)
      PropertyValue(a.id.toString, Some(s"""${a.nationalId} $stopName"""), checked = false)
    })
    asset.copy(propertyData = asset.propertyData.filterNot(p => p.publicId == terminalChildrenPublicId) ++ Seq(newProperty))
  }

  private def extractStopName(properties: Seq[Property]): String = {
    properties
      .filter { property => property.publicId.equals("nimi_ruotsiksi") }
      .filterNot { property => property.values.isEmpty }
      .map(_.values.head)
      .map(_.propertyValue)
      .headOption
      .getOrElse("")
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): Long = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, municipality, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)
    //TODO: When table is complete uncomment this if attribute BUS_STOP_ASSET_ID can be null
    //   massTransitStopDao.insertTerminal(assetId)

    val children = MassTransitStopOperations.getTerminalMassTransitStopChildren(asset.properties)
    //TODO: fetch by Seq[Long] id to check if the children exist and if they are at 200m

    massTransitStopDao.insertChildren(assetId, children)

    //TODO: Change property Value
    val properties = setPropertiesDefaultValues(asset.properties) ++ Seq(SimpleProperty(MassTransitStopOperations.MassTransitStopTypePublicId, Seq(PropertyValue("5"))))

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    if (MassTransitStopOperations.mixedStoptypes(properties.toSet))
      throw new IllegalArgumentException

    massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, administrativeClass.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))
    assetId
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int) => Unit): Long = {

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode)

    val linkId = optionalPosition match {
      case Some(position) => position.linkId
      case _ => asset.linkId
    }

    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, newTransaction = false).
      getOrElse(throw new NoSuchElementException)

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

    id
  }

  override def delete(asset: PersistedMassTransitStop): Unit = {
    massTransitStopDao.deleteTerminalMassTransitStopData(asset.id)
  }

  private def fetchByRadius(position : Point, meters: Int, terminalId: Long): Seq[PersistedMassTransitStop] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = OracleDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"where a.asset_type_id = $typeId and ($boundingBoxFilter or terminal_asset_id = $terminalId)"
    massTransitStopDao.fetchPointAssets(massTransitStopDao.withFilter(filter)).
      filter(r => GeometryUtils.geometryLength(Seq(position, Point(r.lon, r.lat))) <= meters)
  }
}

class TierekisteriBusStopVariation(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, tierekisteriClient: TierekisteriMassTransitStopClient) extends DefaultBusStopVariation(typeId, massTransitStopDao, roadLinkService)
{
  //TODO check the really need for that or put it in the parent class
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  val MaxMovementDistanceMeters = 50
  val geometryTransform = new GeometryTransform


  lazy val massTransitStopEnumeratedPropertyValues = {
    withDynSession{
      val properties = Queries.getEnumeratedPropertyValues(typeId)
      properties.map(epv => epv.publicId -> epv.values).toMap
    }
  }

  /**
    * Verify if the stop is relevant to Tierekisteri: Must be non-virtual and must be administered by ELY or HSL.
    * Convenience method
    *
    */
  def isStoredInTierekisteri(properties: Seq[AbstractProperty], administrativeClass: Option[AdministrativeClass]): Boolean ={
    val administrationProperty = properties.find(_.publicId == MassTransitStopOperations.AdministratorInfoPublicId)
    val stopType = properties.find(pro => pro.publicId == MassTransitStopOperations.MassTransitStopTypePublicId)
    val elyAdministrated = administrationProperty.exists(_.values.headOption.exists(_.propertyValue == MassTransitStopOperations.CentralELYPropertyValue))
    val isVirtualStop = stopType.exists(_.values.exists(_.propertyValue == MassTransitStopOperations.VirtualBusStopPropertyValue))
    val isHSLAdministrated =  administrationProperty.exists(_.values.headOption.exists(_.propertyValue == MassTransitStopOperations.HSLPropertyValue))
    val isAdminClassState = administrativeClass.map(_ == State).getOrElse(false)
    !isVirtualStop && (elyAdministrated || (isHSLAdministrated && isAdminClassState))
  }

  override def is(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    if (!tierekisteriClient.isTREnabled)
      return false

    //TODO Check if this is really needed
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimpleProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }

    isStoredInTierekisteri(properties, roadLink.map(_.administrativeClass))
  }

  override def was(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    if (!tierekisteriClient.isTREnabled || existingAssetOption.isEmpty)
      return false

    val existingAsset = existingAssetOption.get
    val administrationClass = MassTransitStopOperations.getAdministrationClass(existingAsset.propertyData)
    isStoredInTierekisteri(existingAsset.propertyData, administrationClass)
  }

  override def revert(existingAsset: PersistedMassTransitStop, newProperties: Set[SimpleProperty], roadLink: RoadLink): Unit = {
    massTransitStopDao.updateTextPropertyValue(existingAsset.id, MassTransitStopOperations.LiViIdentifierPublicId, "")
    //Some(SimpleProperty(MassTransitStopOperations.LiViIdentifierPublicId, Seq(PropertyValue("", Some("")))))
    val isVirtualBusStop = MassTransitStopOperations.isVirtualBusStop(newProperties)
    //TODO Probably i will need to have roadlink information here too
    if(isVirtualBusStop)
      executeTierekisteriOperation(Operation.Remove, existingAsset, None, None, None)
    else
      executeTierekisteriOperation(Operation.Expire, existingAsset, None, None, None)
  }

  override def enrichBusStop(persistedStop: PersistedMassTransitStop): PersistedMassTransitStop = {
      val properties = persistedStop.propertyData
      val liViProp = properties.find(_.publicId == MassTransitStopOperations.LiViIdentifierPublicId)
      val liViId = liViProp.flatMap(_.values.headOption).map(_.propertyValue)
      val tierekisteriStop = liViId.flatMap(tierekisteriClient.fetchMassTransitStop)
      tierekisteriStop.isEmpty match {
        /*case true => (persistedStop, true)
        case false => (enrichWithTierekisteriInfo(persistedStop, tierekisteriStop.get), false)*/
        case true => persistedStop
        case false => enrichWithTierekisteriInfo(persistedStop, tierekisteriStop.get)
      }
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): Long = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, municipality, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)

    val properties = setPropertiesDefaultValues(asset.properties)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    if (MassTransitStopOperations.mixedStoptypes(properties.toSet))
      throw new IllegalArgumentException

    massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, administrativeClass.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))
    val newAdminClassProperty = SimpleProperty(MassTransitStopOperations.MassTransitStopAdminClassPublicId, Seq(PropertyValue(administrativeClass.getOrElse(Unknown).value.toString)))
    val propsWithAdminClass = properties.filterNot(_.publicId == MassTransitStopOperations.MassTransitStopAdminClassPublicId) ++ Seq(newAdminClassProperty)

    val liviId = "OTHJ%d".format(nationalId)
    massTransitStopDao.updateTextPropertyValue(assetId, MassTransitStopOperations.LiViIdentifierPublicId, liviId)
    val liviIdProperty = SimpleProperty(MassTransitStopOperations.LiViIdentifierPublicId, Seq(PropertyValue(liviId, Some(liviId))))

    //TODO DO here the tierekisteri Operations
    //executeTierekisteriOperation(Operation.Create)

    assetId
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: (Int) => Unit): Long = {
    val props = setPropertiesDefaultValues(properties.toSeq)

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode)
    val linkId = optionalPosition match {
      case Some(position) => position.linkId
      case _ => asset.linkId
    }

    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, newTransaction = false).
      getOrElse(throw new NoSuchElementException)

    val (municipalityCode, geometry) = (roadLink.municipalityCode, roadLink.geometry)

    // Enrich properties with old administrator, if administrator value is empty in CSV import
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)

    val id = asset.id
    massTransitStopDao.updateAssetLastModified(id, username)
    massTransitStopDao.updateAssetProperties(id, verifiedProperties.toSeq)
    updateAdministrativeClassValue(id, roadLink.administrativeClass)

    optionalPosition.map(updatePosition(asset.id, roadLink))

    //Remove from common assets the side code property
    val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
      filterNot(_._1 == AssetPropertyConfiguration.ValidityDirectionId)

    val mergedProperties = (asset.propertyData.
      filterNot(property => properties.exists(_.publicId == property.publicId)).
      map(property => SimpleProperty(property.publicId, property.values)) ++ properties).
      filterNot(property => commonAssetProperties.exists(_._1 == property.publicId))

    //If it was already in Tierekisteri
    if (was(properties, Some(roadLink), Some(asset))) {
      val position = optionalPosition.get
      val assetPoint = Point(asset.lon, asset.lat)
      val newPoint = Point(position.lon, position.lat)
      val assetDistance = assetPoint.distance2DTo(newPoint)
      if (assetDistance > MaxMovementDistanceMeters) {
        val newInventoryDateValue =
          asset.propertyData.filter(_.publicId == MassTransitStopOperations.InventoryDateId).map(prop =>
            Property(prop.id, prop.publicId, prop.propertyType, prop.required, Seq())
          )
        val newPropertyData = asset.propertyData.filterNot(_.publicId == MassTransitStopOperations.InventoryDateId) ++ newInventoryDateValue
        val newAsset = asset.copy(propertyData = newPropertyData)

        //Expire the old asset
        expireMassTransitStop(username, newAsset)

        //Remove the InventoryDate Property to used the actual instead the old value when create a new asset
        val mergedPropertiesWithOutInventoryDate = mergedProperties.filterNot(_.publicId == MassTransitStopOperations.InventoryDateId)

        //Create a new asset
        return create(NewMassTransitStop(position.lon, position.lat, linkId, position.bearing.getOrElse(asset.bearing.get),
          mergedPropertiesWithOutInventoryDate), username, newPoint, geometry, municipalityCode, Some(roadLink.administrativeClass), roadLink.linkSource)
      }

      //TODO execute tierekisteri operations
      //executeTierekisteriOperation(Operation.Update, asset)
    }
    asset.id
  }

  private def getLiviIdValue(properties: Seq[AbstractProperty]) = {
    properties.find(_.publicId == MassTransitStopOperations.LiViIdentifierPublicId).flatMap(prop => prop.values.headOption)
  }

  //  @throws(classOf[TierekisteriClientException])
  private def expireMassTransitStop(username: String, persistedStop: PersistedMassTransitStop) = {
    val expireDate= new Date()
    massTransitStopDao.expireMassTransitStop(username, persistedStop.id)
    val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get)
    val updatedTierekisteriMassTransitStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(persistedStop, address, Option(roadSide), Option(expireDate))
    tierekisteriClient.updateMassTransitStop(updatedTierekisteriMassTransitStop, None, Some(username))
  }

  /**
    * Override the properties values passed as parameter using override operations
    *
    * @param tierekisteriStop         Tierekisteri Asset
    * @param persistedMassTransitStop Asset properties
    * @return Sequence of overridden properties
    */
  private def enrichWithTierekisteriInfo(persistedMassTransitStop: PersistedMassTransitStop, tierekisteriStop: TierekisteriMassTransitStop): PersistedMassTransitStop = {

    val overridePropertyValueOperations: Seq[(TierekisteriMassTransitStop, Property) => Property] = Seq(
      setEquipments,
      setTextPropertyValueIfEmpty(MassTransitStopOperations.nameFiPublicId, { ta => ta.nameFi.getOrElse("") }),
      setTextPropertyValueIfEmpty(MassTransitStopOperations.nameSePublicId, { ta => ta.nameSe.getOrElse("") })
      //In the future if we need to override some property just add here the operation
    )

    persistedMassTransitStop.copy(propertyData = persistedMassTransitStop.propertyData.map {
        property =>
          overridePropertyValueOperations.foldLeft(property) { case (prop, operation) =>
            operation(tierekisteriStop, prop)
          }
      }
    )
  }

  def setTextPropertyValueIfEmpty (publicId: String, getValue: TierekisteriMassTransitStop => String)(tierekisteriStop: TierekisteriMassTransitStop, property: Property): Property = {
    if (property.publicId == publicId && property.values.isEmpty) {
      val propertyValueString = getValue(tierekisteriStop)
      property.copy(values = Seq(new PropertyValue(propertyValueString, Some(propertyValueString))))
    } else {
      property
    }
  }

  /**
    * Override property values of all equipment properties
    *
    * @param tierekisteriStop Tierekisteri Asset
    * @param property         Asset property
    * @return Property passed as parameter if have no match with equipment property or property overriden with tierekisteri values
    */
  private def setEquipments(tierekisteriStop: TierekisteriMassTransitStop, property: Property) = {
    if (tierekisteriStop.equipments.isEmpty) {
      property
    } else {
      val equipment = Equipment.fromPublicId(property.publicId)
      val existence = tierekisteriStop.equipments.get(equipment)
      existence.isEmpty || !equipment.isMaster match {
        case true => property
        case false =>
          val propertyValueString = existence.get.propertyValue.toString
          val propertyOverrideValue = massTransitStopEnumeratedPropertyValues.
            get(property.publicId).get.find(_.propertyValue == propertyValueString).get
          property.copy(values = Seq(propertyOverrideValue))
      }
    }
  }

  //TODO this can be refactored too
  private def executeTierekisteriOperation(operation: Operation, persistedStop: PersistedMassTransitStop, roadLink: Option[RoadLinkLike], liviId: Option[String], username: Option[String] = None) = {
    if (operation != Operation.Noop) {
      val road = roadLink.flatMap(_.roadNumber match {
        case Some(str) => Try(str.toString.toInt).toOption
        case _ => None
      }
      )
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get, road = road)

      val expire = if(operation == Operation.Expire) Some(new Date()) else None
      val newTierekisteriMassTransitStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(persistedStop, address, Option(roadSide), expire, liviId)

      operation match {
        case Create => tierekisteriClient.createMassTransitStop(newTierekisteriMassTransitStop)
        case Update => tierekisteriClient.updateMassTransitStop(newTierekisteriMassTransitStop, liviId)
        case Expire => tierekisteriClient.updateMassTransitStop(newTierekisteriMassTransitStop, liviId, username)
        case Remove => tierekisteriClient.deleteMassTransitStop(liviId.getOrElse(newTierekisteriMassTransitStop.liviId))
        case Noop =>
      }
    }
  }
}