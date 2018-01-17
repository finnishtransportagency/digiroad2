package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{SideCode, UnknownLinkType}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.slick.jdbc.{StaticQuery => Q}

class TextValueLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  override def getAssetsByMunicipality(typeId: Int, municipality: Int): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
    val linkIds = roadLinks.map(_.linkId)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(changes, roadLinks)
    withDynTransaction {
      dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.getValuePropertyId(typeId))
    }.filterNot(_.expired)
  }

  override protected def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
          dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.getValuePropertyId(typeId))
      }.filterNot(_.expired)
    existingAssets
  }

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchAssetsWithTextualValuesByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
    }
  }

  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId))
    }
  }

  override protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]]) = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }
    toUpdate.foreach { linearAsset =>
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(TextualValue(textValue)) =>
            dao.updateValue(id, textValue, LinearAssetTypes.getValuePropertyId(linearAsset.typeId), LinearAssetTypes.VvhGenerated)
          case _ => None
        }
      }
    }
  }
  override protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case TextualValue(textValue) =>
          dao.fetchAssetsWithTextualValuesByIds(Set(assetId), valuePropertyId).head
        case _ => return None
      }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
    //Create New Asset
    val newAssetIDcreate = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, getVerifiedBy(username, oldAsset.typeId))

    Some(newAssetIDcreate)
  }

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                         createdByFromUpdate: Option[String] = Some(""),
                                         createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy)
    value match {
      case TextualValue(textValue) =>
        dao.insertValue(id, LinearAssetTypes.getValuePropertyId(typeId), textValue)
      case _ => None
    }
    id
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, measures: Option[Measures] = None, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      value match {
        case TextualValue(textValue) =>
          updateValueByExpiration(id, TextualValue(textValue), LinearAssetTypes.getValuePropertyId(typeId), username, measures, vvhTimeStamp, sideCode)
        case _ =>
          Some(id)
      }
    }
  }


}
