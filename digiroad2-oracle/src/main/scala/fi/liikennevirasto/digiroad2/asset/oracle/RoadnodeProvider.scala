package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.Point
import oracle.spatial.geometry.JGeometry
import scala.slick.jdbc.{PositionedResult, GetResult}
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

trait NodeProvider {
  def createRoadNode(point: Point): Int
  def updateRoadNode(id: Int, updateTo: Point)
}

object RoadnodeProvider extends NodeProvider {
  import scala.slick.jdbc.StaticQuery.interpolation

  def updateRoadNode(id: Int, updateTo: Point) {
    sqlu"""update road_node
           set geom =
                   SDO_GEOMETRY(3001, 3067,
                                MDSYS.SDO_POINT_TYPE(${updateTo.x}, ${updateTo.y}, ${updateTo.z}),
                                NULL, NULL)
           where id = $id""".execute()
  }

  def createRoadNode(point: Point) = {
    queryNodeInSpatial(point) match {
      case Some(id) => id
      case None => insertNode(point)
    }
  }

  private def queryNodeInSpatial(point: Point) = {
    sql"""select id from road_node
            where mdsys.sdo_relate(road_node.geom, mdsys.SDO_GEOMETRY(
            3001,
            3067,
            MDSYS.SDO_POINT_TYPE(${point.x}, ${point.y}, ${point.z}),
            NULL,
            NULL), 'mask=equal querytype=WINDOW') = 'TRUE'""".as[Int].firstOption
  }

  private def insertNode(point: Point) = {
    sqlu"""insert into road_node
           values (road_node_seq.nextval, 1,
                   SDO_GEOMETRY(3001, 3067,
                                MDSYS.SDO_POINT_TYPE(${point.x}, ${point.y}, ${point.z}),
                                NULL, NULL))""".execute()

    sql"select road_node_seq.currval from dual".as[Int].first()
  }

  implicit val getRoadNode = new GetResult[(Int, Point)] {
    def apply(r: PositionedResult) = {
      val (id, geomBytes) = (r.nextInt(), r.nextBytes())
      val point = JGeometry.load(geomBytes).getPoint()
      (id, Point(point(0), point(1), point(2)))
    }
  }

  def getRoadnodeById(nodeId: Int) = {
    sql"select id, geom from road_node where id = $nodeId".as[(Int, Point)].firstOption
  }
}

