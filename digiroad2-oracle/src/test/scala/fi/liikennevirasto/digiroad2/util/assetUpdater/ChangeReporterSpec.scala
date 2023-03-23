package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.{RoadLinkProperties}
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.Replaced
import org.scalatest.{FunSuite, Matchers}

class ChangeReporterSpec extends FunSuite with Matchers{

  test("create csv for road link property changes") {
    val changeReport = ChangeReport(RoadLinkProperties.typeId, Seq(
    TrafficDirectionChange("41cca8ff-4644-41aa-8de1-2702f1a57f80:2",Replaced,3,Some(3)),
    AdministrativeClassChange("41cca8ff-4644-41aa-8de1-2702f1a57f80:2",Replaced,2,Some(2)),
    FunctionalClassChange("41cca8ff-4644-41aa-8de1-2702f1a57f80:2",Replaced,Some(7),Some(7),"oldLink"),
    LinkTypeChange("41cca8ff-4644-41aa-8de1-2702f1a57f80:2",Replaced,None,Some(3),"mtkClass"),
    RoadLinkAttributeChange("41cca8ff-4644-41aa-8de1-2702f1a57f80:2",Replaced,Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"),
      Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"))
    ))
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport)
    val contents = csv.split("\\\n")(1)
    contents.startsWith("41cca8ff-4644-41aa-8de1-2702f1a57f80:2,4,3,3,2,2,7,7,oldLink,,3,mtkClass,") should be(true)
    contentRows should be(1)
  }
}
