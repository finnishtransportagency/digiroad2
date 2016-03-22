package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport


class ChangeApi extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
    contentType = formats("json")
  }

  get("/speedLimits") {
    val since = DateTime.parse(params("since"))
    changedSpeedLimitsToApi(since, speedLimitService.getChanged(since))
  }

  private def changedSpeedLimitsToApi(since: DateTime, speedLimits: Seq[ChangedSpeedLimit]) = {
    val (addedSpeedLimits, updatedSpeedLimits) =
      speedLimits.partition { case ChangedSpeedLimit(speedLimit, link) =>
        speedLimit.createdDateTime match {
          case None => false
          case Some(createdDate) => createdDate.isAfter(since)
        }
      }

    Map("added" -> addedSpeedLimits.map(speedLimitToApi),
      "updated"  -> updatedSpeedLimits.map(speedLimitToApi))
  }

  private def speedLimitToApi(changedSpeedLimit: ChangedSpeedLimit) = {
    val ChangedSpeedLimit(speedLimit, link) = changedSpeedLimit

    Map("id" -> speedLimit.id,
      "value" -> speedLimit.value,
      "linkId" -> speedLimit.linkId,
      "linkGeometry" -> link.geometry,
      "linkFunctionalClass" -> link.functionalClass,
      "linkType" -> link.linkType,
      "sideCode" -> speedLimit.sideCode.value,
      "startMeasure" -> speedLimit.startMeasure,
      "endMeasure" -> speedLimit.endMeasure,
      "geometry" -> speedLimit.geometry,
      "createdBy" -> speedLimit.createdBy,
      "modifiedAt" -> speedLimit.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
      "createdAt" -> speedLimit.createdDateTime.map(DateTimePropertyFormat.print(_)),
      "modifiedBy" -> speedLimit.modifiedBy)
  }
}
