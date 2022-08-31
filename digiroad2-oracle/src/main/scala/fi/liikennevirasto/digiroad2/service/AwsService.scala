package fi.liikennevirasto.digiroad2.service

import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, HeadObjectRequest, PutObjectRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.time.Instant
import java.time.temporal.ChronoUnit

class AwsService {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  object S3 {
    val s3: S3Client = S3Client.create()

    def saveFileToS3(s3Bucket: String, id: String, body: String, responseType: String): Unit = {
      val contentType = responseType match {
        case _ => "application/json"
      }
      try {
        val putRequest = PutObjectRequest.builder()
                                         .bucket(s3Bucket)
                                         .key(id)
                                         .contentType(contentType)
                                         .build()
        s3.putObject(putRequest, RequestBody.fromString(body))
      } catch {
        case e: Throwable =>
          logger.error("Unable to save to s3", e)
      }
    }

    def getPreSignedUrl(s3Bucket: String, jobId: String): String = {
      val getRequest = GetObjectRequest.builder()
                                       .bucket(s3Bucket)
                                       .key(jobId)
                                       .build()
      val s3PreSigner = S3Presigner.create()
      val preSignGetRequest = GetObjectPresignRequest.builder()
                                                     .signatureDuration(java.time.Duration.ofHours(1))
                                                     .getObjectRequest(getRequest)
                                                     .build()
      val preSignedGetRequest = s3PreSigner.presignGetObject(preSignGetRequest)
      preSignedGetRequest.url().toString
    }

    def isS3ObjectAvailable(s3Bucket: String, workId: String, waitTimeMillis: Long,
                            modifiedWithinSeconds: Option[Int] = None): Boolean = {
      try {
        val waiter = s3.waiter()
        val waitRequest = HeadObjectRequest.builder()
                                           .bucket(s3Bucket)
                                           .key(workId)
        val waitRequestBuilt =
          if (modifiedWithinSeconds.nonEmpty)
            waitRequest.ifModifiedSince(Instant.now().minus(modifiedWithinSeconds.get, ChronoUnit.SECONDS)).build()
          else waitRequest.build()
        val waiterOverrides = WaiterOverrideConfiguration.builder()
                                                         .waitTimeout(java.time.Duration.ofMillis(waitTimeMillis))
                                                         .build()
        val waitResponse = waiter.waitUntilObjectExists(waitRequestBuilt, waiterOverrides)
        waitResponse.matched().response().isPresent
      } catch {
        case e: SdkClientException =>
          if (e.getCause != null && e.getCause.getLocalizedMessage.contains("Unable to load credentials")) {
            throw e
          }
          false //Return false when wait object request time outs
      }
    }

    def getObjectFromS3(s3bucket: String, key: String) = {
      val getObjectRequest = GetObjectRequest.builder().bucket(s3bucket).key(key).build()
      s3.getObject(getObjectRequest)
    }
  }
}
