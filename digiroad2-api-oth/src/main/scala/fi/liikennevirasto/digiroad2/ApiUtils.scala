package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context.awsService
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.joda.time.DateTime
import org.json4s.DefaultFormats
import org.json4s.native.Json
import org.scalatra._
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Random, Success}
sealed trait HttpStatusCodeError {
  def value: Int
}

object HttpStatusCodeError {

  // --- 4xx Client Error ---
  case object BAD_REQUEST extends HttpStatusCodeError { val value = 400 }
  case object UNAUTHORIZED extends HttpStatusCodeError { val value = 401 }
  case object FORBIDDEN extends HttpStatusCodeError { val value = 403 }
  case object NOT_FOUND extends HttpStatusCodeError { val value = 404 }
  case object METHOD_NOT_ALLOWED extends HttpStatusCodeError { val value = 405 }
  case object NOT_ACCEPTABLE extends HttpStatusCodeError { val value = 406 }
  case object REQUEST_TIMEOUT extends HttpStatusCodeError { val value = 408 }
  case object REQUEST_TOO_LONG extends HttpStatusCodeError { val value = 413 }
  case object THROTTLING extends HttpStatusCodeError { val value = 429 }

  // --- 5xx Server Error ---
  case object INTERNAL_SERVER_ERROR extends HttpStatusCodeError { val value = 500 }
  case object BAD_GATEWAY extends HttpStatusCodeError { val value = 502 }
  case object SERVICE_UNAVAILABLE extends HttpStatusCodeError { val value = 503 }
  case object GATEWAY_TIMEOUT extends HttpStatusCodeError { val value = 504 }
}


object ReturnResponse{
  def error[T](queryId: String, e: DigiroadApiError,logger: Logger): ActionResult = {
    logger.error(s"API LOG $queryId: error with message ${e.msg} and stacktrace: ", e);

    e.httpCode match {
      case HttpStatusCodeError.BAD_REQUEST => BadRequest(e.msg)
      case HttpStatusCodeError.UNAUTHORIZED => Unauthorized(e.msg)
      case HttpStatusCodeError.FORBIDDEN => Forbidden(e.msg)
      case HttpStatusCodeError.NOT_FOUND => NotFound(e.msg)
      case HttpStatusCodeError.METHOD_NOT_ALLOWED => MethodNotAllowed(e.msg)
      case HttpStatusCodeError.NOT_ACCEPTABLE => NotAcceptable(e.msg)
      case HttpStatusCodeError.REQUEST_TIMEOUT => RequestTimeout(e.msg)
      case HttpStatusCodeError.REQUEST_TOO_LONG => RequestEntityTooLarge(e.msg)
      case HttpStatusCodeError.THROTTLING => TooManyRequests(e.msg)
      case HttpStatusCodeError.INTERNAL_SERVER_ERROR => InternalServerError(e.msg)
      case HttpStatusCodeError.BAD_GATEWAY => BadGateway(e.msg)
      case HttpStatusCodeError.SERVICE_UNAVAILABLE => ServiceUnavailable(e.msg)
      case HttpStatusCodeError.GATEWAY_TIMEOUT => GatewayTimeout(e.msg)
    }
  }
  
}

object RequestMiddleware {
  def prepare(request: HttpServletRequest) = {
    val queryString = if (request.getQueryString != null) s"?${request.getQueryString}" else ""
    val path = "/digiroad" + request.getRequestURI + queryString
    val id = Integer.toHexString(new Random().nextInt)
    (path, id)
  }
  /**
    * Small middleware which handle request. For security reason for unknown exception return only HTTP 500.
    * For handling validation exception throw [[DigiroadApiError]].
    * @param title Header when logging.
    */
  def handleRequest[T](params: Params, logger: Logger,request: HttpServletRequest, title:String = "API LOG")(f: Params => T): Any = {
    val (path: String, id: String) = prepare(request)
    try {f(params)} 
    catch { // handle imminent error
      case e: DigiroadApiError => ReturnResponse.error(id, e, logger)
      case e: Throwable =>
        logger.error(s"$title Received query $path $id: error with message ${e.getMessage} and stacktrace: ", e);
        InternalServerError(s"Request with id $id failed.")
    }
  }

  /**
    * Small middleware which handle request and log it. For security reason for unknown exception return only HTTP 500.
    * For handling validation exception throw [[DigiroadApiError]].
    *
    * @param title Header when logging.
    */
  def handleRequestLog[T](params: Params, logger: Logger, request: HttpServletRequest, title: String = "API LOG")(f: Params => T): Any = {
    val (path: String, id: String) = prepare(request)
    logger.info(s"$title $id: Received query $path at ${DateTime.now}")
    try {
      val r = f(params)
      logger.info(s"$title $id: Completed the query at ${DateTime.now}")
      r
    } catch { // handle imminent error
      case e: DigiroadApiError => ReturnResponse.error(id, e, logger)
      case e: Throwable =>
        logger.error(s"$title $id: error with message ${e.getMessage} and stacktrace: ", e);
        InternalServerError(s"Request with id $id failed.")
    }
  }
}

case class DigiroadApiError(httpCode:HttpStatusCodeError, msg:String) extends Throwable(msg)
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
   * For handling validation exception throw [[DigiroadApiError]].
   * API Gateway timeouts if response is not received in 30 sec
   *  -> Return redirect to same url with retry param if query is not finished within maxWaitTime
   *  -> Save response to S3 when its ready (access with pre-signed url)
   * API Gateway maximum response body size is 10 Mb
   *  -> Save bigger responses to S3 (access with pre-signed url)
   */
  def avoidRestrictions[T](requestId: String, request: HttpServletRequest, params: Params,
                           responseType: String = "json")(f: Params => T): Any = {
    if (!Digiroad2Properties.awsConnectionEnabled) return  RequestMiddleware.handleRequestLog(params,logger,request){f}
    val (path: String, id: String) = RequestMiddleware.prepare(request)
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
        try {newQuery(workId, queryId, path, f, params, responseType)} catch {
          // handle imminent error
          case e: DigiroadApiError => ReturnResponse.error(queryId, e,logger)
          case e: Throwable => 
            logger.error(s"API LOG $queryId: error with message ${e.getMessage} and stacktrace: ", e);
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
              case Failure(e) => saveError(workId, queryId, e)
              case Success(t) => ""
            }
            redirectToUrl(path, queryId, Some(1))
          }
      }
    } catch {
      case _: TimeoutException =>
          ret.onComplete {
            case Failure(e) => saveError(workId, queryId, e)
            case Success(t) => 
              val responseBody = formatResponse(t, responseType, queryId)
              s3Service.saveFileToS3(s3Bucket, workId, responseBody, responseType)
          }
        redirectToUrl(path, queryId, Some(1))
    }
  }

  private def saveError[T](workId: String, queryId: String, e: Throwable): Unit = {
    logger.error(s"API LOG $queryId: error with message ${e.getMessage}, stacktrace: ", e);
    // Save the error message to S3, so that the caller gets the error feedback from there,
    // and does not stay stuck waiting forever in the case of a failure.
    s3Service.saveFileToS3(s3Bucket, workId, s"Request with id $queryId failed.", "text/plain")
  }
  def formatResponse(content: Any, responseType: String, queryId: String): String = {
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
