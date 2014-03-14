package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue, EnumeratedPropertyValue}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.AssetRow
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import org.joda.time.format.{DateTimeFormat}
import org.joda.time.DateTime

case class CommonAssetProperty(id: String, column: String, propertyType: String, propertyDescriptor: Property, lrmPositionProperty: Boolean = false)
object AssetPropertyConfiguration {
  val ValidityDirectionId = "validityDirection"
  val ValidFromId = "validFrom"
  val ValidToId = "validTo"
  val CreatedId = "created"
  val ModifiedId = "modified"
  val Format = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")

  val ValidityDirectionSame = 2
  val ValidityDirectionOpposite = 3
  val validityDirectionValues = Seq(PropertyValue(ValidityDirectionSame, "Digitointisuuntaan"),PropertyValue(ValidityDirectionOpposite, "Digitointisuuntaa vastaan"))
  val enumeratedValidityDirectionValues = EnumeratedPropertyValue(ValidityDirectionId, "Vaikutussuunta", SingleChoice, values = validityDirectionValues)

  val commonAssetPropertyEnumeratedValues: Seq[EnumeratedPropertyValue] = List(enumeratedValidityDirectionValues)

  val commonAssetProperties: Map[String, CommonAssetProperty] = Map(
    ValidityDirectionId -> CommonAssetProperty(ValidityDirectionId, "side_code", SingleChoice, Property(ValidityDirectionId, "Vaikutussuunta", SingleChoice, values = Seq()), true),
    ValidFromId -> CommonAssetProperty(ValidFromId, "valid_from", Date,  Property(ValidFromId, "Ensimmäinen voimassaolopäivä", Date, values = Seq())),
    ValidToId -> CommonAssetProperty(ValidToId, "valid_to", Date, Property(ValidToId, "Viimeinen voimassaolopäivä", Date, values = Seq())),
    CreatedId -> CommonAssetProperty(CreatedId, "", ReadOnlyText, Property(ValidToId, "Lisätty järjestelmään", ReadOnlyText, values = Seq())),
    ModifiedId -> CommonAssetProperty(ModifiedId, "",  ReadOnlyText, Property(ValidToId, "Muokattu viimeksi", ReadOnlyText, values = Seq()))
  )

  def assetRowToCommonProperties(row: AssetRow): Seq[Property] = {
    List(
      createProperty(CreatedId, row.created.modifier, row.created.modificationTime),
      createProperty(ModifiedId, row.modified.modifier, row.modified.modificationTime),
      commonAssetProperties(ValidityDirectionId).propertyDescriptor.copy(values = Seq(validityDirectionValues.find(_.propertyValue == row.validityDirection).getOrElse(PropertyValue(0, "")))),
      createProperty(ValidFromId, row.validFrom.map(_.toString)),
      createProperty(ValidToId, row.validTo.map(_.toString))
    )
  }

  private def createProperty(id: String, value: Option[String]): Property = {
    commonAssetProperties(id).propertyDescriptor.copy(values = Seq(PropertyValue(0, value.getOrElse(null))))
  }

  private def createProperty(id: String, value: Option[String], dateTime: Option[DateTime]): Property = {
    createProperty(id, Some(value.getOrElse("-") + " " + dateTime.map(Format.print(_)).getOrElse("")))
  }
}