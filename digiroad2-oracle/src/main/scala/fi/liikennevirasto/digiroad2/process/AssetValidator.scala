package fi.liikennevirasto.digiroad2.process

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, Queries}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType, TrafficSignTypeGroup}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, GeometryUtils, Point}
import org.joda.time.DateTime

case class Inaccurate(assetId: Option[Long], linkId: Option[Long], municipalityCode: Int,  administrativeClass: AdministrativeClass)
case class AssetValidatorInfo(oldIds: Set[Long], newIds: Set[Long])

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
  lazy val inaccurateAssetDAO = new InaccurateAssetDAO()


  type AssetType
  def assetName: String
  def assetType: Int
  val radiusDistance: Int
  val TrafficSignsGroup: Option[TrafficSignTypeGroup] = None

  def getAssetName: String = {
    assetName
  }

  def verifyAsset(assets: Seq[AssetType], roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Set[Inaccurate]

//  def verifyAssetX(asset: AssetType, roadLink: RoadLink, trafficSigns: Seq[PersistedTrafficSign]): Boolean

  def getAsset(roadLink: Seq[RoadLink]): Seq[AssetType]

//  def getAssetTrafficSign(roadLink: RoadLink): Seq[PersistedTrafficSign]

  def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], point: Point, distance: Double): Seq[AssetType]

  def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo) : Unit

  val allowedTrafficSign: Set[TrafficSignType]

  protected def getProperty(name: String): String = {
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
      case _ => Seq(first, last)
    }
  }

  def verifyInaccurate(): Unit

}

trait AssetServiceValidatorOperations extends AssetServiceValidator{

  def findNearestRoadLink(point: Point, roadLinks: Seq[RoadLink]): RoadLink = {
    roadLinks.minBy { roadLink =>
      GeometryUtils.minimumDistance(point, roadLink.geometry)
    }
  }

  def getLinkIdsByRadius(point: Point): Seq[RoadLink] = {
    val topLeft = Point(point.x - radiusDistance, point.y - radiusDistance)
    val bottomRight = Point(point.x + radiusDistance, point.y + radiusDistance)

    roadLinkService.getRoadLinksWithComplementaryFromVVH(BoundingRectangle(topLeft, bottomRight), newTransaction = false)
  }

  def getAdjacents(previousInfo: (Point, RoadLink), roadLinks: Seq[RoadLink], trafficSign: PersistedTrafficSign): Seq[(RoadLink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousInfo._1)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo._1)) (first, last) else (last, first)

      (roadLink, points)
    }
  }

  //TODO needs to be refactor
  def assetValidator(trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    val point = Point(trafficSign.lon, trafficSign.lat)
    val roadLinks = getLinkIdsByRadius(point)
    val trafficSignRoadLink = findNearestRoadLink(point, roadLinks)

    val (first, last) = GeometryUtils.geometryEndpoints(trafficSignRoadLink.geometry)
    val pointOfInterest = getPointOfInterest(first, last, SideCode.apply(trafficSign.validityDirection)).head

    val distance = if(GeometryUtils.areAdjacent(pointOfInterest, first))
      GeometryUtils.calculateLinearReferenceFromPoint(point, trafficSignRoadLink.geometry)
    else
      GeometryUtils.calculateLinearReferenceFromPoint(point, trafficSignRoadLink.geometry.reverse)

    val assets = getAsset(roadLinks)


    val asset = filteredAsset(trafficSignRoadLink, assets, pointOfInterest, distance)
    if (asset.isEmpty) {
      validator((pointOfInterest, trafficSignRoadLink), roadLinks: Seq[RoadLink], (trafficSign, trafficSignRoadLink.administrativeClass), assets, Set(), distance)
    } else {
      verifyAsset(asset, roadLinks, trafficSign)
    }
//
//    validator((pointOfInterest, trafficSignRoadLink), roadLinks: Seq[RoadLink], (trafficSign, trafficSignRoadLink.administrativeClass), assets, Set(), distance)
  }

//  def assetValidatorX(asset: AssetType, pointOfInterest: Point, defaultRoadLink: RoadLink): Boolean = {
//    val roadLinks = getLinkIdsByRadius(pointOfInterest)
//    validatorX(pointOfInterest, defaultRoadLink, roadLinks, asset)
//  }


//  def assetValidatorX(asset: AssetType, pointOfInterest: Point, defaultRoadLink: RoadLink): Boolean = {
//    val roadLinks = getLinkIdsByRadius(pointOfInterest)
//    validatorX(pointOfInterest, defaultRoadLink, roadLinks, asset)
//  }

  protected def validator(previousInfo: (Point, RoadLink), roadLinks: Seq[RoadLink], trafficSign: (PersistedTrafficSign, AdministrativeClass), assets: Seq[AssetType], inaccurate: Set[Inaccurate], distance: Double): Set[Inaccurate] = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == previousInfo._2.linkId)
    val adjAcents = getAdjacents(previousInfo, filteredRoadLink, trafficSign._1)
    if(adjAcents.isEmpty || distance >= radiusDistance)
      inaccurate ++ Seq(Inaccurate(None, Some(trafficSign._1.linkId), trafficSign._1.municipalityCode, trafficSign._2))
    else {
      adjAcents.flatMap{ case (newRoadLink, (_, oppositePoint)) =>
        val asset = filteredAsset(newRoadLink, assets, oppositePoint, distance)
        if (asset.isEmpty) {
          validator((oppositePoint, newRoadLink), filteredRoadLink, trafficSign, assets, inaccurate, distance + GeometryUtils.geometryLength(newRoadLink.geometry))
        } else {
          verifyAsset(asset, roadLinks, trafficSign._1) ++ inaccurate
        }
      }
    }.toSet
  }

  //TODO move this method to a generic place
  def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String) : Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }

  def verifyInaccurate() : Unit = {
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
            OracleDatabase.withDynSession {
              assetValidator(trafficSign).foreach {
                inaccurate =>
                  (inaccurate.assetId, inaccurate.linkId) match {
                    case (Some(asset), _) =>
                      println(s"Creating inaccurate asset for assetType $assetType and assetId $asset")
                      inaccurateAssetDAO.createInaccurateAsset(asset, assetType, inaccurate.municipalityCode, inaccurate.administrativeClass)
                    case (_, Some(linkId)) =>
                      println(s"Creating inaccurate link id for assetType $assetType and linkId $linkId")
                      inaccurateAssetDAO.createInaccurateLink(linkId, assetType, inaccurate.municipalityCode, inaccurate.administrativeClass)
                    case (_, _) =>
                  }
              }
            }
        }
    }
  }
}