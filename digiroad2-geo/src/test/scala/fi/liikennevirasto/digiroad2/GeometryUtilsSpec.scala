package fi.liikennevirasto.digiroad2

import com.vividsolutions.jts.geom.LineSegment
import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.clusterLinks
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import org.geotools.graph.build.line.BasicLineGraphGenerator
import org.geotools.graph.structure.Graph
import org.geotools.graph.structure.basic.{BasicEdge, BasicGraph}
import org.joda.time.DateTime
import org.scalatest._

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.math.Ordering.Implicits.seqDerivedOrdering

class GeometryUtilsSpec extends FunSuite with Matchers {
  test("truncate empty geometry") {
    val truncated = truncateGeometry3D(Nil, 10, 15)
    truncated should be (Nil)
  }

  test("truncation fails when start measure is after end measure") {
    an [IllegalArgumentException] should be thrownBy truncateGeometry3D(Nil, 15, 10)
  }

  test("truncation fails on one point geometry") {
    an [IllegalArgumentException] should be thrownBy truncateGeometry3D(Seq(Point(0.0, 0.0)), 10, 15)
  }

  test("truncate geometry from beginning") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 6, 10)
    truncatedGeometry should be (Seq(Point(6.0, 0.0), Point(10.0, 0.0)))
  }

  test("truncate geometry from end") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 0, 6)
    truncatedGeometry should be (Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(6.0, 0.0)))
  }

  test("truncate geometry from beginning and end") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 2, 6)
    truncatedGeometry should be (Seq(Point(2.0, 0.0), Point(5.0, 0.0), Point(6.0, 0.0)))
  }

  test("truncate geometry where start and end point are on the same segment") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 2, 3)
    truncatedGeometry should be (Seq(Point(2.0, 0.0), Point(3.0, 0.0)))
  }

  test("truncate geometry where start and end point are outside geometry") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0)), 11.0, 15.0)
    truncatedGeometry should be(empty)
  }

  test("splitting fails if measure is not within link segment") {
    val link1 = (0.0, 100.0)
    intercept[IllegalArgumentException] {
      createSplit(105.0, link1)
    }
  }

  test("splitting one link speed limit") {
    val link1 = (0.0, 100.0)
    val (existingLinkMeasures, createdLinkMeasures) = createSplit(40.0, link1)

    existingLinkMeasures shouldBe(40.0, 100.0)
    createdLinkMeasures shouldBe(0.0, 40.0)
  }

  test("subtract contained interval from intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (4.0, 5.0))
    result shouldBe Seq((3.0, 4.0), (5.0, 6.0))
  }

  test("subtract outlying interval from intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (1.0, 2.0))
    result shouldBe Seq((3.0, 6.0))
  }

  test("subtract interval from beginning of intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (2.0, 4.0))
    result shouldBe Seq((4.0, 6.0))
  }

  test("subtract interval from end of intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (5.0, 7.0))
    result shouldBe Seq((3.0, 5.0))
  }

  test("subtract containing interval from intervals") {
    val result = subtractIntervalFromIntervals(Seq((3.0, 6.0)), (2.0, 7.0))
    result shouldBe Seq()
  }

  test("Calculate linear reference point") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val point: Point = calculatePointFromLinearReference(linkGeometry, 0.5).get
    point.x should be(0.5)
    point.y should be(0.0)
  }

  test("Calculate linear reference point on three-point geometry") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val point: Point = calculatePointFromLinearReference(linkGeometry, 1.5).get
    point.x should be(1.0)
    point.y should be(0.5)
  }

  test("Linear reference point on less than two-point geometry should be undefined") {
    val linkGeometry = Nil
    val point: Option[Point] = calculatePointFromLinearReference(linkGeometry, 1.5)
    point should be(None)
  }

  test("Linear reference point on negative measurement should be undefined") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val point: Option[Point] = calculatePointFromLinearReference(linkGeometry, -1.5)
    point should be(None)
  }

  test("Linear reference point outside geometry should be undefined") {
    val linkGeometry = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val point: Option[Point] = calculatePointFromLinearReference(linkGeometry, 1.5)
    point should be(None)
  }

  test("Calculate length of two point geometry") {
    val geometry = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val length: Double = geometryLength(geometry)
    length should be(1.0)
  }

  test("Calculate length of three point geometry") {
    val geometry = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val length: Double = geometryLength(geometry)
    length should be(2.0)
  }

  test("Return zero length on empty geometry") {
    val length: Double = geometryLength(Nil)
    length should be(0.0)
  }

  test("Return zero length on one-point geometry") {
    val length: Double = geometryLength(List(Point(0.0, 0.0)))
    length should be(0.0)
  }

  test("Minimum distance to line segment") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0))
    val distance = minimumDistance(Point(0.0, 0.0), segment)
    distance should be(.4472135954999579)
  }

  test("Minimum distance to line segment, close to segment start") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0))
    val distance = minimumDistance(Point(-2.0, 0.0), segment)
    distance should be(1)
  }

  test("Minimum distance to line segment, close to segment end") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0))
    val distance = minimumDistance(Point(1.0, 1.5), segment)
    distance should be(.5)
  }

  test("Minimum distance to line segment, multiple segments") {
    val segment = Seq(Point(-1.0, 0.0), Point(1.0, 1.0), Point(2.0, 1.0))
    val distance = minimumDistance(Point(1.5, 1.1), segment)
    distance should be >= .0999
    distance should be <= .1001
  }

  test("Get minimum distance from point to segment") {
    val distance = minimumDistance(Point(0,0,0), (Point(-1,1,0), Point(1,1,0)))
    distance should be (1.0)
  }

  test("Get minimum distance from point to segment end") {
    val distance = minimumDistance(Point(0,0,0), (Point(-1,-1,0), Point(-.5,-.5,0)))
    distance should be > .707
    distance should be < .70711
  }

  test("Get minimum distance from point to segment midpoint") {
    val distance = minimumDistance(Point(0,0,0),
      segmentByMinimumDistance(Point(0,0,0), Seq(Point(-1,1,0), Point(0,.9,0), Point(1,1,0))))
    distance should be(0.9)
  }

  test("overlap cases") {
    overlaps((0.0, 0.1), (0.1,0.2)) should be(false)
    overlaps((0.0, 0.15), (0.1,0.2)) should be(true)
    overlaps((0.11, 0.15), (0.1,0.2)) should be(true)
    overlaps((0.15, 0.11), (0.1,0.2)) should be(true)
    overlaps((0.15, 0.21), (0.2,0.1)) should be(true)
    overlaps((0.21, 0.01), (0.1,0.2)) should be(true)
    overlaps((0.21, 0.01), (0.1,0.2)) should be(true)
    overlaps((0.21, 0.22), (0.1,0.2)) should be(false)
    overlaps((0.22, 0.21), (0.1,0.2)) should be(false)
  }

  test("within tolerance") {
    val p1 = Point(0,0)
    val p2 = Point(1,1)
    val p3 = Point(1.5,1.5)
    val p4 = Point(1.01,.99)
    withinTolerance(Seq(p1, p2), Seq(p1, p2), 0.0001) should be (true)
    withinTolerance(Seq(p1, p2), Seq(p1, p3), 0.0001) should be (false)
    withinTolerance(Seq(p1, p2), Seq(p2, p1), 0.0001) should be (false)
    withinTolerance(Seq(p1, p2), Seq(p1, p3), 1.0001) should be (true)
    withinTolerance(Seq(p1, p2), Seq(p1, p4), .0001) should be (false)
    withinTolerance(Seq(p1, p2), Seq(p1, p4), .015) should be (true)
    withinTolerance(Seq(p1), Seq(p1, p4), .0001) should be (false)
    withinTolerance(Seq(), Seq(p1, p4), .0001) should be (false)
    withinTolerance(Seq(p1), Seq(p1), .0001) should be (true)
    withinTolerance(Seq(p1), Seq(), .0001) should be (false)
    withinTolerance(Seq(), Seq(), .0001) should be (true)
  }

  test("truncation calculates 3 dimensional LRM distances as lengths on map") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 0.0, 0.0), Point(5.0, 0.0, 5.0), Point(10.0, 0.0, 2.0)), 6, 10)
    truncatedGeometry.map(_.copy(z = 0.0)) should be (Seq(Point(6.0, 0.0), Point(10.0, 0.0)))
  }

  test("truncation in 2 dimensions is not affected") {
    val truncatedGeometry = truncateGeometry3D(Seq(Point(0.0, 5.0), Point(0.0, 0.0), Point(5.0, 0.0)), 6, 10)
    truncatedGeometry should be (Seq(Point(1.0, 0.0), Point(5.0, 0.0)))
  }

  test("geometry moved more over 1 meter on road end"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(5.0, 5.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (true)
  }

  test("geometry moved more over 1 meter on road start"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(5.0, 1.0), Point(1.0, 0.0), Point(1.0, 1.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (true)
  }

  test("geometry moved less than 1 meter"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(0.5, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (false)
  }

  test("geometry remains the same"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (false)
  }

  test("geometry is reversed"){
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0))
    val geometry2 = List(Point(1.0, 1.0), Point(1.0, 0.0), Point(0.0, 0.0))
    geometryMoved(1.0)(geometry1, geometry2) should be (true)
  }

  test("calculate middle point of multiple geometries") {
    val geometry1 = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0))
    val geometry2 = List(Point(2.0, 0.0), Point(2.0, 1.0), Point(2.0, 2.0))
    val geometry3 = List(Point(2.0, 2.0), Point(1.0, 2.0), Point(0.0, 2.0))
    val geometry4 = List(Point(0.0, 2.0), Point(0.0, 1.0), Point(0.0, 0.0))

    val point = GeometryUtils.middlePoint(Seq(geometry1, geometry2, geometry3, geometry4))

    point.x should be(1)
    point.y should be(1)
  }

  test("calculate angle of a vector") {
    val center = Point(5,15)
    val p1 = Point(10, 20)
    val p2 = Point(0,20)
    val p3 = Point(0,10)
    val p4 = Point(10,10)

    Math.toDegrees(GeometryUtils.calculateAngle(p1, center)) should be(45)
    Math.toDegrees(GeometryUtils.calculateAngle(p2, center)) should be(135)
    Math.toDegrees(GeometryUtils.calculateAngle(p3, center)) should be(225)
    Math.toDegrees(GeometryUtils.calculateAngle(p4, center)) should be(315)
  }

  test("Project stop location on two-point geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val location: Point = Point(0.5, 0.5)
    val mValue: Double = GeometryUtils.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(0.5)
  }

  test("Project stop location on three-point geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 0.5))
    val location: Point = Point(1.2, 0.25)
    val mValue: Double = GeometryUtils.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(1.25)
  }

  test("Project stop location to beginning of geometry if point lies behind geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val location: Point = Point(-0.5, 0.0)
    val mValue: Double = GeometryUtils.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(0.0)
  }

  test("Project stop location to the end of geometry if point lies beyond geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val location: Point = Point(1.5, 0.5)
    val mValue: Double = GeometryUtils.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(1.0)
  }

  test("Test bearing calculation on Roadlink only with two points, without asset mValue") {
    val linkGeometry: Seq[Point] = List(Point(1.0, 1.0), Point(5.0, 5.0))
    val bearingValue = GeometryUtils.calculateBearing(linkGeometry)
    bearingValue should be(45.0)
  }

  test("Test bearing calculation on Roadlink only with two points, with asset mValue") {
    val linkGeometry: Seq[Point] = List(Point(10.0, 10.0), Point(50.0, 50.0))
    val assetPosition: Point = Point(20.0, 20.0)
    val mValue: Double = GeometryUtils.calculateLinearReferenceFromPoint(assetPosition, linkGeometry)
    val bearingValue = GeometryUtils.calculateBearing(linkGeometry, Some(mValue))
    bearingValue should be(45.0)
  }

  test("Test bearing calculation on Roadlink with more that two points and with asset mValue") {
    val assetPosition: Point = Point(20.0, 20.0)
    val linkGeometry: Seq[Point] = List(Point(10.0, 10.0), Point(50.0, 50.0), Point(70.0, 10.0), Point(70.0, -20.0))
    val mValue: Double = GeometryUtils.calculateLinearReferenceFromPoint(assetPosition, linkGeometry)
    val bearingValue = GeometryUtils.calculateBearing(linkGeometry, Some(mValue))
    bearingValue should be(45.0)
  }

  test("partitionTesti") {


    def clusterLinks(links: Seq[PieceWiseLane], tolerance: Double = 0.5): Seq[Graph] = {
      val generator = new BasicLineGraphGenerator(tolerance)
      links.sortBy(_.endMeasure).foreach { link =>
        val (sp, ep) = GeometryUtils.geometryEndpoints(link.geometry)
        val segment = new LineSegment(sp.x, sp.y, ep.x, ep.y)
        val graphable = generator.add(segment)
        graphable.setObject(link)
      }
      clusterGraph(generator.getGraph)
    }

    def linksFromCluster[T <: PolyLine](cluster: Graph): Seq[T] = {
      val edges = cluster.getEdges.toList.asInstanceOf[List[BasicEdge]]
      edges.map { edge: BasicEdge => edge.getObject.asInstanceOf[T] }
    }

    def clusterGraph(graph: Graph): Seq[Graph] = {
      val partitioner = new org.geotools.graph.util.graph.GraphPartitioner(graph)
      println(graph)
      partitioner.partition()
      partitioner.getPartitions.toList.asInstanceOf[List[Graph]]

    }
    val clustersAux =
//      List(List(
//        PieceWiseLane(642078,6394218,1,false,List(Point(531043.865,6754834.059,106.58599999999569), Point(531055.31,6754837.993,106.66000000000349), Point(531066.823,6754841.885,106.71799999999348), Point(531085.725,6754848.164,106.75599999999395), Point(531104.669,6754854.318,106.76300000000629), Point(531123.652,6754860.348,106.81900000000314), Point(531142.675,6754866.253,106.89100000000326), Point(531161.736,6754872.033,106.89999999999418), Point(531180.835,6754877.687,106.89100000000326), Point(531199.97,6754883.215,106.903999999995), Point(531219.141,6754888.617,106.91099999999278), Point(531238.348,6754893.893,106.88199999999779), Point(531257.589,6754899.043,106.95500000000175), Point(531276.863,6754904.066,107.05499999999302), Point(531296.17,6754908.962,107.14999999999418), Point(531315.509,6754913.731,107.21700000000419), Point(531334.878,6754918.373,107.3179999999993), Point(531354.278,6754922.887,107.42999999999302), Point(531373.707,6754927.274,107.49899999999616), Point(531393.165,6754931.533,107.61299999999756), Point(531412.65,6754935.663,107.75199999999313), Point(531432.162,6754939.666,107.9719999999943), Point(531451.699,6754943.54,108.15099999999802), Point(531471.262,6754947.285,108.32000000000698), Point(531490.849,6754950.902,108.52700000000186), Point(531510.46,6754954.389,108.69899999999325), Point(531530.092,6754957.748,108.846000000005), Point(531549.747,6754960.977,109.02400000000489), Point(531569.422,6754964.078,109.18600000000151), Point(531589.118,6754967.048,109.3640000000014), Point(531609.66,6754970.003,109.26499999999942)),0.0,582.797,Set(Point(531043.865,6754834.059,106.58599999999569), Point(531609.66,6754970.003,109.26499999999942)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 441, "trafficDirection" -> AgainstDigitizing)),
//        PieceWiseLane(642133,6394220,1,false,List(Point(531057.434,6754833.96,106.75400000000081), Point(531064.253,6754836.253,106.78800000000047), Point(531077.687,6754840.771,106.83400000000256), Point(531096.582,6754846.979,106.91000000000349), Point(531115.517,6754853.062,106.8579999999929), Point(531134.492,6754859.021,106.94000000000233), Point(531153.506,6754864.854,106.96799999999348), Point(531172.557,6754870.562,106.96799999999348), Point(531191.646,6754876.145,106.9600000000064), Point(531210.771,6754881.603,106.9890000000014), Point(531229.932,6754886.934,106.98200000000361), Point(531249.127,6754892.139,106.93300000000454), Point(531268.356,6754897.218,107.028999999995), Point(531287.618,6754902.17,107.15200000000186), Point(531306.913,6754906.996,107.24499999999534), Point(531326.238,6754911.694,107.27300000000105), Point(531345.594,6754916.265,107.375), Point(531364.98,6754920.709,107.51399999999558), Point(531384.394,6754925.025,107.62799999999697), Point(531403.837,6754929.214,107.7329999999929), Point(531423.306,6754933.274,107.92500000000291), Point(531442.802,6754937.207,108.09500000000116), Point(531462.324,6754941.011,108.25699999999779), Point(531481.87,6754944.686,108.46700000000419), Point(531501.439,6754948.233,108.65799999999581), Point(531521.032,6754951.651,108.81100000000151), Point(531540.646,6754954.94,108.92999999999302), Point(531560.282,6754958.1,109.14800000000105), Point(531579.938,6754961.131,109.32600000000093), Point(531599.614,6754964.033,109.43700000000536), Point(531609.1126598136,6754965.369952118,109.40500114601058)),0.0,567.949,Set(Point(531057.434,6754833.96,106.75400000000081), Point(531609.1126598136,6754965.369952118,109.40500114601058)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 441, "trafficDirection" -> TowardsDigitizing)),
//        PieceWiseLane(642008,6394286,1,false,List(Point(531664.689,6754972.677,109.33599999999569), Point(531678.495,6754974.343,109.49000000000524), Point(531698.255,6754976.596,109.45699999999488), Point(531718.03,6754978.719,109.30400000000373), Point(531737.819,6754980.712,109.18399999999383), Point(531757.62,6754982.575,109.01399999999558), Point(531777.433,6754984.307,108.7670000000071), Point(531797.257,6754985.909,108.56100000000151), Point(531817.091,6754987.381,108.3179999999993), Point(531836.934,6754988.722,108.18600000000151), Point(531856.786,6754989.932,107.99300000000221), Point(531876.645,6754991.012,107.75800000000163), Point(531896.51,6754991.962,107.51799999999639), Point(531916.382,6754992.78,107.31399999999849), Point(531936.259,6754993.468,107.08000000000175), Point(531956.139,6754994.025,106.87200000000303), Point(531976.023,6754994.452,106.61500000000524), Point(531987.512,6754994.213,106.48099999999977), Point(532007.497,6754993.682,106.26900000000023), Point(532027.482,6754993.15,106.04200000000128), Point(532047.467,6754992.619,105.85599999999977), Point(532067.452,6754992.087,105.60000000000582), Point(532087.438,6754991.556,105.36500000000524), Point(532107.423,6754991.024,105.16199999999662), Point(532127.408,6754990.493,104.94599999999627), Point(532133.796,6754990.412,104.88999999999942)),0.0,470.059,Set(Point(531664.689,6754972.677,109.33599999999569), Point(532133.796,6754990.412,104.88999999999942)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 441, "trafficDirection" -> TowardsDigitizing)),
//        PieceWiseLane(641979,6394251,1,false,List(Point(531665.217,6754977.278,109.13400000000547), Point(531687.864,6754979.955,109.36199999999371), Point(531707.661,6754982.146,109.30999999999767), Point(531727.472,6754984.207,109.17399999999907), Point(531747.297,6754986.138,109.01600000000326), Point(531767.133,6754987.938,108.83900000000722), Point(531786.981,6754989.608,108.60400000000664), Point(531806.84,6754991.147,108.34799999999814), Point(531826.708,6754992.555,108.19000000000233), Point(531846.585,6754993.833,108.01799999999639), Point(531866.47,6754994.98,107.82099999999627), Point(531886.362,6754995.996,107.54499999999825), Point(531906.26,6754996.882,107.33000000000175), Point(531926.164,6754997.636,107.12600000000384), Point(531946.073,6754998.26,106.90799999999581), Point(531965.985,6754998.752,106.6420000000071), Point(531978.136,6754998.988,106.47900000000664), Point(531997.793,6754999.271,106.28299999999581), Point(532017.452,6754999.446,106.07600000000093), Point(532037.111,6754999.533,105.86199999999371), Point(532056.77,6754999.551,105.62900000000081), Point(532076.43,6754999.52,105.38099999999395), Point(532096.089,6754999.458,105.18300000000454), Point(532106.614,6754999.42,105.0679999999993), Point(532126.606,6754999.346,104.89500000000407), Point(532136.602,6754999.309,104.80499999999302), Point(532146.598,6754999.272,104.72500000000582), Point(532166.59,6754999.199,104.51399999999558), Point(532186.582,6754999.125,104.26200000000244), Point(532194.2957472635,6754999.097000917,104.1970021296185)),0.0,529.975,Set(Point(531665.217,6754977.278,109.13400000000547), Point(532194.2957472635,6754999.097000917,104.1970021296185)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 441, "trafficDirection" -> AgainstDigitizing))))


//          List(List(
//    PieceWiseLane(642582,6864079,3,false,List(Point(503375.022,6752350.872,103.23399999999674), Point(503317.774,6752360.868,102.92799999999988), Point(503285.507,6752367.211,102.62600000000384), Point(503238.926,6752377.185,102.22500000000582), Point(503181.289,6752390.027,101.6079999999929), Point(503072.26,6752419.574,100.42299999999523), Point(502997.219,6752439.594,99.59500000000116), Point(502980.207,6752443.941,99.43700000000536)),0.0,405.872,Set(Point(503375.022,6752350.872,103.23399999999674), Point(502980.207,6752443.941,99.43700000000536)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections)),
//    PieceWiseLane(642549,2025377,2,false,List(Point(503911.921,6752313.412,102.27599999999802), Point(503806.729,6752313.918,102.59500000000116), Point(503732.16,6752315.361,102.88499999999476), Point(503660.709,6752318.628,103.15200000000186), Point(503589.237,6752324.361,103.40200000000186), Point(503471.806,6752336.621,103.4600000000064), Point(503423.872,6752343.332,103.36900000000605), Point(503375.022,6752350.872,103.23399999999674)),0.0,538.903,Set(Point(503911.921,6752313.412,102.27599999999802), Point(503375.022,6752350.872,103.23399999999674)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections)),
//    PieceWiseLane(642558,6864079,2,false,List(Point(503375.022,6752350.872,103.23399999999674), Point(503317.774,6752360.868,102.92799999999988), Point(503285.507,6752367.211,102.62600000000384), Point(503238.926,6752377.185,102.22500000000582), Point(503181.289,6752390.027,101.6079999999929), Point(503072.26,6752419.574,100.42299999999523), Point(502997.219,6752439.594,99.59500000000116), Point(502980.207,6752443.941,99.43700000000536)),0.0,405.872,Set(Point(503375.022,6752350.872,103.23399999999674), Point(502980.207,6752443.941,99.43700000000536)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections)),
//    PieceWiseLane(642603,2025377,3,false,List(Point(503911.921,6752313.412,102.27599999999802), Point(503806.729,6752313.918,102.59500000000116), Point(503732.16,6752315.361,102.88499999999476), Point(503660.709,6752318.628,103.15200000000186), Point(503589.237,6752324.361,103.40200000000186), Point(503471.806,6752336.621,103.4600000000064), Point(503423.872,6752343.332,103.36900000000605), Point(503375.022,6752350.872,103.23399999999674)),0.0,538.903,Set(Point(503911.921,6752313.412,102.27599999999802), Point(503375.022,6752350.872,103.23399999999674)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections))))

       List(List(
    PieceWiseLane(642669,2043081,3,false,List(Point(491478.902,6750143.581,89.73699999999371), Point(491574.535,6750152.322,90.18300000000454), Point(491691.11,6750164.436,90.30100000000675), Point(491774.98,6750172.251,90.59100000000035), Point(491790.305,6750174.438,90.63899999999558), Point(491867.229,6750188.161,91.1420000000071), Point(491942.462,6750200.496,91.76499999999942), Point(491944.4699860409,6750200.872997379,91.80399972888678)),0.0,469.367,Set(Point(491478.902,6750143.581,89.73699999999371), Point(491944.4699860409,6750200.872997379,91.80399972888678)),None,None,Some("silari"),None, 1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))), Map("municipality" -> 286, "trafficDirection" -> BothDirections)),
    PieceWiseLane(642714,2043081,2,false,List(Point(491478.902,6750143.581,89.73699999999371), Point(491574.535,6750152.322,90.18300000000454), Point(491691.11,6750164.436,90.30100000000675), Point(491774.98,6750172.251,90.59100000000035), Point(491790.305,6750174.438,90.63899999999558), Point(491867.229,6750188.161,91.1420000000071), Point(491942.462,6750200.496,91.76499999999942), Point(491944.4699860409,6750200.872997379,91.80399972888678)),0.0,469.367,Set(Point(491478.902,6750143.581,89.73699999999371), Point(491944.4699860409,6750200.872997379,91.80399972888678)),None,None,Some(""),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections)),
    PieceWiseLane(642735,2043095,2,false,List(Point(491944.47,6750200.873,91.80400000000373), Point(491990.169,6750209.453,92.2219999999943), Point(491990.76,6750209.562,92.2280000000028), Point(492049.943,6750219.426,92.94000000000233), Point(492102.222,6750226.656,93.63899999999558), Point(492137.646,6750231.976,94.01300000000629), Point(492204.716,6750238.275,94.77599999999802), Point(492255.483,6750241.256,95.29200000000128), Point(492320.281,6750244.069,95.9829999999929), Point(492375.733,6750244.894,96.44700000000012), Point(492436.2637227418,6750244.569001488,96.81499831440264)),0.0,494.764,Set(Point(491944.47,6750200.873,91.80400000000373), Point(492436.2637227418,6750244.569001488,96.81499831440264)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections)),
    PieceWiseLane(642699,2043095,3,false,List(Point(491944.47,6750200.873,91.80400000000373), Point(491990.169,6750209.453,92.2219999999943), Point(491990.76,6750209.562,92.2280000000028), Point(492049.943,6750219.426,92.94000000000233), Point(492102.222,6750226.656,93.63899999999558), Point(492137.646,6750231.976,94.01300000000629), Point(492204.716,6750238.275,94.77599999999802), Point(492255.483,6750241.256,95.29200000000128), Point(492320.281,6750244.069,95.9829999999929), Point(492375.733,6750244.894,96.44700000000012), Point(492436.2637227418,6750244.569001488,96.81499831440264)),0.0,494.764,Set(Point(491944.47,6750200.873,91.80400000000373), Point(492436.2637227418,6750244.569001488,96.81499831440264)),None,None,Some("silari"),None,1633651200000L,None,State,List(LaneProperty("lane_type",Stream(LanePropertyValue(1))), LaneProperty("lane_code",List(LanePropertyValue(1)))),Map("municipality" -> 286, "trafficDirection" -> BothDirections))))


    val clusters = for (linkGroup <- clustersAux.asInstanceOf[Seq[Seq[PieceWiseLane]]];
                        cluster <- clusterLinks(linkGroup)) yield cluster


    }



}
