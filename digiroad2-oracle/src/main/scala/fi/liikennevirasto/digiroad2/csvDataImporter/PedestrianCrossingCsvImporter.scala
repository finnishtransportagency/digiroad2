package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingPedestrianCrossing, PedestrianCrossingService}
import fi.liikennevirasto.digiroad2.user.User

class PedestrianCrossingCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val longValueFieldsMapping: Map[String, String] = coordinateMappings
  override val mandatoryFieldsMapping: Map[String, String] = coordinateMappings

  lazy val pedestrianCrossingService: PedestrianCrossingService = new PedestrianCrossingService(roadLinkService, eventBusImpl)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    pointAssetAttributes.foreach { pedestrianCrossingAttribute =>
      val csvProperties = pedestrianCrossingAttribute.properties
      val nearbyLinks = pedestrianCrossingAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      pedestrianCrossingService.createFromCoordinates(IncomingPedestrianCrossing(position.x, position.y, nearestRoadLink.linkId, Set()), nearestRoadLink, user.username, floating)
    }

    result
  }
}
