package fi.liikennevirasto.digiroad2.util

import org.joda.time.DateTime

object KgvUtil {

  def extractModifiedAt(createdDate:Option[Long],lastEdited:Option[Long]): Option[DateTime] = {
    lastEdited.orElse(createdDate).map(new DateTime(_))
  }
}
