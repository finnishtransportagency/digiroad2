package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, Modification, Property}
import fi.liikennevirasto.digiroad2.dao.Queries.PropertyRow
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import org.joda.time.LocalDate
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{MassTransitStopValidityPeriod, _}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.model.LRMPosition
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{LightGeometryMassTransitStop, MassTransitStopOperations, MassTransitStopRow, PersistedMassTransitStop}
import org.joda.time.{DateTime, Interval, LocalDate}
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory
import scala.language.reflectiveCalls


case class ServicePoint(id: Long, nationalId: Long, stopTypes: Seq[Int],
                        municipalityCode: Int, lon: Double, lat: Double, floating: Boolean,
                        created: Modification, modified: Modification, propertyData: Seq[Property])

case class ServicePointRow(id: Long, externalId: Long, assetTypeId: Long, point: Option[Point],
                           validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                           created: Modification, modified: Modification, municipalityCode: Int, persistedFloating: Boolean)

class ServicePointBusStopDao {
  val logger = LoggerFactory.getLogger(getClass)
  def typeId: Int = 10
  val idField = "external_id"

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  private implicit val getServicePointRow = new GetResult[ServicePointRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong
      val externalId = r.nextLong
      val assetTypeId = r.nextLong
      val validFrom = r.nextDateOption.map(new LocalDate(_))
      val validTo = r.nextDateOption.map(new LocalDate(_))
      val point = r.nextBytesOption.map(bytesToPoint)
      val municipalityCode = r.nextInt()
      val persistedFloating = r.nextBoolean()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyMaxCharacters = r.nextIntOption()
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull,
        propertyMaxCharacters = propertyMaxCharacters)
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      ServicePointRow(id, externalId, assetTypeId, point,
        validFrom, validTo, property, created, modified,
        municipalityCode = municipalityCode, persistedFloating = persistedFloating)
    }
  }

  private def extractStopTypes(rows: Seq[ServicePointRow]): Seq[Int] = {
    rows
      .filter { row => row.property.publicId.equals(MassTransitStopOperations.MassTransitStopTypePublicId) && row.property.propertyValue == "7" }
      .filterNot { row => row.property.propertyValue.isEmpty }
      .map { row => row.property.propertyValue.toInt }
  }

  def assetRowToProperty(assetRows: Iterable[ServicePointRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.map(assetRow =>
          PropertyValue(
            assetRow.property.propertyValue,
            propertyDisplayValueFromAssetRow(assetRow))
        ).filter(_.propertyDisplayValue.isDefined).toSeq,
        numCharacterMax = row.property.propertyMaxCharacters)
    }.toSeq
  }

  private def propertyDisplayValueFromAssetRow(assetRow: ServicePointRow): Option[String] = {
    Option(assetRow.property.propertyDisplayValue)
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

  def fetchAsset(queryFilter: String => String): Seq[ServicePoint] = {
    val query = """
        select a.id, a.external_id, a.asset_type_id,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        p.id, p.public_id, p.property_type, p.required, p.max_value_length, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          else null
        end as display_value,
        a.created_date, a.created_by, a.modified_date, a.modified_by
        from asset a
          join property p on a.asset_type_id = p.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
      """
    queryToServicePoint(queryFilter(query))
  }

  private def queryToServicePoint(query: String): Seq[ServicePoint] = {
    val rows = Q.queryNA[ServicePointRow](query).iterator.toSeq

    rows.groupBy(_.id).map { case (id, stopRows) =>
      val row = stopRows.head
      val commonProperties: Seq[Property] = AssetPropertyConfiguration.assetRowToCommonProperties(row)
      val properties: Seq[Property] = commonProperties ++ assetRowToProperty(stopRows)
      val point = row.point.get
      val stopTypes = extractStopTypes(stopRows)

      id -> ServicePoint(id = row.id, nationalId = row.externalId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y,
        floating = row.persistedFloating, created = row.created, modified = row.modified, propertyData = properties)
    }.values.toSeq
  }

  def expire(id: Long, username: String) = {
    Queries.updateAssetModified(id, username).first
    sqlu"update asset set valid_to = sysdate where id = $id".first
  }
}
