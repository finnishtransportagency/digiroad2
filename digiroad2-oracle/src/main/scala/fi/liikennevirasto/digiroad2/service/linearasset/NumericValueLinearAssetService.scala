package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LogUtils, PolygonTools}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

class NumericValueLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  override def getAssetsByMunicipality(typeId: Int, municipality: Int, newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementary(municipality)
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    if(newTransaction) withDynTransaction {
      dao.fetchLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)
    } else dao.fetchLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)
  }

  override def fetchExistingAssetsByLinksIdsString(typeId: Int, linksIds: Set[String], removedLinkIds: Set[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val existingAssets = if (newTransaction) {
      withDynTransaction {
        dao.fetchLinearAssetsByLinkIds(typeId, (linksIds ++ removedLinkIds).toSeq, LinearAssetTypes.numericValuePropertyId)
      }.filterNot(_.expired)
    } else {
      dao.fetchLinearAssetsByLinkIds(typeId,(linksIds ++ removedLinkIds).toSeq, LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)
    }
    existingAssets
  }
  
  override def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets = if (newTransaction) {
      withDynTransaction {
        dao.fetchLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId)
      }.filterNot(_.expired)
    } else {
      dao.fetchLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)
    }
    existingAssets
  }


  override protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], timeStamp: Option[Long], sideCode: Option[Int], informationSource: Option[Int] = None): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case NumericValue(intValue) =>
          dao.fetchLinearAssetsByIds(Set(assetId), valuePropertyId).head
        case _ => return None
      }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(oldAsset.linkId, newTransaction = false)
    //Create New Asset
    val newAssetIDcreate = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, timeStamp.getOrElse(createTimeStamp()), roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, verifiedBy = getVerifiedBy(username, oldAsset.typeId))

    Some(newAssetIDcreate)
  }

  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None,  informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      value match {
        case NumericValue(intValue) =>
          updateValueByExpiration(id, NumericValue(intValue), LinearAssetTypes.numericValuePropertyId, username, measures, timeStamp, sideCode)
        case _ =>
          Some(id)
      }
    }
  }

  override def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                         createdByFromUpdate: Option[String] = Some(""),
                                         createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int]): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      timeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, modifiedByFromUpdate, modifiedDateTimeFromUpdate, verifiedBy, geometry = getGeometry(roadLink))
    value match {
      case NumericValue(intValue) =>
        dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
      case _ => None
    }
    id
  }
  override def createMultipleLinearAssets(list: Seq[NewLinearAssetMassOperation]): Unit = {
    val assetsSaved = dao.createMultipleLinearAssets(list)
    LogUtils.time(logger,"Saving assets properties"){
      assetsSaved.foreach(a => {
        val value = a.asset.value
        value match {
          case NumericValue(intValue) =>
            dao.insertValue(a.id, LinearAssetTypes.numericValuePropertyId, intValue)
          case _ => None
        }
      })
    }
  }

}
