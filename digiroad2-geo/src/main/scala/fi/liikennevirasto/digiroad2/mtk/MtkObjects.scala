package fi.liikennevirasto.digiroad2.mtk

import org.joda.time.{LocalDate, DateTime}
import java.text.DecimalFormat

case class MtkRoadLink(id: Long, startDate: DateTime, endDate: Option[LocalDate],
                       municipalityCode: Int, points: Seq[Point])
case class Point(x: Double, y: Double, z: Double)

object MtkRoadLinkUtils {
  private def pointToStoringItem(point: Point): String = {
    val formatter = new DecimalFormat("#.000")
    s"${formatter.format(point.x)}, ${formatter.format(point.y)}, ${formatter.format(point.z)}, null"
  }

  def fromPointListToStoringGeometry(roadlink: MtkRoadLink): String = {
    roadlink.points.map(pointToStoringItem).reduceLeft(_+ ", " +_)
  }
}

object MtkFormats {
  val dateFormat = "yyyy-MM-dd"
}
