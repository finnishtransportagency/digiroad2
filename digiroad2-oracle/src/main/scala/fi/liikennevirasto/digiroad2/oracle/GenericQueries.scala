package fi.liikennevirasto.digiroad2.oracle

import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation

object GenericQueries {
  val logger = LoggerFactory.getLogger(getClass)

  case class AssetTypeMetadataRow(publicId: String, propertyType: String, propertyRequired: Int, propertyName: String, valueName: Option[String], valueValue: Option[String], orderField: Option[Int] = None)

  implicit val getAsseMetadata = new GetResult[AssetTypeMetadataRow] {
    def apply(r: PositionedResult) = {

      val publicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextInt
      val propertyName = r.nextString
      val valueName = r.nextStringOption
      val valueValue = r.nextStringOption

      AssetTypeMetadataRow(publicId, propertyType, propertyRequired, propertyName, valueName, valueValue)
    }
  }

  def getAssetTypeMetadataRow(typeID: Long): Seq[AssetTypeMetadataRow] = {
    sql"""
      select p.public_id, p.property_type, p.required, ls.value_fi,
      case
        when e.value is not null then to_char(e.value)
        when tp.value_fi is not null then tp.value_fi
        when np.value is not null then to_char(np.value)
      else null
      end as value, e.name_fi
      from asset_type a
      join property p on p.asset_type_id = a.id
      left join enumerated_value e on e.property_id = p.id
      left join localized_string ls on ls.id = p.name_localized_string_id
      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
      left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
      left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
      where a.id = $typeID
      """.as[AssetTypeMetadataRow].list //need to be created a orderFieldInForm , valueByDefaulField
  }

}
