package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.ProhibitionClass._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LogUtils, PolygonTools}
import org.joda.time.DateTime
import org.postgresql.util.PSQLException

class ProhibitionCreationException(val response: Set[String]) extends RuntimeException {}

class ProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  override def assetFiller: AssetFiller = new OneWayAssetFiller
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = throw new UnsupportedOperationException("Not supported method")

  /**
    * Returns linear assets by asset type and asset ids. Used by Digiroad2Api /linearassets POST and /linearassets DELETE endpoints.
    */
  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        dao.fetchProhibitionsByIds(typeId, ids)
      }
    else
      dao.fetchProhibitionsByIds(typeId, ids)
  }

  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
       dao.fetchProhibitionsByLinkIds(typeId, linkIds)
      }
    else
      dao.fetchProhibitionsByLinkIds(typeId, linkIds)
  }

  override def fetchExistingAssetsByLinksIdsString(typeId: Int, linksIds: Set[String], removedLinkIds: Set[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] ={
    if (newTransaction)
      withDynTransaction {
        dao.fetchProhibitionsByLinkIds(typeId, (linksIds++removedLinkIds).toSeq)
      }
    else
      dao.fetchProhibitionsByLinkIds(typeId, (linksIds++removedLinkIds).toSeq)
  }

  override def getAssetsByMunicipality(typeId: Int, municipality: Int, newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val (roadLinks) = roadLinkService.getRoadLinksWithComplementary(municipality)
    val linkIds = roadLinks.map(_.linkId)
    if(newTransaction) withDynTransaction {
      dao.fetchProhibitionsByLinkIds(typeId, linkIds, includeFloating = false).filterNot(_.expired)
    } else dao.fetchProhibitionsByLinkIds(typeId, linkIds, includeFloating = false).filterNot(_.expired)

  }

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], generateUnknownBoolean: Boolean = true, showHistory: Boolean = false,
                                        roadLinkFilter: RoadLink => Boolean = _ => true): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)

    val existingAssets =
      withDynTransaction {
        dao.fetchProhibitionsByLinkIds(typeId, linkIds)
      }.filterNot(_.expired)

    val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(existingAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
    if(generateUnknownBoolean) generateUnknowns(roadLinks, linearAssets.groupBy(_.linkId), typeId) else linearAssets
  }

  /**
    * Make sure operations are small and fast
    * Do not try to use methods which also use event bus, publishing will not work
    * @param linksIds
    * @param typeId asset type
    */
  override def adjustLinearAssetsAction(linksIds: Set[String], typeId: Int, newTransaction: Boolean = true,adjustSideCode: Boolean = false): Unit = {
    if (newTransaction) withDynTransaction {action(false)} else action(newTransaction)
    def action(newTransaction: Boolean): Unit = {
      try {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksIds, newTransaction = newTransaction)
        val existingAssets = dao.fetchProhibitionsByLinkIds(typeId, roadLinks.map(_.linkId)).filterNot(_.expired)
        val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(existingAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
        val groupedAssets = linearAssets.groupBy(_.linkId)

        LogUtils.time(logger, s"Check for and adjust possible linearAsset adjustments on ${roadLinks.size} roadLinks. TypeID: $typeId") {
          if (adjustSideCode) adjustLinearAssetsSideCode(roadLinks, groupedAssets, typeId, geometryChanged = false)
          else adjustLinearAssets(roadLinks, groupedAssets, typeId, geometryChanged = false)
        }

      } catch {
        case e: PSQLException => logger.error(s"Database error happened on asset type ${typeId}, on links ${linksIds.mkString(",")} : ${e.getMessage}", e)
        case e: Throwable => logger.error(s"Unknown error happened on asset type ${typeId}, on links ${linksIds.mkString(",")} : ${e.getMessage}", e)
      }
    }
  }
  
  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      val oldAsset = dao.fetchProhibitionsByIds(typeId, Set(id)).head
      val newMeasures = measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldAsset.sideCode)
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(oldAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      value match {
        case prohibitions: Prohibitions =>
          if ((validateMinDistance(newMeasures.startMeasure, oldAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldAsset.endMeasure)) || newSideCode != oldAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, prohibitions, newSideCode, newMeasures, username, createTimeStamp(), Some(roadLink), verifiedBy = getVerifiedBy(username, oldAsset.typeId)))
          }
          else {
            dao.updateVerifiedInfo(Set(id), username)
            dao.updateProhibitionValue(id, typeId, prohibitions, username)
          }
        case _ =>
          Some(id)
      }
    }
  }

  protected def updateValueByExpiration(assetId: Long, prohibitions: Prohibitions, assetTypeId: Int, username: String, measures: Option[Measures], timeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    dao.fetchProhibitionsByIds(assetTypeId, Set(assetId)).headOption.map {
      oldAsset =>
        if (oldAsset.value.contains(prohibitions)) {
          dao.updateProhibitionValue(assetId, assetTypeId, prohibitions, username, measures).head
        } else {
          //Expire the old asset
          dao.updateExpiration(assetId)
          val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(oldAsset.linkId, newTransaction = false)
          //Create New Asset
          createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, prohibitions, sideCode.getOrElse(oldAsset.sideCode),
            measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, timeStamp.getOrElse(createTimeStamp()), roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, verifiedBy = getVerifiedBy(username, oldAsset.typeId))
        }
    }
  }

  override def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      timeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, modifiedByFromUpdate, modifiedDateTimeFromUpdate, verifiedBy)
    value match {
      case prohibitions: Prohibitions =>
        dao.insertProhibitionValue(id, typeId, prohibitions)
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
          case prohibitions: Prohibitions =>
            dao.insertProhibitionValue(a.id, a.asset.typeId, prohibitions)
          case _ => None
        }
      })
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
    val ids = withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

      val linearAsset = dao.fetchProhibitionsByIds(assetTypeById(id), Set(id)).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink)))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink)))
      Seq(existingId, createdId).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }

  override def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
   val ids = withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

      val existing = dao.fetchProhibitionsByIds(assetTypeById(id), Set(id)).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id, expired = true, username)

      val (newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink))),
          valueAgainstDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.AgainstDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink))))
      Seq(newId1, newId2).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }

  override def adjustAssets(ids: Seq[Long]): Seq[Long] = {
    withDynTransaction {
      val assetTypeById = assetDao.getAssetTypeId(ids).foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }
      val linearAsset = dao.fetchProhibitionsByIds(assetTypeById(ids.head), ids.toSet)
      adjustLinearAssetsAction(linearAsset.map(_.linkId).toSet, linearAsset.head.typeId, newTransaction = false)
    }
    ids
  }
  override def getChanged(typeId: Int, since: DateTime, until: DateTime, withAutoAdjust: Boolean = false, token: Option[String] = None): Seq[ChangedLinearAsset] = {
    val excludedTypes = Seq(PassageThrough, HorseRiding, SnowMobile, RecreationalVehicle, OversizedTransport)
    val prohibitions = withDynTransaction {
      dao.getProhibitionsChangedSince(typeId, since, until, excludedTypes, withAutoAdjust, token)
    }
    val roadLinks = roadLinkService.getRoadLinksByLinkIds(prohibitions.map(_.linkId).toSet)
    processLinearAssetChanges(prohibitions, roadLinks)
  }


}
