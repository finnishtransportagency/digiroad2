package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries.{existsDateProperty, existsNumberProperty, existsSingleChoiceProperty, existsTextProperty}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery

object PropertyValidator {

  private val defaultPropertiesValues = Map("suggest_box" -> "0",
                                      "opposite_side_sign" -> "0" )


  def propertyValueValidation( publicId: String, propertyValue: String ): String = {

    val isDefaultProperty = defaultPropertiesValues.keySet.contains(publicId)
    val isPropertyValueEmpty = propertyValue.trim.isEmpty

    if ( isDefaultProperty && isPropertyValueEmpty )
      defaultPropertiesValues(publicId)
    else
      propertyValue
  }

  def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def numberPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsNumberProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def datePropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsDateProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }
}
