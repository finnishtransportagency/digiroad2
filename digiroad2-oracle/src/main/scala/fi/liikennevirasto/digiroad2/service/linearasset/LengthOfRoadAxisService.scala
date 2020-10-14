package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.DynamicProperty
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, NewLinearAsset}
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class LengthOfRoadAxisService(roadLinkServiceImpl: RoadLinkService,
                              eventBusImpl: DigiroadEventBus)
  extends DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService,
    eventBusImpl: DigiroadEventBus) {

    override def validateCondition(asset: NewLinearAsset): Unit = {
        val value = asset.value.asInstanceOf[DynamicValue].value
        val PT_regulatory_number = value.properties.filter(_.publicId == "PT_regulatory_number")

        val validateEnumurationExist =(item:Seq[DynamicProperty])=>{
            if (item.isEmpty) throw new Exception("mandatory")
            else {
                val innerValue = item.head.values
                if (innerValue.isEmpty) throw new Exception("Empty values")
                else
                    innerValue.head.value
            }
        }
        val assestsInLink= super.getPersistedAssetsByLinkIds(460,Seq(asset.linkId))

        val PT_regulatory_numberFromLink=assestsInLink
          .map(item => item.value.getOrElse("error").asInstanceOf[DynamicValue]
            .value.properties.filter(_.publicId == "PT_regulatory_number")).flatMap(item => item.map(item => item.values.map(item => item.value.toString)))
      var expresion =PT_regulatory_numberFromLink.contains(Seq(validateEnumurationExist(PT_regulatory_number).toString))
        if (validateEnumurationExist(PT_regulatory_number).toString.toInt == 99) {} else{
          if(expresion) {
              throw new Exception("This regulatory number already exist in this roadlink.")}
    }
        }
}