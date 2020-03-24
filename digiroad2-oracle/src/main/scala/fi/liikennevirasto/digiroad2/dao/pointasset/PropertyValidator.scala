package fi.liikennevirasto.digiroad2.dao.pointasset

object PropertyValidator {

  private val DEFAULT_PROPERTIES_VALUES = Map("suggest_box" -> "0",
                                      "opposite_side_sign" -> "0" )


  def propertyValueValidation( publicId: String, propertyValue: String ): String = {

    val isDefaultProperty = DEFAULT_PROPERTIES_VALUES.keySet.contains(publicId)
    val isPropertyValueEmpty = propertyValue.trim.isEmpty

    if ( isDefaultProperty && isPropertyValueEmpty )
      DEFAULT_PROPERTIES_VALUES(publicId)
    else
      propertyValue
  }

}
