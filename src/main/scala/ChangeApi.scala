import fi.liikennevirasto.digiroad2.linearasset.SpeedLimit
import fi.liikennevirasto.digiroad2.linearasset.oracle.PersistedSpeedLimit
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._


class ChangeApi extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/speedLimits") {
    val since = DateTime.parse(params("since"))
    val (addedSpeedLimits, updatedSpeedLimits) = speedLimitService.getChanged(since).partition { speedLimit =>
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
