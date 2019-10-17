package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class AwsDao {
  def insertDataset(id: String, geojson: String, roadlinks: String, datasetStatus: Int) {
    OracleDatabase.withDynTransaction {
      sqlu"""insert into dataset(dataset_id, geojson, roadlinks, status)
          values ($id, $geojson, $roadlinks, $datasetStatus)
      """.execute
    }
  }

  def updateDatasetStatus(dataset_id: String, status: Int) {
    OracleDatabase.withDynTransaction {
      sqlu"""update dataset
          set status = $status
          where dataset_id = $dataset_id
      """.execute
    }
  }

  def insertFeature(feature_id: Long, dataset_id: String, status: Int) {
    OracleDatabase.withDynTransaction {
      sqlu"""insert into feature(feature_id, dataset_id, status)
          values ($feature_id, $dataset_id, $status)
      """.execute
    }
  }

  def updateFeatureStatus(feature_id: Long, status: String) {
    val statusText = "," + status
    OracleDatabase.withDynTransaction {
      sqlu"""update feature
          set status = (select status from feature where feature_id = $feature_id) || $statusText
          where feature_id = $feature_id
      """.execute
    }
  }

  def checkDatasetStatus(dataset_id: String): String = {
    OracleDatabase.withDynSession {
      sql"""select status
          from dataset
          where dataset_id = $dataset_id
      """.as[String].first
    }
  }

  def checkFeatureStatus(feature_id: Long): String = {
    OracleDatabase.withDynSession {
      sql"""select status
          from feature
          where feature_id = $feature_id
      """.as[String].first
    }
  }

  def checkAllFeatureIdAndStatusByDataset(dataset_id: String): List[(Long, String)] = {
    OracleDatabase.withDynSession {
      sql"""select feature_id,status
          from feature
          where dataset_id = $dataset_id
      """.as[(Long, String)].list
    }
  }

  def checkProcessedDatasetFeaturesForErrors(dataset_id: String): Int = {
    OracleDatabase.withDynSession {
      sql"""select count(*)
          from feature
          where dataset_id = $dataset_id and status != '0,2'
      """.as[Int].first
    }
  }
}