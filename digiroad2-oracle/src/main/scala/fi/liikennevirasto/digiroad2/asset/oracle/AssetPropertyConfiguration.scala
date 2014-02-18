package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue, EnumeratedPropertyValue}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.AssetRow
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.DateTime

object AssetPropertyConfiguration {
  val ValidityDirectionId = "validityDirection"
  val ValidFromId = "validFrom"
  val ValidToId = "validTo"
  val CreatedId = "created"
  val ModifiedId = "modified"
  val Format = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")

  val commonAssetPropertyColumns: Map[String, String] = Map(
    ValidityDirectionId -> "validity_direction",
    ValidFromId -> "valid_from",
    ValidToId -> "valid_to",
    CreatedId -> "",
    ModifiedId -> ""
  )

  val commonAssetPropertyTypes: Map[String, String] = Map(
    ValidityDirectionId -> SingleChoice,
    ValidFromId -> Date,
    ValidToId -> Date,
    CreatedId -> ReadOnlyText,
    ModifiedId -> ReadOnlyText
  )

  val commonAssetPropertyDescriptors: Map[String, Property] = Map(
    ValidityDirectionId -> Property(ValidityDirectionId, "Vaikutussuunta", SingleChoice, values = Seq()),
    ValidFromId -> Property(ValidFromId, "Käytössä alkaen", Date, values = Seq()),
    ValidToId -> Property(ValidToId, "Käytössä päättyen", Date, values = Seq()),
    CreatedId -> Property(ValidToId, "Lisätty järjestelmään", ReadOnlyText, values = Seq()),
    ModifiedId -> Property(ValidToId, "Viimeksi muokannut", ReadOnlyText, values = Seq())
  )

  val ValidityDirectionSame = 2
  val ValidityDirectionOpposite = 3
  val validityDirectionValues = Seq(
      PropertyValue(ValidityDirectionSame, "Digitointisuuntaan"),
      PropertyValue(ValidityDirectionOpposite, "Digitointisuuntaa vastaan"))
  val enumeratedValidityDirectionValues = EnumeratedPropertyValue(ValidityDirectionId, "Vaikutussuunta", commonAssetPropertyTypes(ValidityDirectionId), values = validityDirectionValues)

  val commonAssetPropertyEnumeratedValues: Seq[EnumeratedPropertyValue] = List(enumeratedValidityDirectionValues)

  def assetRowToCommonProperties(row: AssetRow): Seq[Property] = {
    List(
      createProperty(CreatedId, row.created.modifier, row.created.modificationTime),
      createProperty(ModifiedId, row.modified.modifier, row.modified.modificationTime),
      commonAssetPropertyDescriptors(ValidityDirectionId).copy(values = Seq(validityDirectionValues.find(_.propertyValue == row.validityDirection).getOrElse(PropertyValue(0, "")))),
      createProperty(ValidFromId, row.validFrom.map(_.toString)),
      createProperty(ValidToId, row.validTo.map(_.toString))
    )
  }

  private def createProperty(id: String, value: Option[String]): Property = {
    commonAssetPropertyDescriptors(id).copy(values = Seq(PropertyValue(0, value.getOrElse(null))))
  }

  private def createProperty(id: String, value: Option[String], dateTime: Option[DateTime]): Property = {
    createProperty(id, Some(value.getOrElse("-") + " " + dateTime.map(Format.print(_)).getOrElse("")))
  }
}