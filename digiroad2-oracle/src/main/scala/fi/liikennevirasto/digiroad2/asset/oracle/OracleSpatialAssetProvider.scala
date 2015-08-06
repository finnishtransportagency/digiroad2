package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.{EventBusMassTransitStop, DigiroadEventBus, Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import org.apache.commons.lang3.StringUtils.isBlank
import org.joda.time.LocalDate
import org.slf4j.LoggerFactory

import scala.slick.driver.JdbcDriver.backend.Database

trait DatabaseTransaction {
  def withDynTransaction[T](f: => T): T
}
object DefaultDatabaseTransaction extends DatabaseTransaction {
  override def withDynTransaction[T](f: => T): T = Database.forDataSource(ds).withDynTransaction(f)
}

// FIXME:
// - move common asset functionality to asset service
class OracleSpatialAssetProvider(eventbus: DigiroadEventBus, userProvider: UserProvider, databaseTransaction: DatabaseTransaction = DefaultDatabaseTransaction) extends AssetProvider {
  val logger = LoggerFactory.getLogger(getClass)

  private def getMunicipalityName(roadLinkId: Long): String = {
    val municipalityId = RoadLinkService.getMunicipalityCode(roadLinkId)
    municipalityId.map { OracleSpatialAssetDao.getMunicipalityNameByCode(_) }.get
  }

  private def userCanModifyMunicipality(municipalityNumber: Int): Boolean = {
    val user = userProvider.getCurrentUser()
    user.isOperator() || user.isAuthorizedToWrite(municipalityNumber)
  }

  private def userCanModifyAsset(asset: AssetWithProperties): Boolean =
    userCanModifyMunicipality(asset.municipalityNumber)

  private def userCanModifyRoadLink(roadLinkId: Long): Boolean =
    RoadLinkService.getMunicipalityCode(roadLinkId).map(userCanModifyMunicipality(_)).getOrElse(false)

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    databaseTransaction.withDynTransaction {
      OracleSpatialAssetDao.getAssetById(assetId)
    }
  }

  def getAssetByExternalId(externalId: Long): Option[AssetWithProperties] = {
    databaseTransaction.withDynTransaction {
      OracleSpatialAssetDao.getAssetByExternalId(externalId)
    }
  }

  def getAssetPositionByExternalId(externalId: Long): Option[Point] = {
    databaseTransaction.withDynTransaction {
      OracleSpatialAssetDao.getAssetPositionByExternalId(externalId)
    }
  }

  def getAssets(user: User, bounds: BoundingRectangle, validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = {
    databaseTransaction.withDynTransaction {
      OracleSpatialAssetDao.getAssets(user, Some(bounds), validFrom, validTo)
    }
  }

  private def validatePresenceOf(requiredProperties: Set[String], properties: Seq[SimpleProperty]): Unit = {
    val providedProperties = properties.map { property =>
      property.publicId
    }.toSet
    val missingProperties = requiredProperties -- providedProperties
    if (!missingProperties.isEmpty) {
      throw new IllegalArgumentException("Missing required properties: " + missingProperties.mkString(", "))
    }
  }

  private def validateMultipleChoice(propertyPublicId: String, values: Seq[PropertyValue]): Unit = {
    values.foreach { value =>
      if (value.propertyValue == "99") throw new IllegalArgumentException("Invalid value for property " + propertyPublicId)
    }
  }

  private def validateNotBlank(propertyPublicId: String, values: Seq[PropertyValue]): Unit = {
    values.foreach { value =>
      if (isBlank(value.propertyValue)) throw new IllegalArgumentException("Invalid value for property " + propertyPublicId)
    }
  }

  private def validateRequiredPropertyValues(requiredProperties: Set[Property], properties: Seq[SimpleProperty]): Unit = {
    requiredProperties.foreach { requiredProperty =>
      val values = properties.find(_.publicId == requiredProperty.publicId).get.values
      requiredProperty.propertyType match {
        case PropertyTypes.MultipleChoice => validateMultipleChoice(requiredProperty.publicId, values)
        case _ => validateNotBlank(requiredProperty.publicId, values)
      }
    }
  }

  private def eventBusMassTransitStop(asset: AssetWithProperties, municipalityName: String): EventBusMassTransitStop = {
    EventBusMassTransitStop(municipalityNumber = asset.municipalityNumber, municipalityName = municipalityName,
      nationalId = asset.nationalId, lon = asset.lon, lat = asset.lat, bearing = asset.bearing, validityDirection = asset.validityDirection,
      created = asset.created, modified = asset.modified, propertyData = asset.propertyData)
  }

  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties = {
    val definedProperties = properties.filterNot( simpleProperty => simpleProperty.values.isEmpty )
    databaseTransaction.withDynTransaction {
      val requiredProperties = OracleSpatialAssetDao.requiredProperties(assetTypeId)
      validatePresenceOf(Set(AssetPropertyConfiguration.ValidityDirectionId) ++ requiredProperties.map(_.publicId), definedProperties)
      validateRequiredPropertyValues(requiredProperties, properties)
      if (!userCanModifyRoadLink(roadLinkId)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      val asset = OracleSpatialAssetDao.createAsset(assetTypeId, lon, lat, roadLinkId, bearing, creator, definedProperties)
      eventbus.publish("asset:saved", eventBusMassTransitStop(asset, getMunicipalityName(roadLinkId)))
      asset
    }
  }

  def updateAsset(assetId: Long, position: Option[Position], properties: Seq[SimpleProperty]): AssetWithProperties = {
    databaseTransaction.withDynTransaction {
      val asset = OracleSpatialAssetDao.getAssetById(assetId).get
      if (!userCanModifyAsset(asset)) { throw new IllegalArgumentException("User does not have write access to municipality") }
      val updatedAsset = OracleSpatialAssetDao.updateAsset(assetId, position, userProvider.getCurrentUser().username, properties)
      val municipalityName = OracleSpatialAssetDao.getMunicipalityNameByCode(updatedAsset.municipalityNumber)
      eventbus.publish("asset:saved", eventBusMassTransitStop(updatedAsset, municipalityName))
      updatedAsset
    }
  }

  def updateAssetByExternalId(externalId: Long, properties: Seq[SimpleProperty]): AssetWithProperties = {
    databaseTransaction.withDynTransaction {
      val optionalAsset = OracleSpatialAssetDao.getAssetByExternalId(externalId)
      optionalAsset match {
        case Some(asset) =>
          if (!userCanModifyAsset(asset)) { throw new IllegalArgumentException("User does not have write access to municipality") }
          OracleSpatialAssetDao.updateAsset(asset.id, None, userProvider.getCurrentUser().username, properties)
        case None => throw new AssetNotFoundException(externalId)
      }
    }
  }


  def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[SimpleProperty], roadTypeLimitations: Set[AdministrativeClass]): Either[AdministrativeClass, AssetWithProperties] = {
    databaseTransaction.withDynTransaction {
      val optionalAsset = OracleSpatialAssetDao.getAssetByExternalId(externalId)
      optionalAsset match {
        case Some(asset) =>
          if (!userCanModifyAsset(asset)) { throw new IllegalArgumentException("User does not have write access to municipality") }
          val roadLinkType = asset.roadLinkType
          if (roadTypeLimitations(roadLinkType)) Right(OracleSpatialAssetDao.updateAsset(asset.id, None, userProvider.getCurrentUser().username, properties))
          else Left(roadLinkType)
        case None => throw new AssetNotFoundException(externalId)
      }
    }
  }

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues ++
      databaseTransaction.withDynTransaction {
        OracleSpatialAssetDao.getEnumeratedPropertyValues(assetTypeId)
      }
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    (AssetPropertyConfiguration.commonAssetProperties.values.map(_.propertyDescriptor).toSeq ++ databaseTransaction.withDynTransaction {
      OracleSpatialAssetDao.availableProperties(assetTypeId)
    }).sortBy(_.propertyUiIndex)
  }

  def getMunicipalities: Seq[Int] = {
    Database.forDataSource(ds).withDynSession {
      OracleSpatialAssetDao.getMunicipalities
    }
  }

  def assetPropertyNames(language: String): Map[String, String] = {
    AssetPropertyConfiguration.assetPropertyNamesByLanguage(language) ++ databaseTransaction.withDynTransaction {
      OracleSpatialAssetDao.assetPropertyNames(language)
    }
  }
}
