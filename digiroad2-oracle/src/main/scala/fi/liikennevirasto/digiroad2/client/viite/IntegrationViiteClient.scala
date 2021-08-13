package fi.liikennevirasto.digiroad2.client.viite

import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.impl.client.CloseableHttpClient
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import java.util.Date

case class ChangeInformation(roadwayChangeId: String,
                             changeType: String, reversed: String,
                             oldRoadNumbering: Source, newRoadNumbering: Target,
                             changeDate: Option[Date], creationDate: Option[Date])

case class Source(roadNumber: Long, roadPartNumber: Long,
                  track: Track, startAddrMValue: Long,
                  endAddrMValue: Long, discontinuity: String,
                  roadType: String, administrativeValues: AdministrativeClass, ely: String)

case class Target(roadNumber: Long, roadPartNumber: Long,
                  track: Track, startAddrMValue: Long,
                  endAddrMValue: Long, discontinuity: String,
                  roadType: String, administrativeValues: AdministrativeClass, ely: String)

class IntegrationViiteClient(viiteUrl: String, httpClient: CloseableHttpClient) extends ViiteClientOperations {

  override type ViiteType = ChangeInformation

  override protected def client: CloseableHttpClient = httpClient

  override protected def restApiEndPoint: String = viiteUrl

  override protected def serviceName: String = viiteUrl + "integration/"

  override protected def mapFields(data: Map[String, Any]): Option[ChangeInformation] = ???

  private def formatDateTimeToIsoString(dateOption: Option[DateTime]): Option[String] = {
    val formatterNoMillis = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")
    dateOption.map { date => formatterNoMillis.print(date) }
  }

  def fetchRoadwayChangesChanges(since: DateTime, until: DateTime = new DateTime()): List[ChangeInformation] = {
    get[Map[String, Map[String, Any]]](serviceName + "roadway_changes/changes?since=" +
      formatDateTimeToIsoString(Some(since)).get + "&until="
      + formatDateTimeToIsoString(Some(until)).get) match {
      case Left(changes) => mapFields(changes)
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  override protected def mapFields[A](data: A): Option[List[ChangeInformation]] = {
    val changes = getFieldGeneric[List[Map[String, Any]]](data.asInstanceOf[Map[String, Any]], "muutos_tieto").get
    if (changes.size >= 0) {
      Option(
        changes.map(data => {
          val sourceMap = getFieldGeneric[Map[String, Any]](data, "kohde").get
          val sourceObject = Source(
            getFieldValue(sourceMap, "tie").get.toLong,
            getFieldValue(sourceMap, "osa").get.toLong,
            Track(getFieldValue(sourceMap, "ajorata").get.toInt),
            getFieldValue(sourceMap, "etaisyys").get.toLong,
            getFieldValue(sourceMap, "etaisyys_loppu").get.toLong,
            getFieldValue(sourceMap, "jatkuvuuskoodi").get,
            getFieldValue(sourceMap, "tietyyppi").get,
            AdministrativeClass(getFieldValue(sourceMap, "hallinnollinen_luokka").get.toInt),
            getFieldValue(sourceMap, "ely").get)

          val targetMap = getFieldGeneric[Map[String, Any]](data, "lahde").get

          val targetObject = Target(
            getFieldValue(targetMap, "tie").get.toLong,
            getFieldValue(targetMap, "osa").get.toLong,
            Track(getFieldValue(targetMap, "ajorata").get.toInt),
            getFieldValue(targetMap, "etaisyys").get.toLong,
            getFieldValue(targetMap, "etaisyys_loppu").get.toLong,
            getFieldValue(targetMap, "jatkuvuuskoodi").get,
            getFieldValue(targetMap, "tietyyppi").get,
            AdministrativeClass(getFieldValue(targetMap, "hallinnollinen_luokka").get.toInt),
            getFieldValue(targetMap, "ely").get)

          ChangeInformation(
            getFieldValue(data, "muutostunniste").get,
            getFieldValue(data, "muutostyyppi").get,
            getFieldValue(data, "kaannetty").get
            , sourceObject, targetObject,
            convertToDate(getFieldValue(data, "muutospaiva")),
            convertToDate(getFieldValue(data, "laatimisaika"))
          )
        }))
    } else {
      None
    }
  }

}
