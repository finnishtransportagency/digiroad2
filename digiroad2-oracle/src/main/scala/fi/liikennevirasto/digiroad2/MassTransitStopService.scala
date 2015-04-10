package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, LRMPosition, OracleSpatialAssetDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.{DateTime, Interval, LocalDate}

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class MassTransitStop(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Option[Int],
                           validityDirection: Int, municipalityNumber: Int,
                           validityPeriod: String, floating: Boolean, stopTypes: Seq[Int])

// TODO: rename
case class NewMassTransitStop(id: Long, nationalId: Long, stopTypes: Seq[Int], lon: Double, lat: Double,
                              validityDirection: Option[Int], bearing: Option[Int],
                              validityPeriod: Option[String], floating: Boolean,
                              propertyData: Seq[Property])

trait MassTransitStopService {
  def withDynSession[T](f: => T): T
  def roadLinkService: RoadLinkService
  def withDynTransaction[T](f: => T): T

  case class PersistedMassTransitStop(id: Long, nationalId: Long, mmlId: Long, stopTypes: Seq[Int],
                                      municipalityCode: Int, lon: Double, lat: Double, mValue: Double,
                                      validityDirection: Option[Int], bearing: Option[Int],
                                      validityPeriod: Option[String], floating: Boolean,
                                      propertyData: Seq[Property])

  case class MassTransitStopRow(id: Long, externalId: Long, assetTypeId: Long, point: Option[Point], productionRoadLinkId: Option[Long], roadLinkId: Long, mmlId: Long, bearing: Option[Int],
                                validityDirection: Int, validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                                created: Modification, modified: Modification, wgsPoint: Option[Point], lrmPosition: LRMPosition,
                                roadLinkType: AdministrativeClass = Unknown, municipalityCode: Int, persistedFloating: Boolean) extends IAssetRow

  def getByNationalId(nationalId: Long, municipalityValidation: Int => Unit): Option[NewMassTransitStop] = {
    withDynTransaction {
      val persistedMassTransitStop = getPersistedMassTransitStop(withNationalId(nationalId))
      persistedMassTransitStop.map(_.municipalityCode).foreach(municipalityValidation)
      persistedMassTransitStop.map { persistedStop =>
        val roadLink = roadLinkService.fetchVVHRoadlink(persistedStop.mmlId)
        val point = Point(persistedStop.lon, persistedStop.lat)
        val floating = roadLink match {
          case None => true
          case Some((municipalityCode, geometry)) => municipalityCode != persistedStop.municipalityCode ||
            !coordinatesWithinThreshold(Some(point), calculatePointFromLinearReference(geometry, persistedStop.mValue))
        }
        if (persistedStop.floating != floating) updateFloating(persistedStop.id, floating)
        NewMassTransitStop(id = persistedStop.id, nationalId = persistedStop.nationalId, stopTypes = persistedStop.stopTypes,
          lon = persistedStop.lon, lat = persistedStop.lat, validityDirection = persistedStop.validityDirection,
          bearing = persistedStop.bearing, validityPeriod = persistedStop.validityPeriod, floating = floating,
          propertyData = persistedStop.propertyData)
      }
    }
  }

  private def getPersistedMassTransitStop(filteringQuery: String => Option[PersistedMassTransitStop]): Option[PersistedMassTransitStop] = {
    val query = """
        select a.id, a.external_id, a.asset_type_id, a.bearing, lrm.side_code,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        p.id, p.public_id, p.property_type, p.ui_position_index, p.required, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          else null
        end as display_value,
        lrm.id, lrm.start_measure, lrm.end_measure, lrm.prod_road_link_id, lrm.road_link_id, lrm.mml_id,
        a.created_date, a.created_by, a.modified_date, a.modified_by,
        SDO_CS.TRANSFORM(a.geometry, 4326) AS position_wgs84
        from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
        join property p on a.asset_type_id = p.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
      """
    filteringQuery(query)
  }

  private def withNationalId(nationalId: Long)(query: String): Option[PersistedMassTransitStop] = {
    val filteredQuery = query + s" where external_id = $nationalId"
    val rows = StaticQuery.queryNA[MassTransitStopRow](filteredQuery).iterator().toSeq
    val commonProperties: Seq[Property] = rows.headOption.map(AssetPropertyConfiguration.assetRowToCommonProperties).getOrElse(Nil)
    val properties: Seq[Property] = commonProperties ++ OracleSpatialAssetDao.assetRowToProperty(rows)
    rows.headOption.map { row =>
      val point = row.point.get
      val validityPeriod = Some(constructValidityPeriod(row.validFrom, row.validTo))
      val stopTypes = extractStopTypes(rows)
      val mValue = row.lrmPosition.startMeasure
      PersistedMassTransitStop(id = row.id, nationalId = row.externalId, mmlId = row.mmlId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y, mValue = mValue,
        validityDirection = Some(row.validityDirection), bearing = row.bearing,
        validityPeriod = validityPeriod, floating = row.persistedFloating, propertyData = properties)
    }
  }

  private def extractStopTypes(rows: Seq[MassTransitStopRow]): Seq[Int] = {
    rows
      .filter { row => row.property.publicId.equals("pysakin_tyyppi") }
      .filterNot { row => row.property.propertyValue.isEmpty }
      .map { row => row.property.propertyValue.toInt }
  }

  private def getMassTransitStopMmlId(nationalId: Long): Option[Long] = {
    Some(0l)
  }

  private def getByNationalId(nationalId: Long, roadLinkGeometry: Seq[Point]): AssetWithProperties = ???

  def updatePosition(id: Long, position: Position) = {
    val point = Point(position.lon, position.lat)
    val mmlId = position.roadLinkId
    val (municipalityCode, geometry) = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new NoSuchElementException)
    val mValue = calculateLinearReferenceFromPoint(point, geometry)

    withDynTransaction {
      updateLrmPosition(id, mValue, mmlId)
      updateBearing(id, position)
      updateMunicipality(id, municipalityCode)
      OracleSpatialAssetDao.updateAssetGeometry(id, point)
      val massTransitStop = getUpdatedMassTransitStop(id, geometry)
      updateFloating(id, massTransitStop.floating)
      massTransitStop
    }
  }

  def createNew(lon: Double, lat: Double, mmlId: Long, bearing: Int, username: String, properties: Seq[SimpleProperty]): NewMassTransitStop = {
    val (municipalityCode, geometry) = roadLinkService.fetchVVHRoadlink(mmlId).getOrElse(throw new NoSuchElementException)
    val mValue = calculateLinearReferenceFromPoint(Point(lon, lat), geometry)

    val massTransitStop = withDynTransaction {
      val assetId = OracleSpatialAssetDao.nextPrimaryKeySeqValue
      val lrmPositionId = OracleSpatialAssetDao.nextLrmPositionPrimaryKeySeqValue
      val nationalId = OracleSpatialAssetDao.getNationalBusStopId
      insertLrmPosition(lrmPositionId, mValue, mmlId)
      insertAsset(assetId, nationalId, lon, lat, bearing, username, municipalityCode)
      insertAssetLink(assetId, lrmPositionId)
      val defaultValues = OracleSpatialAssetDao.propertyDefaultValues(10).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
      OracleSpatialAssetDao.updateAssetProperties(assetId, properties ++ defaultValues)
      getUpdatedMassTransitStop(assetId, geometry)
    }
    massTransitStop
  }

  def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[MassTransitStop] = {
    case class MassTransitStopBeforeUpdate(stop: MassTransitStop, persistedFloating: Boolean)
    type MassTransitStopAndType = (Long, Long, Option[Int], Int, Int, Double, Long, Point, Option[LocalDate], Option[LocalDate], Boolean, Int)
    type MassTransitStopWithTypes = (Long, Long, Option[Int], Int, Int, Double, Long, Point, Option[LocalDate], Option[LocalDate], Boolean, Seq[Int])

    val roadLinks = roadLinkService.fetchVVHRoadlinks(bounds)
    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")

      val massTransitStopsAndStopTypes = sql"""
          select a.id, a.external_id, a.bearing, lrm.side_code,
          a.municipality_code, lrm.start_measure, lrm.mml_id,
          a.geometry, a.valid_from, a.valid_to, a.floating, e.value
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          left join multiple_choice_value v on a.id = v.asset_id
          left join enumerated_value e on v.enumerated_value_id = e.id
          where a.asset_type_id = 10
          and #$boundingBoxFilter
          and v.property_id = (select id from property where public_id = 'pysakin_tyyppi')
       """.as[MassTransitStopAndType].list()

      val massTransitStops: Seq[MassTransitStopWithTypes] = massTransitStopsAndStopTypes.groupBy(_._1).map { case(id, rows) =>
        val stopTypes = rows.map(_._12)
        val (_, nationalId, bearing, sideCode, municipalityCode, startMeasure, mmlId, geometry, validFrom, validTo, floating, _) = rows.head
        id -> (id, nationalId, bearing, sideCode, municipalityCode, startMeasure, mmlId, geometry, validFrom, validTo, floating, stopTypes)
      }.values.toSeq

      val stopsBeforeUpdate = massTransitStops.filter { massTransitStop =>
        val (_, _, _, _, municipalityCode, _, _, _, _, _, _, _) = massTransitStop
        user.isAuthorizedToRead(municipalityCode)
      }.map { massTransitStop =>
        val (id, nationalId, bearing, sideCode, municipalityCode, measure, mmlId, point, validFrom, validTo, persistedFloating, stopTypes) = massTransitStop
        val roadLinkForStop: Option[(Long, Int, Seq[Point])] = roadLinks.find(_._1 == mmlId)
        val floating = roadLinkForStop match {
          case None => true
          case Some(roadLink) => roadLink._2 != municipalityCode || !coordinatesWithinThreshold(Some(point), calculatePointFromLinearReference(roadLink._3, measure))
        }
        MassTransitStopBeforeUpdate(MassTransitStop(id, nationalId, point.x, point.y, bearing, sideCode, municipalityCode, constructValidityPeriod(validFrom, validTo), floating, stopTypes), persistedFloating)
      }

      stopsBeforeUpdate.foreach { stop =>
        if (stop.stop.floating != stop.persistedFloating) {
          sqlu"""update asset set floating = ${stop.stop.floating} where id = ${stop.stop.id}""".execute()
        }
      }

      stopsBeforeUpdate.map(_.stop)
    }
  }

  def mandatoryProperties(): Map[String, String] = {
    val requiredProperties = withDynSession {
      sql"""select public_id, property_type from property where asset_type_id = 10 and required = 1""".as[(String, String)].iterator().toMap
    }
    val validityDirection = AssetPropertyConfiguration.commonAssetProperties(AssetPropertyConfiguration.ValidityDirectionId)
    requiredProperties + (validityDirection.publicId -> validityDirection.propertyType)
  }

  def calculatePointFromLinearReference(geometry: Seq[Point], measure: Double): Option[Point] = {
    case class AlgorithmState(previousPoint: Point, remainingMeasure: Double, result: Option[Point])
    if (geometry.size < 2 || measure < 0) { None }
    else {
      val state = geometry.tail.foldLeft(AlgorithmState(geometry.head, measure, None)) { (acc, point) =>
        if (acc.result.isDefined) {
          acc
        } else {
          val distance = point.distanceTo(acc.previousPoint)
          if (acc.remainingMeasure <= distance) {
            val directionVector = (point - acc.previousPoint).normalize()
            val result = Some(acc.previousPoint + directionVector.scale(acc.remainingMeasure))
            AlgorithmState(point, acc.remainingMeasure - distance, result)
          } else {
            AlgorithmState(point, acc.remainingMeasure - distance, None)
          }
        }
      }
      state.result
    }
  }

  def calculateLinearReferenceFromPoint(point: Point, points: Seq[Point]): Double = {
    case class Projection(distance: Double, segmentIndex: Int, segmentLength: Double, mValue: Double)
    val lineSegments: Seq[((Point, Point), Int)] = points.zip(points.tail).zipWithIndex
    val projections: Seq[Projection] = lineSegments.map { case((p1: Point, p2: Point), segmentIndex: Int) =>
      val segmentLength = (p2 - p1).length()
      val directionVector = (p2 - p1).normalize()
      val negativeMValue = (p1 - point).dot(directionVector)
      val clampedNegativeMValue =
        if (negativeMValue > 0) 0
        else if (negativeMValue < (-1 * segmentLength)) -1 * segmentLength
        else negativeMValue
      val projectionVectorOnLineSegment: Vector3d = directionVector.scale(clampedNegativeMValue)
      val pointToLineSegment: Vector3d = (p1 - point) - projectionVectorOnLineSegment
      Projection(
        distance = pointToLineSegment.length(),
        segmentIndex = segmentIndex,
        segmentLength = segmentLength,
        mValue = -1 * clampedNegativeMValue)
    }
    val targetIndex = projections.sortBy(_.distance).head.segmentIndex
    val distanceBeforeTarget = projections.take(targetIndex).map(_.segmentLength).sum
    distanceBeforeTarget + projections(targetIndex).mValue
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
      val propertyUiIndex = r.nextInt
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(propertyId, propertyPublicId, propertyType, propertyUiIndex, propertyRequired, propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString, propertyDisplayValue.getOrElse(null))
      val lrmId = r.nextLong
      val startMeasure = r.nextInt
      val endMeasure = r.nextInt
      val productionRoadLinkId = r.nextLongOption()
      val roadLinkId = r.nextLong
      val mmlId = r.nextLong
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val wgsPoint = r.nextBytesOption.map(bytesToPoint)
      MassTransitStopRow(id, externalId, assetTypeId, point, productionRoadLinkId, roadLinkId, mmlId, bearing, validityDirection,
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
      case (Some(from), None) => if (from.isAfter(LocalDate.now())) { ValidityPeriod.Future } else { ValidityPeriod.Current }
      case (None, Some(to)) => if (LocalDate.now().isAfter(to)) { ValidityPeriod.Past } else { ValidityPeriod.Current }
      case (Some(from), Some(to)) =>
        val interval = new Interval(from.toDateMidnight, to.toDateMidnight)
        if (interval.containsNow()) { ValidityPeriod.Current }
        else if (interval.isBeforeNow) { ValidityPeriod.Past }
        else { ValidityPeriod.Future }
      case _ => ValidityPeriod.Current
    }
  }

  private val FLOAT_THRESHOLD_IN_METERS = 3

  private def coordinatesWithinThreshold(pt1: Option[Point], pt2: Option[Point]): Boolean = {
    (pt1, pt2) match {
      case (Some(point1), Some(point2)) => point1.distanceTo(point2) <= FLOAT_THRESHOLD_IN_METERS
      case _ => false
    }
  }

  private def updateLrmPosition(id: Long, mValue: Double, mmlId: Long) {
    sqlu"""
           update lrm_position
           set start_measure = $mValue, end_measure = $mValue, mml_id = $mmlId
           where id = (
            select lrm.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = $id)
      """.execute
  }

  private def insertLrmPosition(id: Long, mValue: Double, mmlId: Long) {
    sqlu"""
           insert into lrm_position (id, start_measure, end_measure, mml_id)
           values ($id, $mValue, $mValue, $mmlId)
      """.execute
  }

  private def insertAsset(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Int, creator: String, municipalityCode: Int): Unit = {
    sqlu"""
           insert into asset (id, external_id, asset_type_id, bearing, created_by, municipality_code, geometry)
           values ($id, $nationalId, 10, $bearing, $creator, $municipalityCode,
           MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1), MDSYS.SDO_ORDINATE_ARRAY($lon, $lat, 0, 0)))
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

  private def getUpdatedMassTransitStop(id: Long, roadlinkGeometry: Seq[Point]) = {
    def extractStopTypes(rows: Seq[MassTransitStopRow]): Seq[Int] = {
      rows
        .filter { row => row.property.publicId.equals("pysakin_tyyppi") }
        .filterNot { row => row.property.propertyValue.isEmpty }
        .map { row => row.property.propertyValue.toInt }
    }

    val assetWithPositionById = sql"""
        select a.id, a.external_id, a.asset_type_id, a.bearing, lrm.side_code,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        p.id, p.public_id, p.property_type, p.ui_position_index, p.required, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          else null
        end as display_value,
        lrm.id, lrm.start_measure, lrm.end_measure, lrm.prod_road_link_id, lrm.road_link_id, lrm.mml_id,
        a.created_date, a.created_by, a.modified_date, a.modified_by,
        SDO_CS.TRANSFORM(a.geometry, 4326) AS position_wgs84
        from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
        join property p on a.asset_type_id = p.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
        where a.id = $id
      """
    assetWithPositionById.as[(MassTransitStopRow)].list().groupBy(_.id).map { case (_, rows) =>
      val row = rows.head
      val point = row.point.get
      val floating = !coordinatesWithinThreshold(Some(point), calculatePointFromLinearReference(roadlinkGeometry, row.lrmPosition.startMeasure))

      NewMassTransitStop(id, row.externalId, extractStopTypes(rows),
        point.x, point.y, Some(row.validityDirection), row.bearing,
        Some(constructValidityPeriod(row.validFrom, row.validTo)), floating,(AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ OracleSpatialAssetDao.assetRowToProperty(rows)).sortBy(_.propertyUiIndex))
    }.head
  }
  private def updateFloating(id: Long, floating: Boolean) = sqlu"""update asset set floating = $floating where id = $id""".execute()

}

object MassTransitStopService extends MassTransitStopService {
  def withDynSession[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynSession(f)
  def withDynTransaction[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynTransaction(f)
  val roadLinkService: RoadLinkService = RoadLinkService
}
