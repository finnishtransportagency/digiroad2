package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, NewLinearAsset}
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class CyclingAndWalkingService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {

  override def validateCondition(asset: NewLinearAsset): Unit = {
    val specialCases = Seq("3", "4", "5", "18")

    val value = asset.value.asInstanceOf[DynamicValue].value
    val cyclingAndWalkingType = value.properties.filter(_.publicId == "cyclingAndWalking_type")

    val singleChoiceId = if (cyclingAndWalkingType.isEmpty) throw new Exception("Value in Dropdown not allowed.")
    else {
      val innerValue = cyclingAndWalkingType.head.values
      if (innerValue.isEmpty) throw new Exception("Empty values.")
      else
        innerValue.head.value.toString
    }

    if (specialCases.contains(singleChoiceId)) {

      roadLinkServiceImpl.getRoadLinkAndComplementaryFromVVH(asset.linkId) match {
        case Some(roadLink) =>
          val functionalClass = roadLink.functionalClass
          val administrativeClass = roadLink.administrativeClass.value
          val linkType = roadLink.linkType.value

          // validate if singleChoiceId match is possible values
          if ( singleChoiceId == "3" && (functionalClass != 8 || linkType != 8) ||
            ( singleChoiceId == "4" && !Seq(1, 3).contains(administrativeClass)) || // AdministrativeClass For Road (1) Or Private Road (3)
            ( singleChoiceId == "5" && administrativeClass != 2) ||
            ( singleChoiceId == "18" && linkType != 12) )
            throw new Exception("Invalid Values")
        case _ => throw new Exception("Invalid roadLink")
      }
    }
  }

}
