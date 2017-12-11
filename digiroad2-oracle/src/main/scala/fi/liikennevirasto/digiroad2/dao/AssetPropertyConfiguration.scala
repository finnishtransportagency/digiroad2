package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset.LocalizedString._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{EnumeratedPropertyValue, Property, PropertyValue}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopRow
import org.joda.time.DateTime

case class CommonAssetProperty(publicId: String, column: String, propertyType: String, propertyDescriptor: Property, lrmPositionProperty: Boolean = false)
object AssetPropertyConfiguration {
  val ValidityDirectionId = "vaikutussuunta"
  val ValidFromId = "ensimmainen_voimassaolopaiva"
  val ValidToId = "viimeinen_voimassaolopaiva"
  val CreatedId = "lisatty_jarjestelmaan"
  val ModifiedId = "muokattu_viimeksi"
  //TODO this is not a common property
  val ConnectedToTerminal = "liitetty_terminaaliin"
  val assetPropertyNamesByLanguage: Map[String, Map[String, String]] = Map(
    LangFi -> Map(ValidityDirectionId -> "Vaikutussuunta", ValidFromId -> "Ensimmäinen voimassaolopäivä", ValidToId -> "Viimeinen voimassaolopäivä", CreatedId -> "Lisätty järjestelmään", ModifiedId -> "Muokattu viimeksi", ConnectedToTerminal -> "Liitetty Terminaaliin"),
    LangSv -> Map()
  )

  val ValidityDirectionSame = "2"
  val ValidityDirectionOpposite = "3"
  val validityDirectionValues = Seq(PropertyValue(ValidityDirectionSame, Some("Digitointisuuntaan")),PropertyValue(ValidityDirectionOpposite, Some("Digitointisuuntaa vastaan")))
  val enumeratedValidityDirectionValues = EnumeratedPropertyValue(0, ValidityDirectionId, "Vaikutussuunta", SingleChoice, values = validityDirectionValues)

  val commonAssetPropertyEnumeratedValues: Seq[EnumeratedPropertyValue] = List(enumeratedValidityDirectionValues)

  val commonAssetProperties: Map[String, CommonAssetProperty] = Map(
    ValidityDirectionId -> CommonAssetProperty(ValidityDirectionId, "side_code", SingleChoice, Property(0, ValidityDirectionId, SingleChoice, values = Seq(PropertyValue(ValidityDirectionSame, Some(ValidityDirectionSame)))), true),
    ValidFromId -> CommonAssetProperty(ValidFromId, "valid_from", Date,  Property(0, ValidFromId, Date, values = Seq())),
    ValidToId -> CommonAssetProperty(ValidToId, "valid_to", Date, Property(0, ValidToId, Date, values = Seq())),
    CreatedId -> CommonAssetProperty(CreatedId, "", ReadOnlyText, Property(0, CreatedId, ReadOnlyText, values = Seq())),
    ModifiedId -> CommonAssetProperty(ModifiedId, "",  ReadOnlyText, Property(0, ModifiedId, ReadOnlyText, values = Seq()))
  )

  def assetRowToCommonProperties(row: MassTransitStopRow): Seq[Property] = {
   List(
      createProperty(CreatedId, row.created.modifier, row.created.modificationTime),
      createProperty(ModifiedId, row.modified.modifier, row.modified.modificationTime),
      commonAssetProperties(ValidityDirectionId).propertyDescriptor.copy(values = Seq(validityDirectionValues.find(_.propertyValue.toInt == row.validityDirection).getOrElse(PropertyValue("", None)))),
      createProperty(ValidFromId, row.validFrom.map(_.toString)),
      createProperty(ValidToId, row.validTo.map(_.toString))
    )
  }

  private def createProperty(id: String, value: Option[String]): Property = {
    commonAssetProperties(id).propertyDescriptor.copy(values = Seq(PropertyValue(value.getOrElse(""), value)))
  }

  private def createProperty(id: String, value: Option[String], dateTime: Option[DateTime]): Property = {
    createProperty(id, Some(value.getOrElse("-") + " " + dateTime.map(DateTimePropertyFormat.print(_)).getOrElse("")))
  }
}