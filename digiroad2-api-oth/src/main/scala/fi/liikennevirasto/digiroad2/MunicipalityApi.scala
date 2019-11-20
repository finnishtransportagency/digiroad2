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

case class Dataset(datasetId: String, geoJson: Map[String, Any], roadlinks: List[List[BigInt]]) extends Serializable

sealed trait DatasetStatus{
  def value: Int
  def description: String
}

object DatasetStatus{
  val values = Set[DatasetStatus](Inserted, FeatureRoadlinksDontMatch, ErrorsFeatures, Processed, ErrorsProcessing)

  def apply(intValue: Int): DatasetStatus= {
    values.find(_.value == intValue).getOrElse(ErrorsProcessing)
  }

  def apply(stringValue: String): DatasetStatus= {
    values.find(_.description == stringValue).getOrElse(ErrorsProcessing)
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

  def apply(stringValue: String): FeatureStatus= {
    values.find(_.description == stringValue).getOrElse(ErrorsWhileUpdating)
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
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
  }

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def awsDao: AwsDao = new AwsDao

  final val AwsUser = "AwsUpdater"

  private def linkIdValidation(datasetId: String, featureId: Long, linkIds: Set[Long]): (Int, Int) = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds, false)
    if(!(linkIds.nonEmpty && roadLinks.count(road => road.administrativeClass != State) == linkIds.size))
    {
      (FeatureStatus.WrongRoadlinks.value, 0)
    } else {
      (FeatureStatus.Inserted.value, 0)
    }
  }

  private def validatePoint(datasetId: String, featureId: Long, properties: Map[String, Any], assetType: String): List[(Int, Int)] = {
    assetType match {
      case "obstacle" =>
        properties.get("class") match {
          case Some(value) =>
            if (!Set(1, 2).contains(value.asInstanceOf[BigInt].intValue())) {
              List((FeatureStatus.WrongObstacleClass.value, 0))
            } else {
              List((FeatureStatus.Inserted.value, 0))
            }
          case None =>
            List((FeatureStatus.WrongObstacleClass.value, 0))
        }
    }
  }

  private def validateRoadlink(datasetId: String, featureId: Long, properties: Map[String, Any]): List[(Int, Int)] = {
    val speedLimit = properties.get("speedLimit")
    val pavementClass = properties.get("pavementClass")
    val sideCode = properties.get("sideCode")

    val speedlimitStatus: (Int, Int) = speedLimit match {
      case Some(value) =>
        if (!Set("20", "30", "40", "50", "60", "70", "80", "90", "100", "120").contains(value.asInstanceOf[String])) {
          (FeatureStatus.WrongSpeedLimit.value, 0)
        } else {
          (FeatureStatus.Inserted.value, 0)
        }
      case None => (FeatureStatus.Inserted.value, 0)
    }

    val pavementClassStatus: (Int, Int) = pavementClass match {
      case Some(value) =>
        if (!Seq("1", "2", "10", "20", "30", "40", "50").contains(value.asInstanceOf[String])) {
          (FeatureStatus.WrongPavementClass.value, 0)
        } else {
          (FeatureStatus.Inserted.value, 0)
        }
      case None => (FeatureStatus.Inserted.value, 0)
    }

    val sideCodeStatus: (Int, Int) = sideCode match {
      case Some(value) =>
        if (!(value.asInstanceOf[BigInt] == 1)) {
          (FeatureStatus.WrongSideCode.value, 0)
        }else {
          (FeatureStatus.Inserted.value, 0)
        }
      case None =>
        (FeatureStatus.WrongSideCode.value, 0)
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

  private def updateRoadlink(properties: Map[String, Any], links: Seq[RoadLink]) = {
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
          duplicate.isEmpty match {
            case false =>
              pavedRoadService.update(Seq(duplicate.head.id), pavementClassValue, AwsUser)
            case true =>
              val newPavementClassAsset = NewLinearAsset(link.linkId, 0, link.length, pavementClassValue, properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
              pavedRoadService.create(Seq(newPavementClassAsset), PavedRoad.typeId, AwsUser)
          }
        case None =>
      }
    }
  }

  def validateAndInsertDataset(dataset: Dataset): Int = {
    val assets = dataset.geoJson("features").asInstanceOf[List[Map[String, Any]]]
    val roadlinks = dataset.roadlinks

    if (assets.length != dataset.roadlinks.length) {
      awsDao.insertDataset(dataset.datasetId, write(dataset.geoJson), write(dataset.roadlinks), DatasetStatus.FeatureRoadlinksDontMatch.value)
    }
    else{
      awsDao.insertDataset(dataset.datasetId, write(dataset.geoJson), write(dataset.roadlinks), DatasetStatus.Inserted.value)
    }

    if (awsDao.getDatasetStatus(dataset.datasetId) != DatasetStatus.FeatureRoadlinksDontMatch.value) {
      val featuresWithoutIds: Int = (roadlinks, assets).zipped.map((featureRoadlinks, feature) => {
        val missingIds: Int = feature.get("properties") match {
          case Some(prop) =>
            val properties = prop.asInstanceOf[Map[String, Any]]
            val status: List[(Int, Int)] = properties.get("id") match {
              case Some(id) =>
                val featureId = id.asInstanceOf[BigInt].longValue()
                try {
                  val linkIdValidationStatus = linkIdValidation(dataset.datasetId, featureId, featureRoadlinks.map(number => number.longValue()).toSet)

                  val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type")
                  val propertiesStatus = assetTypeGeometry match {
                    case "LineString" =>
                      properties("type").asInstanceOf[String] match {
                        case "Roadlink" => validateRoadlink(dataset.datasetId, featureId, properties)
                        case _ =>
                          List((FeatureStatus.RoadlinkNoTypeInProperties.value, 0))
                      }
                    case "Point" => validatePoint(dataset.datasetId, featureId, properties, properties("type").asInstanceOf[String])
                    case _ =>
                      List((FeatureStatus.NoGeometryType.value, 0))
                  }
                  linkIdValidationStatus :: propertiesStatus
                } catch {
                  case _: Throwable =>
                    List((FeatureStatus.ErrorsWhileValidating.value, 0))
                }
              case None =>
                List((FeatureStatus.ErrorsWhileValidating.value, 1))
            }
            val (featureStatus, featuresWithoutIds): (List[Int], List[Int]) = status.unzip

            if (!featuresWithoutIds.contains(1)) {
              val allFeatureStatus = featureStatus.distinct.filterNot(code => code == FeatureStatus.Inserted.value)
              if (allFeatureStatus.isEmpty) {
                awsDao.insertFeature(properties("id").asInstanceOf[BigInt].longValue(), dataset.datasetId, FeatureStatus.Inserted.value.toString)
              } else {
                awsDao.insertFeature(properties("id").asInstanceOf[BigInt].longValue(), dataset.datasetId, allFeatureStatus.mkString(","))
                awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.ErrorsFeatures.value)
              }
            } else {
              awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.ErrorsFeatures.value)
            }

            featuresWithoutIds.sum
          case None =>
            awsDao.updateDatasetStatus(dataset.datasetId, DatasetStatus.ErrorsFeatures.value)
            1
        }
        missingIds
      }).sum
      featuresWithoutIds
    } else {
      0
    }
  }

  def updateDataset(dataset: Dataset) = {
    if (awsDao.getDatasetStatus(dataset.datasetId) != DatasetStatus.FeatureRoadlinksDontMatch.value) {
      val assets = dataset.geoJson("features").asInstanceOf[List[Map[String, Any]]]
      val roadlinks = dataset.roadlinks

      (roadlinks, assets).zipped.foreach((featureRoadlinks, feature) => {
        val properties = feature("properties").asInstanceOf[Map[String, Any]]

        properties.get("id") match {
          case Some(id) =>
            val featureId = id.asInstanceOf[BigInt].longValue()
            val status = awsDao.getFeatureStatus(featureId, dataset.datasetId)
            try {
              if (status == FeatureStatus.Inserted.value.toString) {

                val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type").asInstanceOf[String]
                val assetType = properties("type").asInstanceOf[String]

                assetTypeGeometry match {
                  case "LineString" => assetType match {
                    case "Roadlink" =>
                      val links = roadLinkService.getRoadsLinksFromVVH(featureRoadlinks.map(_.longValue()).toSet, false)
                        .filter(link => !Set(7,8,99).contains(link.functionalClass))

                      updateRoadlink(properties, links)
                  }
                    awsDao.updateFeatureStatus(featureId, FeatureStatus.Processed.value)

                  case "Point" =>
                    val assetCoordinates = feature("geometry").asInstanceOf[Map[String, Any]]("coordinates").asInstanceOf[List[List[Double]]].head
                    val link = roadLinkService.getRoadLinkFromVVH(featureRoadlinks.head.longValue(), false).get
                    updatePoint(properties, link, assetType, assetCoordinates)
                    awsDao.updateFeatureStatus(featureId, FeatureStatus.Processed.value)
                }
              }
            } catch {
              case _: Throwable =>
                awsDao.updateFeatureStatus(featureId, FeatureStatus.ErrorsWhileUpdating.value)
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

    val featuresStatusMap = featuresStatusCode.map(tuple =>
      Map(
        "FeatureId" -> tuple._1.toString,
        "Message" -> tuple._2.split(",").map(message => FeatureStatus(message.toInt).description)
      )
    ) ++ {
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
      val jsonDatasets: List[List[Any]] = parsedBody.extractOrElse[List[List[Any]]](throw new ClassCastException)

      val listDatasets = jsonDatasets.map(data =>
        Dataset(data.head.asInstanceOf[String], data(1).asInstanceOf[Map[String, Any]], data(2).asInstanceOf[List[List[BigInt]]])
      )

      OracleDatabase.withDynTransaction {
        val datasetFeaturesWithoutIds: Map[String, Int] = listDatasets.flatMap(dataset =>
          Map(dataset.datasetId -> validateAndInsertDataset(dataset))).toMap

        listDatasets.foreach(dataset =>
          updateDataset(dataset)
        )

        listDatasets.map{dataset =>
          val datasetId = dataset.datasetId
          val datasetStatus = getDatasetStatusById(datasetId)
          if ((datasetStatus == DatasetStatus.Processed.description || datasetStatus == DatasetStatus.FeatureRoadlinksDontMatch.description) && datasetFeaturesWithoutIds(datasetId) == 0) {
            Map(
              "DataSetId" -> datasetId,
              "Status" -> datasetStatus
            )
          } else {
            Map(
              "DataSetId" -> datasetId,
              "Status" -> datasetStatus,
              "Features with errors" -> getFeatureErrorsByDatasetId(datasetId, datasetFeaturesWithoutIds(datasetId))
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
