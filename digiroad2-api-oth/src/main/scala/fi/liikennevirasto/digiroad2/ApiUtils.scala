package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context.awsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.json4s.DefaultFormats
import org.json4s.native.Json
import org.scalatra.{ActionResult, BadRequest, Found, Params}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global

object ApiUtils {
  val logger: Logger = LoggerFactory.getLogger(getClass)
  val s3Service: awsService.S3.type = awsService.S3
  val s3Bucket: String = Digiroad2Properties.apiS3BucketName

  val MAX_WAIT_TIME_SECONDS: Int = 20
  val MAX_RESPONSE_SIZE_BYTES: Long = 1024 * 1024 * 10 // 10Mb in bytes
  val MAX_RETRIES: Int = 540 // 3 hours / 20sec per retry
  val objectModifiedWithinHours: Int = 12

  /**
   * Avoid API Gateway restrictions
   * API Gateway timeouts if response is not received in 30 sec
   *  -> Return redirect to same url with retry param if query is not finished within maxWaitTime
   *  -> Save response to S3 when its ready (access with pre-signed url)
   * API Gateway maximum response body size is 10 Mb
   *  -> Save bigger responses to S3 (access with pre-signed url)
   */
  def avoidRestrictions[T](requestId: String, request: HttpServletRequest, params: Params,
                           responseType: String = "json")(f: Params => T): Any = {
    if (!Digiroad2Properties.awsConnectionEnabled) return f(params)

    val queryString = if (request.getQueryString != null) s"?${request.getQueryString}" else ""
    val fullPath = request.getPathInfo + queryString
    val path = fullPath.substring(fullPath.lastIndexOf("/") + 1)
    val workId = getWorkId(requestId, params, responseType)
    val objectExists = s3Service.isS3ObjectAvailable(s3Bucket, workId, 2, Some(objectModifiedWithinHours))

    (params.get("retry"), objectExists) match {
      case (_, true) =>
        val preSignedUrl = s3Service.getPreSignedUrl(s3Bucket, workId)
        redirectToUrl(preSignedUrl)

      case (None, false) =>
        newQuery(workId, path, f, params, responseType)

      case (Some(retry: String), false) =>
        val currentRetry = retry.toInt
        if (currentRetry <= MAX_RETRIES)
          redirectBasedOnS3ObjectExistence(workId, path, currentRetry)
        else
          BadRequest("Maximum retries reached. Unable to get object.")
    }
  }

  /** Work id formed of request id (i.e. "integration") and query params */
  def getWorkId(requestId: String, params: Params, contentType: String): String = {
    val sortedParams = params.toSeq.filterNot(_._1 == "retry").sortBy(_._1)
    val identifiers = Seq(requestId) ++ sortedParams.map(_._2.replaceAll(",", "-"))
    s"${identifiers.mkString("_")}.$contentType"
  }

  def newQuery[T](workId: String, path: String, f: Params => T, params: Params, responseType: String): Any = {
    val ret = Future { f(params) }
    try {
      val response = Await.result(ret, Duration.apply(MAX_WAIT_TIME_SECONDS, TimeUnit.SECONDS))
      response match {
        case _: ActionResult => response
        case _ =>
          val responseString = formatResponse(response, responseType)
          val responseSize = responseString.getBytes("utf-8").length
          if (responseSize < MAX_RESPONSE_SIZE_BYTES) response
          else {
            Future { s3Service.saveFileToS3(s3Bucket, workId, responseString, responseType) }
            redirectToUrl(path, Some(1))
          }
      }
    } catch {
      case _: TimeoutException =>
        Future { // Complete query and save results to s3 in future
          val finished = Await.result(ret, Duration.Inf)
          val responseBody = formatResponse(finished,  responseType)
          s3Service.saveFileToS3(s3Bucket, workId, responseBody, responseType)
        }
        redirectToUrl(path, Some(1))
    }
  }

  def formatResponse(content: Any, responseType: String): String = {
    (content, responseType) match {
      case (response: Seq[_], "json") =>
        Json(DefaultFormats).write(response.asInstanceOf[Seq[Map[String, Any]]])
      case (response: Set[_], "json") =>
        Json(DefaultFormats).write(response.asInstanceOf[Set[Map[String, Any]]])
      case (response: Map[_, _], "json") =>
        Json(DefaultFormats).write(response.asInstanceOf[Map[String, Any]])
      case _ =>
        throw new NotImplementedError("Unrecognized response format")
    }
  }

  def redirectToUrl(path: String, nextRetry: Option[Int] = None): ActionResult = {
    nextRetry match {
      case Some(retryValue) if retryValue == 1 =>
        val paramSeparator = if (path.contains("?")) "&" else "?"
        Found.apply(path + paramSeparator + s"retry=$retryValue")
      case Some(retryValue) if retryValue > 1 =>
        val newPath = path.replaceAll("""retry=\d+""", s"retry=$retryValue")
        Found.apply(newPath)
      case _ =>
        Found.apply(path)
    }
  }

  def objectAvailableInS3(workId: String, timeToQuery: Long): Boolean = {
    val startTime = System.currentTimeMillis()
    val s3ObjectAvailable = s3Service.isS3ObjectAvailable(s3Bucket, workId, timeToQuery, Some(objectModifiedWithinHours))
    if (s3ObjectAvailable) true
    else {
      val endTime = System.currentTimeMillis()
      val timeLeft = timeToQuery - (endTime - startTime)
      val millisToNextQuery = 2000
      if (timeLeft > millisToNextQuery) {
        Thread.sleep(millisToNextQuery)
        objectAvailableInS3(workId, timeLeft - millisToNextQuery)
      } else false
    }
  }

  def redirectBasedOnS3ObjectExistence(workId: String, path: String, currentRetry: Int): ActionResult = {
    // If object exists in s3, returns pre-signed url otherwise redirects to same url with incremented retry param
    val s3ObjectAvailable = objectAvailableInS3(workId, TimeUnit.SECONDS.toMillis(MAX_WAIT_TIME_SECONDS))
    if (s3ObjectAvailable) {
      val preSignedUrl = s3Service.getPreSignedUrl(s3Bucket, workId)
      redirectToUrl(preSignedUrl)
    } else {
      redirectToUrl(path, Some(currentRetry + 1))
    }
  }
}
