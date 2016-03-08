import fi.liikennevirasto.digiroad2.AuthenticationSupport
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimit
import fi.liikennevirasto.digiroad2.linearasset.oracle.PersistedSpeedLimit
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._


class ChangeApi extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
    contentType = formats("json")
  }

  get("/speedLimits2") {
    val since = DateTime.parse(params("since"))
    params.get("municipality").map { municipality =>
      changesToApi(since, speedLimitService.get(municipality.toInt).filter(speedLimitIsChanged(since)))
    }.getOrElse {
      BadRequest("Missing mandatory 'municipality' parameter")
    }
  }

  private def speedLimitIsChanged(since: DateTime)(speedLimit: SpeedLimit) = {
    Seq(speedLimit.createdDateTime, speedLimit.modifiedDateTime)
      .flatten
      .exists(_.isAfter(since))
  }

  get("/speedLimits") {
    val since = DateTime.parse(params("since"))
    changesToApi(since, speedLimitService.getChanged(since))
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
