package fi.liikennevirasto.digiroad2.util

import org.joda.time.DateTime

object KgvUtil {

  def extractModifiedAt(createdDate:Option[Long],lastEdited:Option[Long]): Option[DateTime] = {
    Some(new DateTime(lastEdited.getOrElse(createdDate.getOrElse(0L))))
  }
}
