package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.TrafficSignType
import fi.liikennevirasto.digiroad2.asset.ProhibitionClass._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService, TrafficSignToGenerateLinear}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.AssetValidatorProcess.dr2properties
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProhibitionCreationException(val response: Set[String]) extends RuntimeException {}

class ProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  lazy val userProvider: UserProvider = {
    Class.forName(dr2properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }
  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, userProvider, eventBus)

  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)

  override def municipalityDao: MunicipalityDao = new MunicipalityDao

  override def eventBus: DigiroadEventBus = eventBusImpl

  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

  override def polygonTools: PolygonTools = new PolygonTools()

  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def assetFiller: AssetFiller = new ProhibitionFiller

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

  override def getAssetsByMunicipality(typeId: Int, municipality: Int): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    withDynTransaction {
      dao.fetchProhibitionsByLinkIds(typeId, linkIds ++ removedLinkIds, includeFloating = false)
    }.filterNot(_.expired)
  }

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)

    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)

    val existingAssets =
      withDynTransaction {
        dao.fetchProhibitionsByLinkIds(typeId, linkIds ++ removedLinkIds, includeFloating = false)
      }.filterNot(_.expired)

    val timing = System.currentTimeMillis

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

    val projectableTargetRoadLinks = roadLinks.filter(
      rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)


    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment])

    val combinedAssets = existingAssets.filterNot(a => assetsWithoutChangedLinks.exists(_.id == a.id))

    val (newAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes, initChangeSet)

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

    val newAssetsOnFilledTopology = filledTopology.filter(ft => ft.id == 0 && ft.createdBy.contains("automatic_process_prohibitions")).map { asset =>
      PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value, asset.value, asset.startMeasure,
        asset.endMeasure, asset.createdBy, asset.createdDateTime, asset.modifiedBy, asset.modifiedDateTime,
        asset.expired, asset.typeId, asset.vvhTimeStamp, asset.geomModifiedDate, asset.linkSource, asset.verifiedBy,
        asset.verifiedDate, asset.informationSource)
    }

    //Remove the asset ids adjusted in the "prohibition:saveProjectedProhibition" otherwise if the "prohibition:saveProjectedLinearAssets" is executed after the "linearAssets:update"
    //it will update the mValues to the previous ones
    eventBus.publish("prohibition:update", changeSet)
    eventBus.publish("prohibition:saveProjectedProhibition", newAssets.filter(_.id == 0L) ++ newAssetsOnFilledTopology)

    filledTopology
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected prohibition assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
      if (toUpdate.nonEmpty) {
        val prohibitions = toUpdate.filter(a => Set(LinearAssetTypes.ProhibitionAssetTypeId).contains(a.typeId))
        val persisted = dao.fetchProhibitionsByIds(LinearAssetTypes.ProhibitionAssetTypeId, prohibitions.map(_.id).toSet).groupBy(_.id)
        updateProjected(toUpdate, persisted)
        if (newLinearAssets.nonEmpty)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
      }
      toInsert.foreach { linearAsset =>
        val id =
          (linearAsset.createdBy, linearAsset.createdDateTime) match {
            case (Some(createdBy), Some(createdDateTime)) =>
              dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
                Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
                getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate)
            case _ =>
              dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
                Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
          }
        linearAsset.value match {
          case Some(prohibitions: Prohibitions) =>
            dao.insertProhibitionValue(id, prohibitions)
          case _ => None
        }
      }
      if (newLinearAssets.nonEmpty)
        logger.info("Added assets for linkids " + toInsert.map(_.linkId))
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
          case Some(prohibitions: Prohibitions) =>
            dao.updateProhibitionValue(id, prohibitions, LinearAssetTypes.VvhGenerated)
          case _ => None
        }
      }
    }
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      val oldAsset = dao.fetchProhibitionsByIds(typeId, Set(id)).head
      val newMeasures = measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldAsset.sideCode)
      val roadLink = vvhClient.fetchRoadLinkByLinkId(oldAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      value match {
        case prohibitions: Prohibitions =>
          if ((validateMinDistance(newMeasures.startMeasure, oldAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldAsset.endMeasure)) || newSideCode != oldAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, prohibitions, newSideCode, newMeasures, username, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), verifiedBy = getVerifiedBy(username, oldAsset.typeId)))
          }
          else {
            dao.updateVerifiedInfo(Set(id), username)
            dao.updateProhibitionValue(id, prohibitions, username)
          }
        case _ =>
          Some(id)
      }
    }
  }

  protected def updateValueByExpiration(assetId: Long, prohibitions: Prohibitions, assetTypeId: Int, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    dao.fetchProhibitionsByIds(assetTypeId, Set(assetId)).headOption.map {
      oldAsset =>
        if (oldAsset.value.contains(prohibitions)) {
          dao.updateProhibitionValue(assetId, prohibitions, username, measures).head
        } else {
          //Expire the old asset
          dao.updateExpiration(assetId)
          val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
          //Create New Asset
          createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, prohibitions, sideCode.getOrElse(oldAsset.sideCode),
            measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, getVerifiedBy(username, oldAsset.typeId))
        }
    }
  }

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None, trafficSignId: Option[Long] = None): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy, trafficSignId = trafficSignId)
    value match {
      case prohibitions: Prohibitions =>
        dao.insertProhibitionValue(id, prohibitions)
      case _ => None
    }
    id
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

      val linearAsset = dao.fetchProhibitionsByIds(assetTypeById(id), Set(id)).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink)))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink)))
      Seq(existingId, createdId).flatten
    }
  }

  override def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

      val existing = dao.fetchProhibitionsByIds(assetTypeById(id), Set(id)).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id, expired = true, username)

      val (newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink))),
          valueAgainstDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.AgainstDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink))))

      Seq(newId1, newId2).flatten
    }
  }


  override def getChanged(typeId: Int, since: DateTime, until: DateTime, withAutoAdjust: Boolean = false): Seq[ChangedLinearAsset] = {
    val excludedTypes = Seq(PassageThrough, HorseRiding, SnowMobile, RecreationalVehicle, OversizedTransport)
    val prohibitions = withDynTransaction {
      dao.getProhibitionsChangedSince(typeId, since, until, excludedTypes, withAutoAdjust)
    }
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(prohibitions.map(_.linkId).toSet).filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)
    mapPersistedAssetChanges(prohibitions, roadLinks)

  }


  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId), newTransaction = false).head

    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))
    expireAsset(oldAsset.typeId, oldAsset.id, LinearAssetTypes.VvhGenerated, expired = true, newTransaction = false)
    createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAsset.value.get, adjustment.sideCode.value, Measures(oldAsset.startMeasure, oldAsset.endMeasure), LinearAssetTypes.VvhGenerated, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), false, Some(LinearAssetTypes.VvhGenerated), None, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))
  }

  def createValidPeriod(trafficSignType: TrafficSignType, additionalPanel: AdditionalPanel): Set[ValidityPeriod] = {
    TimePeriodClass.fromTrafficSign(trafficSignType).filterNot(_ == TimePeriodClass.Unknown).flatMap { period =>
      val regexMatch = "[(]?\\d+\\s*[-]{1}\\s*\\d+[)]?".r
      val validPeriodsCount = regexMatch.findAllIn(additionalPanel.panelInfo)
      val validPeriods = regexMatch.findAllMatchIn(additionalPanel.panelInfo)

      if (validPeriodsCount.length == 3 && ValidityPeriodDayOfWeek.fromTimeDomainValue(period.value) == ValidityPeriodDayOfWeek.Sunday) {
        val convertPeriod = Map(0 -> ValidityPeriodDayOfWeek.Weekday, 1 -> ValidityPeriodDayOfWeek.Saturday, 2 -> ValidityPeriodDayOfWeek.Sunday)
        validPeriods.zipWithIndex.map { case (timePeriod, index) =>
          val splitTime = timePeriod.toString.replaceAll("[\\(\\)]|\\s", "").split("-")
          ValidityPeriod(splitTime.head.toInt, splitTime.last.toInt, convertPeriod(index))
        }.toSet

      } else
        validPeriods.map { timePeriod =>
          val splitTime = timePeriod.toString.replaceAll("[\\(\\)]|\\s", "").split("-")
          ValidityPeriod(splitTime.head.toInt, splitTime.last.toInt, ValidityPeriodDayOfWeek.fromTimeDomainValue(period.value))
        }
    }
  }

  def createValue(trafficSign: PersistedTrafficSign): ProhibitionValue = {
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    val additionalPanel = trafficSignService.getAllProperties(trafficSign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
    val typeId = ProhibitionClass.fromTrafficSign(TrafficSignType.applyOTHValue(signType))
    val additionalPanels = additionalPanel.sortBy(_.formPosition)

    val validityPeriods: Set[ValidityPeriod] =
      additionalPanels.flatMap { additionalPanel =>
        val trafficSignType = TrafficSignType.applyOTHValue(additionalPanel.panelType)
        createValidPeriod(trafficSignType, additionalPanel)
      }.toSet

    ProhibitionValue(typeId.head.value, validityPeriods, Set())
  }

  override protected def createLinearAssetFromTrafficSign(trafficSignInfo: TrafficSignInfo): Seq[Long] = {
    Seq()
  }

  def createLinearXXXX(sign: PersistedTrafficSign, roadLink: RoadLink): Seq[TrafficSignToGenerateLinear] = {
    logger.info("Creating prohibition from traffic sign")


    val (tsRoadNamePublicId, tsRroadName): (String, String) =
      roadLink.attributes.get("ROADNAME_FI") match {
        case Some(nameFi) =>
          ("ROADNAME_FI", nameFi.toString)
        case _ =>
          ("ROADNAME_SE", roadLink.attributes.getOrElse("ROADNAME_SE", "").toString)
      }

    //RoadLink with the same Finnish name
    val roadLinks = roadLinkService.fetchVVHRoadlinks(Set(tsRroadName), tsRoadNamePublicId)

    val trafficSignsOnRoadLinks =
      trafficSignService.getTrafficSign(roadLinks.map(_.linkId)).filter { trafficSign =>
        val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
        TrafficSignManager.belongsToProhibition(signType)
      }
    logger.info("Fetching Traffic Signs on related road links")

    val (othersRoadLinks, finalRoadLinks) = roadLinks.partition { r =>
      val (first, last) = GeometryUtils.geometryEndpoints(r.geometry)
      val roadLinksFiltered = roadLinks.filterNot(_.linkId == r.linkId)

      roadLinksFiltered.exists { r3 =>
        val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
        GeometryUtils.areAdjacent(first, first2) || GeometryUtils.areAdjacent(first, last2)
      } &&
        roadLinksFiltered.exists { r3 =>
          val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
          GeometryUtils.areAdjacent(last, first2) || GeometryUtils.areAdjacent(last, last2)
        }
    }

    val futureLinearAssets =
      finalRoadLinks.flatMap { frl =>
        val signsOnRoadLink = trafficSignsOnRoadLinks.filter(_.linkId == frl.linkId)
        signsOnRoadLink.map { sign =>
          val (first, last) = GeometryUtils.geometryEndpoints(frl.geometry)
          val pointOfInterest = trafficSignService.getPointOfInterest(first, last, SideCode(sign.validityDirection)).head

          val pairSign = getPairSign(frl, sign, signsOnRoadLink.filterNot(_.id == sign.id), pointOfInterest)
          processing(frl, roadLinks.filterNot(_.linkId == frl.linkId), sign, trafficSignsOnRoadLinks, pointOfInterest, Seq(generateLinear(frl, sign, pairSign)))
        }
      }.flatten

    createLinearAssetAccordingTrafficSigns(roadLinks, futureLinearAssets)
    Seq() //-> TO remove
  }

  def processing(actualRoadLink: VVHRoadlink, allRoadLinks: Seq[VVHRoadlink], sign: PersistedTrafficSign,
                 signsOnRoadLink: Seq[PersistedTrafficSign], pointOfInterest: Point, generatedLinear: Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {

    val pairSign = getPairSign(actualRoadLink, sign, signsOnRoadLink.filterNot(_.id == sign.id), pointOfInterest)
    val generated = generateLinear(actualRoadLink, sign, pairSign)

    (if (pairSign.isEmpty) {
      generatedLinear ++ getAdjacents((pointOfInterest, actualRoadLink), allRoadLinks).flatMap { case (newRoadLink, (_, oppositePoint)) =>
        processing(newRoadLink, allRoadLinks.filterNot(_.linkId == newRoadLink.linkId), sign, signsOnRoadLink, oppositePoint, generated +: generatedLinear)
      }
    } else
      generated +: generatedLinear).toSet
  }

  def generateLinear(currentRoadLink: VVHRoadlink, sign: PersistedTrafficSign, pairedSign: Option[PersistedTrafficSign]): TrafficSignToGenerateLinear = {
    val prohibitionValue = Seq(createValue(sign))
    pairedSign match {
      case Some(pair) if pair.linkId == sign.linkId =>
        val orderedMValue = Seq(sign.mValue, pair.mValue).sorted
        TrafficSignToGenerateLinear(currentRoadLink, prohibitionValue, sign.validityDirection, orderedMValue.head, orderedMValue.last, sign.id)
      case Some(pair) =>
        TrafficSignToGenerateLinear(currentRoadLink, prohibitionValue, sign.validityDirection, 0, pair.mValue, sign.id)
      case _ =>
        TrafficSignToGenerateLinear(currentRoadLink, prohibitionValue, sign.validityDirection, sign.mValue, currentRoadLink.length, sign.id)
    }
  }

  def getPairSign(actualRoadLink: VVHRoadlink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], pointOfInterest: Point): Option[PersistedTrafficSign] = {
    val mainSignType = trafficSignService.getProperty(mainSign, trafficSignService.typePublicId).get.propertyValue.toInt

    allSignsRelated.find { relatedSign =>
      val relatedSignType = trafficSignService.getProperty(relatedSign, trafficSignService.typePublicId).get.propertyValue.toInt
      val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
      val pointOfInterestRelatedSign = trafficSignService.getPointOfInterest(first, last, SideCode(relatedSign.validityDirection)).head

      relatedSign.linkId == actualRoadLink.linkId && relatedSign.id != mainSign.id && relatedSignType == mainSignType && GeometryUtils.areAdjacent(pointOfInterestRelatedSign, pointOfInterest)


    }
  }

  def createLinearAssetAccordingTrafficSigns(roadLinksWithSameRoadName: Seq[VVHRoadlink], futureLinears: Seq[TrafficSignToGenerateLinear]) {
    //    def createNewAsset() {
    //      val assetId = createWithoutTransaction(Prohibition.typeId, newAsset.roadLink.linkId, Prohibitions(newLinear.prohibitionValue), newLinear.sideCodeToAsset, Measures(newLinear.startMeasure, newLinear.endMeasure),
    //        "automatic_process_prohibitions", vvhClient.roadLinkData.createVVHTimeStamp(), Some(newLinear.roadLink), trafficSignId = Some(newLinear.signId))
    //
    //      dao.insertConnectedAsset(assetId, newLinear.signId)
    //      logger.info(s"Prohibition created with id: $assetId")
    //    }


    val segmentsPointsOnFutureLinears = futureLinears.map(fl => (fl.roadLink.linkId, fl.startMeasure, fl.endMeasure, fl.prohibitionValue))

    //    val oldAssets = getPersistedAssetsByLinkIds(Prohibition.typeId, roadLinksWithSameRoadName.map(_.linkId))
    val oldAssets = Seq.empty[PersistedLinearAsset]
    val segmentsPointsOnOldAssets = oldAssets.map(oa => (oa.linkId, oa.startMeasure, oa.endMeasure, oa.value.map(_.asInstanceOf[Prohibitions])))

    val allSegmentsByLinkId = (segmentsPointsOnFutureLinears ++ segmentsPointsOnOldAssets).groupBy(_._1)

    allSegmentsByLinkId.keys.map { linkId =>
      val minLengthToZip = 0.01
      val segmentsPoints = allSegmentsByLinkId(linkId).flatMap(fl => Seq(fl._2, fl._3)).distinct.sorted
      val segments = segmentsPoints.zip(segmentsPoints.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }

      //      segments.map { case (startMeasurePOI, endMeasurePOI) =>
      //        val assetByLinkIdValidOnPair = allSegmentsByLinkId(linkId).filter(s => s._2 <= startMeasurePOI && s._3 >= endMeasurePOI)
      //
      //        assetByLinkIdValidOnPair.map { _._4 }
      //        }

      val newSegment = segments.flatMap { case (startMeasurePOI, endMeasurePOI) =>
        allSegmentsByLinkId(linkId).flatMap { case (linkId, startM, endM, value) =>
          if (startM <= startMeasurePOI && endM >= endMeasurePOI) {
            Seq((linkId, startMeasurePOI, endMeasurePOI, value))
          } else
            Seq()
        }
      }



      newSegment


    }


    //      val assetId = createWithoutTransaction(Prohibition.typeId, newAsset.roadLink.linkId, Prohibitions(newLinear.prohibitionValue), newLinear.sideCodeToAsset, Measures(newLinear.startMeasure, newLinear.endMeasure),
    //        "automatic_process_prohibitions", vvhClient.roadLinkData.createVVHTimeStamp(), Some(newLinear.roadLink), trafficSignId = Some(newLinear.signId))
    //
    //      dao.insertConnectedAsset(assetId, newLinear.signId)


    //      val newAssetsInsidePOI =
    //        segmentsPointsZiped.map { poi =>
    //          val startMeasurePOI = poi._1
    //          val endMeasurePOI = poi._2
    //          futureLinearsOnRoadLink.filter(s => startMeasurePOI >= s.startMeasure && endMeasurePOI <= s.endMeasure)
    //        }


    //      allSegmentsByLinkId(linkId).map{}
    //      values.map { case (linkId, start, end) =>
    //        linkId ==
    //
    //      }
    //    }


    //    val minLengthToZip = 1.0
    //
    //
    //
    //
    //
    //    roadLinksWithSameRoadName.foreach { roadLink =>
    ////      val futureLinearsOnRoadLink = futureLinears.filter(_.roadLink.linkId == roadLink.linkId)
    ////      val segmentsPointsOnFutureLinears = futureLinearsOnRoadLink.map(fl => Seq(fl.startMeasure, fl.endMeasure, fl.roadLink.linkId))
    ////
    ////      val oldAssets = getPersistedAssetsByLinkIds(Prohibition.typeId, Seq(roadLink.linkId))
    ////      val segmentsPointsOnOldAssets = oldAssets.map(oa => Seq(oa.startMeasure, oa.endMeasure, oa.linkId))
    //
    //      val allSegments = (segmentsPointsOnFutureLinears ++ segmentsPointsOnOldAssets).groupBy()
    //
    ////        .distinct.sorted
    ////      val allSegmentsByLinkId = allSegments.groupBy
    //
    //
    //
    //
    //      val allSegmentsZiped = allSegments.zip(allSegments.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }
    //
    //      allSegmentsZiped.map { poi =>
    //        val startMeasurePOI = poi._1
    //        val endMeasurePOI = poi._2
    //        val segmentsInsidePOI = (futureLinearsOnRoadLink ++ oldAssets).filter(s => startMeasurePOI >= s.startMeasure && endMeasurePOI <= s.endMeasure)
    //      }
    //    }


    //    roadLinksWithSameRoadName.foreach { roadLink =>
    //      val minLengthToZip = 1.0
    //      val futureLinearsOnRoadLink = futureLinears.filter(_.roadLink.linkId == roadLink.linkId)
    //      val segmentsPoints = futureLinearsOnRoadLink.flatMap(fl => Seq(fl.startMeasure, fl.endMeasure)).distinct.sorted
    //      val segmentsPointsZiped = segmentsPoints.zip(segmentsPoints.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }
    //
    //      val newAssetsInsidePOI =
    //        segmentsPointsZiped.map { poi =>
    //          val startMeasurePOI = poi._1
    //          val endMeasurePOI = poi._2
    //          futureLinearsOnRoadLink.filter(s => startMeasurePOI >= s.startMeasure && endMeasurePOI <= s.endMeasure)
    //        }
    //
    ////      val oldAssetsOnRoadLink = getPersistedAssetsByLinkIds(Prohibition.typeId, Seq(roadLink.linkId))
    //
    //      newAssetsInsidePOI.foreach { newAsset =>
    //
    //        val assetId = createWithoutTransaction(Prohibition.typeId, newAsset.roadLink.linkId, Prohibitions(newLinear.prohibitionValue), newLinear.sideCodeToAsset, Measures(newLinear.startMeasure, newLinear.endMeasure),
    //          "automatic_process_prohibitions", vvhClient.roadLinkData.createVVHTimeStamp(), Some(newLinear.roadLink), trafficSignId = Some(newLinear.signId))
    //
    //        dao.insertConnectedAsset(assetId, newLinear.signId)
    //        logger.info(s"Prohibition created with id: $assetId")
    //      }
    //    }

    //
    //    val minLengthToZip = 1.0
    //    val roadLinksWithFurturesLinears = roadLinksWithSameRoadName.filter(_.linkId == futureLinears.map(_.roadLink.linkId))
    //    val oldAssets = getPersistedAssetsByLinkIds(Prohibition.typeId, roadLinksWithFurturesLinears.map(_.linkId))
    //
    //
    //    val measuresAndRoadLinksToCreate = futureLinears.map(fl => (fl.startMeasure, fl.endMeasure, fl.roadLink))
    //    val measuresAndRoadLinksOldAssets = oldAssets.map(oa => (oa.startMeasure, oa.endMeasure, roadLinksWithFurturesLinears.filter(_.linkId == oa.linkId)))
    //
    //    val measuresAndRoadLinksGrouped = (measuresAndRoadLinksToCreate ++ measuresAndRoadLinksOldAssets).groupBy(_._3)
    //
    //    measuresAndRoadLinksGrouped.
    //

    //    measuresAndRoadLinksGrouped.
    //
    //
    //    futureLinears.foreach { newLinear =>
    //      val existOldAsset =
    //        oldAssets.filter { o =>
    //          o.linkId == newLinear.roadLink.linkId &&
    //            (GeometryUtils.liesInBetween(newLinear.startMeasure, (o.startMeasure, o.endMeasure)) || GeometryUtils.liesInBetween(newLinear.endMeasure, (o.startMeasure, o.endMeasure)))
    //        }
    //
    //      if (existOldAsset.isEmpty) {
    //        val assetId = createWithoutTransaction(Prohibition.typeId, newLinear.roadLink.linkId, Prohibitions(newLinear.prohibitionValue), newLinear.sideCodeToAsset, Measures(newLinear.startMeasure, newLinear.endMeasure),
    //          "automatic_process_prohibitions", vvhClient.roadLinkData.createVVHTimeStamp(), Some(newLinear.roadLink), trafficSignId = Some(newLinear.signId))
    //
    //        dao.insertConnectedAsset(assetId, newLinear.signId)
    //        logger.info(s"Prohibition created with id: $assetId")
    //
    //      } else {
    //        val pointsOfInterestOnSegments =
    //          (Seq(newLinear.startMeasure, newLinear.endMeasure) ++ existOldAsset.map(_.startMeasure) ++ existOldAsset.map(_.endMeasure)).distinct.sorted
    //        val pointsOfInterestOnSegmentsZiped =
    //          pointsOfInterestOnSegments.zip(pointsOfInterestOnSegments.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }
    //
    //        pointsOfInterestOnSegmentsZiped.foreach { poi =>
    //          poi
    //        }
    //      }
    //    }
  }

  def getAdjacents(previousInfo: (Point, VVHRoadlink), roadLinks: Seq[VVHRoadlink]): Seq[(VVHRoadlink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousInfo._1)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo._1)) (first, last) else (last, first)

      (roadLink, points)
    }
  }

  private def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String): Option[TextPropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[TextPropertyValue]).headOption
  }

  private def getOpositePoint(geometry: Seq[Point], point: Point) = {
    val (headPoint, lastPoint) = GeometryUtils.geometryEndpoints(geometry)
    if (GeometryUtils.areAdjacent(headPoint, point))
      lastPoint
    else
      headPoint
  }

}
