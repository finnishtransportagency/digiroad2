package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.{Modification, Property}
import fi.liikennevirasto.digiroad2.dao.Queries.PropertyRow
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{MassTransitStopValidityPeriod, _}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopOperations
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.Point
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}
import scala.language.reflectiveCalls
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, Modification, Property}
import fi.liikennevirasto.digiroad2.dao.Queries.PropertyRow
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{MassTransitStopValidityPeriod, _}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{LightGeometryMassTransitStop, MassTransitStopOperations, MassTransitStopRow, PersistedMassTransitStop}
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.ISODateTimeFormat
import scala.language.reflectiveCalls

case class ServicePoint(id: Long, nationalId: Long, stopTypes: Seq[Int],
                        municipalityCode: Int, lon: Double, lat: Double,
                        created: Modification, modified: Modification, propertyData: Seq[Property])

case class ServicePointRow(id: Long, nationalId: Long, assetTypeId: Long, point: Option[Point],
                           validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                           created: Modification, modified: Modification, municipalityCode: Int)

class ServicePointBusStopDao extends MassTransitStopDao {
  private implicit val getServicePointRow = new GetResult[ServicePointRow] {
    def apply(r: PositionedResult): ServicePointRow = {
      val id = r.nextLong
      val nationalId = r.nextLong
      val assetTypeId = r.nextLong
      val validFrom = r.nextDateOption.map(new LocalDate(_))
      val validTo = r.nextDateOption.map(new LocalDate(_))
      val point = r.nextObjectOption().map(objectToPoint)
      val municipalityCode = r.nextInt()
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

      ServicePointRow(id, nationalId, assetTypeId, point, validFrom, validTo, property, created, modified, municipalityCode = municipalityCode)
    }
  }

  def servicePointRowToProperty(assetRows: Iterable[ServicePointRow]): Seq[Property] = {
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

  def fetchAsset(queryFilter: String => String): Seq[ServicePoint] = {
    val query = """
        select a.id, a.national_id, a.asset_type_id,
        a.valid_from, a.valid_to, geometry, a.municipality_code,
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
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
        where exists (
            select '1'
            from multiple_choice_value aux_mc
            join enumerated_value aux_e on aux_mc.enumerated_value_id = aux_e.id and aux_e.value = '7'
            where aux_mc.asset_id = a.id) and (a.valid_to > current_timestamp or a.valid_to is null)
      """
    queryToServicePoint(queryFilter(query))
  }

  private def queryToServicePoint(query: String): Seq[ServicePoint] = {
    val rows = Q.queryNA[ServicePointRow](query)(getServicePointRow).iterator.toSeq

    rows.groupBy(_.id).map { case (id, stopRows) =>
      val row = stopRows.head
      val commonProperties: Seq[Property] = AssetPropertyConfiguration.assetRowToCommonProperties(row)
      val properties: Seq[Property] = commonProperties ++ servicePointRowToProperty(stopRows)
      val point = row.point.get
      val stopTypes = extractStopTypes(stopRows)

      id -> ServicePoint(id = row.id, nationalId = row.nationalId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y, created = row.created, modified = row.modified, propertyData = properties)
    }.values.toSeq
  }

  private def extractStopTypes(rows: Seq[ServicePointRow]): Seq[Int] = {
    rows.filter(_.property.publicId.equals(MassTransitStopOperations.MassTransitStopTypePublicId)).map { row => row.property.propertyValue.toInt }
  }

  def update(assetId: Long, properties: Seq[SimplePointAssetProperty], user: String) = {
    updateAssetLastModified(assetId, user)
    updateAssetProperties(assetId, properties)
    assetId
  }

  def insertAsset(id: Long, lon: Double, lat: Double, creator: String, municipalityCode: Int): Unit = {
    val pointGeometry =Queries.pointGeometry(lon,lat)
    sqlu"""insert into asset (id, national_id, asset_type_id, created_by, municipality_code, geometry) values (
           $id, nextval('national_bus_stop_id_seq'), $typeId, $creator, $municipalityCode,
           ST_GeomFromText($pointGeometry,3067)
    )
      """.execute
  }

  private def propertyWithTypeAndId(property: SimplePointAssetProperty): (String, Option[Long], SimplePointAssetProperty) = {
    val propertyId = Q.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (Q.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  private def updateAssetProperties(assetId: Long, properties: Seq[SimplePointAssetProperty]) {
    properties.filterNot(prop => AssetPropertyConfiguration.commonAssetProperties.contains(prop.publicId)).map(propertyWithTypeAndId).foreach { propertyWithTypeAndId =>
      updateProperties(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._2.get, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
    }
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def updateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue]) {
    propertyType match {
      case Text | LongText =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        }

      case MultipleChoice | CheckBox =>
        createOrUpdateMultipleChoiceProperty(propertyValues.asInstanceOf[Seq[PropertyValue]], assetId, propertyId)

      case SingleChoice =>
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }

      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }
}
