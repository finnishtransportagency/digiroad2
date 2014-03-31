package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.{LocalizedString, Property}
import LocalizedString._
import scala.slick.jdbc.{PositionedResult, GetResult}
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Queries._
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.slf4j.LoggerFactory

object LocalizationDao {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val getUser = new GetResult[LocalizedString] {
    def apply(r: PositionedResult) = {
      LocalizedString(id = Some(r.nextLong()), values = Map(LangFi -> r.nextString(), LangSv -> r.nextString()))
    }
  }

  def insertLocalizedString(ls: LocalizedString): LocalizedString = {
    val seqId = sql"""select primary_key_seq.nextval from dual""".as[Long].first()
    sqlu"""
      insert into localized_string (id, value_fi, value_sv, created_date) values ($seqId, ${ls.forLanguage(LangFi).getOrElse("")}, ${ls.forLanguage(LangSv).getOrElse("")}, current_timestamp)
    """.execute()
    ls.copy(id = Some(seqId))
  }

  def updateLocalizedString(ls: LocalizedString) {
    val updated: Int = sqlu"""
      update localized_string set value_fi = ${ls.forLanguage(LangFi).getOrElse("")}, value_sv = ${ls.forLanguage(LangSv).getOrElse("")}, modified_date=current_timestamp where id = ${ls.id.get}
    """.first
    if (updated == 0) {
      logger.error("Couldn't update localized string: " + ls)
    }
  }

  def getLocalizedString(id: Long): Option[LocalizedString] = {
    sql"""
      select id, value_fi, value_sv from localized_string where id = $id
    """.as[LocalizedString].firstOption
  }
}
