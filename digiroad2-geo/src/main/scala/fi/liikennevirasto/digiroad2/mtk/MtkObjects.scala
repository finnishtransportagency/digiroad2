package fi.liikennevirasto.digiroad2.mtk

import org.joda.time.{LocalDate, DateTime}
import java.text.{NumberFormat, DecimalFormat}
import java.util.Locale

case class MtkRoadLink(id: Long, startDate: DateTime, endDate: Option[LocalDate],
                       municipalityCode: Int, points: Seq[Point])
case class Point(x: Double, y: Double, z: Double)

object MtkRoadLinkUtils {
  private def pointToStoringItem(point: Point): String = {
    val decimalPattern = "#.000"
    val formatter = NumberFormat.getNumberInstance(Locale.US).asInstanceOf[DecimalFormat]
    formatter.applyPattern(decimalPattern)
    s"${formatter.format(point.x)}, ${formatter.format(point.y)}, ${formatter.format(point.z)}, null"
  }

  def fromPointListToStoringGeometry(roadlink: MtkRoadLink): String = {
    roadlink.points.map(pointToStoringItem).reduceLeft(_+ ", " +_)
  }
}

object MtkFormats {
  val DateFormat = "yyyy-MM-dd"
}
