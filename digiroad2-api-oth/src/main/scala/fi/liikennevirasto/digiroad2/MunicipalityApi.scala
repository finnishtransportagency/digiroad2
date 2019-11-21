package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{HeightLimit => _, WidthLimit => _, _}
import org.json4s._
import org.json4s.jackson.Serialization.write
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}

case class Dataset(datasetId: String, features: Map[String, Any], roadlinks: List[List[Long]]) extends Serializable

sealed trait DatasetStatus{
  def value: Int
  def description: String
}

object DatasetStatus{
  val values = Set[DatasetStatus](Inserted, FeatureRoadlinksDontMatch, ErrorsFeatures, Processed, ErrorsProcessing)

  def apply(intValue: Int): DatasetStatus= {
    values.find(_.value == intValue).getOrElse(ErrorsProcessing)
  }

  case object Inserted extends DatasetStatus{ def value = 0; def description = "Inserted successfuly";}
  case object FeatureRoadlinksDontMatch extends DatasetStatus{ def value = 1; def description = "Amount of features and roadlinks do not match";}
  case object ErrorsFeatures extends DatasetStatus{ def value = 2; def description = "Errors in the features";}
  case object Processed extends DatasetStatus{ def value = 3; def description = "Processed successfuly";}
  case object ErrorsProcessing extends DatasetStatus{ def value = 4; def description = "Errors while processing";}
}

sealed trait FeatureStatus{
  def value: Int
  def description: String
}

object FeatureStatus{
  val values = Set[FeatureStatus](Inserted, ErrorsWhileValidating, Processed, WrongSpeedLimit, WrongPavementClass, WrongObstacleClass,
    WrongSideCode, NoGeometryType, RoadlinkNoTypeInProperties, ErrorsWhileUpdating, WrongRoadlinks)

  def apply(intValue: Int): FeatureStatus= {
    values.find(_.value == intValue).getOrElse(ErrorsWhileUpdating)
  }

  case object Inserted extends FeatureStatus{ def value = 0; def description = "Inserted successfuly";}
  case object ErrorsWhileValidating extends FeatureStatus{ def value = 1; def description = "Errors while validating";}
  case object Processed extends FeatureStatus{ def value = 2; def description = "Processed successfuly";}
  case object WrongSpeedLimit extends FeatureStatus{ def value = 3; def description = "SpeedLimit with invalid speed";}
  case object WrongPavementClass extends FeatureStatus{ def value = 4; def description = "PavementClass with invalid pavement class";}
  case object WrongObstacleClass extends FeatureStatus{ def value = 5; def description = "Obstacle with invalid class";}
  case object WrongSideCode extends FeatureStatus{ def value = 6; def description = "Invalid sideCode";}
  case object NoGeometryType extends FeatureStatus{ def value = 7; def description = "Geometry type not found";}
  case object RoadlinkNoTypeInProperties extends FeatureStatus{ def value = 8; def description = "Roadlink with no type in properties";}
  case object ErrorsWhileUpdating extends FeatureStatus{ def value = 9; def description = "Errors while updating";}
  case object WrongRoadlinks extends FeatureStatus{ def value = 10; def description = "Wrong roadlinks";}
}

class MunicipalityApi(val vvhClient: VVHClient,
                      val roadLinkService: RoadLinkService,
                      val speedLimitService: SpeedLimitService,
                      val pavedRoadService: PavedRoadService,
                      val obstacleService: ObstacleService,
                      implicit val swagger: Swagger
                     ) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport with SwaggerSupport {

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"
  protected val applicationDescription = "Municipality API "

  case object DatasetSerializer extends CustomSerializer[Dataset](format =>
    ({
      case jsonObj: JObject =>
        val id = (jsonObj \ "datasetId").extract[String]
        val roadlinks = (jsonObj \ "matchedRoadlinks").extract[List[List[Long]]]
        val features = (jsonObj \ "geojson").extract[Map[String, Any]]

        Dataset(id, features, roadlinks)
    },
      {
        case tv : Dataset => Extraction.decompose(tv)
      }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DatasetSerializer

  before() {
    basicAuth
  }

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def awsDao: AwsDao = new AwsDao

  final val AwsUser = "AwsUpdater"

  private def insertFeatureAndUpdateDataset(datasetId: String, featureId: Long, featureStatus: List[Int]) = {
    val allFeatureStatus = featureStatus.distinct.filterNot(code => code == FeatureStatus.Inserted.value)

    if (allFeatureStatus.isEmpty) {
      awsDao.insertFeature(featureId, datasetId, FeatureStatus.Inserted.value.toString)
    } else {
      awsDao.insertFeature(featureId, datasetId, allFeatureStatus.mkString(","))
      awsDao.updateDatasetStatus(datasetId, DatasetStatus.ErrorsFeatures.value)
    }
  }

  private def linkIdValidation(datasetId: String, featureId: Long, linkIds: Set[Long]): Int = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds, false)
    if(!(linkIds.nonEmpty && roadLinks.count(road => road.administrativeClass != State) == linkIds.size))
    {
      FeatureStatus.WrongRoadlinks.value
    } else {
      FeatureStatus.Inserted.value
    }
  }

  private def validatePoint(datasetId: String, featureId: Long, properties: Map[String, Any], assetType: String): List[Int] = {
    assetType match {
      case "obstacle" =>
        properties.get("class") match {
          case Some(value) =>
            if (!Set(1, 2).contains(value.asInstanceOf[BigInt].intValue())) {
              List(FeatureStatus.WrongObstacleClass.value)
            } else {
              List(FeatureStatus.Inserted.value)
            }
          case None =>
            List(FeatureStatus.WrongObstacleClass.value)
        }
    }
  }

  private def validateLinearAssets(datasetId: String, featureId: Long, properties: Map[String, Any]): List[Int] = {
    val speedLimit = properties.get("speedLimit")
    val pavementClass = properties.get("pavementClass")
    val sideCode = properties.get("sideCode")

    val speedlimitStatus = speedLimit match {
      case Some(value) =>
        if (!Set("20", "30", "40", "50", "60", "70", "80", "90", "100", "120").contains(value.asInstanceOf[String])) {
          FeatureStatus.WrongSpeedLimit.value
        } else {
          FeatureStatus.Inserted.value
        }
      case None => FeatureStatus.Inserted.value
    }

    val pavementClassStatus = pavementClass match {
      case Some(value) =>
        if (!Seq("1", "2", "10", "20", "30", "40", "50").contains(value.asInstanceOf[String])) {
          FeatureStatus.WrongPavementClass.value
        } else {
          FeatureStatus.Inserted.value
        }
      case None => FeatureStatus.Inserted.value
    }

    val sideCodeStatus = sideCode match {
      case Some(value) =>
        if (!Seq(1, 2, 3).contains(value.asInstanceOf[BigInt])) {
          FeatureStatus.WrongSideCode.value
        } else {
          FeatureStatus.Inserted.value
        }
      case None =>
        FeatureStatus.WrongSideCode.value
    }

    List(speedlimitStatus, pavementClassStatus, sideCodeStatus)
  }

  private def updatePoint(properties: Map[String, Any], link: RoadLink, assetType: String, assetCoordinates: List[Double]): Unit = {
    assetType match {
      case "obstacle" =>
        val obstacleType = properties("class").asInstanceOf[BigInt]
        val newObstacle = IncomingObstacle(assetCoordinates.head, assetCoordinates(1), link.linkId, obstacleType.intValue())
        obstacleService.createFromCoordinates(newObstacle, link, AwsUser, false)
    }
  }

  private def updateLinearAssets(properties: Map[String, Any], links: Seq[RoadLink]) = {
    val speedLimit = properties.get("speedLimit")
    val pavementClass = properties.get("pavementClass")

    links.foreach { link =>
      speedLimit match {
        case Some(value) =>
          val speedLimitValue = value.asInstanceOf[String]
          val speedLimitsOnRoadLink = speedLimitService.getExistingAssetByRoadLink(link, false)

          if (speedLimitsOnRoadLink.isEmpty) {
            val newSpeedLimitAsset = NewLinearAsset(link.linkId, 0, link.length, NumericValue(speedLimitValue.toInt), properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
            speedLimitService.createMultiple(Seq(newSpeedLimitAsset), speedLimitValue.toInt, AwsUser, vvhClient.roadLinkData.createVVHTimeStamp(), (_, _) => Unit)
          } else
            speedLimitsOnRoadLink.foreach { sl =>
              val newSpeedLimitAsset = NewLinearAsset(link.linkId, sl.startMeasure, sl.endMeasure, NumericValue(speedLimitValue.toInt), properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
              speedLimitService.update(sl.id, Seq(newSpeedLimitAsset), AwsUser)
            }
        case None =>
      }

      pavementClass match {
        case Some(value) =>
          val pavementClassValue = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue(value.asInstanceOf[String].toInt))))))

          val duplicate = pavedRoadService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(link.linkId))
          if(duplicate.isEmpty) {
            val newPavementClassAsset = NewLinearAsset(link.linkId, 0, link.length, pavementClassValue, properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
            pavedRoadService.create(Seq(newPavementClassAsset), PavedRoad.typeId, AwsUser)
          } else {
            pavedRoadService.update(Seq(duplicate.head.id), pavementClassValue, AwsUser)
          }
        case None =>
      }
    }
  }

  def validateAndInsertDataset(dataset: Dataset): Option[Int] = {
    val assets = dataset.features("features").asInstanceOf[List[Map[String, Any]]]
    val roadlinks = dataset.roadlinks

    if (assets.length != roadlinks.length) {
      awsDao.insertDataset(dataset.datasetId, write(dataset.features), write(dataset.roadlinks), DatasetStatus.FeatureRoadlinksDontMatch.value)
      None
    }
    else{
      awsDao.insertDataset(dataset.datasetId, write(dataset.features), write(dataset.roadlinks), DatasetStatus.Inserted.value)

      val featuresWithoutIds: List[Option[Int]] = (roadlinks, assets).zipped.map((featureRoadlinks, feature) => {
        feature.get("properties") match {
          case Some(prop) =>
            val properties = prop.asInstanceOf[Map[String, Any]]
            properties.get("id") match {
              case Some(id) =>
                val featureId = id.asInstanceOf[BigInt].longValue()
                val linkIdValidationStatus = linkIdValidation(dataset.datasetId, featureId, featureRoadlinks.toSet)

                val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type")
                val propertiesStatus: List[Int] = assetTypeGeometry match {
                  case "LineString" =>
                    properties("type").asInstanceOf[String] match {
                      case "Roadlink" => validateLinearAssets(dataset.datasetId, featureId, properties)
                      case _ =>
                        List(FeatureStatus.RoadlinkNoTypeInProperties.value)
                    }
                  case "Point" => validatePoint(dataset.datasetId, featureId, properties, properties("type").asInstanceOf[String])
                  case _ =>
                    List(FeatureStatus.NoGeometryType.value)
                }
                val featureStatus = linkIdValidationStatus :: propertiesStatus

                insertFeatureAndUpdateDataset(dataset.datasetId, featureId, featureStatus)
                None

              case None => awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.ErrorsFeatures.value)
                Some(1)
            }
          case None =>
            awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.ErrorsFeatures.value)
            None
        }
      })
      val totalFeaturesWithoutIds = featuresWithoutIds.flatten

      if(totalFeaturesWithoutIds.isEmpty){
        None
      } else {
        Some(totalFeaturesWithoutIds.sum)
      }
    }
  }

  def updateDataset(dataset: Dataset) = {
    if (awsDao.getDatasetStatus(dataset.datasetId) != DatasetStatus.FeatureRoadlinksDontMatch.value) {
      val assets = dataset.features("features").asInstanceOf[List[Map[String, Any]]]
      val roadlinks = dataset.roadlinks

      (roadlinks, assets).zipped.foreach((featureRoadlinks, feature) => {
        val properties = feature("properties").asInstanceOf[Map[String, Any]]

        properties.get("id") match {
          case Some(id) =>
            val featureId = id.asInstanceOf[BigInt].longValue()
            val status = awsDao.getFeatureStatus(featureId, dataset.datasetId)
            if (status == FeatureStatus.Inserted.value.toString) {

              val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type").asInstanceOf[String]
              val assetType = properties("type").asInstanceOf[String]

              assetTypeGeometry match {
                case "LineString" => assetType match {
                  case "Roadlink" =>
                    val links = roadLinkService.getRoadsLinksFromVVH(featureRoadlinks.toSet, false)
                      .filter(link => !Set(7,8,99).contains(link.functionalClass))

                    updateLinearAssets(properties, links)
                }
                  awsDao.updateFeatureStatus(featureId, FeatureStatus.Processed.value)

                case "Point" =>
                  val assetCoordinates = feature("geometry").asInstanceOf[Map[String, Any]]("coordinates").asInstanceOf[List[List[Double]]].head
                  val link = roadLinkService.getRoadLinkFromVVH(featureRoadlinks.head.longValue(), false).get
                  updatePoint(properties, link, assetType, assetCoordinates)
                  awsDao.updateFeatureStatus(featureId, FeatureStatus.Processed.value)
              }
            }
          case None =>
        }
      }
      )

      val errors = awsDao.getProcessedDatasetFeaturesForErrors(dataset.datasetId)
      if (errors == 0) {
        awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.Processed.value)
      } else {
        awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.ErrorsProcessing.value)
      }
    }
  }

  def getDatasetStatusById(datasetId: String): String = {
    DatasetStatus(awsDao.getDatasetStatus(datasetId)).description
  }

  def getFeatureErrorsByDatasetId(datasetId: String, datasetFeaturesWithoutIds: Int): Any = {
    val featuresStatusCode = awsDao.getAllFeatureIdAndStatusByDataset(datasetId).filter { case (_, status) => status != DatasetStatus.ErrorsFeatures.value.toString}

    val featuresStatusMap = featuresStatusCode.map { case (featureId, status) =>
      Map(
        "FeatureId" -> featureId.toString,
        "Message" -> status.split(",").map(message => FeatureStatus(message.toInt).description)
      )
    } ++ {
      if (datasetFeaturesWithoutIds != 0) {
        Map("Features without ids" -> datasetFeaturesWithoutIds.toString)
      } else {
        Map()
      }
    }

    featuresStatusMap
  }

  put("/assetUpdateFromAWS") {
    try {
      val listDatasets: List[Dataset] = parsedBody.extractOrElse[List[Dataset]](throw new ClassCastException)

      OracleDatabase.withDynTransaction {
        val datasetFeaturesWithoutIds: Map[String, Option[Int]] = listDatasets.flatMap(dataset =>
          Map(dataset.datasetId -> validateAndInsertDataset(dataset))).toMap

        listDatasets.foreach(dataset =>
          updateDataset(dataset)
        )

        listDatasets.map{dataset =>
          val datasetId = dataset.datasetId
          val datasetStatus = getDatasetStatusById(datasetId)
          if ((datasetStatus == DatasetStatus.Processed.description || datasetStatus == DatasetStatus.FeatureRoadlinksDontMatch.description) && datasetFeaturesWithoutIds(datasetId).isEmpty) {
            Map(
              "DataSetId" -> datasetId,
              "Status" -> datasetStatus
            )
          } else {
            Map(
              "DataSetId" -> datasetId,
              "Status" -> datasetStatus,
              "Features with errors" -> getFeatureErrorsByDatasetId(datasetId, datasetFeaturesWithoutIds(datasetId).get)
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
