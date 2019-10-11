package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.AssetService
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, NumericValue, RoadLink, Value}
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingObstacle, ObstacleService, PavedRoadService, PedestrianCrossingService, RailwayCrossingService, TrafficLightService, HeightLimit => _, WidthLimit => _}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient

case class Dataset(datasetId: Option[String] = None, geoJson: Option[Map[Any, Any]] = None, roadlinks: Option[List[List[BigInt]]] = None)

class AwsService(vvhClient: VVHClient,
                  val onOffLinearAssetService: OnOffLinearAssetService,
                        val roadLinkService: RoadLinkService,
                        val linearAssetService: LinearAssetService,
                        val speedLimitService: SpeedLimitService,
                        val pavedRoadService: PavedRoadService,
                        val roadWidthService: RoadWidthService,
                        val manoeuvreService: ManoeuvreService,
                        val assetService: AssetService,
                        val obstacleService: ObstacleService,
                        val pedestrianCrossingService: PedestrianCrossingService,
                        val railwayCrossingService: RailwayCrossingService,
                        val trafficLightService: TrafficLightService,
                        val massTransitLaneService: MassTransitLaneService,
                        val numberOfLanesService: NumberOfLanesService
                       ) {

  implicit val formats = Serialization.formats(NoTypeHints)
  def awsDao = new AwsDao

  final val AwsUser = "AwsUpdater"

  val allDatasetStatus = Map(
    0 -> "Inserted successfuly",
    1 -> "Amount of features and roadlinks do not match",
    2 -> "Errors in the features",
    3 -> "Processed successfuly",
    4 -> "Processed with errors"
  )

  val allFeatureStatus = Map(
    0 -> "Inserted successfuly",
    1 -> "Errors in the feature",
    2 -> "Processed successfuly"
  )

  private def linkIdValidation(linkIds: Set[Long]): Seq[RoadLink] = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds)
    linkIds.foreach { linkId =>
      roadLinks.find(road => road.linkId == linkId && road.administrativeClass != State).get
    }
    roadLinks
  }

  private def updateRoadlink(properties: Map[String, Any], links: Seq[RoadLink])= {
    val speedLimit = properties.getOrElse("speedLimit", "").asInstanceOf[String]
    val pavementClass = properties.getOrElse("pavementClass", "").asInstanceOf[String]

    links.foreach { link =>
      speedLimit match {
        case "" =>
        case _ =>
          speedLimitService.getExistingAssetByRoadLink(link) match {
            case Some(value) =>
              val newSpeedLimitAsset = NewLinearAsset(link.linkId, value.startMeasure ,value.endMeasure , NumericValue(speedLimit.toInt), properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
              speedLimitService.update(value.id, Seq(newSpeedLimitAsset), AwsUser)
            case None =>
              val newSpeedLimitAsset = NewLinearAsset(link.linkId, 0 , link.length, NumericValue(speedLimit.toInt), properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
              speedLimitService.createMultiple(Seq(newSpeedLimitAsset), speedLimit.toInt, AwsUser, vvhClient.roadLinkData.createVVHTimeStamp(), (_, _) => Unit)
          }
      }

      pavementClass match {
        case "" =>
        case _ =>
          val pavementClassValue = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", false, Seq(DynamicPropertyValue(pavementClass.toInt))))))

          val duplicate = pavedRoadService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(link.linkId))
          duplicate.isEmpty match {
            case false =>
            pavedRoadService.update(Seq(duplicate.head.id), pavementClassValue, AwsUser)
            case true =>
              val newPavementClassAsset = NewLinearAsset(link.linkId, 0 , link.length, pavementClassValue, properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
            pavedRoadService.create(Seq(newPavementClassAsset), PavedRoad.typeId, AwsUser)
          }
      }
    }
  }

  def validateAndInsertDataset(dataset: Dataset): Int = {
    var featuresWithoutIds = 0

    var datasetStatus = 0
    val assets = dataset.geoJson.get("features").asInstanceOf[List[Map[String, Any]]]
    val roadlinks = dataset.roadlinks.get

    if(assets.length != dataset.roadlinks.get.length) {
      datasetStatus = 1
    }

    awsDao.insertDataset(dataset.datasetId.get, write(dataset.geoJson.get), write(dataset.roadlinks.get), datasetStatus)
    if(datasetStatus != 1) {
      var roadlinksCount = 0
      assets.foreach(feature => {
        var featureStatus = 0

        val featureRoadlinks = roadlinks(roadlinksCount)
        roadlinksCount += 1

        val properties = feature.getOrElse("properties", "").asInstanceOf[Map[String, Any]]
        val featureId = properties.getOrElse("id", 0)

        if (featureId != 0) {
          try {
            val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type")
            linkIdValidation(featureRoadlinks.map(number => number.longValue()).toSet)

            assetTypeGeometry match {
              case "LineString" =>
                properties("type")
                properties("name")
                properties("functionalClass")
              case "Point" =>
                properties("type")
              case _ =>
                featureStatus = 1
                datasetStatus = 2
            }
            awsDao.insertFeature(featureId.asInstanceOf[BigInt].longValue(), dataset.datasetId.get, featureStatus)
          } catch {
            case _: Throwable =>
              featureStatus = 1
              awsDao.insertFeature(featureId.asInstanceOf[BigInt].longValue(), dataset.datasetId.get, featureStatus)
              datasetStatus = 2
          }
        } else {
          featuresWithoutIds+=1
          datasetStatus = 2
        }
      }
      )
      awsDao.updateDatasetStatus(dataset.datasetId.get, datasetStatus)
    }
    featuresWithoutIds
  }

  def updateDataset(dataset: Dataset) = {
    if (awsDao.checkDatasetStatus(dataset.datasetId.get).toInt != 1) {
      val assets = dataset.geoJson.get("features").asInstanceOf[List[Map[String, Any]]]
      val roadlinks = dataset.roadlinks.get

      var roadlinksCount = 0
      assets.foreach(feature => {
        val featureRoadlinks = roadlinks(roadlinksCount)
        roadlinksCount += 1

        val properties = feature("properties").asInstanceOf[Map[String, Any]]
        val featureId = properties.getOrElse("id", 0)

        if (featureId != 0) {
          val status = awsDao.checkFeatureStatus(featureId.asInstanceOf[BigInt].longValue())
          try {
            if (status == "0") {

              val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type").asInstanceOf[String]
              val assetType = properties("type").asInstanceOf[String]

              assetTypeGeometry match {
                case "LineString" => assetType match {
                  case "Roadlink" =>
                    val links = roadLinkService.getRoadLinksAndComplementariesFromVVH(featureRoadlinks.map(_.longValue()).toSet)
                      .filter(link => link.functionalClass != 99 && link.functionalClass != 7 && link.functionalClass != 8)

                    updateRoadlink(properties, links)
                }
                  awsDao.updateFeatureStatus(featureId.asInstanceOf[BigInt].longValue(), 2)

                case "Point" => assetType match {
                  case "obstacle" =>
                    val obstacleCoordinates = feature("geometry").asInstanceOf[Map[String, Any]]("coordinates").asInstanceOf[List[List[Double]]].head
                    val obstacleType = properties("class").asInstanceOf[BigInt]
                    val newObstacle = IncomingObstacle(obstacleCoordinates.head, obstacleCoordinates(1), featureRoadlinks.head.longValue(), obstacleType.intValue())
                    val link = roadLinkService.getRoadLinkAndComplementaryFromVVH(featureRoadlinks.head.longValue())

                    obstacleService.checkDuplicates(newObstacle, true) match {
                      case Some(value) =>
                        obstacleService.expire(value.id, AwsUser)
                        obstacleService.create(newObstacle, AwsUser, link.get)
                      case None =>
                        obstacleService.create(newObstacle, AwsUser, link.get)
                    }
                }
                  awsDao.updateFeatureStatus(featureId.asInstanceOf[BigInt].longValue(), 2)
              }
            }
          } catch {
            case _: Throwable =>
              awsDao.updateFeatureStatus(featureId.asInstanceOf[BigInt].longValue(), 1)
          }
        }
      }
      )

      val errors = awsDao.checkProcessedDatasetFeaturesForErrors(dataset.datasetId.get)
      if (errors == 0) {
        awsDao.updateDatasetStatus(dataset.datasetId.get, 3)
      } else {
        awsDao.updateDatasetStatus(dataset.datasetId.get, 4)
      }
    }
  }

  def getDatasetStatusById(datasetId: String, datasetFeaturesWithoutIds: Int): Any = {
    val statusCode = awsDao.checkDatasetStatus(datasetId)
    if((statusCode.toInt == 1 || statusCode.toInt == 3) && datasetFeaturesWithoutIds == 0) {
      allDatasetStatus(statusCode.toInt)
    }else{
      val featuresStatusCode = awsDao.checkAllFeatureIdAndStatusByDataset(datasetId).filter{case (_, status) => status.toInt != 2}
      var featuresStatusMap = Map[String, String]()

      featuresStatusCode.foreach(tuple =>
        featuresStatusMap += (tuple._1.toString -> allFeatureStatus(tuple._2.toInt))
      )

      if(datasetFeaturesWithoutIds != 0) {
        featuresStatusMap += ("Features without ids" -> datasetFeaturesWithoutIds.toString)
      }

      featuresStatusMap
    }
  }
}