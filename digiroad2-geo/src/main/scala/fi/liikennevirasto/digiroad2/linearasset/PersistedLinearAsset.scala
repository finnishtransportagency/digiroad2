package fi.liikennevirasto.digiroad2.linearasset

import org.joda.time.DateTime

case class PersistedLinearAsset(id: Long, mmlId: Long, sideCode: Int, value: Option[Int],
                         startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDateTime: Option[DateTime],
                         modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean, typeId: Int)

case class NewLinearAsset(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Option[Int], sideCode: Int)

