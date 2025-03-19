package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode, UnknownLinkType}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LogUtils, PolygonTools}
import org.joda.time.DateTime

class TextValueLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  override def getAssetsByMunicipality(typeId: Int, municipality: Int, newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val (roadLinks) = roadLinkService.getRoadLinksWithComplementary(municipality)
    val linkIds = roadLinks.map(_.linkId)
    if(newTransaction) withDynTransaction {
      dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId)).filterNot(_.expired)
    } else dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId)).filterNot(_.expired)
  }

  override def fetchExistingAssetsByLinksIdsString(typeId: Int, linksIds: Set[String], removedLinkIds: Set[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val existingAssets = if (newTransaction) {
      withDynTransaction {
        dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linksIds.toSeq ++ removedLinkIds.toSeq, LinearAssetTypes.getValuePropertyId(typeId))
      }.filterNot(_.expired)
    } else {
      dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linksIds.toSeq ++ removedLinkIds.toSeq, LinearAssetTypes.getValuePropertyId(typeId)).filterNot(_.expired)
    }
    existingAssets
  }

  override def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets = if (newTransaction) {
      withDynTransaction {
          dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.getValuePropertyId(typeId))
      }.filterNot(_.expired)
    } else {
      dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.getValuePropertyId(typeId)).filterNot(_.expired)
    }
    existingAssets
  }

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction)
      withDynTransaction {
        dao.fetchAssetsWithTextualValuesByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
      }
    else
      dao.fetchAssetsWithTextualValuesByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
  }

  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchAssetsWithTextualValuesByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId))
    }
  }

  override protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], timeStamp: Option[Long], sideCode: Option[Int], informationSource: Option[Int] = None): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case TextualValue(textValue) =>
          dao.fetchAssetsWithTextualValuesByIds(Set(assetId), valuePropertyId).head
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

  override def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                         createdByFromUpdate: Option[String] = Some(""),
                                         createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None, externalIds: Seq[String] = Seq()): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      timeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, modifiedByFromUpdate, modifiedDateTimeFromUpdate, verifiedBy)
    value match {
      case TextualValue(textValue) =>
        dao.insertValue(id, LinearAssetTypes.getValuePropertyId(typeId), textValue)
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
          case TextualValue(textValue) =>
            dao.insertValue(a.id, LinearAssetTypes.getValuePropertyId(a.asset.typeId), textValue)
          case _ => None
        }
      })
    }

  }

  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None,  informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      val oldLinearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.getValuePropertyId(typeId)).head
      val newMeasures = measures.getOrElse(Measures(oldLinearAsset.startMeasure, oldLinearAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldLinearAsset.sideCode)
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(oldLinearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      value match {
        case TextualValue(textValue) =>
          if ((validateMinDistance(newMeasures.startMeasure, oldLinearAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldLinearAsset.endMeasure)) || newSideCode != oldLinearAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, TextualValue.apply(textValue), newSideCode, newMeasures, username, createTimeStamp(), Some(roadLink), fromUpdate = true, createdByFromUpdate = Some(username), verifiedBy = oldLinearAsset.verifiedBy, informationSource = informationSource))
          }
          else
            dao.updateValue(id, textValue, LinearAssetTypes.getValuePropertyId(typeId), username)
        case _ => Some(id)
      }
    }
  }

  override def validateCondition(asset: NewLinearAsset): Unit = {
    val euroAndExitPattern = "^[0-9|Ee][0-9|a-zA-Z]{0,2}$"
    asset.value match {
      case TextualValue(textValue) => textValue.split(",").forall(_.trim.matches(euroAndExitPattern)) match {
          case false => throw new AssetValueException(textValue)
          case _ => None
        }
      case _ => throw new AssetValueException("incorrect asset type")
    }
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon). Used by Digiroad2Api /linearassets/:id POST endpoint.
    */
  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
    val ids =  withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

      val linearAsset = dao.fetchAssetsWithTextualValuesByIds(Set(id), LinearAssetTypes.getValuePropertyId(assetTypeById(id))).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink),fromUpdate = true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }

  override def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
 val ids= withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

      val existing =  dao.fetchAssetsWithTextualValuesByIds(Set(id), LinearAssetTypes.getValuePropertyId(assetTypeById(id))).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id, expired = true, username)

      val(newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)),
          valueAgainstDigitization.map( createWithoutTransaction(existing.typeId, existing.linkId, _,  SideCode.AgainstDigitizing.value,  Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)))
      
      Seq(newId1, newId2).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }
  override def adjustAssets( ids: Seq[Long]): Seq[Long] = {
    withDynTransaction {
      val assetTypeById = assetDao.getAssetTypeId(ids).foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }
      val linearAsset = dao.fetchAssetsWithTextualValuesByIds(ids.toSet, LinearAssetTypes.getValuePropertyId(assetTypeById(ids.head)))
      adjustLinearAssetsAction(linearAsset.map(_.linkId).toSet, linearAsset.head.typeId, newTransaction = false)
    }
    ids
  }
}
