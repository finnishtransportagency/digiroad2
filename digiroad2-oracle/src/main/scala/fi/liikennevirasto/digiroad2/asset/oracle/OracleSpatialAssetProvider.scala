package fi.liikennevirasto.digiroad2.asset.oracle

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import scala.slick.driver.JdbcDriver.backend.Database

import org.joda.time.LocalDate
import org.slf4j.LoggerFactory

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import fi.liikennevirasto.digiroad2.user.{User, Role, UserProvider}

class OracleSpatialAssetProvider(userProvider: UserProvider) extends AssetProvider {
  val logger = LoggerFactory.getLogger(getClass)

  private def userCanModifyMunicipality(municipalityNumber: Int): Boolean = {
    val user = userProvider.getCurrentUser()
    user.configuration.roles.contains(Role.Operator) || user.configuration.authorizedMunicipalities.contains(municipalityNumber)
  }

  private def userCanModifyAsset(assetId: Long): Boolean =
    getAssetById(assetId).flatMap(a => a.municipalityNumber.map(userCanModifyMunicipality)).getOrElse(false)

  private def userCanModifyRoadLink(roadLinkId: Long): Boolean =
    getRoadLinkById(roadLinkId).map(rl => userCanModifyMunicipality(rl.municipalityNumber)).getOrElse(false)

  def getAssetTypes: Seq[AssetType] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getAssetTypes
    }
  }

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getAssetById(assetId)
    }
  }

  def getAssets(assetTypeId: Long, user: User, bounds: Option[BoundingRectangle], validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getAssets(assetTypeId, user, bounds, validFrom, validTo)
    }
  }

  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties = {
    Database.forDataSource(ds).withDynTransaction {
      if (!userCanModifyRoadLink(roadLinkId)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      OracleSpatialAssetDao.createAsset(assetTypeId, lon, lat, roadLinkId, bearing, creator, properties)
    }
  }

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues ++
      Database.forDataSource(ds).withDynTransaction {
        OracleSpatialAssetDao.getEnumeratedPropertyValues(assetTypeId)
      }
  }

  def updateAssetLocation(asset: Asset): AssetWithProperties = {
    Database.forDataSource(ds).withDynTransaction {
      if (!userCanModifyAsset(asset.id)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      OracleSpatialAssetDao.updateAssetLocation(asset)
    }
  }

  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit = {
    // TODO: Verify write access?
    val parallerSeq = roadlinks.par
    parallerSeq.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(20))
    parallerSeq.foreach(RoadlinkProvider.updateRoadLink(ds, _))
  }

  def getRoadLinks(user: User, bounds: Option[BoundingRectangle]): Seq[RoadLink] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getRoadLinks(user, bounds)
    }
  }

  def getRoadLinkById(roadLinkId: Long): Option[RoadLink] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getRoadLinkById(roadLinkId)
    }
  }

  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue]) {
    if (!userCanModifyAsset(assetId)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }

    Database.forDataSource(ds).withDynTransaction {
      if (AssetPropertyConfiguration.commonAssetPropertyColumns.keySet.contains(propertyId)) {
        OracleSpatialAssetDao.updateCommonAssetProperty(assetId, propertyId, propertyValues)
      } else {
        OracleSpatialAssetDao.updateAssetSpecificProperty(assetId, propertyId.toLong, propertyValues)
      }
    }
  }

  def deleteAssetProperty(assetId: Long, propertyId: String) {
    if (AssetPropertyConfiguration.commonAssetPropertyColumns.keySet.contains(propertyId)) {
      throw new IllegalArgumentException("Cannot delete common asset property value: " + propertyId)
    }
    Database.forDataSource(ds).withDynTransaction {
      if (!userCanModifyAsset(assetId)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      OracleSpatialAssetDao.deleteAssetProperty(assetId, propertyId)
    }
  }

  def getImage(imageId: Long): Array[Byte] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getImage(imageId)
    }
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    AssetPropertyConfiguration.commonAssetPropertyDescriptors.values.toSeq ++ Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.availableProperties(assetTypeId)
    }.sortBy(_.propertyId.toLong)
  }

}
