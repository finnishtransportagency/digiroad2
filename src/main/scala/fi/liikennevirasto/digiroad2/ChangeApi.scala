package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimit
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{BadRequest, ScalatraServlet}


class ChangeApi extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
    contentType = formats("json")
  }

  /**
    * Api that fetches changes for municipality after first adjusting to latest geometry from vvh
    */
  get("/speedLimits2") {
    val since = DateTime.parse(params("since"))
    params.get("municipality").map { municipality =>
      changesToApi(since, speedLimitService.get(municipality.toInt).filter(speedLimitIsChanged(since)))
    }.getOrElse {
      BadRequest("Missing mandatory 'municipality' parameter")
    }
  }

  /**
    * Api that simply fetches all changed speed limits from db
    */
  get("/speedLimits") {
    val since = DateTime.parse(params("since"))
    changesToApi(since, speedLimitService.getChanged(since))
  }

  private def speedLimitIsChanged(since: DateTime)(speedLimit: SpeedLimit) = {
    Seq(speedLimit.createdDateTime, speedLimit.modifiedDateTime)
      .flatten
      .exists(_.isAfter(since))
  }

  private def changesToApi(since: DateTime, speedLimits: Seq[SpeedLimit]) = {
    val (addedSpeedLimits, updatedSpeedLimits) = speedLimits.partition { speedLimit =>
      speedLimit.createdDateTime match {
        case None => false
        case Some(createdDate) => createdDate.isAfter(since)
      }
    }

    Map("added" -> addedSpeedLimits.map(speedLimitToApi),
      "updated"  -> updatedSpeedLimits.map(speedLimitToApi))
  }

  private def speedLimitToApi(speedLimit: SpeedLimit) = {
    Map("id" -> speedLimit.id,
      "value" -> speedLimit.value,
      "linkId" -> speedLimit.linkId,
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
