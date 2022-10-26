package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{LogUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime

import scala.slick.jdbc.{StaticQuery => Q}

class PavedRoadService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  val defaultMultiTypePropSeq = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("99")))))
  val defaultPropertyData = DynamicValue(defaultMultiTypePropSeq)
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = throw new UnsupportedOperationException("Not supported method")

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink]): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)

    val existingAssets =
      withDynTransaction {
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(PavedRoad.typeId, linkIds)
      }.filterNot(_.expired)

    val groupedAssets = existingAssets.groupBy(_.linkId)
    val adjustedAssets = withDynTransaction {
      LogUtils.time(logger, "Check for and adjust possible linearAsset adjustments on " + roadLinks.size + " roadLinks. TypeID: " + typeId) {
        adjustLinearAssets(roadLinks, groupedAssets, typeId)
      }
    }
    adjustedAssets
  }

  def getPavedRoadAssetChanges(existingLinearAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink],
                            changeInfos: Seq[ChangeInfo], typeId: Long): (Set[Long], Seq[PersistedLinearAsset]) = {

    //Group last vvhchanges by link id
    val lastChanges = changeInfos.filter(_.newId.isDefined).groupBy(_.newId.get).mapValues(c => c.maxBy(_.timeStamp))

    //Map all existing assets by roadlink and changeinfo
    val changedAssets = lastChanges.map{
      case (linkId, changeInfo) =>
        (roadLinks.find(_.linkId == linkId), changeInfo, existingLinearAssets.filter(_.linkId == linkId))
    }

    /* Note: This uses isNotPaved that excludes "unknown" pavement status. In OTH unknown means
    *  "no pavement" but in case OTH has pavement info with value 1 then VVH "unknown" should not affect OTH.
    *  Additionally, should there be an override that is later fixed we let the asset expire here as no
    *  override is needed anymore.
    */
    val expiredAssetsIds = changedAssets.flatMap {
      case (Some(roadLink), changeInfo, assets) =>
        if (roadLink.isNotPaved && assets.nonEmpty)
          assets.filter(_.timeStamp < changeInfo.timeStamp).map(_.id)
        else
          List()
      case _ =>
        List()
    }.toSet[Long]

    /* Note: This will not change anything if asset is stored using value None (null in database)
    *  This is the intended consequence as it enables the UI to write overrides to VVH pavement info */
    val newAndUpdatedAssets = changedAssets.flatMap{
      case (Some(roadLink), changeInfo, assets) =>
        if(roadLink.isPaved)
          if (assets.isEmpty)
            Some(PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(defaultPropertyData), 0,
              GeometryUtils.geometryLength(roadLink.geometry), None, None, None, None, false,
              PavedRoad.typeId, changeInfo.timeStamp, None, linkSource = roadLink.linkSource, None, None, Some(MmlNls)))
          else
            assets.filterNot(a => expiredAssetsIds.contains(a.id) ||
              (a.value.isEmpty || a.timeStamp >= changeInfo.timeStamp)
            ).map(a => a.copy(timeStamp = changeInfo.timeStamp, value=a.value,
              startMeasure=0.0, endMeasure=roadLink.length, informationSource = Some(MmlNls)))
        else
          None
      case _ =>
        None
    }.toSeq

    (expiredAssetsIds, newAndUpdatedAssets)
  }

  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    ids.flatMap { id =>
      value match {
        case DynamicValue(multiTypeProps) =>
          updateValueByExpiration(id, DynamicValue(multiTypeProps), LinearAssetTypes.numericValuePropertyId, username, measures, timeStamp, sideCode, informationSource = informationSource)
        case _ =>
          Some(id)
      }
    }
  }

  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, timeStamp: Long = createTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadlinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.flatMap { newAsset =>
        Some(createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, timeStamp, roadlinks.find(_.linkId == newAsset.linkId), informationSource = Some(MunicipalityMaintenainer.value)))
      }
    }
  }

  override protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures] = None, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, informationSource: Option[Int] = None): Option[Long] = {
    //Get Old Asset
    val oldAsset =
    valueToUpdate match {
      case DynamicValue(multiTypeProps) =>
        enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(assetId))).head
      case _ => return None
    }

    val measure = measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure))

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(oldAsset.linkId, newTransaction = false)
    if (valueToUpdate.toJson == 0 && measures.nonEmpty){
      Seq(Measures(oldAsset.startMeasure, measure.startMeasure), Measures(measure.endMeasure, oldAsset.endMeasure)).map {
        m =>
          if (m.endMeasure - m.startMeasure > 0.01)
            createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
              m, username, timeStamp.getOrElse(createTimeStamp()), roadLink, true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())), informationSource = informationSource)
      }
      Some(0L)
    }else {
      Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
        measure, username, timeStamp.getOrElse(createTimeStamp()), roadLink, true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())), informationSource = informationSource))
    }
  }

  /**
    * Saves updated linear asset from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def update(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, informationSource = Some(MunicipalityMaintenainer.value))
    }
  }

  override def update(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, timeStamp, sideCode, measures, informationSource = Some(MunicipalityMaintenainer.value))
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(linearAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      val newIdsToReturn = existingValue match {
        case None => dao.updateExpiration(id, expired = true, username).toSeq
        case Some(value) => updateWithoutTransaction(Seq(id), value, username, measures = Some(Measures(existingLinkMeasures._1, existingLinkMeasures._2)), informationSource = Some(MunicipalityMaintenainer.value))
      }

      val createdIdOption = createdValue.map(
        createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.timeStamp,
          Some(roadLink), informationSource = Some(MunicipalityMaintenainer.value)))

      newIdsToReturn ++ Seq(createdIdOption).flatten
    }
  }
}
