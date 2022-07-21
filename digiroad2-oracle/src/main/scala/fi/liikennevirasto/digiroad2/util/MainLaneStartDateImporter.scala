package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.csvDataImporter.LanesCsvImporter
import fi.liikennevirasto.digiroad2.middleware.UpdateOnlyStartDates
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.{InstanceProfileCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

object MainLaneStartDateImporter {
  lazy val vvhClient = new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  lazy val lanesCsvImporter: LanesCsvImporter = new LanesCsvImporter(roadLinkService, new DummyEventBus)
  val logger = LoggerFactory.getLogger(getClass)
  val s3: S3Client = S3Client.create()

  def main(args: Array[String]): Unit = {

    val s3bucket = args(0)
    val objectKey = args(1)
    processStartDates(s3bucket, objectKey)

    def getObjectFromS3(s3bucket: String, key: String) = {
      val getObjectRequest = GetObjectRequest.builder().bucket(s3bucket).key(key).build()
      s3.getObject(getObjectRequest)
    }

    def processStartDates(s3bucket: String, objectKey: String) = {
      val user = User(0, "start_date_importer", Configuration())
      val onlyStartDates = UpdateOnlyStartDates(true)

      val s3Object = getObjectFromS3(s3bucket, objectKey)
      val fileName = objectKey
      val result = lanesCsvImporter.processing(s3Object, user, onlyStartDates, fileName)

      logger.info("Failed rows: " + result.notImportedData)
    }
  }
}
