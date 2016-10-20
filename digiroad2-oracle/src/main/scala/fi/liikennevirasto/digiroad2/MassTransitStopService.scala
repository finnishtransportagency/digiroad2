package fi.liikennevirasto.digiroad2

import java.util.Date

import fi.liikennevirasto.digiroad2.asset.{Property, _}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{AssetPropertyConfiguration, LRMPosition, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, RoadAddress, Track}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Interval, LocalDate}
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
                                    validityPeriod: Option[String], floating: Boolean,
                                    created: Modification, modified: Modification,
                                    propertyData: Seq[Property]) extends PersistedPointAsset with TimeStamps

case class MassTransitStopRow(id: Long, externalId: Long, assetTypeId: Long, point: Option[Point], linkId: Long, bearing: Option[Int],
                              validityDirection: Int, validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                              created: Modification, modified: Modification, wgsPoint: Option[Point], lrmPosition: LRMPosition,
                              roadLinkType: AdministrativeClass = Unknown, municipalityCode: Int, persistedFloating: Boolean)

trait MassTransitStopService extends PointAssetOperations {
  type IncomingAsset = NewMassTransitStop
  type PersistedAsset = PersistedMassTransitStop

  val massTransitStopDao: MassTransitStopDao
  val tierekisteriClient: TierekisteriClient
  val tierekisteriEnabled: Boolean
  override val idField = "external_id"

  override def typeId: Int = 10

  val CommuterBusStopPropertyValue: String = "2"
  val LongDistanceBusStopPropertyValue: String = "3"
  val VirtualBusStopPropertyValue: String = "5"
  val MassTransitStopTypePublicId = "pysakin_tyyppi"

  val CentralELYPropertyValue = "2"
  val AdministratorInfoPublicId = "tietojen_yllapitaja"
  val LiViIdentifierPublicId = "yllapitajan_koodi"
  val InventoryDateId = "inventointipaiva"

  val nameFiPublicId = "nimi_suomeksi"
  val nameSePublicId = "nimi_ruotsiksi"

  val MaxMovementDistanceMeters = 50

  val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  val geometryTransform = new GeometryTransform

  def withDynSession[T](f: => T): T
  def withDynTransaction[T](f: => T): T
  def eventbus: DigiroadEventBus


  def getByNationalId[T <: FloatingAsset](nationalId: Long, municipalityValidation: Int => Unit, persistedStopToFloatingStop: PersistedMassTransitStop => (T, Option[FloatingReason])): Option[T] = {
    withDynTransaction {
      val persistedStop = fetchPointAssets(withNationalId(nationalId)).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)
      if(isStoredInTierekisteri(persistedStop)){
        val properties = persistedStop.map(_.propertyData).get
        val liViProp = properties.find(_.publicId == LiViIdentifierPublicId)
        if (tierekisteriEnabled) {
          val liViId = liViProp.map(_.values.head).get.propertyValue
          val tierekisteriStop = tierekisteriClient.fetchMassTransitStop(liViId)
          val enrichedStop = enrichPersistedMassTransitStop(persistedStop, tierekisteriStop)
          return enrichedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
        }
      }
      persistedStop.map(withFloatingUpdate(persistedStopToFloatingStop))
    }
  }

  /**
    * Override property values of all equipment properties
    *
    * @param tierekisteriStop Tierekisteri Asset
    * @param property Asset property
    * @return Property passed as parameter if have no match with equipment property or property overriden with tierekisteri values
    */
  private def setEquipments(tierekisteriStop: TierekisteriMassTransitStop, property: Property): Property = {
    if (tierekisteriStop == null)
      throw new TierekisteriClientException("Null object read")
    if (tierekisteriStop.equipments.isEmpty)
      return property
    val equipmentProperty = tierekisteriStop.equipments.filter(_._1.publicId == property.publicId)
    val mappedProperties = equipmentProperty.map {
      case (equipment, existence) =>
        PropertyValue(existence.propertyValue.toString, Some(massTransitStopDao.getPropertyDescription(property.publicId, existence.propertyValue.toString).head))
    }
    if (mappedProperties.isEmpty)
      property
    else
      property.copy(values = mappedProperties.toSeq)
  }

  /**
    * Override property value when the value is empty
    *
    * @param publidId The public id of the property
    * @param getValue Function to get the property value from Tierekisteri Asset
    * @param tierekisteriStop  Tierekisteri Asset
    * @param property Asset property
    * @return Property passed as parameter if have no match with equipment property or property overriden with tierekisteri values
    */
  private def setPropertyValueIfEmpty(publidId: String, getValue: TierekisteriMassTransitStop => String)(tierekisteriStop: TierekisteriMassTransitStop, property: Property): Property = {

    if(property.publicId == publidId){
      property.copy( values = property.values.map{
        case value if value.propertyValue.isEmpty => PropertyValue(getValue(tierekisteriStop), Some(massTransitStopDao.getPropertyDescription(publidId, getValue(tierekisteriStop)).head))
        case value => value
      })
    }
    property
  }

  /**
    * Override the properties values passed as parameter using override operations
    *
    * @param tierekisteriStop Tierekisteri Asset
    * @param persistedMassTransitStop Asset properties
    * @return Sequence of overridden properties
    */
  private def enrichPersistedMassTransitStop(persistedMassTransitStop: Option[PersistedMassTransitStop], tierekisteriStop: TierekisteriMassTransitStop): Option[PersistedMassTransitStop] = {
    val overridePropertyValueOperations: Seq[(TierekisteriMassTransitStop, Property) => Property] = Seq(
      setEquipments,
      setPropertyValueIfEmpty(nameFiPublicId, { ta => ta.nameFi.getOrElse("") }),
      setPropertyValueIfEmpty(nameSePublicId, { ta => ta.nameSe.getOrElse("") })
      //In the future if we need to override some property just add here the operation
    )

    persistedMassTransitStop match {
      case Some(masstransitStop) =>
        Some(masstransitStop.copy(propertyData = masstransitStop.propertyData.map { property =>
          var overriddenProperty = property
          overridePropertyValueOperations.foreach { operation =>
            overriddenProperty = operation(tierekisteriStop, overriddenProperty)
          }
          overriddenProperty
        }))
      case massTransitStop => massTransitStop
    }
  }

  /**
    * Verify if the stop is relevant to Tierekisteri: Must be non-virtual and must be administered by ELY
    *
    * @param persistedStopOption The persisted stops
    * @return returns true if the stop is not vitual and is a ELY bus stop
    */
  def isStoredInTierekisteri(persistedStopOption: Option[PersistedMassTransitStop]): Boolean ={
    persistedStopOption match {
      case Some(persistedStop) =>
        val administrationProperty = persistedStop.propertyData.find(_.publicId == AdministratorInfoPublicId)
        val elyAdministrated = administrationProperty.exists(_.values.headOption.exists(_.propertyValue == CentralELYPropertyValue))
        val stopType = persistedStop.propertyData.find(pro => pro.publicId == MassTransitStopTypePublicId)
        val isVirtualStop = stopType.exists(_.values.exists(_.propertyValue == VirtualBusStopPropertyValue))
        !isVirtualStop && elyAdministrated
      case _ =>
        false
    }
  }

  def wasStoredInTierekisteri(properties: Seq[SimpleProperty]): Boolean ={
    val administrationProperty = properties.find(_.publicId == AdministratorInfoPublicId)
    val elyAdministrated = administrationProperty.exists(_.values.headOption.exists(_.propertyValue == CentralELYPropertyValue))
    val stopType = properties.find(pro => pro.publicId == MassTransitStopTypePublicId)
    val isVirtualStop = stopType.exists(_.values.exists(_.propertyValue == VirtualBusStopPropertyValue))
    !isVirtualStop && elyAdministrated
  }

  def getMassTransitStopByNationalId(nationalId: Long, municipalityValidation: Int => Unit): Option[MassTransitStopWithProperties] = {
    getByNationalId(nationalId, municipalityValidation, persistedStopToMassTransitStopWithProperties(fetchRoadLink))
  }

  private def persistedStopToMassTransitStopWithProperties(roadLinkByLinkId: Long => Option[VVHRoadlink])
                                                          (persistedStop: PersistedMassTransitStop): (MassTransitStopWithProperties, Option[FloatingReason]) = {
    val (floating, floatingReason) = isFloating(persistedStop, roadLinkByLinkId(persistedStop.linkId))
    (MassTransitStopWithProperties(id = persistedStop.id, nationalId = persistedStop.nationalId, stopTypes = persistedStop.stopTypes,
      lon = persistedStop.lon, lat = persistedStop.lat, validityDirection = persistedStop.validityDirection,
      bearing = persistedStop.bearing, validityPeriod = persistedStop.validityPeriod, floating = floating,
      propertyData = persistedStop.propertyData), floatingReason)
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks:Seq[VVHRoadlink]): Seq[PersistedMassTransitStop] = {
    val query = """
        select a.id, a.external_id, a.asset_type_id, a.bearing, lrm.side_code,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        p.id, p.public_id, p.property_type, p.required, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          when np.value is not null then to_char(np.value)
          else null
        end as display_value,
        lrm.id, lrm.start_measure, lrm.end_measure, lrm.link_id,
        a.created_date, a.created_by, a.modified_date, a.modified_by,
        SDO_CS.TRANSFORM(a.geometry, 4326) AS position_wgs84
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

  override def isFloating(persistedAsset: PersistedPointAsset, roadLinkOption: Option[VVHRoadlink]): (Boolean, Option[FloatingReason]) = {
    roadLinkOption match {
      case None => return super.isFloating(persistedAsset, roadLinkOption)
      case Some(roadLink) =>
        val administrationClass = massTransitStopDao.getAssetAdministrationClass(persistedAsset.id)
        if(administrationClass.isDefined && administrationClass.get == State &&  roadLink.administrativeClass.value != administrationClass.get.value){
          return (true, Some(FloatingReason.RoadOwnerChanged))
        }
    }

    super.isFloating(persistedAsset, roadLinkOption)
  }

  protected override def floatingReason(persistedAsset: PersistedAsset, roadLinkOption: Option[VVHRoadlink]) : String = {

    roadLinkOption match {
      case None => return super.floatingReason(persistedAsset, roadLinkOption) //This is just because the warning
      case Some(roadLink) =>
        val administrationClass = massTransitStopDao.getAssetAdministrationClass(persistedAsset.id)
        if(administrationClass.isDefined && administrationClass.get == State &&  roadLink.administrativeClass.value != administrationClass.get.value){
          return "Road link administration class have changed from %d to %d".format(roadLink.administrativeClass.value, administrationClass.get.value)
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

      id -> PersistedMassTransitStop(id = row.id, nationalId = row.externalId, linkId = row.linkId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y, mValue = mValue,
        validityDirection = Some(row.validityDirection), bearing = row.bearing,
        validityPeriod = validityPeriod, floating = row.persistedFloating, created = row.created, modified = row.modified,
        propertyData = properties)
    }.values.toSeq
  }

  private def withNationalId(nationalId: Long)(query: String): String = {
    query + s" where a.external_id = $nationalId"
  }

  private def withId(id: Long)(query: String): String = {
    query + s" where a.id = $id"
  }

  private def extractStopTypes(rows: Seq[MassTransitStopRow]): Seq[Int] = {
    rows
    .filter { row => row.property.publicId.equals(MassTransitStopTypePublicId) }
    .filterNot { row => row.property.propertyValue.isEmpty }
    .map { row => row.property.propertyValue.toInt }
  }

  private def eventBusMassTransitStop(stop: PersistedMassTransitStop, municipalityName: String) = {
    EventBusMassTransitStop(municipalityNumber = stop.municipalityCode, municipalityName = municipalityName,
      nationalId = stop.nationalId, lon = stop.lon, lat = stop.lat, bearing = stop.bearing,
      validityDirection = stop.validityDirection, created = stop.created, modified = stop.modified,
      propertyData = stop.propertyData)
  }

  override def update(id: Long, updatedAsset: NewMassTransitStop, geometry: Seq[Point], municipality: Int, username: String): Long = {
    throw new NotImplementedError("Use updateExisting instead. Mass transit is legacy.")
  }

  /**
    * Checks that virtualTransitStop doesn't have any other types selected
    *
    * @param stopProperties
    * @return
    */
  private def mixedStoptypes(stopProperties: Set[SimpleProperty]): Boolean =
  {
    val propertiesSelected = stopProperties.filter(p => MassTransitStopTypePublicId.equalsIgnoreCase(p.publicId))
    .flatMap(_.values).map(_.propertyValue)
    propertiesSelected.contains(VirtualBusStopPropertyValue) && propertiesSelected.exists(!_.equals(VirtualBusStopPropertyValue))
  }

  private def updateExisting(queryFilter: String => String, optionalPosition: Option[Position], properties: Set[SimpleProperty], username: String, municipalityValidation: Int => Unit): MassTransitStopWithProperties = {
    withDynTransaction {

      if (mixedStoptypes(properties))
        throw new IllegalArgumentException

      val persistedStop = fetchPointAssets(queryFilter).headOption
      persistedStop.map(_.municipalityCode).foreach(municipalityValidation)

      val linkId = optionalPosition match {
        case Some(position) => position.linkId
        case _ => persistedStop.get.linkId
      }

      val roadLink = vvhClient.fetchVVHRoadlink(linkId)
      val (municipalityCode, geometry) = roadLink
        .map{ x => (x.municipalityCode, x.geometry) }
        .getOrElse(throw new NoSuchElementException)

      val asset = persistedStop.getOrElse(throw new NoSuchElementException)

      val mergedProperties = (asset.propertyData.
        filterNot(property => properties.exists(_.publicId == property.publicId)).
        map(property => SimpleProperty(property.publicId, property.values)) ++ properties).
        filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))

      //Expire existing asset and create a new one
      if(optionalPosition.isDefined && wasStoredInTierekisteri(mergedProperties)){
        val position = optionalPosition.get
        val assetPoint = Point(asset.lon, asset.lat)
        val newPoint = Point(position.lon, position.lat)
        val assetDistance = assetPoint.distance2DTo(newPoint)

        if(assetDistance > MaxMovementDistanceMeters){
          //Expire the old asset
          expireMassTransitStop(username, asset)

          //Create a new asset
          create(NewMassTransitStop(position.lon, position.lat, linkId, position.bearing.getOrElse(asset.bearing.get), mergedProperties), username, newPoint, geometry, municipalityCode, Some(roadLink.get.administrativeClass))
        }
        else
        {
          update(asset, optionalPosition, username, properties.toSeq, roadLink.get)
        }
      }
      else{
        update(asset, optionalPosition, username, properties.toSeq, roadLink.get)
      }
    }
  }

  private def update(persistedStop: PersistedMassTransitStop, optionalPosition: Option[Position], username: String, properties: Seq[SimpleProperty], roadLink: VVHRoadlink): MassTransitStopWithProperties = {

    val id = persistedStop.id
    massTransitStopDao.updateAssetLastModified(id, username)
    if (properties.nonEmpty) {
      massTransitStopDao.updateAssetProperties(id, properties.toSeq)
      updateAdministrativeClassValue(id, roadLink.administrativeClass)
      updateLiViIdentifierProperty(id, persistedStop.nationalId, properties.toSeq)
    }
    if (optionalPosition.isDefined) {
      val position = optionalPosition.get
      val point = Point(position.lon, position.lat)
      val mValue = calculateLinearReferenceFromPoint(point, roadLink.geometry)
      updateLrmPosition(id, mValue, roadLink.linkId)
      updateBearing(id, position)
      updateMunicipality(id, roadLink.municipalityCode)
      updateAssetGeometry(id, point)
    }
    getPersistedStopWithPropertiesAndPublishEvent(id, { _ => Some(roadLink) }, tierekisteriClient.updateMassTransitStop)
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

  private def fetchRoadLink(linkId: Long): Option[VVHRoadlink] = {
    vvhClient.fetchVVHRoadlink(linkId)
  }

  override def create(asset: NewMassTransitStop, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass]): Long = {
    withDynTransaction {
      val point = Point(asset.lon, asset.lat)
      create(asset, username, point, geometry, municipality, administrativeClass).id
    }
  }

  def updatedProperties(properties: Seq[SimpleProperty]): Seq[SimpleProperty] = {
    val inventoryDate = properties.find(_.publicId == InventoryDateId)
    val notInventoryDate = properties.filterNot(_.publicId == InventoryDateId)
    if (inventoryDate.nonEmpty && inventoryDate.get.values.exists(_.propertyValue != "")) {
      properties
    } else {
      notInventoryDate ++ Seq(SimpleProperty(InventoryDateId, Seq(PropertyValue(toIso8601.print(DateTime.now())))))
    }
  }

  private def create(asset: NewMassTransitStop, username: String, point: Point, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass]): MassTransitStopWithProperties = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = calculateLinearReferenceFromPoint(point, geometry)
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, mValue))
    insertLrmPosition(lrmPositionId, mValue, asset.linkId)
    insertAsset(assetId, nationalId, asset.lon, asset.lat, asset.bearing, username, municipality, floating)
    insertAssetLink(assetId, lrmPositionId)

    val properties = updatedProperties(asset.properties)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    if (!mixedStoptypes(properties.toSet))
    {
      massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
      updateAdministrativeClassValue(assetId, administrativeClass.getOrElse(throw new IllegalArgumentException("AdministrativeClass argument is mandatory")))
      updateLiViIdentifierProperty(assetId, nationalId, properties)
      getPersistedStopWithPropertiesAndPublishEvent(assetId, fetchRoadLink, tierekisteriClient.createMassTransitStop)
    }
    else
      throw new IllegalArgumentException
  }

  private def updateLiViIdentifierProperty(assetId: Long, nationalId: Long, properties: Seq[SimpleProperty]) : Unit = {
    properties.foreach{ property =>
      if(property.publicId == AdministratorInfoPublicId){
        val administrationPropertyValue = property.values.headOption
        val isVirtualStop = properties.exists(pro => pro.publicId == MassTransitStopTypePublicId && pro.values.exists(_.propertyValue == VirtualBusStopPropertyValue))

        if(!isVirtualStop && administrationPropertyValue.isDefined && administrationPropertyValue.get.propertyValue == CentralELYPropertyValue) {
          massTransitStopDao.updateTextPropertyValue(assetId, LiViIdentifierPublicId, "OTHJ%d".format(nationalId))
        }else{
          massTransitStopDao.updateTextPropertyValue(assetId, LiViIdentifierPublicId, "")
        }
      }
    }
  }

  private def getPersistedStopWithPropertiesAndPublishEvent(assetId: Long, roadLinkByLinkId: Long => Option[VVHRoadlink], createOrUpdateMassTransitStop: TierekisteriMassTransitStop => Unit) : MassTransitStopWithProperties = {
    val persistedStop = fetchPointAssets(withId(assetId)).headOption
    val relevantToTR = isStoredInTierekisteri(persistedStop)

    if (relevantToTR) {
      val roadLink = roadLinkByLinkId.apply(persistedStop.get.linkId)
      val road = roadLink.map(rl => rl.attributes.get("ROADNUMBER")) match {
        case Some(str) => Try(str.toString.toInt).toOption
        case _ => None
      }
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.get.lon, persistedStop.get.lat), persistedStop.get.bearing.get, road)

      val newTierekisteriMassTransitStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(persistedStop.get, address, Option(roadSide))

      createOrUpdateMassTransitStop(newTierekisteriMassTransitStop)
    }

    persistedStop.foreach { stop =>
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
      val relevantToTR = isStoredInTierekisteri(Some(persistedStop.get))

      if ((relevantToTR) && (tierekisteriClient.isTREnabled)) {
        val liviIdOption = persistedStop.get.propertyData.find(propertyData =>
          propertyData.publicId.equals(LiViIdentifierPublicId)).flatMap(propertyData => propertyData.values.headOption).map(_.propertyValue).headOption

        liviIdOption match {
          case Some(liviId) => tierekisteriClient.deleteMassTransitStop(liviId)
          case _ => throw new RuntimeException(s"bus stop relevant to Tierekisteri doesn't have 'yllapitajan koodi' property")
        }
        massTransitStopDao.deleteAllMassTransitStopData(assetId)
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
      MassTransitStopRow(id, externalId, assetTypeId, point, linkId, bearing, validityDirection,
        validFrom, validTo, property, created, modified, wgsPoint,
        lrmPosition = LRMPosition(lrmId, startMeasure, endMeasure, point), municipalityCode = municipalityCode, persistedFloating = persistedFloating)
    }
  }

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  private def constructValidityPeriod(validFrom: Option[LocalDate], validTo: Option[LocalDate]): String = {
    (validFrom, validTo) match {
      case (Some(from), None) => if (from.isAfter(LocalDate.now())) { MassTransitStopValidityPeriod.Future } else { MassTransitStopValidityPeriod.Current }
      case (None, Some(to)) => if (LocalDate.now().isAfter(to)) { MassTransitStopValidityPeriod.Past } else { MassTransitStopValidityPeriod.Current }
      case (Some(from), Some(to)) =>
        val interval = new Interval(from.toDateMidnight, to.toDateMidnight)
        if (interval.containsNow()) { MassTransitStopValidityPeriod.Current }
        else if (interval.isBeforeNow) { MassTransitStopValidityPeriod.Past }
        else { MassTransitStopValidityPeriod.Future }
      case _ => MassTransitStopValidityPeriod.Current
    }
  }

  private def updateLrmPosition(id: Long, mValue: Double, linkId: Long) {
    sqlu"""
           update lrm_position
           set start_measure = $mValue, end_measure = $mValue, link_id = $linkId
           where id = (
            select lrm.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = $id)
      """.execute
  }

  private def insertLrmPosition(id: Long, mValue: Double, linkId: Long) {
    sqlu"""
           insert into lrm_position (id, start_measure, end_measure, link_id)
           values ($id, $mValue, $mValue, $linkId)
      """.execute
  }

  private def insertAsset(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Int, creator: String, municipalityCode: Int, floating: Boolean): Unit = {
    sqlu"""
           insert into asset (id, external_id, asset_type_id, bearing, created_by, municipality_code, geometry, floating)
           values ($id, $nationalId, $typeId, $bearing, $creator, $municipalityCode,
           MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1), MDSYS.SDO_ORDINATE_ARRAY($lon, $lat, 0, 0)),
           $floating)
      """.execute
  }

  private def insertAssetLink(assetId: Long, lrmPositionId: Long): Unit = {

    sqlu"""
           insert into asset_link(asset_id, position_id)
           values ($assetId, $lrmPositionId)
      """.execute
  }

  private def updateBearing(id: Long, position: Position) {
    position.bearing.foreach { bearing =>
      sqlu"""
           update asset
           set bearing = $bearing
           where id = $id
        """.execute
    }
  }

  private def updateMunicipality(id: Long, municipalityCode: Int) {
    sqlu"""
           update asset
           set municipality_code = $municipalityCode
           where id = $id
      """.execute
  }

//  @throws(classOf[TierekisteriClientException])
  private def expireMassTransitStop(username: String, persistedStop: PersistedMassTransitStop) = {
    val expireDate= new Date()
    massTransitStopDao.expireMassTransitStop(username, persistedStop.id)
    if (tierekisteriClient.isTREnabled) {
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get)
      val updatedTierekisteriMassTransitStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(persistedStop, address, Option(roadSide), Option(expireDate))
      tierekisteriClient.updateMassTransitStop(updatedTierekisteriMassTransitStop)
    }
  }
}
