package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.mtk.{MtkRoadLinkUtils, MtkRoadLink}
import fi.liikennevirasto.digiroad2.mtk.MtkFormats.DateFormat
import javax.sql.DataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import Q.interpolation
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

object RoadlinkProvider {
  private sealed abstract class RoadLinkExistence
  private case class RoadLinkDoNotExist(startNodeId: Int, endNodeId: Int) extends RoadLinkExistence
  private case object RoadLinkExist extends RoadLinkExistence

  var roadNodeProvider: NodeProvider = RoadnodeProvider

  private def getRoadlinkExistence(roadlink: MtkRoadLink, isEndDateGiven: Boolean): Option[RoadLinkExistence] = {
    val roadlinkId = roadlink.id
    import scala.slick.jdbc.StaticQuery.interpolation

    sql"""select start_road_node_id, end_road_node_id
          from road_link where id = $roadlinkId""".as[(Int, Int)].firstOption match {
      case Some((startNodeId, endNodeId)) =>
        {
          roadNodeProvider.updateRoadNode(startNodeId, roadlink.points.head)
          roadNodeProvider.updateRoadNode(endNodeId, roadlink.points.last)
          Some(RoadLinkExist)
        }
      case None => {
        if(isEndDateGiven) {
          None
        } else {
          val startNodeId = roadNodeProvider.createRoadNode(roadlink.points.head)
          val endNodeId = roadNodeProvider.createRoadNode(roadlink.points.last)
          Some(RoadLinkDoNotExist(startNodeId, endNodeId))
        }
      }
    }
  }

  private def getRoadlinkQOperation(existence: RoadLinkExistence, roadlink: MtkRoadLink): String = {
    val roadlinkId = roadlink.id
    val municipality_number = roadlink.municipalityCode
    val end_date = dateOptionToSqlDate(roadlink.endDate)
    existence match {
      case RoadLinkDoNotExist(startNodeId, endNodeId) =>
        s"""
          INSERT INTO
            road_link(id, road_type, functional_class,
                      municipality_number, start_road_node_id, end_road_node_id, geom)
          VALUES
            ($roadlinkId, 34, 1, $municipality_number, $startNodeId, $endNodeId, geometry);
        """.stripMargin
      case RoadLinkExist =>
        s"""
          UPDATE road_link r
          SET r.geom = geometry, municipality_number = $municipality_number, end_date = $end_date
          WHERE r.id = $roadlinkId;
        """.stripMargin
    }
  }

  private def dateOptionToSqlDate(date: Option[LocalDate]) : String = {
    date.map(formatDate).map(date => { s"""TO_DATE('$date', '$DateFormat')""" }).orNull
  }

  private def formatDate(date: LocalDate) : String = {
    DateTimeFormat.forPattern(DateFormat).print(date)
  }

  def updateRoadLink(ds: DataSource, roadlink: MtkRoadLink) {
    Database.forDataSource(ds).withDynSession {
      getRoadlinkExistence(roadlink, roadlink.endDate.isEmpty == false).foreach(existence => {
          val ordinates = MtkRoadLinkUtils.fromPointListToStoringGeometry(roadlink)
          val operation = getRoadlinkQOperation(existence, roadlink)

          val sql = s"""
              declare
              geometry MDSYS.SDO_GEOMETRY;

              begin

              geometry:=MDSYS.SDO_GEOMETRY(
                4402,
                3067,
                NULL,
                MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),
                MDSYS.SDO_ORDINATE_ARRAY($ordinates)
              );

              sdo_lrs.DEFINE_GEOM_SEGMENT_3D(geometry);
              $operation
              end;""".stripMargin

          (Q.u + sql).execute()
        })
    }
  }
}
