package fi.liikennevirasto.digiroad2

import java.util.Date

import fi.liikennevirasto.digiroad2.Operation._
import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{Property, _}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop.MassTransitStopOperations
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle._
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Interval, LocalDate}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

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
                                    propertyData: Seq[Property], linkSource: LinkGeomSource) extends PersistedPointAsset with TimeStamps

case class MassTransitStopRow(id: Long, externalId: Long, assetTypeId: Long, point: Option[Point], linkId: Long, bearing: Option[Int],
                              validityDirection: Int, validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                              created: Modification, modified: Modification, wgsPoint: Option[Point], lrmPosition: LRMPosition,
                              roadLinkType: AdministrativeClass = Unknown, municipalityCode: Int, persistedFloating: Boolean)

trait MassTransitStopService extends PointAssetOperations {
  type IncomingAsset = NewMassTransitStop
  type PersistedAsset = PersistedMassTransitStop

  lazy val logger = LoggerFactory.getLogger(getClass)
  val massTransitStopDao: MassTransitStopDao
  val tierekisteriClient: TierekisteriMassTransitStopClient
  val tierekisteriEnabled: Boolean
  val roadLinkService: RoadLinkService
  override val idField = "external_id"

  override def typeId: Int = 10

  val MaxMovementDistanceMeters = 50

  val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  val geometryTransform = new GeometryTransform

  lazy val massTransitStopEnumeratedPropertyValues = {
    withDynSession{
      val properties = Queries.getEnumeratedPropertyValues(typeId)
      properties.map(epv => epv.publicId -> epv.values).toMap
    }
  }

  def withDynSession[T](f: => T): T

  def withDynTransaction[T](f: => T): T

  def eventbus: DigiroadEventBus

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, persistedStop.nationalId, asset.linkId, persistedStop.stopTypes, persistedStop.municipalityCode, asset.lon, asset.lat,
      asset.mValue, persistedStop.validityDirection, persistedStop.bearing, persistedStop.validityPeriod, persistedStop.floating, persistedStop.vvhTimeStamp,
      persistedStop.created, persistedStop.modified, persistedStop.propertyData, persistedStop.linkSource)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment): Long = {
    updateAjustedGeometry(adjustment, persistedAsset.linkSource)
    persistedAsset.id
  }


  def getByNationalId[T <: FloatingAsset](nationalId: Long, municipalityValidation: Int => Unit, persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason])): Option[T] = {
    withDynTransaction {
      val persistedStop = fetchPointAssets(withNationalId(nationalId)).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      persistedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
    }
  }

  def getByLiviId[T <: FloatingAsset](liviId: String, municipalityValidation: Int => Unit, persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason])): Option[T] = {
    withDynTransaction {
      val nationalId = getNationalIdByLiviId(liviId).headOption
      nationalId match {
        case Some(id) =>
          val persistedStop = fetchPointAssets(withNationalId(id)).headOption
          persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
          persistedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
        case None => None
      }
    }
  }

  private def getNationalIdByLiviId(liviId: String): Seq[Long] = {
    sql"""
      select a.external_id
      from text_property_value tp
      join asset a on a.id = tp.asset_id
      where tp.property_id = (select p.id from property p where p.public_id = 'yllapitajan_koodi')
      and tp.value_fi = $liviId""".as[Long].list
  }

  /**
    * Run enriching if the stop is found in Tierekisteri. Return enriched stop with a boolean flag for errors (not found in TR)
    *
    * @param persistedStop Optional mass transit stop
    * @return Enriched stop with a boolean flag for TR operation errors
    */
  private def enrichStopIfInTierekisteri(persistedStop: Option[PersistedMassTransitStop]) = {

    if (MassTransitStopOperations.isStoredInTierekisteri(persistedStop) && tierekisteriEnabled) {
      val properties = persistedStop.map(_.propertyData).get
      val liViProp = properties.find(_.publicId == MassTransitStopOperations.LiViIdentifierPublicId)
      val liViId = liViProp.flatMap(_.values.headOption).map(_.propertyValue)
      val tierekisteriStop = liViId.flatMap(tierekisteriClient.fetchMassTransitStop)
      tierekisteriStop.isEmpty match {
        case true => (persistedStop, true)
        case false => (enrichPersistedMassTransitStop(persistedStop, tierekisteriStop.get), false)
      }
    } else {
      (persistedStop, false)
    }
  }

  def getByNationalIdWithTRWarnings[T <: FloatingAsset](nationalId: Long, municipalityValidation: Int => Unit,
                                                        persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason])): (Option[T], Boolean) = {
    withDynTransaction {
      val persistedStop = fetchPointAssets(withNationalId(nationalId)).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      val (enrichedStop, trError) = enrichStopIfInTierekisteri(persistedStop)
      (enrichedStop.map(withFloatingUpdate(persistedStopToFloatingStop)), trError)
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

  /**
    * Override property value when the value is empty
    *
    * @param publicId         The public id of the property
    * @param getValue         Function to get the property value from Tierekisteri Asset
    * @param tierekisteriStop Tierekisteri Asset
    * @param property         Asset property
    * @return Property passed as parameter if have no match with equipment property or property overriden with tierekisteri values
    */
  private def setTextPropertyValueIfEmpty(publicId: String, getValue: TierekisteriMassTransitStop => String)(tierekisteriStop: TierekisteriMassTransitStop, property: Property): Property = {
    if (property.publicId == publicId && property.values.isEmpty) {
      val propertyValueString = getValue(tierekisteriStop)
      property.copy(values = Seq(new PropertyValue(propertyValueString, Some(propertyValueString))))
    } else {
      property
    }
  }

  /**
    * Override the properties values passed as parameter using override operations
    *
    * @param tierekisteriStop         Tierekisteri Asset
    * @param persistedMassTransitStop Asset properties
    * @return Sequence of overridden properties
    */
  private def enrichPersistedMassTransitStop(persistedMassTransitStop: Option[PersistedMassTransitStop], tierekisteriStop: TierekisteriMassTransitStop): Option[PersistedMassTransitStop] = {
    val overridePropertyValueOperations: Seq[(TierekisteriMassTransitStop, Property) => Property] = Seq(
      setEquipments,
      setTextPropertyValueIfEmpty(MassTransitStopOperations.nameFiPublicId, { ta => ta.nameFi.getOrElse("") }),
      setTextPropertyValueIfEmpty(MassTransitStopOperations.nameSePublicId, { ta => ta.nameSe.getOrElse("") })
      //In the future if we need to override some property just add here the operation
    )

    persistedMassTransitStop.map(massTransitStop =>
      massTransitStop.copy(propertyData = massTransitStop.propertyData.map {
        property =>
          overridePropertyValueOperations.foldLeft(property) { case (prop, operation) =>
            operation(tierekisteriStop, prop)
          }
      }
      )
    )
  }

  def getMassTransitStopByNationalId(nationalId: Long, municipalityValidation: Int => Unit): Option[MassTransitStopWithProperties] = {
    getByNationalId(nationalId, municipalityValidation, persistedStopToMassTransitStopWithProperties(fetchRoadLink))
  }

  def getMassTransitStopByNationalIdWithTRWarnings(nationalId: Long, municipalityValidation: Int => Unit): (Option[MassTransitStopWithProperties], Boolean) = {
    getByNationalIdWithTRWarnings(nationalId, municipalityValidation, persistedStopToMassTransitStopWithProperties(fetchRoadLink))
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

  private def persistedStopToMassTransitStopWithProperties(roadLinkByLinkId: Long => Option[RoadLinkLike])
                                                          (persistedStop: PersistedMassTransitStop): (MassTransitStopWithProperties, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    (MassTransitStopWithProperties(id = persistedStop.id, nationalId = persistedStop.nationalId, stopTypes = persistedStop.stopTypes,
      lon = persistedStop.lon, lat = persistedStop.lat, validityDirection = persistedStop.validityDirection,
      bearing = persistedStop.bearing, validityPeriod = persistedStop.validityPeriod, floating = floating,
      propertyData = persistedStop.propertyData), floatingReason)
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PersistedMassTransitStop] = {
    val query = """
        select a.id, a.external_id, a.asset_type_id, a.bearing, lrm.side_code,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        lrm.adjusted_timestamp, p.id, p.public_id, p.property_type, p.required, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          when np.value is not null then to_char(np.value)
          else null
        end as display_value,
        lrm.id, lrm.start_measure, lrm.end_measure, lrm.link_id,
        a.created_date, a.created_by, a.modified_date, a.modified_by,
        SDO_CS.TRANSFORM(a.geometry, 4326) AS position_wgs84, lrm.link_source
        from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
        join property p on a.asset_type_id = p.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
      """
    queryToPersistedMassTransitStops(queryFilter(query))
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
        val administrationClass = getAdministrationClass(persistedAsset.asInstanceOf[PersistedMassTransitStop])
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
        val administrationClass = getAdministrationClass(persistedAsset.asInstanceOf[PersistedMassTransitStop])
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

  private def queryToPersistedMassTransitStops(query: String): Seq[PersistedMassTransitStop] = {
    val rows = StaticQuery.queryNA[MassTransitStopRow](query).iterator.toSeq

    rows.groupBy(_.id).map { case (id, stopRows) =>
      val row = stopRows.head
      val commonProperties: Seq[Property] = AssetPropertyConfiguration.assetRowToCommonProperties(row)
      val properties: Seq[Property] = commonProperties ++ massTransitStopDao.assetRowToProperty(stopRows)
      val point = row.point.get
      val validityPeriod = Some(constructValidityPeriod(row.validFrom, row.validTo))
      val stopTypes = extractStopTypes(stopRows)
      val mValue = row.lrmPosition.startMeasure
      val vvhTimeStamp = row.lrmPosition.vvhTimeStamp
      val linkSource = row.lrmPosition.linkSource

      id -> PersistedMassTransitStop(id = row.id, nationalId = row.externalId, linkId = row.linkId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y, mValue = mValue,
        validityDirection = Some(row.validityDirection), bearing = row.bearing,
        validityPeriod = validityPeriod, floating = row.persistedFloating, vvhTimeStamp = vvhTimeStamp, created = row.created, modified = row.modified,
        propertyData = properties, linkSource = LinkGeomSource(linkSource))
    }.values.toSeq
  }

  def getByMunicipality(municipalityCode: Int, enrichWithTR: Boolean): Seq[PersistedAsset] = {
    if (enrichWithTR)
      getByMunicipality(municipalityCode)
    else {
      val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(municipalityCode)
      val mapRoadLinks = roadLinks.map(roadLink => roadLink.linkId -> roadLink).toMap
      super.getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createOperation))
    }
  }

  private def withNationalId(nationalId: Long)(query: String): String = {
    query + s" where a.external_id = $nationalId"
  }

  private def withId(id: Long)(query: String): String = {
    query + s" where a.id = $id"
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

  override def update(id: Long, updatedAsset: NewMassTransitStop, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    throw new NotImplementedError("Use updateExisting instead. Mass transit is legacy.")
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedMassTransitStop] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(roadLink => roadLink.linkId -> roadLink).toMap
    val assets = super.getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, Seq(), floatingAdjustment(adjustmentOperation, createOperation))
    assets.flatMap(a => enrichStopIfInTierekisteri(Some(a))._1)
  }

  private def updateExisting(queryFilter: String => String, optionalPosition: Option[Position],
                             properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit): MassTransitStopWithProperties = {
    withDynTransaction {

      if (MassTransitStopOperations.mixedStoptypes(properties))
        throw new IllegalArgumentException

      val persistedStop = fetchPointAssets(queryFilter).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      val asset = persistedStop.get

      val linkId = optionalPosition match {
        case Some(position) => position.linkId
        case _ => asset.linkId
      }

      val roadLink = fetchRoadLinkAndComplementary(linkId)
      val (municipalityCode, geometry) = roadLink
        .map { x => (x.municipalityCode, x.geometry) }
        .getOrElse(throw new NoSuchElementException)

      // Enrich properties with old administrator, if administrator value is empty in CSV import
      val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)

      val id = asset.id
      massTransitStopDao.updateAssetLastModified(id, username)
      val isVirtualBusStop = MassTransitStopOperations.isVirtualBusStop(properties)
      val oldLiviIdProperty = MassTransitStopOperations.liviIdValueOption(asset.propertyData)
      val newLiviIdProperty = if (verifiedProperties.nonEmpty) {
        val administrationProperty = verifiedProperties.find(_.publicId == MassTransitStopOperations.AdministratorInfoPublicId)
        val elyAdministrated = administrationProperty.exists(_.values.headOption.exists(_.propertyValue == MassTransitStopOperations.CentralELYPropertyValue))
        val hslAdministrated = administrationProperty.exists(_.values.headOption.exists(_.propertyValue == MassTransitStopOperations.HSLPropertyValue))
        if ((!(elyAdministrated || hslAdministrated) || isVirtualBusStop) && MassTransitStopOperations.liviIdValueOption(asset.propertyData).exists(_.propertyValue != "")) {
          updatePropertiesForAsset(id, verifiedProperties.toSeq, roadLink.get.administrativeClass, asset.nationalId, None)
        } else {
          updatePropertiesForAsset(id, verifiedProperties.toSeq, roadLink.get.administrativeClass, asset.nationalId, MassTransitStopOperations.liviIdValueOption(asset.propertyData))
        }
      } else {
        None
      }
      if (optionalPosition.isDefined) {
        val position = optionalPosition.get
        val point = Point(position.lon, position.lat)
        val mValue = calculateLinearReferenceFromPoint(point, geometry)
        massTransitStopDao.updateLrmPosition(id, mValue, linkId, roadLink.get.linkSource)
        massTransitStopDao.updateBearing(id, position)
        massTransitStopDao.updateMunicipality(id, municipalityCode)
        updateAssetGeometry(id, point)
      }

      //Remove from common assets the side code property
      val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
        filterNot(_._1 == AssetPropertyConfiguration.ValidityDirectionId)

      val mergedProperties = (asset.propertyData.
        filterNot(property => properties.exists(_.publicId == property.publicId)).
        map(property => SimpleProperty(property.publicId, property.values)) ++ properties).
        filterNot(property => commonAssetProperties.exists(_._1 == property.publicId))

      val wasStoredInTierekisteri = MassTransitStopOperations.isStoredInTierekisteri(persistedStop)
      val shouldBeInTierekisteri = MassTransitStopOperations.isStoredInTierekisteri(mergedProperties)

      val operation = (wasStoredInTierekisteri, shouldBeInTierekisteri, isVirtualBusStop) match {
        case (true, true, _) => Operation.Update
        case (true, false, false) => Operation.Expire
        case (true, false, true) => Operation.Remove
        case (false, true, _) => Operation.Create
        case (false, false, _) => Operation.Noop
      }

      if (optionalPosition.isDefined && operation == Operation.Update) {
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
          create(NewMassTransitStop(position.lon, position.lat, linkId, position.bearing.getOrElse(asset.bearing.get),
            mergedPropertiesWithOutInventoryDate), username, newPoint, geometry, municipalityCode, Some(roadLink.get.administrativeClass), roadLink.get.linkSource)
        } else {
          update(asset, optionalPosition, username, mergedProperties, roadLink.get, operation)
        }
      } else {
        update(asset, optionalPosition, username, mergedProperties, roadLink.get, operation)
      }
    }
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

  /**
    * Update properties and administrative class for asset. Return optionally new LiviId
    *
    * @param id
    * @param properties
    * @param administrativeClass
    * @return
    */
  private def updatePropertiesForAsset(id: Long, properties: Seq[SimpleProperty], administrativeClass: AdministrativeClass,
                                       nationalId: Long, assetLiviId: Option[PropertyValue]) = {
    massTransitStopDao.updateAssetProperties(id, properties)
    updateAdministrativeClassValue(id, administrativeClass)
    if (!assetLiviId.exists(_.propertyValue != ""))
      overWriteLiViIdentifierProperty(id, nationalId, properties, Some(administrativeClass))
    else
      None
  }

  private def update(persistedStop: PersistedMassTransitStop, optionalPosition: Option[Position], username: String,
                     properties: Seq[SimpleProperty], roadLink: RoadLinkLike, tierekisteriOperation: Operation): MassTransitStopWithProperties = {

    val id = persistedStop.id
    if (optionalPosition.isDefined) {
      val position = optionalPosition.get
      val point = Point(position.lon, position.lat)
      val mValue = calculateLinearReferenceFromPoint(point, roadLink.geometry)
      val newPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(point)
      massTransitStopDao.updateLrmPosition(id, mValue, roadLink.linkId, roadLink.linkSource)
      massTransitStopDao.updateBearing(id, position)
      massTransitStopDao.updateMunicipality(id, roadLink.municipalityCode)
      updateAssetGeometry(id, newPoint)
    }
    val tierekisteriLiviId = MassTransitStopOperations.liviIdValueOption(persistedStop.propertyData).map(_.propertyValue) // Using the saved LiviId if any
    val modifiedAsset = fetchPointAssets(withId(id)).headOption // Reload from database
    if(tierekisteriOperation == Operation.Expire || tierekisteriOperation == Operation.Remove){
      executeTierekisteriOperation(tierekisteriOperation, modifiedAsset.get, { _ => Some(roadLink) }, tierekisteriLiviId, Some(username))
      convertPersistedStopWithPropertiesAndPublishEvent(modifiedAsset, { _ => Some(roadLink) }, Operation.Noop, tierekisteriLiviId, Some(username))
    } else {
      convertPersistedStopWithPropertiesAndPublishEvent(modifiedAsset, { _ => Some(roadLink) }, tierekisteriOperation, tierekisteriLiviId, Some(username))
    }
  }

  def updateExistingById(id: Long, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit): MassTransitStopWithProperties = {
    val props = updatedProperties(properties.toSeq)
    updateExisting(withId(id), optionalPosition, props.toSet, username, municipalityValidation)
  }

  def updateAdministrativeClassValue(assetId: Long, administrativeClass: AdministrativeClass): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "linkin_hallinnollinen_luokka", administrativeClass.value)
  }

  private def updateFloatingReasonValue(assetId: Long, floatingReason: FloatingReason): Unit ={
    massTransitStopDao.updateNumberPropertyValue(assetId, "kellumisen_syy", floatingReason.value)
  }

  private def deleteFloatingReasonValue(assetId: Long): Unit ={
    massTransitStopDao.deleteNumberPropertyValue(assetId, "kellumisen_syy")
  }

  private def fetchRoadLink(linkId: Long): Option[RoadLinkLike] = {
    roadLinkService.getRoadLinkFromVVH(linkId, newTransaction = false)
  }

  private def fetchRoadLinkAndComplementary(linkId: Long): Option[RoadLinkLike] = {
    roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId, newTransaction = false)
  }

  override def create(asset: NewMassTransitStop, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      val point = Point(asset.lon, asset.lat)
      create(asset, username, point, geometry, municipality, administrativeClass, linkSource).id
    }
  }

  def updatedProperties(properties: Seq[SimpleProperty]): Seq[SimpleProperty] = {
    val inventoryDate = properties.find(_.publicId == MassTransitStopOperations.InventoryDateId)
    val notInventoryDate = properties.filterNot(_.publicId == MassTransitStopOperations.InventoryDateId)
    if (inventoryDate.nonEmpty && inventoryDate.get.values.exists(_.propertyValue != "")) {
      properties
    } else {
      notInventoryDate ++ Seq(SimpleProperty(MassTransitStopOperations.InventoryDateId, Seq(PropertyValue(toIso8601.print(DateTime.now())))))
    }
  }

  private def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass], linkSource: LinkGeomSource): MassTransitStopWithProperties = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = calculateLinearReferenceFromPoint(point, geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, municipality, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)

    val properties = updatedProperties(asset.properties)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    if (!MassTransitStopOperations.mixedStoptypes(properties.toSet))
    {
      massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
      updateAdministrativeClassValue(assetId, administrativeClass.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))
      val newAdminClassProperty = SimpleProperty(MassTransitStopOperations.MassTransitStopAdminClassPublicId, Seq(PropertyValue(administrativeClass.getOrElse(Unknown).value.toString)))
      val propsWithAdminClass = properties.filterNot(_.publicId == MassTransitStopOperations.MassTransitStopAdminClassPublicId) ++ Seq(newAdminClassProperty)
      val liviId = overWriteLiViIdentifierProperty(assetId, nationalId, propsWithAdminClass, administrativeClass)
      val operation = if (MassTransitStopOperations.isStoredInTierekisteri(propsWithAdminClass, administrativeClass)) Operation.Create else Operation.Noop
      getPersistedStopWithPropertiesAndPublishEvent(assetId, fetchRoadLink, operation, liviId.map(_.values.head.propertyValue), Some(username))
    }
    else
      throw new IllegalArgumentException
  }

  private def overWriteLiViIdentifierProperty(assetId: Long, nationalId: Long, properties: Seq[SimpleProperty], administrativeClass: Option[AdministrativeClass]) : Option[SimpleProperty] = {
    if(MassTransitStopOperations.isStoredInTierekisteri(properties, administrativeClass)) {
      val id = "OTHJ%d".format(nationalId)
      massTransitStopDao.updateTextPropertyValue(assetId, MassTransitStopOperations.LiViIdentifierPublicId, id)
      Some(SimpleProperty(MassTransitStopOperations.LiViIdentifierPublicId, Seq(PropertyValue(id, Some(id)))))
    } else{
      massTransitStopDao.updateTextPropertyValue(assetId, MassTransitStopOperations.LiViIdentifierPublicId, "")
      Some(SimpleProperty(MassTransitStopOperations.LiViIdentifierPublicId, Seq(PropertyValue("", Some("")))))
    }
  }

  private def getPersistedStopWithPropertiesAndPublishEvent(assetId: Long, roadLinkByLinkId: Long => Option[RoadLinkLike],
                                                            operation: Operation, liviId: Option[String], username: Option[String]): MassTransitStopWithProperties = {
    // TODO: use already loaded asset
    convertPersistedStopWithPropertiesAndPublishEvent(fetchPointAssets(withId(assetId)).headOption, roadLinkByLinkId, operation, liviId, username)
  }

  private def convertPersistedStopWithPropertiesAndPublishEvent(persistedStop: Option[PersistedMassTransitStop], roadLinkByLinkId: Long => Option[RoadLinkLike],
                                                            operation: Operation, liviId: Option[String], username: Option[String]): MassTransitStopWithProperties = {
    persistedStop.foreach { stop =>
      executeTierekisteriOperation(operation, stop, roadLinkByLinkId, liviId, username)

      val municipalityName = massTransitStopDao.getMunicipalityNameByCode(stop.municipalityCode)
      eventbus.publish("asset:saved", eventBusMassTransitStop(stop, municipalityName))
    }
    persistedStop
      .map(withFloatingUpdate(persistedStopToMassTransitStopWithProperties(roadLinkByLinkId)))
      .get
  }

  def mandatoryProperties(): Map[String, String] = {
    val requiredProperties = withDynSession {
      sql"""select public_id, property_type from property where asset_type_id = $typeId and required = 1""".as[(String, String)].iterator.toMap
    }
    val validityDirection = AssetPropertyConfiguration.commonAssetProperties(AssetPropertyConfiguration.ValidityDirectionId)
    requiredProperties + (validityDirection.publicId -> validityDirection.propertyType)
  }

  def calculateLinearReferenceFromPoint(point: Point, points: Seq[Point]): Double = {
    GeometryUtils.calculateLinearReferenceFromPoint(point, points)
  }

  def deleteAllMassTransitStopData(assetId: Long) = {
    withDynTransaction {

      val persistedStop = fetchPointAssets(withId(assetId)).headOption
      val relevantToTR = MassTransitStopOperations.isStoredInTierekisteri(Some(persistedStop.get))

      massTransitStopDao.deleteAllMassTransitStopData(assetId)

      if (relevantToTR && tierekisteriClient.isTREnabled) {
        val liviIdOption = MassTransitStopOperations.liviIdValueOption(persistedStop.get.propertyData).map(_.propertyValue)

        liviIdOption match {
          case Some(liviId) => tierekisteriClient.deleteMassTransitStop(liviId)
          case _ => throw new RuntimeException(s"bus stop relevant to Tierekisteri doesn't have 'yllapitajan koodi' property")
        }
      }
    }
  }

  implicit val getMassTransitStopRow = new GetResult[MassTransitStopRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong
      val externalId = r.nextLong
      val assetTypeId = r.nextLong
      val bearing = r.nextIntOption
      val validityDirection = r.nextInt
      val validFrom = r.nextDateOption.map(new LocalDate(_))
      val validTo = r.nextDateOption.map(new LocalDate(_))
      val point = r.nextBytesOption.map(bytesToPoint)
      val municipalityCode = r.nextInt()
      val persistedFloating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull)
      val lrmId = r.nextLong
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val linkId = r.nextLong
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val wgsPoint = r.nextBytesOption.map(bytesToPoint)
      val linkSource = r.nextInt
      MassTransitStopRow(id, externalId, assetTypeId, point, linkId, bearing, validityDirection,
        validFrom, validTo, property, created, modified, wgsPoint,
        lrmPosition = LRMPosition(lrmId, startMeasure, endMeasure, point, vvhTimeStamp, linkSource), municipalityCode = municipalityCode, persistedFloating = persistedFloating)
    }
  }

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  def executeTierekisteriOperation(operation: Operation, persistedStop: PersistedMassTransitStop, roadLinkByLinkId: Long => Option[RoadLinkLike], overrideLiviId: Option[String], username: Option[String] = None) = {
    if (operation != Operation.Noop) {
      val roadLink = roadLinkByLinkId.apply(persistedStop.linkId)
      val road = roadLink.flatMap(_.roadNumber
         match {
          case Some(str) => Try(str.toString.toInt).toOption
          case _ => None
        }
      )
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get, road = road)

      val expire = if(operation == Operation.Expire) Some(new Date()) else None
      val newTierekisteriMassTransitStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(persistedStop, address, Option(roadSide), expire, overrideLiviId)

      operation match {
        case Create => tierekisteriClient.createMassTransitStop(newTierekisteriMassTransitStop)
        case Update => tierekisteriClient.updateMassTransitStop(newTierekisteriMassTransitStop, overrideLiviId)
        case Expire => tierekisteriClient.updateMassTransitStop(newTierekisteriMassTransitStop, overrideLiviId, username)
        case Remove => tierekisteriClient.deleteMassTransitStop(overrideLiviId.getOrElse(newTierekisteriMassTransitStop.liviId))
        case Noop =>
      }
    }
  }

  private def constructValidityPeriod(validFrom: Option[LocalDate], validTo: Option[LocalDate]): String = {
    (validFrom, validTo) match {
      case (Some(from), None) => if (from.isAfter(LocalDate.now())) { MassTransitStopValidityPeriod.
        Future }
      else { MassTransitStopValidityPeriod.
        Current }
      case (None, Some(to)) => if (LocalDate.now().isAfter(to
      )) { MassTransitStopValidityPeriod
        .Past }
      else { MassTransitStopValidityPeriod.
        Current }
      case (Some(from), Some(to)) =>
        val interval = new Interval(from.toDateMidnight, to.toDateMidnight)
        if (interval.
          containsNow()) { MassTransitStopValidityPeriod
          .Current }
        else if (interval.
          isBeforeNow) {
          MassTransitStopValidityPeriod.Past }
        else {
          MassTransitStopValidityPeriod.Future }
      case _ => MassTransitStopValidityPeriod.Current
    }
  }

  private def getAdministrationClass(persistedAsset: PersistedMassTransitStop): Option[AdministrativeClass] = {
    MassTransitStopOperations.getAdministrationClass(persistedAsset.propertyData)
  }


  //  @throws(classOf[TierekisteriClientException])
  private def expireMassTransitStop(username: String, persistedStop: PersistedMassTransitStop) = {
    val expireDate= new Date()
    massTransitStopDao.expireMassTransitStop(username, persistedStop.id)
    if (tierekisteriClient.isTREnabled) {
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get)
      val updatedTierekisteriMassTransitStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(persistedStop, address, Option(roadSide), Option(expireDate))
      tierekisteriClient.updateMassTransitStop(updatedTierekisteriMassTransitStop, None, Some(username))
    }
  }
}
