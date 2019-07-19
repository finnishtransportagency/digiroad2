package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingObstacle, ObstacleService}
import fi.liikennevirasto.digiroad2.user.User

class ObstaclesCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val codeValueFieldsMapping: Map[String, String] = Map("esterakennelman tyyppi" -> "type")
  override val mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ codeValueFieldsMapping

  lazy val obstaclesService: ObstacleService = new ObstacleService(roadLinkService)

  private val allowedTypeValues: Seq[Int] = Seq(1, 2)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val notImportedObstacle = pointAssetAttributes.flatMap { obstacleAttribute =>
      val csvProperties = obstacleAttribute.properties
      val nearbyLinks = obstacleAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)
      val obstacleType = getPropertyValue(csvProperties, "type").asInstanceOf[String].toInt

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      val validData =
        if(!allowedTypeValues.contains(obstacleType))
          Seq(NotImportedData(reason = s"Obstacle type $obstacleType does not exist.", csvRow = rowToString(csvProperties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
        else
          Seq()

      if(validData.isEmpty)
        obstaclesService.createFromCoordinates(IncomingObstacle(position.x, position.y, nearestRoadLink.linkId, obstacleType), nearestRoadLink, user.username, floating)

      validData
    }

    result.copy(notImportedData = notImportedObstacle.toList ++ result.notImportedData)
  }
}
