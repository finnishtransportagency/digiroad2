package fi.liikennevirasto.digiroad2.client

import com.vividsolutions.jts.geom.GeometryFactory
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.geotools.geometry.jts.GeometryBuilder
import org.scalatest.{FunSuite, Matchers}

class RoadLinkClientSpec extends FunSuite with Matchers{

  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)
  
  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Fetch changes with polygon string ") {
    val roadLinkClient= new RoadLinkClient()
    val result= roadLinkClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon(528428,6977212,543648,6977212,543648,7002668,528428,7002668))
    result.size should be >1
  }
  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Empty polygon should not return anything") {
    val roadLinkClient= new RoadLinkClient()
    val result= roadLinkClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon())
    result.size should be (0)
  }
  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Fetch changes with by bounding box and municipalities") {
    val roadLinkClient = new RoadLinkClient()
    val result= roadLinkClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalities(BoundingRectangle(Point(532578.3338013917,6993401.605560873,0.0),Point(532978.3338013917,6994261.605560873,0.0)), Set.empty[Int])
    result.size should be >1
  }

  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Test Change Info fetch by LinkId") {
    val roadLinkClient = new RoadLinkClient()
    val result = roadLinkClient.roadLinkChangeInfo.fetchByLinkIds(Set("5176799"))
    result.nonEmpty should be (true)
  }
}

