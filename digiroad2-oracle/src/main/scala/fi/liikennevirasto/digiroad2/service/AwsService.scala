package fi.liikennevirasto.digiroad2.service

import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{AbortMultipartUploadRequest, CompleteMultipartUploadRequest, CompletedMultipartUpload, CompletedPart, CreateMultipartUploadRequest, GetObjectRequest, HeadObjectRequest, ListObjectsV2Request, PutObjectRequest, S3Object, UploadPartRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.io.Source.fromInputStream
import scala.collection.mutable
import scala.collection.mutable.Map
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

class AwsService {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  object S3 {
    val s3: S3Client = S3Client.create()
    val CHUNK_SIZE = 100 * 1024 * 1024 // 100MB
    val SINGLE_UPLOAD_MAX_SIZE = 100 * 1024 * 1024 // files greater than 100MB should be uploaded using MultipartUpload

    def saveFileToS3(s3Bucket: String, id: String, body: String, responseType: String) = {
      requiresMultipartUpload(body) match {
        case true => saveFileToS3Multipart(s3Bucket, id, body, responseType)
        case false => saveFileToS3Singlepart(s3Bucket, id, body, responseType)
      }
    }

    def requiresMultipartUpload(body: String): Boolean = {
      if (body.length > SINGLE_UPLOAD_MAX_SIZE) {
        true
      } else {
        // double check, as body size might still exceed the limit due to multi-byte characters
        val sizeInBytes = body.getBytes("UTF-8").length
        sizeInBytes > SINGLE_UPLOAD_MAX_SIZE
      }
    }

    private def getContentType(responseType: String): String = responseType match {
      case "csv" => "text/csv"
      case _ => "application/json"
    }

    def saveFileToS3Singlepart(s3Bucket: String, id: String, body: String, responseType: String): Unit = {
      val contentType = getContentType(responseType)
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

    def saveFileToS3Multipart(s3Bucket: String, id: String, body: String, responseType: String): Unit = {
      val contentType = getContentType(responseType)
      val uploadId = initiateMultipartUpload(s3Bucket, id, contentType)

      try {
        val uploadedETags = Await.result(uploadParts(s3Bucket, id, uploadId, body), 15.minutes)
        completeMultipartUpload(s3Bucket, id, uploadId, uploadedETags)
        logger.info(s"Successfully saved ${id} to S3")
      } catch {
        case e: Throwable =>
          abortMultipartUpload(s3Bucket, id, uploadId)
          logger.error(s"Unable to save ${id} to s3", e)
      }
    }

    private def initiateMultipartUpload(s3Bucket: String, id: String, contentType: String): String = {
      val uploadRequest = CreateMultipartUploadRequest.builder()
        .bucket(s3Bucket)
        .key(id)
        .contentType(contentType)
        .build()
      val createResponse = s3.createMultipartUpload(uploadRequest)
      createResponse.uploadId()
    }


    def uploadParts(s3Bucket: String, id: String, uploadId: String, body: String): Future[mutable.Map[Int, String]] = {
      val totalChunks = Math.ceil(body.length.toDouble / CHUNK_SIZE).toInt
      val uploadedETags = mutable.Map[Int, String]()

      Future.traverse(1 to totalChunks) { partNumber =>
        val offset = (partNumber - 1) * CHUNK_SIZE
        val chunkData = body.substring(offset, Math.min(offset + CHUNK_SIZE, body.length))
        uploadPart(s3Bucket, id, uploadId, partNumber, chunkData).map { eTag =>
          uploadedETags.synchronized {
            uploadedETags += (partNumber -> eTag)
          }
          (partNumber, eTag)
        }
      }.map(_ => uploadedETags)
    }

    private def uploadPart(s3Bucket: String, id: String, uploadId: String, partNumber: Int, chunkData: String): Future[String] = {
      val partRequest = UploadPartRequest.builder()
        .bucket(s3Bucket)
        .key(id)
        .uploadId(uploadId)
        .partNumber(partNumber)
        .build()
      val chunkInputStream = new ByteArrayInputStream(chunkData.getBytes(StandardCharsets.UTF_8))
      val requestBody = RequestBody.fromInputStream(chunkInputStream, chunkData.getBytes(StandardCharsets.UTF_8).length)

      Future {
        val uploadPartResponse = s3.uploadPart(partRequest, requestBody)
        uploadPartResponse.eTag()
      }
    }

    private def completeMultipartUpload(s3Bucket: String, id: String, uploadId: String, uploadedETags: Map[Int, String]): Unit = {
      val completedParts = uploadedETags.map {
        case (partNumber, eTag) => CompletedPart.builder().partNumber(partNumber).eTag(eTag).build()
      }.toList.sortBy(_.partNumber).asJava

      val completeRequest = CompleteMultipartUploadRequest.builder()
        .bucket(s3Bucket)
        .key(id)
        .uploadId(uploadId)
        .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
        .build()

      s3.completeMultipartUpload(completeRequest)
    }

    private def abortMultipartUpload(s3Bucket: String, id: String, uploadId: String): Unit = {
      val abortRequest = AbortMultipartUploadRequest.builder()
        .bucket(s3Bucket)
        .key(id)
        .uploadId(uploadId)
        .build()
      s3.abortMultipartUpload(abortRequest)
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

    def getObjectFromS3(s3bucket: String, key: String): String = {
      val getObjectRequest = GetObjectRequest.builder().bucket(s3bucket).key(key).build()
      val s3Object = s3.getObject(getObjectRequest)
      fromInputStream(s3Object).mkString
    }

    def listObjects(s3bucket: String): List[S3Object] = {
      listBucketObjects(s3bucket).toList
    }

    @tailrec
    def listBucketObjects(s3Bucket: String, results: Seq[S3Object] = Seq(), continuationToken: Option[String] = None): Seq[S3Object] = {
      val listObjectsRequest = ListObjectsV2Request.builder().bucket(s3Bucket)
      if (continuationToken.isDefined) listObjectsRequest.continuationToken(continuationToken.get)

      val result = s3.listObjectsV2(listObjectsRequest.build())
      val objects = results ++ result.contents().asScala

      if (result.isTruncated) {
        listBucketObjects(s3Bucket, objects, Some(result.nextContinuationToken()))
      } else {
        objects
      }
    }
  }
}