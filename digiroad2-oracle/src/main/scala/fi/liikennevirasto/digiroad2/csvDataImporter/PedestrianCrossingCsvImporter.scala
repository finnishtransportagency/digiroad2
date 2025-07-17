package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.{PedestrianCrossings, State}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingPedestrianCrossing, PedestrianCrossingService}
import fi.liikennevirasto.digiroad2.user.User

class PedestrianCrossingCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val longValueFieldsMapping: Map[String, String] = coordinateMappings
  override val mandatoryFieldsMapping: Map[String, String] = coordinateMappings

  lazy val pedestrianCrossingService: PedestrianCrossingService = new PedestrianCrossingService(roadLinkService, eventBusImpl)

  override def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTraffic(user, Point(lon.toLong, lat.toLong)).filter(_.administrativeClass != State)
        roadLinks.isEmpty match {
          case true => (List(s"No Rights for Municipality or nonexistent non-state road links near asset position"), Seq())
          case false =>
            if (assetHasEditingRestrictions(PedestrianCrossings.typeId, roadLinks)) {
              (List("Asset type editing is restricted within municipality or admininistrative class."), Seq())
            } else {
              (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
            }
        }
      case _ =>
        (Nil, Nil)
    }
  }

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    pointAssetAttributes.foreach { pedestrianCrossingAttribute =>
      val csvProperties = pedestrianCrossingAttribute.properties
      val nearbyLinks = pedestrianCrossingAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)

      val nearestRoadLink = nearbyLinks.minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      pedestrianCrossingService.createFromCoordinates(IncomingPedestrianCrossing(position.x, position.y, nearestRoadLink.linkId, Set()), nearestRoadLink, user.username, floating)
    }

    result
  }
}
