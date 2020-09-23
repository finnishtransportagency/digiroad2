package fi.liikennevirasto.digiroad2.linearasset

import java.util.Optional

import org.joda.time.DateTime

case class LengthOfRoadAxisModel(
                          regulatoryNumber: String,
                          laneNumber: Int,
                          laneType: Optional[Int],
                          locationRelativeToLane: String,
                          municipalId: String,
                          condition: Optional[Int],
                          material: Optional[Int],
                          length: Optional[Int],
                          width: Optional[Int],
                          raised: Optional[Int],
                          milled: Optional[Int],
                          additional_info: Int,
                          state: Int,
                          start_date: DateTime,
                          end_date: DateTime
                        )

case class LengthOfRoadAxisCreate(typeId: Int, assetSequence: Seq[NewLinearAsset]) {}

case class LengthOfRoadAxisUpdate(ids: Seq[Long], value: Value) {}

case class LengthOfRoadAxisExpire(ids: Seq[Long]) {}
