package fi.liikennevirasto.digiroad2.process

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, Queries}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime

case class Inaccurate(assetId: Option[Long], linkId: Option[Long], municipalityCode: Int,  administrativeClass: AdministrativeClass)
case class AssetValidatorInfo(ids: Set[Long], newLinkIds: Set[Long] = Set())

trait AssetServiceValidator {

  val eventbus = new DummyEventBus

  lazy val roadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val manoeuvreService = new ManoeuvreService(roadLinkService, eventbus)
  lazy val prohibitionService = new ProhibitionService(roadLinkService, eventbus)
  lazy val vvhClient: VVHClient = { new VVHClient(Digiroad2Properties.vvhRestApiEndPoint) }
  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, eventbus)
  lazy val inaccurateAssetDAO = new InaccurateAssetDAO()

  type AssetType
  def assetTypeInfo: AssetTypeInfo
  val radiusDistance: Int

  def getAssetName: String = {
    assetTypeInfo.label
  }

  def verifyAsset(assets: Seq[AssetType], roadLink: RoadLink, trafficSign: PersistedTrafficSign): Set[Inaccurate]
  def getAsset(roadLink: Seq[RoadLink]): Seq[AssetType]
  def filteredAsset(roadLink: RoadLink, assets: Seq[AssetType], point: Point, distance: Double, trafficSign: Option[PersistedTrafficSign] = None): Seq[AssetType]

  def reprocessRelevantTrafficSigns(assetInfo: AssetValidatorInfo) : Unit

  val allowedTrafficSign: Set[TrafficSignType]

  protected def getPointOfInterest(first: Point, last: Point, sideCode: SideCode): Seq[Point] = {
    sideCode match {
      case SideCode.TowardsDigitizing => Seq(last)
      case SideCode.AgainstDigitizing => Seq(first)
      case _ => Seq(first, last)
    }
  }

  def verifyInaccurate(): Unit

}

trait AssetServiceValidatorOperations extends AssetServiceValidator {

  def findNearestRoadLink(point: Point, roadLinks: Seq[RoadLink]): RoadLink = {
    roadLinks.minBy { roadLink =>
      GeometryUtils.minimumDistance(point, roadLink.geometry)
    }
  }

  def getRoadLinksByRadius(point: Point, radiusDistance: Int, newTransaction : Boolean = true): Seq[RoadLink] = {
    val topLeft = Point(point.x - radiusDistance, point.y - radiusDistance)
    val bottomRight = Point(point.x + radiusDistance, point.y + radiusDistance)

    roadLinkService.getRoadLinksWithComplementaryFromVVH(BoundingRectangle(topLeft, bottomRight), Set(),  newTransaction)
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

  def assetValidator(trafficSign: PersistedTrafficSign): Set[Inaccurate] = {
    val point = Point(trafficSign.lon, trafficSign.lat)
    val roadLinks = getRoadLinksByRadius(point, radiusDistance, false).filterNot(_.administrativeClass == Private)
    val trafficSignRoadLink = findNearestRoadLink(point, roadLinks)

    val (first, last) = GeometryUtils.geometryEndpoints(trafficSignRoadLink.geometry)
    val pointOfInterest = getPointOfInterest(first, last, SideCode.apply(trafficSign.validityDirection)).head

    val assets = getAsset(roadLinks)
    val filterAssets = filteredAsset(trafficSignRoadLink, assets, pointOfInterest, 0, Some(trafficSign))
    if (filterAssets.isEmpty) {
      val distance = if(GeometryUtils.areAdjacent(pointOfInterest, first))
        GeometryUtils.calculateLinearReferenceFromPoint(point, trafficSignRoadLink.geometry)
      else
        GeometryUtils.calculateLinearReferenceFromPoint(point, trafficSignRoadLink.geometry.reverse)
      validator((pointOfInterest, trafficSignRoadLink), roadLinks: Seq[RoadLink], (trafficSign, trafficSignRoadLink.administrativeClass), assets, Set(), distance)
    } else {
      verifyAsset(filterAssets, trafficSignRoadLink, trafficSign)
    }
  }

  protected def validator(previousInfo: (Point, RoadLink), roadLinks: Seq[RoadLink], trafficSign: (PersistedTrafficSign, AdministrativeClass), assets: Seq[AssetType], inaccurate: Set[Inaccurate], distance: Double): Set[Inaccurate] = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == previousInfo._2.linkId)
    val adjAcents = getAdjacents(previousInfo, filteredRoadLink, trafficSign._1)
    if(adjAcents.isEmpty || distance >= radiusDistance)
      inaccurate ++ Seq(Inaccurate(None, Some(trafficSign._1.linkId), trafficSign._1.municipalityCode, trafficSign._2))
    else {
      adjAcents.flatMap{ case (newRoadLink, (adjacentPoint, oppositePoint)) =>
        val asset = filteredAsset(newRoadLink, assets, adjacentPoint, distance)
        if (asset.isEmpty) {
          validator((oppositePoint, newRoadLink), filteredRoadLink, trafficSign, assets, inaccurate, distance + GeometryUtils.geometryLength(newRoadLink.geometry))
        } else {
          verifyAsset(asset, newRoadLink, trafficSign._1) ++ inaccurate
        }
      }
    }.toSet
  }

  protected def insertInaccurate(insertInaccurate: (Long, Int, Int, AdministrativeClass) => Unit, id: Long, assetType: Int, municipalityCode: Int, adminClass: AdministrativeClass): Unit = {
    try {
      insertInaccurate(id, assetType, municipalityCode, adminClass)
    } catch {
      case ex: SQLIntegrityConstraintViolationException =>
        print("duplicate key inserted ")
      case e: Exception => print("duplicate key inserted ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }


  def splitBothDirectionTrafficSignInTwo(trafficSigns: Seq[PersistedTrafficSign]) : Set[PersistedTrafficSign] = {
    trafficSigns.flatMap { trafficSign =>
      SideCode.apply(trafficSign.validityDirection) match {
        case SideCode.BothDirections =>
          Seq(trafficSign.copy(validityDirection = SideCode.TowardsDigitizing.value)) ++ Seq(trafficSign.copy(validityDirection = SideCode.AgainstDigitizing.value))
        case _ =>
          Seq(trafficSign)
      }
    }.toSet
  }

  def verifyInaccurate() : Unit = {

    println(s"Start verification for asset ${assetTypeInfo.label} ")
    println(DateTime.now())

    println("Fetching municipalities")
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession{
      Queries.getMunicipalities
    }

    PostGISDatabase.withDynTransaction {
      inaccurateAssetDAO.deleteAllInaccurateAssets(assetTypeInfo.typeId)
  }

    municipalities.foreach{
      municipality =>
        println(s"Start process for municipality $municipality")
        val trafficSigns = trafficSignService.getByMunicipalityExcludeByAdminClass(municipality, Private).filterNot(_.floating)
          .filter(sign => allowedTrafficSign.contains(TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, "trafficSigns_type").get.propertyValue.toInt)))
        splitBothDirectionTrafficSignInTwo(trafficSigns).foreach {
          trafficSign =>
            println(s"Validating assets for traffic sign with id: ${trafficSign.id} on linkId: ${trafficSign.linkId}")
            PostGISDatabase.withDynTransaction {
              assetValidator(trafficSign).foreach {
                inaccurate =>
                  (inaccurate.assetId, inaccurate.linkId) match {
                    case (Some(asset), _) =>
                      println(s"Creating inaccurate asset for assetType ${assetTypeInfo.typeId} and assetId $asset")
                      insertInaccurate(inaccurateAssetDAO.createInaccurateAsset, asset, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
                    case (_, Some(linkId)) =>
                      println(s"Creating inaccurate link id for assetType ${assetTypeInfo.typeId} and linkId $linkId")
                      insertInaccurate(inaccurateAssetDAO.createInaccurateLink, linkId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
                    case (_, _) =>
                  }
              }
            }
        }
    }
  }

  protected def validateAndInsert(trafficSign: PersistedTrafficSign) : Unit = {
    assetValidator(trafficSign).foreach {
      inaccurate =>
        (inaccurate.assetId, inaccurate.linkId) match {
          case (Some(assetId), _) => insertInaccurate(inaccurateAssetDAO.createInaccurateAsset, assetId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
          case (_, Some(linkId)) => insertInaccurate(inaccurateAssetDAO.createInaccurateLink, linkId, assetTypeInfo.typeId, inaccurate.municipalityCode, inaccurate.administrativeClass)
          case _ => None
        }
    }
  }
}
