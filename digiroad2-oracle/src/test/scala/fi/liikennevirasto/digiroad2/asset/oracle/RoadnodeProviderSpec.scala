package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.Point
import org.scalatest._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import scala.collection.mutable

class RoadnodeProviderSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {

  val ds = OracleDatabase.initDataSource
  val point1 = Point(100.0, 200.0, 300.0)
  val point2 = Point(1001.0, 2002.0, 3003.0)

  //TODO: specify threshold on spatial relate match
  class TemporaryRoadNodes {
    val nodes = mutable.MutableList[Int]()

    def createRoadNode(point: Point) = {
      val nodeId = RoadnodeProvider.createRoadNode(point)
      nodes += nodeId
      nodeId
    }

    def cleanUp() = {
      nodes.map(_.toString).foreach(cleanDbForNodesWithId)
    }

    def cleanDbForNodesWithId(roadNodeId: String): Int = {
      (Q.u + s"delete from road_node where id in ($roadNodeId)").first()
    }
  }

  def withTemporaryRoadNodes(function: (TemporaryRoadNodes) => Unit) = {
    val nodes = new TemporaryRoadNodes
    try {
      function(nodes)
    } finally {
      nodes.cleanUp()
    }
  }

  it must "update node if id is given" in {
    Database.forDataSource(ds).withDynSession {
      withTemporaryRoadNodes(nodes => {
        val nodeId = nodes.createRoadNode(point1)
        RoadnodeProvider.getRoadnodeById(nodeId) must equal(Some(nodeId, point1))
        RoadnodeProvider.updateRoadNode(nodeId, point2)
        RoadnodeProvider.getRoadnodeById(nodeId) must equal(Some(nodeId, point2))
      })
    }
  }

  it must "insert new node, if no spatial node found" in {
    Database.forDataSource(ds).withDynSession {
      withTemporaryRoadNodes(nodes => {
        val idUt = getNextValueForNodeId
        val point = Point(999.0, 1000.0, 1111.0)
        val nodeId = nodes.createRoadNode(point)
        nodeId must equal(idUt)
        RoadnodeProvider.getRoadnodeById(nodeId) must equal(Some(nodeId, point))
      })
    }
  }

  it must "return node if found in spatial query" in {
    Database.forDataSource(ds).withDynSession {
      withTemporaryRoadNodes(nodes => {
        val idUt = getNextValueForNodeId
        val nodeId = nodes.createRoadNode(Point(2999.0, 4000.0, 111.90))
        nodeId must equal(idUt)
        val nodeId2 = nodes.createRoadNode(Point(2999.0, 4000.0, 111.90))
        nodeId2 must equal(nodeId)
      })
    }
  }

  private def getNextValueForNodeId = {
    import scala.slick.jdbc.StaticQuery.interpolation
    val currentValue = sql"select road_node_seq.nextval from dual".as[Int].first()
    currentValue + 1
  }
}