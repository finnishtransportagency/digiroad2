package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.AssetService
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{ObstacleService, PavedRoadService, PedestrianCrossingService, RailwayCrossingService, TrafficLightService, HeightLimit => _, WidthLimit => _}
import org.json4s.jackson.Serialization.write
import org.json4s._
import org.json4s.jackson.Serialization

case class Dataset(datasetId: Option[String] = None, geoJson: Option[Map[Any, Any]] = None, roadlinks: Option[List[List[BigInt]]] = None)
case class PointAssetCoordinates(lon: Double, lat: Double, z: Double)

class AwsService(val onOffLinearAssetService: OnOffLinearAssetService,
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

  val alldatasetStatus = Map(
    0 -> "Inserted successfuly",
    1 -> "Errors in the dataset",
    2 -> "Errors in the features",
    3 -> "Processed successfuly",
    4 -> "Processed with errors"
  )

  val allfeatureStatus = Map(
    0 -> "Inserted successfuly",
    1 -> "Errors in the feature",
    2 -> "Processed successfuly"
  )

  private def linkIdValidation(linkIds: Set[Long]): Seq[RoadLink] = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds)
    linkIds.foreach { linkId =>
      roadLinks.find(road => road.linkId == linkId && road.administrativeClass != State).
        getOrElse(s"Link id: $linkId is not valid, doesn't exist or have an administrative class 'State'.") // Need to put this to throw a exception
    }
    roadLinks
  }

  def validateAndInsertDataset(dataset: Dataset) = {
    var datasetStatus = 0
    val assets = dataset.geoJson.get("features").asInstanceOf[List[Map[String, Any]]]
    val roadlinks = dataset.roadlinks.get.flatten.toSet
    linkIdValidation(roadlinks.map(number => number.longValue()))

    if(assets.length != dataset.roadlinks.get.length)
      datasetStatus = 1

    awsDao.insertDataset(dataset.datasetId.get, write(dataset.geoJson.get), write(dataset.roadlinks.get), datasetStatus)

    assets.foreach(feature => {
      var featureStatus = 0
      val assetTypeGeometry = feature.getOrElse("geometry", "").asInstanceOf[Map[String, Any]].getOrElse("type", "")
      val properties = feature.getOrElse("properties", "").asInstanceOf[Map[String, Any]]
      val featureId = properties.getOrElse("id", "").asInstanceOf[BigInt].longValue()

      assetTypeGeometry match {
        case "LineString" =>
          properties.getOrElse("type", "").asInstanceOf[String] match {
            case "" =>
              datasetStatus = 2
              featureStatus = 1
            case _ =>
          }
          properties.getOrElse("name", "").asInstanceOf[String] match {
            case "" =>
              datasetStatus = 2
              featureStatus = 1
            case _ =>
          }
          properties.getOrElse("functionalClass", "").asInstanceOf[String] match {
            case "" =>
              datasetStatus = 2
              featureStatus = 1
            case _ =>
          }
        case "Point" =>
          properties.getOrElse("type", "").asInstanceOf[String] match {
            case "" =>
              datasetStatus = 2
              featureStatus = 1
            case _ =>
          }
        case _ =>
          featureStatus = 1
          datasetStatus = 2
      }
       awsDao.insertFeature(featureId, dataset.datasetId.get, featureStatus)
    })
    awsDao.updateDatasetStatus(dataset.datasetId.get, datasetStatus)
  }

  def updateDataset(dataset: Dataset) = {
    val assets = dataset.geoJson.get("features").asInstanceOf[List[Map[String, Any]]]
    val roadlinks = dataset.roadlinks.get

    var roadlinksCount = 0
    assets.foreach(feature => {
      val featureRoadlinks = roadlinks(roadlinksCount)
      roadlinksCount+=1

      val properties = feature("properties").asInstanceOf[Map[String, Any]]
      val featureId = properties("id").asInstanceOf[BigInt].longValue()
      val status = awsDao.checkFeatureStatus(featureId)
      if (status == "0") {

        val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type").asInstanceOf[String]
        val assetType = properties("type").asInstanceOf[String]

        assetTypeGeometry match {
          case "LineString" => assetType match {
            case "Roadlink" =>
            //Call right service
          }
            awsDao.updateFeatureStatus(featureId, 2)

          case "Point" => assetType match {
            case "obstacle" =>
            //Call right service
          }
            awsDao.updateFeatureStatus(featureId, 2)
        }
      }
    })

    val errors = awsDao.checkProcessedDatasetFeaturesForErrors(dataset.datasetId.get)
    if (errors == 0){
      awsDao.updateDatasetStatus(dataset.datasetId.get, 3)
    } else {
      awsDao.updateDatasetStatus(dataset.datasetId.get, 4)
    }
  }
}