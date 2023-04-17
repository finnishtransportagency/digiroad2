package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.FloatingReason.NoRoadLinkFound
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.Remove
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
    val contents = csv.split("\\\n")(2)
    val url = ChangeReporter.getUrl(linkId)
    contents.startsWith(s"41cca8ff-4644-41aa-8de1-2702f1a57f80:2,${url},4,3,3,2,2,7,7,oldLink,,3,mtkClass,") should be(true)
    contentRows should be(1)
  }

  test("create csv with geometry for point asset change") {
    val changedAsset = ChangedAsset("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",1,Floating,Remove,
      Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),Some(List(Point(366414.9482441691,6674451.461887036))),
        Some(LinearReference("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",14.033238836181871,None,None,None, 0.0)),true,None),
      List(Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),None,None,true,Some(NoRoadLinkFound))))
    val changeReport = ChangeReport(PedestrianCrossings.typeId, Seq(changedAsset))
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport, true)
    val contents = csv.split("\\\n")(2)
    contents.startsWith(s"200,8,2,remove,1,POINT (366414.9482441691 6674451.461887036),") should be(true)
    contentRows should be(1)
  }

  test("create csv without geometry for point asset change") {
    val changedAsset = ChangedAsset("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",1,Floating,Remove,
      Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),Some(List(Point(366414.9482441691,6674451.461887036))),
        Some(LinearReference("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",14.033238836181871,None,None,None, 0.0)),true,None),
      List(Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),None,None,true,Some(NoRoadLinkFound))))
    val changeReport = ChangeReport(PedestrianCrossings.typeId, Seq(changedAsset))
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport)
    val contents = csv.split("\\\n")(2)
    contents.startsWith(s"""200,8,2,remove,1,"[{""id"":1,""publicId"":""suggest_box"",""") should be(true)
    contentRows should be(1)
  }
}
