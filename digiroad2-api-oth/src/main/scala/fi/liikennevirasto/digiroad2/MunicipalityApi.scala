package fi.liikennevirasto.digiroad2

import java.sql.SQLException

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Manoeuvres, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{Obstacle, PedestrianCrossing, RailwayCrossing, TrafficLight}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{AssetPropertyService, AwsService, Dataset, DatasetStatus, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{HeightLimit => _, WidthLimit => _, _}
import org.joda.time.DateTime
import org.json4s.JsonAST.JObject
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.json4s._
import org.scalatra.swagger.{ResponseMessage, Swagger, SwaggerSupport}
import fi.liikennevirasto.digiroad2.service.AwsService

class MunicipalityApi(val onOffLinearAssetService: OnOffLinearAssetService,
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
                      val numberOfLanesService: NumberOfLanesService,
                      implicit val swagger: Swagger
                     ) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport with SwaggerSupport {

  def awsService = new AwsService(vvhClient,onOffLinearAssetService, roadLinkService, linearAssetService, speedLimitService, pavedRoadService, roadWidthService, manoeuvreService,
    assetService, obstacleService, pedestrianCrossingService, railwayCrossingService, trafficLightService, massTransitLaneService, numberOfLanesService)

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"
  protected val applicationDescription = "Municipality API "
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
  }

  put("/assetUpdateFromAWS") {
    try {
      val jsonDatasets: List[List[Any]] = parsedBody.extractOrElse[List[List[Any]]](throw new ClassCastException)

      val listDatasets = jsonDatasets.map(data =>
        Dataset(data.head.asInstanceOf[String], data(1).asInstanceOf[Map[String, Any]], data(2).asInstanceOf[List[List[BigInt]]])
      )

      var datasetFeaturesWithoutIds = Map[String, Int]()

      OracleDatabase.withDynTransaction {
        listDatasets.foreach(dataset =>
          datasetFeaturesWithoutIds += (dataset.datasetId -> awsService.validateAndInsertDataset(dataset))
        )

        listDatasets.foreach(dataset =>
          awsService.updateDataset(dataset)
        )

        listDatasets.map{dataset =>
          val datasetId = dataset.datasetId
          val datasetStatus = awsService.getDatasetStatusById(datasetId)
          if ((datasetStatus == DatasetStatus.Processed.description || datasetStatus == DatasetStatus.FeatureRoadlinksDontMatch.description) && datasetFeaturesWithoutIds(datasetId) == 0) {
            Map(
              "DataSetId" -> datasetId,
              "Status" -> datasetStatus
            )
          } else {
            Map(
              "DataSetId" -> datasetId,
              "Status" -> datasetStatus,
              "Features with errors" -> awsService.getFeatureErrorsByDatasetId(datasetId, datasetFeaturesWithoutIds(datasetId))
            )
          }
        }
      }
    } catch {
      case cce: ClassCastException => halt(BadRequest("Error when extracting dataSet in JSON"))
      case e: Exception => halt(BadRequest("Could not process Datasets. Verify information provided"))
    }
  }
}
