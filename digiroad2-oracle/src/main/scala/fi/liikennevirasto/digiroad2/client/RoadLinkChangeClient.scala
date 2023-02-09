package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.json4s.FieldSerializer.{renameFrom, renameTo}
import org.json4s._
import org.json4s.jackson.parseJson

import scala.io.Source.fromInputStream

class RoadLinkChangeClient {
  val awsService = new AwsService
  val s3Service: awsService.S3.type = awsService.S3
  val s3Bucket: String = Digiroad2Properties.roadLinkChangeS3BucketName

  val changeItemSerializer: FieldSerializer[ChangeItem] = FieldSerializer[ChangeItem](
    renameTo("newLinks", "new") orElse renameTo("oldLink", "old"),
    renameFrom("new", "newLinks") orElse renameFrom("old", "oldLink"))

  implicit val formats = DefaultFormats + changeItemSerializer




  case class RoadLinkInfo(linkId: String, length: Double, geometry: String, roadClass: Int, adminClass: Int, municipality: Int, trafficDirection: Int)
  case class ChangeItem(changeType: String, oldLink: Option[RoadLinkInfo], newLinks: List[RoadLinkInfo], replaceInfo: Option[String])

  def fetchChangesFromS3(filename: String) = {
    val s3Object = s3Service.getObjectFromS3(s3Bucket, filename)
    val jsonString = fromInputStream(s3Object).mkString
    val parsed = parseJson(jsonString)
    println(parsed)
  }


  def convertToRoadLinkChange(changeJson: JsonInput) : Seq[RoadLinkInfo] = {
    val json = parseJson(changeJson)
    val example = json.extract[List[ChangeItem]]
    println(example)
    Seq()
  }

}
