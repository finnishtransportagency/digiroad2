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
  val s3: S3Client = Digiroad2Properties.awsConnectionEnabled match {
    case true => S3Client.builder().credentialsProvider(InstanceProfileCredentialsProvider.create()).build()
    case false => S3Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).build()
  }


  def getObjectFromS3(s3bucket: String, key: String) = {
    val getObjectRequest = GetObjectRequest.builder().bucket(s3bucket).key(key).build()
    s3.getObject(getObjectRequest)
  }

  def processStartDates() = {
    val s3bucket = sys.env.get("bucketname")
    val objectKey = sys.env.get("key")
    val user = User(0, "start_date_importer", Configuration())
    val onlyStartDates = UpdateOnlyStartDates(true)

    val result = (s3bucket, objectKey) match {
      case (Some(bucket), Some(key)) =>
        val s3Object = getObjectFromS3(bucket, key)
        val fileName = key
        Some(lanesCsvImporter.processing(s3Object, user, onlyStartDates, fileName))

      case _ =>
        None
    }

    result match {
      case Some(importResultData) => logger.info("Failed rows: " + importResultData.notImportedData)
      case _ => logger.error("Failed to extract bucket name and key from env variables")
    }
  }
}
