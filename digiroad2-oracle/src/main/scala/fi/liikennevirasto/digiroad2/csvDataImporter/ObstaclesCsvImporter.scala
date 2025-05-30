package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.{Obstacles, PedestrianCrossings, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingObstacle, ObstacleService}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, NotImportedData, Point}

class ObstaclesCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val codeValueFieldsMapping: Map[String, String] = Map("esterakennelman tyyppi" -> "type")
  override val mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ codeValueFieldsMapping

  lazy val obstaclesService: ObstacleService = new ObstacleService(roadLinkService)

  private val allowedTypeValues: Seq[Int] = Seq(1, 2, 3, 99)


  override def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTraffic(user, Point(lon.toLong, lat.toLong))
        roadLinks.isEmpty match {
          case true => (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
          case false =>
            if (assetHasEditingRestrictions(Obstacles.typeId, roadLinks)) {
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
    val notImportedObstacle = pointAssetAttributes.flatMap { obstacleAttribute =>
      val csvProperties = obstacleAttribute.properties
      val nearbyLinks = obstacleAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)
      val obstacleType = getPropertyValue(csvProperties, "type").asInstanceOf[String].toInt

      val nearestRoadLink = nearbyLinks.minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      val validData =
        if(!allowedTypeValues.contains(obstacleType))
          Seq(NotImportedData(reason = s"Obstacle type $obstacleType does not exist.", csvRow = rowToString(csvProperties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
        else
          Seq()

      if(validData.isEmpty)
        obstaclesService.createFromCoordinates(
          IncomingObstacle(position.x, position.y, nearestRoadLink.linkId, Set(SimplePointAssetProperty(obstaclesService.typePublicId, Seq(PropertyValue(obstacleType.toString))))),
          nearestRoadLink, user.username, floating)

      validData
    }

    result.copy(notImportedData = notImportedObstacle.toList ++ result.notImportedData)
  }
}
