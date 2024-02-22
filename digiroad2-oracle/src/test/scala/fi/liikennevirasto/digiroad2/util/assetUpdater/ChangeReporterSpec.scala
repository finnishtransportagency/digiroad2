package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.FloatingReason.NoRoadLinkFound
import fi.liikennevirasto.digiroad2.{FloatingReason, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Remove, Split}
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Deletion, Divided, Floating, Replaced}
import org.scalatest.{FunSuite, Matchers}

class ChangeReporterSpec extends FunSuite with Matchers{
  
  val lifecycleChange = Some(LinkInfo(ConstructionType.InUse))

  test("create csv for road link property changes") {
    val linkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val changeReport = ChangeReport(RoadLinkProperties.typeId, Seq(
    TrafficDirectionChange(linkId,Replaced,3,Some(3)),
    AdministrativeClassChange(linkId,Replaced,2,Some(2)),
    FunctionalClassChange(linkId,Replaced,Some(7),Some(7),"oldLink"),
    LinkTypeChange(linkId,Replaced,None,Some(3),"mtkClass"),
    RoadLinkAttributeChange(linkId,Replaced,Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"),
      Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"))
    ,ConstructionTypeChange(linkId,Replaced,Some(ConstructionType.InUse),Some(ConstructionType.InUse))
    )
      
    )
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport)
    val contents = csv.split("\\\n")(2)
    contents.startsWith(s"41cca8ff-4644-41aa-8de1-2702f1a57f80:2") should be(true)
    contents.contains("2,4,3,3,2,2,7,7,oldLink,,3,mtkClass") should be(true)
    contentRows should be(1)
  }

  test("create csv with geometry for point asset change") {
    val changedAsset = ChangedAsset("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",1,Floating,Remove,
      Some(Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),Some(List(Point(366414.9482441691,6674451.461887036))),
        Some(LinearReferenceForReport("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",14.033238836181871,None,None,None, 0.0)),lifecycleChange,true,None)),
      List(Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),None,None,lifecycleChange,true,Some(NoRoadLinkFound))))
    val changeReport = ChangeReport(PedestrianCrossings.typeId, Seq(changedAsset))
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport, true)
    val contents = csv.split("\\\n")(2)
    contents.startsWith(s"200,7,2,remove,3,1,POINT (366414.948 6674451.462),") should be(true)
    contentRows should be(1)
  }

  test("create csv without geometry for point asset change") {
    val changedAsset = ChangedAsset("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",1,Floating,Remove,
      Some(Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),Some(List(Point(366414.9482441691,6674451.461887036))),
        Some(LinearReferenceForReport("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1",14.033238836181871,None,None,None, 0.0)),lifecycleChange,true,None)),
      List(Asset(1,s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""",
        Some(49),None,None,lifecycleChange,true,Some(NoRoadLinkFound))))
    val changeReport = ChangeReport(PedestrianCrossings.typeId, Seq(changedAsset))
    val (csv, contentRows) = ChangeReporter.generateCSV(changeReport)
    val contents = csv.split("\\\n")(2)
    contents.startsWith(s"""200,7,2,remove,3,1,"[{""id"":1,""publicId"":""suggest_box"",""") should be(true)
    contentRows should be(1)
  }

  test("create csv with geometry for linear asset deletion change") {
    val linkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
    val assetId1 = 123
    val assetId2  = 124
    val geometry1 = Some(List(Point(366408.515,6674439.018,3.933), Point(366409.675,6674441.156,4.082), Point(366413.518,6674448.237,4.573), Point(366418.695,6674459.91,5.805), Point(366425.83199998754,6674457.102000005,5.956999999734991)))
    val geometry2 = Some(List(Point(378371.653,6675257.813,10.874), Point(378371.4270000001,6675265.207,11.077), Point(378373.873,6675279.028,12.0), Point(378375.164,6675294.114,13.166), Point(378375.838,6675302.261,13.648), Point(378379.8780000001,6675312.458,13.945)))
    val values1 = s"""{"publicId":"lane_type","values":["2"],"publicId":"start_date","values":["1.1.1970"],"publicId":"lane_code","values":["2"]}"""
    val values2 = s"""{"publicId":"lane_type","values":["1"],"publicId":"start_date","values":["1.1.1970"],"publicId":"lane_code","values":["1"]}"""
    val linearReference1 = LinearReferenceForReport(linkId, 0.0, Some(30.928), Some(2), None, 30.928)
    val linearReference2 = LinearReferenceForReport(linkId, 0.0, Some(55.717), Some(2), None, 55.757)
    val before1 = Asset(assetId1, values1, Some(49), geometry1, Some(linearReference1), lifecycleChange, isPointAsset = false, None)
    val before2 = Asset(assetId2, values2, Some(49), geometry2, Some(linearReference2), lifecycleChange, isPointAsset = false, None)

    val changedAsset1 = ChangedAsset(linkId = linkId, assetId = assetId1, changeType = Deletion, roadLinkChangeType = Remove, before = Some(before1), after = Seq())
    val changedAsset2 = ChangedAsset(linkId = linkId, assetId = assetId2, changeType = Deletion, roadLinkChangeType = Remove, before = Some(before2), after = Seq())
    val changeReport = ChangeReport(Lanes.typeId, Seq(changedAsset1, changedAsset2))

    val (csv, contentRowCount) = ChangeReporter.generateCSV(changeReport, withGeometry = true)
    contentRowCount should be(2)
  }

  test ("create csv without geometry for linear asset divided change") {
    val oldLinkId = "3a832249-d3b9-4c22-9b08-c7e9cd98cbd7:1"
    val newLinkId1 = "581687d9-f4d5-4fa5-9e44-87026eb74774:1"
    val newLinkId2 = "6ceeebf4-2351-46f0-b151-3ed02c7cfc05:1"
    val values = s"""{"publicId":"lane_type","values":["1"],"publicId":"start_date","values":["1.1.1970"],"publicId":"lane_code","values":["1"]}"""
    val beforeLinearRef = LinearReferenceForReport(linkId = oldLinkId, startMValue = 0.0, endMValue = Some(432.253),
      sideCode = Some(2), validityDirection = None, length = 423.235)
    val after1LinearRef = LinearReferenceForReport(linkId = newLinkId1, startMValue = 0.0, endMValue = Some(156.867),
      sideCode = Some(2), validityDirection = None, length = 156.867)
    val after2LinearRef = LinearReferenceForReport(linkId = newLinkId2, startMValue = 0.0, endMValue = Some(275.368),
      sideCode = Some(2), validityDirection = None, length = 275.368)
    val beforeAsset = Asset(assetId = 123, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(beforeLinearRef),lifecycleChange , isPointAsset = false, floatingReason = None)
    val afterAsset1 = Asset(assetId = 124, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(after1LinearRef),lifecycleChange, isPointAsset = false, floatingReason = None)
    val afterAsset2 = Asset(assetId = 125, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(after2LinearRef),lifecycleChange, isPointAsset = false, floatingReason = None)

    val changedAsset = ChangedAsset(linkId = oldLinkId, assetId = beforeAsset.assetId, changeType = Divided,
      roadLinkChangeType = Split, before = Some(beforeAsset), after = Seq(afterAsset1, afterAsset2))
    val changeReport = ChangeReport(Lanes.typeId, Seq(changedAsset))

    val (csv, contentRowCount) = ChangeReporter.generateCSV(changeReport, withGeometry = false)
    contentRowCount should be(2)

  }

  test("check that all properties are in correct place") {
    val oldLinkId = "3a832249-d3b9-4c22-9b08-c7e9cd98cbd7:1"
    val newLinkId1 = "581687d9-f4d5-4fa5-9e44-87026eb74774:1"
    val values = s"""property"""
    val beforeLinearRef = LinearReferenceForReport(linkId = oldLinkId, startMValue = 0.0, endMValue = Some(432.253),
      sideCode = Some(2), validityDirection = None, length = 423.235)
    val after1LinearRef = LinearReferenceForReport(linkId = newLinkId1, startMValue = 0.0, endMValue = Some(156.867),
      sideCode = Some(2), validityDirection = None, length = 156.867)
    val beforeAsset = Asset(assetId = 123, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(beforeLinearRef), lifecycleChange, isPointAsset = false, floatingReason = None)
    val afterAsset1 = Asset(assetId = 124, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(after1LinearRef), lifecycleChange, isPointAsset = false, floatingReason = None)

    val changedAsset = ChangedAsset(linkId = oldLinkId, assetId = beforeAsset.assetId, changeType = Replaced,
      roadLinkChangeType = RoadLinkChangeType.Replace, before = Some(beforeAsset), after = Seq(afterAsset1))
    val changeReport = ChangeReport(Lanes.typeId, Seq(changedAsset))

    val (csv, contentRowCount) = ChangeReporter.generateCSV(changeReport, withGeometry = false)
    contentRowCount should be(1)

    val contents = csv.split("\\\n")(2)
    val header = csv.split("\\\n")(1).split(",")
    val row1 = contents.split(",")
    
    header.length should be(23)
    row1.length should be(23)
    
    header(0) should be("asset_type_id")
    row1(0) should be("450")
    
    header(1) should be("change_type")
    row1(1) should be(Replaced.value.toString)
    
    header(2) should be("roadlink_change")
    row1(2) should be(RoadLinkChangeType.Replace.value)
    
    header(3) should be("before_constructionType")
    row1(3) should be(lifecycleChange.get.constructionType.value.toString)
    
    header(4) should be("before_asset_id")
    row1(4) should be("123")
    
    header(5) should be("before_value")
    row1(5) should be(values)
    
    header(6) should be("before_municipality_code")
    row1(6) should be("49")
    
    header(7) should be("before_side_code")
    row1(7) should be("2")
    
    header(8) should be("before_link_id")
    row1(8) should be(oldLinkId)
    
    header(9) should be("before_start_m_value")
    row1(9) should be("0.0")
    
    header(10) should be("before_end_m_value")
    row1(10) should be("432.253")
    
    header(11) should be("before_length")
    row1(11) should be("423.235")
    
    header(12) should be("before_roadlink_url")
    //row1(12) should be("")
    
    header(13) should be("after_constructionType")
    row1(13) should be(lifecycleChange.get.constructionType.value.toString)
    
    header(14) should be("after_asset_id")
    row1(14) should be("124")
    
    header(15) should be("after_value")
    row1(15) should be(values)
    
    header(16) should be("after_municipality_code")
    row1(16) should be("49")
    
    header(17) should be("after_side_code")
    row1(17) should be("2")
    
    header(18) should be("after_link_id")
    row1(18) should be(newLinkId1)
    
    header(19) should be("after_start_m_value")
    row1(19) should be("0.0")
    
    header(20) should be("after_end_m_value")
    row1(20) should be("156.867")
    

    header(21) should be("after_length")
    row1(21) should be("156.867")
    
    //row1(22) should be("after_roadlink_url")
    header(22) should be("after_roadlink_url\r")
  }

  test("check that all properties are in correct place, point like") {
    val oldLinkId = "3a832249-d3b9-4c22-9b08-c7e9cd98cbd7:1"
    val newLinkId1 = "581687d9-f4d5-4fa5-9e44-87026eb74774:1"
    val values = s"""property"""
    val beforeLinearRef = LinearReferenceForReport(linkId = oldLinkId, startMValue = 1.0, endMValue = Some(0),
      sideCode = Some(2), validityDirection = Some(1), length = 1)
    val after1LinearRef = LinearReferenceForReport(linkId = newLinkId1, startMValue = 1.0, endMValue = Some(0),
      sideCode = Some(2), validityDirection = Some(1), length = 1)
    val beforeAsset = Asset(assetId = 123, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(beforeLinearRef), lifecycleChange, isPointAsset = true, floatingReason = Some(FloatingReason.RoadOwnerChanged))
    val afterAsset1 = Asset(assetId = 124, values = values, municipalityCode = Some(49), geometry = None,
      linearReference = Some(after1LinearRef), lifecycleChange, isPointAsset = true, floatingReason = Some(FloatingReason.RoadOwnerChanged))

    val changedAsset = ChangedAsset(linkId = oldLinkId, assetId = beforeAsset.assetId, changeType = Replaced,
      roadLinkChangeType = RoadLinkChangeType.Replace, before = Some(beforeAsset), after = Seq(afterAsset1))
    val changeReport = ChangeReport(MassTransitStopAsset.typeId, Seq(changedAsset))

    val (csv, contentRowCount) = ChangeReporter.generateCSV(changeReport, withGeometry = false)
    contentRowCount should be(1)

    val contents = csv.split("\\\n")(2)
    val header = csv.split("\\\n")(1).split(",")
    val row1 = contents.split(",")

    header.length should be(24)
    row1.length should be(24)
    
    header(0) should be("asset_type_id")
    row1(0) should be("10")
    
    header(1) should be("change_type")
    row1(1) should be(Replaced.value.toString)
    
    header(2) should be("floating_reason")
    row1(2) should be(FloatingReason.RoadOwnerChanged.value.toString)
    
    header(3) should be("roadlink_change")
    row1(3) should be(RoadLinkChangeType.Replace.value)
    
    header(4) should be("before_constructionType")
    row1(4) should be(lifecycleChange.get.constructionType.value.toString)
    
    header(5) should be("before_asset_id")
    row1(5) should be("123")
    
    header(6) should be("before_value")
    row1(6) should be(values)
    
    header(7) should be("before_municipality_code")
    row1(7) should be("49")
    
    header(8) should be("before_validity_direction")
    row1(8) should be("1")
    
    header(9) should be("before_link_id")
    row1(9) should be(oldLinkId)
    
    header(10) should be("before_start_m_value")
    row1(10) should be("1.0")
    
    header(11) should be("before_end_m_value")
    row1(11) should be("0.0")
    
    header(12) should be("before_length")
    row1(12) should be("1.0")
    
    header(13) should be("before_roadlink_url")
    //row1(13) should be("")
    
    header(14) should be("after_constructionType")
    row1(14) should be(lifecycleChange.get.constructionType.value.toString)
    
    header(15) should be("after_asset_id")
    row1(15) should be("124")
    
    header(16) should be("after_value")
    row1(16) should be(values)
    
    header(17) should be("after_municipality_code")
    row1(17) should be("49")
    
    header(18) should be("after_validity_direction")
    row1(18) should be("1")
    
    header(19) should be("after_link_id")
    row1(19) should be(newLinkId1)
   
    header(20) should be("after_start_m_value")
    row1(20) should be("1.0")
    
    header(21) should be("after_end_m_value")
    row1(21) should be("0.0")
    
    header(22) should be("after_length")
    row1(22) should be("1.0")
    
    header(23) should be("after_roadlink_url\r")
    //row1(23) should be("after_roadlink_url")
    
  }
}
