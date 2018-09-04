package fi.liikennevirasto.digiroad2.process

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignTypeGroup}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.AssetValidatorProcess.inaccurateAssetDAO
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, GeometryUtils, Point}
import org.joda.time.DateTime

case class Inaccurate(assetIds: Seq[Long], roadLinks: Seq[RoadLink])

trait AssetServiceValidator {

  val eventbus = new DummyEventBus

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  lazy val userProvider: UserProvider = {
    Class.forName(dr2properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }
  lazy val roadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = { new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint")) }
  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, userProvider)

  type AssetType
  def assetName: String
  def assetType: Int
  val radiusDistance: Int
  val TrafficSignsGroup: Option[TrafficSignTypeGroup] = None

  def getAssetName = {
    assetName
  }

  def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean

  def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSigns: Seq[PersistedTrafficSign]): Boolean

  def getAsset(roadLink: RoadLink): Seq[AssetType]

  def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign]

  protected def getProperty(name: String) = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }

  protected def getPointOfInterest(first: Point, last: Point, sideCode: SideCode): Seq[Point] = {
    sideCode match {
      case SideCode.TowardsDigitizing => Seq(first)
      case SideCode.AgainstDigitizing => Seq(last)
      case _ => Seq(first, last) //Not expected a traffic sign with bothDirection
    }
  }

  def verifyInaccurate(): Unit

}

trait AssetServiceValidatorOperations extends AssetServiceValidator{

  def findNearestRoadLink(point: Point, roadLinks: Seq[RoadLink]): RoadLink = {
    roadLinks.map { roadLink =>
      (GeometryUtils.calculateLinearReferenceFromPoint(Point(point.x, point.y), roadLink.geometry), roadLink)
    }.minBy(_._1)._2
  }

  def getLinkIdsByRadius(point: Point): Seq[RoadLink] = {
    val topLeft = Point(point.x - radiusDistance, point.y - radiusDistance)
    val bottomRight = Point(point.x + radiusDistance, point.y + radiusDistance)

    roadLinkService.getRoadLinksWithComplementaryFromVVH(BoundingRectangle(topLeft, bottomRight))
  }
  def getAdjacentRoadLink(point: Point,  prevRoadLink: RoadLink, roadLinks: Seq[RoadLink]) : Seq[(RoadLink, (Point, Point))] = {
    getAdjacents(point, roadLinks)
  }

  def getAdjacents(previousPoint: Point, roadLinks: Seq[RoadLink]): Seq[(RoadLink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousPoint)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousPoint)) (first, last) else (last, first)

      (roadLink, points)
    }
  }

  //TODO needs to be refactor
  def assetValidator(trafficSign: PersistedTrafficSign): Boolean = {
    val point = Point(trafficSign.lon, trafficSign.lon)
    val roadLinks = getLinkIdsByRadius(point)
    val trafficSignRoadLink = findNearestRoadLink(point, roadLinks)

    val (first, last) = GeometryUtils.geometryEndpoints(trafficSignRoadLink.geometry)
    val pointOfInterest = getPointOfInterest(first, last, SideCode.apply(trafficSign.validityDirection)).head

    validator(pointOfInterest, trafficSignRoadLink, roadLinks: Seq[RoadLink], trafficSign)
  }

  def assetValidator_(trafficSign: PersistedTrafficSign): Inaccurate = {
    Inaccurate(Seq.empty[Long], Seq.empty[RoadLink])
  }

  def assetValidatorX(asset: AssetType, pointOfInterest: Point, defaultRoadLink: RoadLink): Boolean = {
    val roadLinks = getLinkIdsByRadius(pointOfInterest)
    validatorX(pointOfInterest, defaultRoadLink, roadLinks, asset)
  }

  protected def validator(point: Point, oldRoadLink: RoadLink, roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Boolean = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == oldRoadLink.linkId)
    val adjAcents = getAdjacents(point, filteredRoadLink)
    if(adjAcents.isEmpty)
      false
    else {
      adjAcents.forall { case (newRoadLink, (_, oppositePoint)) =>

        val asset = getAsset(newRoadLink)
        if (asset.isEmpty) {
          validator(oppositePoint, newRoadLink: RoadLink, filteredRoadLink, trafficSign)
        } else {
          verifyAsset(asset, roadLinks, trafficSign)
        }
      }
    }
  }

  protected def validatorX(point: Point, prevRoadLink: RoadLink, roadLinks: Seq[RoadLink], asset: AssetType): Boolean = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == prevRoadLink.linkId)
    getAdjacentRoadLink(point, prevRoadLink, filteredRoadLink).exists { case (newRoadLink, (_, oppositePoint)) =>
      val trafficSign = getAssetTrafficSign(newRoadLink)
      if (trafficSign.isEmpty) {
        validatorX(oppositePoint, newRoadLink, filteredRoadLink, asset)
      } else {
        verifyAssetX(asset, newRoadLink, trafficSign)
      }
    }
  }

  //TODO move this method to a generic place
  def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }

  def verifyInaccurate() = {
    println(s"Start verification for asset $assetName")
    println(DateTime.now())

    println("Fetching municipalities")
    val municipalities: Seq[Int] = OracleDatabase.withDynSession{
      Queries.getMunicipalities
    }

    municipalities.foreach{
      municipality =>
        println(s"Start process for municipality $municipality")
        val trafficSigns = trafficSignService.getByMunicipality(municipality)
        trafficSigns.foreach {
          trafficSign =>
            println(s"Validating assets for traffic sign with id: ${trafficSign.id} on linkId: ${trafficSign.linkId}")
            val inaccurate = assetValidator_(trafficSign)  // Will return an Inaccurate with a Seq[assetId] and Seq[LinkId]
            val assetIds = inaccurate.assetIds

            println("Processing inaccurate linkIds")
            inaccurate.roadLinks.foreach {
              roadLink =>
                println(s"Creating inaccurate link id for assetType $assetType and linkId ${roadLink.linkId}")
                inaccurateAssetDAO.createInaccurateLink(roadLink.linkId, assetType, municipality, roadLink.administrativeClass)
            }
            //Iterate through the Seq[LinkId] and add it to the Inaccurate table
            //Iterate through the Seq[AssetId] and add it to the Inaccurate table
        }
    }
  }
}