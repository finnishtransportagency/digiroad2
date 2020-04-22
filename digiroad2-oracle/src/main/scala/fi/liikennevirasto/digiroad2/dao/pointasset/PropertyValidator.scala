package fi.liikennevirasto.digiroad2.dao.pointasset

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

}
