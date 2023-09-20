package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLastModification, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LogUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.postgresql.util.PSQLException

class RoadWidthService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], generateUnknownBoolean: Boolean = true, showHistory: Boolean = false,
                                        roadLinkFilter: RoadLink => Boolean = _ => true): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, linkIds)
      }

    val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(existingAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))

    if(generateUnknownBoolean) generateUnknowns(roadLinks, linearAssets.groupBy(_.linkId), typeId) else linearAssets
  }

  /**
    * Make sure operations are small and fast
    * Do not try to use methods which also use event bus, publishing will not work
    * @param linksIds
    * @param typeId asset type
    */
  override def adjustLinearAssetsAction(linksIds: Set[String], typeId: Int, newTransaction: Boolean = true, adjustSideCode: Boolean = false): Unit = {
    if (newTransaction) withDynTransaction {action(false)} else action(newTransaction)

    def action(newTransaction: Boolean): Unit = {
      try {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksIds, newTransaction = newTransaction)
        val existingAssets = dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, roadLinks.map(_.linkId))
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
  
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, timeStamp: Long = createTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadLink = roadLinkService.getRoadLinksAndComplementariesByLinkIds(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, timeStamp, roadLink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId), informationSource = Some(MunicipalityMaintenainer.value))
      }
    }
  }

  /**
    * Saves updated linear asset from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def update(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, measures = measures, sideCode = sideCode, informationSource = Some(MunicipalityMaintenainer.value))
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
   val ids = withDynTransaction {
      val linearAsset = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(linearAsset.linkId).
        getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      val newIdsToReturn = existingValue match {
        case None => dao.updateExpiration(id, expired = true, username).toSeq
        case Some(value) => updateWithoutTransaction(Seq(id), value, username, measures = Some(Measures(existingLinkMeasures._1, existingLinkMeasures._2)), informationSource = Some(MunicipalityMaintenainer.value))
      }

      val createdIdOption = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.timeStamp,
        Some(roadLink), informationSource = Some(MunicipalityMaintenainer.value)))
      newIdsToReturn ++ Seq(createdIdOption).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }
}
