package fi.liikennevirasto.digiroad2.client

import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

class RoadLinkChangeClientSpec extends FunSuite with Matchers {

  val roadLinkChangeClient = new RoadLinkChangeClient

  val changeJson = """[{"changeType":"split","old":{"linkId":"3ef8bd3a-6ded-4b41-8f9f-a380e549d4b8:1","linkLength":27.33015679,"geometry":"SRID=3067;LINESTRING ZM (445821.699 6782950.418 105.496 0,445819.279 6782968.184 104.937 17.93,445816.368 6782977.122 104.622 27.33)","roadClass":12314,"adminClass":1,"municipality":111,"trafficDirection":0},"new":[{"linkId":"0006ace4-6471-402d-8c3d-d64b79555540:2","linkLength":8.82657193,"geometry":"SRID=3067;LINESTRING ZM (445821.699 6782950.418 105.559 0,445820.9650000001 6782959.214 105.335 8.827)","roadClass":12314,"adminClass":1,"municipality":111,"trafficDirection":0},{"linkId":"ea0d4ac5-23ce-40fe-8c32-fa9d4f84d934:3","linkLength":18.52716877,"geometry":"SRID=3067;LINESTRING ZM (445820.9650000001 6782959.214 105.335 0,445819.279 6782968.184 104.968 9.127,445816.368 6782977.122 104.622 18.527)","roadClass":12314,"adminClass":1,"municipality":111,"trafficDirection":0}],"replaceInfo":[{"oldLinkId":"3ef8bd3a-6ded-4b41-8f9f-a380e549d4b8:1","newLinkId":"0006ace4-6471-402d-8c3d-d64b79555540:2","oldFromMValue":0,"oldToMValue":8.815,"newFromMValue":0,"newToMValue":8.827},{"oldLinkId":"3ef8bd3a-6ded-4b41-8f9f-a380e549d4b8:1","newLinkId":"ea0d4ac5-23ce-40fe-8c32-fa9d4f84d934:3","oldFromMValue":8.815,"oldToMValue":27.33,"newFromMValue":0,"newToMValue":18.527}]}]"""


  ignore("test S3 fetch") {
    roadLinkChangeClient.fetchChangeSetFromS3("smallChangeSet.json")
  }

  test("test json convert") {
    val change = roadLinkChangeClient.convertToRoadLinkChange(changeJson)
    println(change.foreach(a => println(a)))
  }

  test("fetch changes from S3 and convert to RoadLinkChange") {
    val roadLinkChanges = roadLinkChangeClient.getRoadLinkChanges()
    roadLinkChanges.foreach(c => println(c))
  }

  ignore("filter files") {
    roadLinkChangeClient.listFilesAccordingToDates(DateTime.parse("2022-5-10"), DateTime.parse("2022-5-17"))
  }

}
