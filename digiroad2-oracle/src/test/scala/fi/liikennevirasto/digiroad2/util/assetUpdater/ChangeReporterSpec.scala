package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.PersistedPointAsset
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Floating, Replaced}
import org.scalatest.{FunSuite, Matchers}

class ChangeReporterSpec extends FunSuite with Matchers{

  test("create csv for road link property changes") {
    val linkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val changeReport = ChangeReport(RoadLinkProperties.typeId, Seq(
    TrafficDirectionChange(linkId,Replaced,3,Some(3)),
    AdministrativeClassChange(linkId,Replaced,2,Some(2)),
    FunctionalClassChange(linkId,Replaced,Some(7),Some(7),"oldLink"),
    LinkTypeChange(linkId,Replaced,None,Some(3),"mtkClass"),
    RoadLinkAttributeChange(linkId,Replaced,Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"),
      Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"))
    ))
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport)
    val contents = csv.split("\\\n")(1)
    val url = ChangeReporter.getUrl(linkId)
    contents.startsWith(s"41cca8ff-4644-41aa-8de1-2702f1a57f80:2,${url},4,3,3,2,2,7,7,oldLink,,3,mtkClass,") should be(true)
    contentRows should be(1)
  }

  test("create csv for floating point asset change") {
    val linkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val linearReference = LinearReference(linkId = linkId, startMValue = 44.83222354244527, endMValue = None, sideCode = SideCode.BothDirections.value, length = 0.0)
    val oldAsset = Asset(assetId = 1, values = "", municipalityCode = Some(49), geometry = None, linearReference = Some(linearReference), isPointAsset = true)
    val changedAsset = ChangedAsset(linkId = linkId, assetId = 1, changeType = Floating, before = oldAsset, after = Seq())
    val csv = ChangeReporter.generateCSV(ChangeReport(Obstacles.typeId, Seq(changedAsset)))
    println(csv._1)
  }
}
