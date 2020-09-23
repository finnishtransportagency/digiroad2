package fi.liikennevirasto.digiroad2.linearasset

import java.util.Optional

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, DynamicPropertyValue}
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
class LengthOfRoadAxisMethod{
  private def createDynamicField()={
    DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("regulatory_number", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("lane_number", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("lane_type", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("lane_location", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("material", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("length", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("width", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("profile_mark", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("additional_information", "string", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("state", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("end_date", "date", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("start_date", "date", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("milled", "single_choice", required = false, Seq(DynamicPropertyValue(""))),
      DynamicProperty("condition", "single_choice", required = false, Seq(DynamicPropertyValue("")))
    )))
  }
}


case class LengthOfRoadAxisCreate(typeId: Int, assetSequence: Seq[NewLinearAsset]) {}

case class LengthOfRoadAxisUpdate(ids: Seq[Long], value: Value) {}

case class LengthOfRoadAxisExpire(ids: Seq[Long]) {}
