package fi.liikennevirasto.digiroad2.asset

case class LocalizedString(values: Map[String, String], id: Option[Long] = None) {
  def forLanguage(languageCode: String): Option[String] = {
    values.get(languageCode)
  }
}

object LocalizedString {
  val LangFi = "fi"
  val LangSv = "sv"
}