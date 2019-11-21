package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class AwsDao {
  def insertDataset(id: String, geojson: String, roadlinks: String, datasetStatus: Int) {
    sqlu"""insert into municipality_dataset(dataset_id, geojson, roadlinks, status)
          values ($id, $geojson, $roadlinks, $datasetStatus)
      """.execute
  }

  def updateDatasetStatus(dataset_id: String, status: Int) {
    sqlu"""update municipality_dataset
          set status = $status
          where dataset_id = $dataset_id
      """.execute
  }

  def insertFeature(feature_id: Long, dataset_id: String, status: String) {
    sqlu"""insert into municipality_feature(feature_id, dataset_id, status)
          values ($feature_id, $dataset_id, $status)
      """.execute
  }

  def updateFeatureStatus(feature_id: Long, status: Int) {
    sqlu"""update municipality_feature
          set status = $status
          where feature_id = $feature_id
      """.execute
  }

  def getDatasetStatus(dataset_id: String): Int = {
    sql"""select status
          from municipality_dataset
          where dataset_id = $dataset_id
      """.as[Int].first
  }

  def getFeatureStatus(feature_id: Long, dataSetId: String): String = {
    sql"""select status
          from municipality_feature
          where feature_id = $feature_id and dataset_id = $dataSetId
      """.as[String].first
  }

  def getAllFeatureIdAndStatusByDataset(dataset_id: String): List[(Long, String)] = {
    sql"""select feature_id,status
          from municipality_feature
          where dataset_id = $dataset_id
      """.as[(Long, String)].list
  }

  def getProcessedDatasetFeaturesForErrors(dataset_id: String): Int = {
    sql"""select count(*)
          from municipality_feature
          where dataset_id = $dataset_id and status != '1'
      """.as[Int].first
  }
}