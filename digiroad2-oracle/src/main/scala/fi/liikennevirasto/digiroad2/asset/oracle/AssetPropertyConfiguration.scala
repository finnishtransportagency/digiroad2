package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue, EnumeratedPropertyValue}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.AssetRow
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import org.joda.time.format.{DateTimeFormat}
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.asset.LocalizedString._

case class CommonAssetProperty(publicId: String, column: String, propertyType: String, propertyDescriptor: Property, lrmPositionProperty: Boolean = false)
object AssetPropertyConfiguration {
  val ValidityDirectionId = "vaikutussuunta"
  val ValidFromId = "ensimmainen_voimassaolopaiva"
  val ValidToId = "viimeinen_voimassaolopaiva"
  val CreatedId = "lisatty_jarjestelmaan"
  val ModifiedId = "muokattu_viimeksi"
  val Format = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
  val assetPropertyNamesByLanguage: Map[String, Map[String, String]] = Map(
    LangFi -> Map(ValidityDirectionId -> "Vaikutussuunta", ValidFromId -> "Ensimmäinen voimassaolopäivä", ValidToId -> "Viimeinen voimassaolopäivä", CreatedId -> "Lisätty järjestelmään", ModifiedId -> "Muokattu viimeksi"),
    LangSv -> Map()
  )

  val ValidityDirectionSame = "2"
  val ValidityDirectionOpposite = "3"
  val validityDirectionValues = Seq(PropertyValue(ValidityDirectionSame, Some("Digitointisuuntaan")),PropertyValue(ValidityDirectionOpposite, Some("Digitointisuuntaa vastaan")))
  val enumeratedValidityDirectionValues = EnumeratedPropertyValue(0, ValidityDirectionId, "Vaikutussuunta", SingleChoice, values = validityDirectionValues)

  val commonAssetPropertyEnumeratedValues: Seq[EnumeratedPropertyValue] = List(enumeratedValidityDirectionValues)

  val commonAssetProperties: Map[String, CommonAssetProperty] = Map(
    ValidityDirectionId -> CommonAssetProperty(ValidityDirectionId, "side_code", SingleChoice, Property(0, ValidityDirectionId, SingleChoice, 65, values = Seq()), true),
    ValidFromId -> CommonAssetProperty(ValidFromId, "valid_from", Date,  Property(0, ValidFromId, Date, 70, values = Seq())),
    ValidToId -> CommonAssetProperty(ValidToId, "valid_to", Date, Property(0, ValidToId, Date, 80, values = Seq())),
    CreatedId -> CommonAssetProperty(CreatedId, "", ReadOnlyText, Property(0, CreatedId, ReadOnlyText, 10, values = Seq())),
    ModifiedId -> CommonAssetProperty(ModifiedId, "",  ReadOnlyText, Property(0, ModifiedId, ReadOnlyText, 20, values = Seq()))
  )

  def assetRowToCommonProperties(row: AssetRow): Seq[Property] = {
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
    createProperty(id, Some(value.getOrElse("-") + " " + dateTime.map(Format.print(_)).getOrElse("")))
  }
}