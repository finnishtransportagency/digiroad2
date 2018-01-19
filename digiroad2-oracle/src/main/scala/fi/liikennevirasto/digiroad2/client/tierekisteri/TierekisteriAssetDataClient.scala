package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.Track
import org.joda.time.DateTime

trait TierekisteriAssetData {
  val roadNumber: Long
  val startRoadPartNumber: Long
  val endRoadPartNumber: Long
  val startAddressMValue: Long
  val endAddressMValue: Long
  val track: Track
}

trait TierekisteriAssetDataClient extends TierekisteriClient {

  private val serviceName = "tietolajit/"
  protected val trRoadNumber = "TIE"
  protected val trRoadPartNumber = "OSA"
  protected val trEndRoadPartNumber = "LOSA"
  protected val trStartMValue = "ETAISYYS"
  protected val trEndMValue = "LET"
  protected val trTrackCode = "AJORATA"
  protected def trAssetType : String

  private val serviceUrl : String = tierekisteriRestApiEndPoint + serviceName

  override type TierekisteriType <: TierekisteriAssetData

  def queryString(changeDate: Option[DateTime]) : String = {
    changeDate match {
      case Some(value) => "?muutospvm="+changeDate.get.toString("yyyy-MM-dd") + "%20" + changeDate.get.toString("hh:mm:ss")
      case _ => ""
    }
  }

  private def serviceUrl(assetType: String, roadNumber: Long) : String = serviceUrl + assetType + "/" + roadNumber
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long) : String = serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + "/" + endPart + "/" + endDistance

  private def serviceHistoryUrl(assetType: String, roadNumber: Long, changeDate:  Option[DateTime]) : String = serviceUrl + assetType + "/" + roadNumber + queryString(changeDate)
  private def serviceHistoryUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, changeDate: Option[DateTime]) : String = serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + queryString(changeDate)
  private def serviceHistoryUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, changeDate: Option[DateTime]) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + queryString(changeDate)
  private def serviceHistoryUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int, changeDate: Option[DateTime]) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + "/" + endPart + "/" + endDistance + queryString(changeDate)

  /**
    * Return all asset data currently active from Tierekisteri
    * Tierekisteri REST API endpoint: GET /trrest/tietolajit/{tietolaji}/{tie}
    *
    * @return
    */
  def fetchActiveAssetData(roadNumber: Long): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber)) match {
      case Left(content) => {
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(roadNumber: Long, roadPartNumber: Long): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber, roadPartNumber)) match {
      case Left(content) =>
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber, roadPartNumber, startDistance)) match {
      case Left(content) =>
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber, roadPartNumber, startDistance, endPart, endDistance)) match {
      case Left(content) =>
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, changeDate:  Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, changeDate)) match {
      case Left(content) => {
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, roadPartNumber: Long, changeDate: Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, roadPartNumber, changeDate)) match {
      case Left(content) =>
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int, changeDate: Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, roadPartNumber, startDistance, changeDate)) match {
      case Left(content) =>
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int, changeDate: Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, roadPartNumber, startDistance, endPart, endDistance, changeDate)) match {
      case Left(content) =>
        content("Data").flatMap{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }
}

