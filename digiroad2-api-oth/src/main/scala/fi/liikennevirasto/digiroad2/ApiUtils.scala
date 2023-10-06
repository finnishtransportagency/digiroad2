package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context.awsService
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.joda.time.DateTime

import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.json4s.DefaultFormats
import org.json4s.native.Json
import org.scalatra.{ActionResult, BadRequest, Found, InternalServerError, Params}
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.compat.Platform.EOL
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Random, Success}

object ApiUtils {
  val logger: Logger = LoggerFactory.getLogger(getClass)
  val s3Service: awsService.S3.type = awsService.S3
  val s3Bucket: String = Digiroad2Properties.apiS3BucketName
  val objectTTLSeconds: Int =
    if (Digiroad2Properties.apiS3ObjectTTLSeconds != null) Digiroad2Properties.apiS3ObjectTTLSeconds.toInt
    else 300

  def timer[R](operationName: String)(f: => R): R = LogUtils.time(logger, operationName)(f)
  
  val MAX_WAIT_TIME_SECONDS: Int = 20
  val MAX_RESPONSE_SIZE_BYTES: Long = 1024 * 1024 * 10 // 10Mb in bytes
  val MAX_RETRIES: Int = 540 // 3 hours / 20sec per retry

  /**
   * Avoid API Gateway restrictions
   * When using avoidRestrictions, do not hide errors but delegate these to avoidRestrictions method.
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
    val path = "/digiroad" + request.getRequestURI + queryString
    val workId = getWorkId(requestId, params, responseType) // Used to name s3 objects
    val queryId = params.get("queryId") match {             // Used to identify requests in logs
      case Some(id) => id
      case None =>
        val id = Integer.toHexString(new Random().nextInt)
        logger.info(s"API LOG $id: Received query $path at ${DateTime.now}")
        id
    }

    val objectExists = s3Service.isS3ObjectAvailable(s3Bucket, workId, 2, Some(objectTTLSeconds))

    (params.get("retry"), objectExists) match {
      case (_, true) =>
        val preSignedUrl = s3Service.getPreSignedUrl(s3Bucket, workId)
        redirectToUrl(preSignedUrl, queryId)

      case (None, false) =>
        try {
          newQuery(workId, queryId, path, f, params, responseType)
        } catch {
          case e: Throwable =>
            logger.error(s"API LOG $queryId: error with message ${e.getMessage} and stacktrace: ",e);
            InternalServerError(s"Request with id $queryId failed.")
        }
      case (Some(retry: String), false) =>
        val currentRetry = retry.toInt
        if (currentRetry <= MAX_RETRIES)
          redirectBasedOnS3ObjectExistence(workId, queryId, path, currentRetry)
        else {
          logger.info(s"API LOG $queryId: Maximum retries reached. Unable to respond to query.")
          BadRequest(s"Request with id $queryId failed. Maximum retries reached.")
        }
    }
  }

  /** Work id formed of request id (i.e. "integration") and query params */
  def getWorkId(requestId: String, params: Params, contentType: String): String = {
    val sortedParams = params.toSeq.filterNot(param => param._1 == "retry" || param._1 == "queryId").sortBy(_._1)
    val identifiers = Seq(requestId) ++ sortedParams.map(_._2.replaceAll(",", "-"))
    s"${identifiers.mkString("_")}.$contentType"
  }

  def newQuery[T](workId: String, queryId: String, path: String, f: Params => T, params: Params, responseType: String): Any = {
    val ret = Future {
      f(params)
    }
    try {
      val response = Await.result(ret, Duration.apply(MAX_WAIT_TIME_SECONDS, TimeUnit.SECONDS))
      
      response match {
        case _: ActionResult => response
        case _ =>
          val responseString = formatResponse(response, responseType, queryId)
          val responseSize = responseString.getBytes("utf-8").length
          if (responseSize < MAX_RESPONSE_SIZE_BYTES) {
            logger.info(s"API LOG $queryId: Completed the query at ${DateTime.now} without any redirects.")
            response
          }
          else {
            Future {
              s3Service.saveFileToS3(s3Bucket, workId, responseString, responseType)
            }.onComplete {
              case Failure(e) => 
                logger.error(s"API LOG $queryId: failed to save S3, stacktrace: ",e);
              case Success(t) => ""
                
            }
            redirectToUrl(path, queryId, Some(1))
          }
      }
    } catch {
      case _: TimeoutException =>
          ret.onComplete {
            case Failure(e) =>  logger.error(s"API LOG $queryId: error with message ${e.getMessage}, stacktrace: ",e);
            case Success(t) => 
              val responseBody = formatResponse(t, responseType, queryId)
              s3Service.saveFileToS3(s3Bucket, workId, responseBody, responseType)
          }
        redirectToUrl(path, queryId, Some(1))
    }
  }
  
  def formatResponse(content: Any, responseType: String,queryId: String): String = {
    (content, responseType) match {
      case (response: Seq[_], "json") =>
        timer(s"API LOG $queryId: convert to json"){
          Json(DefaultFormats).write(response.asInstanceOf[Seq[Map[String, Any]]])
        }
      case (response: Set[_], "json") =>
        timer(s"API LOG $queryId: convert to json"){
        Json(DefaultFormats).write(response.asInstanceOf[Set[Map[String, Any]]])
        }
      case (response: Map[_, _], "json") =>
        timer(s"API LOG $queryId: convert to json"){
        Json(DefaultFormats).write(response.asInstanceOf[Map[String, Any]])
        }
      case (response: mutable.LinkedHashMap[_, _], "json") =>
        timer(s"API LOG $queryId: convert to json"){
          Json(DefaultFormats).write(response.asInstanceOf[mutable.LinkedHashMap[String, Any]])
        }
      case _ =>
        throw new NotImplementedError("Unrecognized response format")
    }
  }

  def redirectToUrl(path: String, queryId: String, nextRetry: Option[Int] = None): ActionResult = {
    nextRetry match {
      case Some(retryValue) if retryValue == 1 =>
        val paramSeparator = if (path.contains("?")) "&" else "?"
        Found.apply(path + paramSeparator + s"queryId=$queryId&retry=$retryValue")
      case Some(retryValue) if retryValue > 1 =>
        val newPath = path.replaceAll("""retry=\d+""", s"retry=$retryValue")
        Found.apply(newPath)
      case _ =>
        logger.info(s"API LOG $queryId: Completed the query at ${DateTime.now}")
        Found.apply(path)
    }
  }

  @tailrec
  def objectAvailableInS3(workId: String, timeToQuery: Long): Boolean = {
    val startTime = System.currentTimeMillis()
    val s3ObjectAvailable = s3Service.isS3ObjectAvailable(s3Bucket, workId, timeToQuery, Some(objectTTLSeconds))
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

  def redirectBasedOnS3ObjectExistence(workId: String, queryId: String, path: String, currentRetry: Int): ActionResult = {
    // If object exists in s3, returns pre-signed url otherwise redirects to same url with incremented retry param
    val s3ObjectAvailable = objectAvailableInS3(workId, TimeUnit.SECONDS.toMillis(MAX_WAIT_TIME_SECONDS))
    if (s3ObjectAvailable) {
      val preSignedUrl = s3Service.getPreSignedUrl(s3Bucket, workId)
      redirectToUrl(preSignedUrl, queryId)
    } else {
      redirectToUrl(path, queryId, Some(currentRetry + 1))
    }
  }
}
