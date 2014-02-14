package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue, EnumeratedPropertyValue}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.AssetRow
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._

object AssetPropertyConfiguration {
  val ValidityDirectionId = "validityDirection"
  val ValidFromId = "validFrom"
  val ValidToId = "validTo"
  val commonAssetPropertyColumns: Map[String, String] = Map(
    ValidityDirectionId -> "validity_direction",
    ValidFromId -> "valid_from",
    ValidToId -> "valid_to"
  )

  val commonAssetPropertyTypes: Map[String, String] = Map(
    ValidityDirectionId -> SingleChoice,
    ValidFromId -> Date,
    ValidToId -> Date
  )

  val commonAssetPropertyDescriptors: Map[String, Property] = Map(
    ValidityDirectionId -> Property(ValidityDirectionId, "Vaikutussuunta", SingleChoice, values = Seq()),
    ValidFromId -> Property(ValidFromId, "Käytössä alkaen", Date, values = Seq()),
    ValidToId -> Property(ValidToId, "Käytössä päättyen", Date, values = Seq())
  )

//val ValidityDirectionBoth = 1 Not valid for bus stops
  val ValidityDirectionSame = 2
  val ValidityDirectionOpposite = 3
  val validityDirectionValues = Seq(
//    PropertyValue(ValidityDirectionBoth, "Molempiin suuntiin"),
      PropertyValue(ValidityDirectionSame, "Digitointisuuntaan"),
      PropertyValue(ValidityDirectionOpposite, "Digitointisuuntaa vastaan"))
  val enumeratedValidityDirectionValues = EnumeratedPropertyValue(ValidityDirectionId, "Vaikutussuunta", commonAssetPropertyTypes(ValidityDirectionId), values = validityDirectionValues)

  val commonAssetPropertyEnumeratedValues: Seq[EnumeratedPropertyValue] = List(enumeratedValidityDirectionValues)

  def assetRowToCommonProperties(row: AssetRow): Seq[Property] = {
    List(
      commonAssetPropertyDescriptors(ValidityDirectionId).copy(values = Seq(validityDirectionValues.find(_.propertyValue == row.validityDirection).getOrElse(PropertyValue(0, "")))),
      commonAssetPropertyDescriptors(ValidFromId).copy(values = Seq(PropertyValue(0, row.validFrom.map(_.toString).getOrElse(null)))),
      commonAssetPropertyDescriptors(ValidToId).copy(values = Seq(PropertyValue(0, row.validTo.map(_.toString).getOrElse(null))))
    )
  }
}