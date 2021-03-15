package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.LocalizedString
import fi.liikennevirasto.digiroad2.asset.LocalizedString._
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

object LocalizationDao {
  def insertLocalizedString(ls: LocalizedString): LocalizedString = {
    val seqId = sql"""select nextval('primary_key_seq')""".as[Long].first
    sqlu"""
      insert into localized_string (id, value_fi, value_sv, created_date) values ($seqId, ${ls.forLanguage(LangFi).getOrElse("")}, ${ls.forLanguage(LangSv).getOrElse("")}, current_timestamp)
    """.execute
    ls.copy(id = Some(seqId))
  }
}
