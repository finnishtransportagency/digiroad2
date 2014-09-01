package fi.liikennevirasto.digiroad2.mtk

import fi.liikennevirasto.digiroad2.asset.Point
import org.joda.time.{LocalDate, DateTime}
import scala.xml.parsing.ConstructingParser
import scala.io.Source
import org.joda.time.format.DateTimeFormat
import fi.liikennevirasto.digiroad2.mtk.MtkFormats.DateFormat

object MtkMessageParser {
  import scala.xml._
  val fmt = DateTimeFormat.forPattern(DateFormat)

  def toPoint(gmlList: String) = {
    val gmlToPoint = (x :List[Double]) => Point(x(0), x(1), x(2))
    gmlList.split(" ").toList.map(_.toDouble).grouped(3).map(gmlToPoint).toSeq
  }

  def toRoadLink(node: Node) = {
    val id = (node \\ "@gid").text
    val startDate = (node \\ "alkupvm").text
    val endDate = (node \\ "loppupvm").text
    val municipalityCode = (node \\ "kuntatunnus").text
    val points = toPoint((node \\ "posList").text)
    MtkRoadLink(id.toLong, DateTime.parse(startDate, fmt), if(endDate == "") None else Some(LocalDate.parse(endDate, fmt)), municipalityCode.toInt, points)
  }

  def parseMtkMessage(source: Source) = {
    val cpa = ConstructingParser.fromSource(source, false)
    val roadlinkNodes = cpa.document.docElem \\ "Tieviiva"
    val roadlinks = roadlinkNodes.map(toRoadLink)
    source.close()
    roadlinks
  }
}
