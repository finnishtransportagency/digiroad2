package fi.liikennevirasto.digiroad2.client

import org.scalatest.{FunSuite, Matchers}

class RoadLinkChangeClientSpec extends FunSuite with Matchers {

  val roadLinkChangeClient = new RoadLinkChangeClient

  val changeJson = """[
    {
      "changeType": "add",
      "old": null,
      "new": [
      {
        "linkId": "newLink",
        "length": 123.694,
        "geometry":  "LINESTRING ZM()",
        "roadClass": 12121,
        "adminClass": 2,
        "municipality": 20,
        "trafficDirection": 0
      }
    ],
    "replaceInfo": []
  },
  {
    "changeType": "remove",
    "old": {
      "linkId": "oldLink",
      "length": 123.694,
      "geometry": "LINESTRING ZM()",
      "roadClass": 12121,
      "adminClass": 2,
      "municipality": 20,
      "trafficDirection": 0
    },
    "new": [],
    "replaceInfo": []
  }]"""

  ignore("test S3 fetch") {
    roadLinkChangeClient.fetchChangesFromS3("smallChangeSet.json")
  }

  test("test json convert") {
    roadLinkChangeClient.convertToRoadLinkChange(changeJson)
  }

}
