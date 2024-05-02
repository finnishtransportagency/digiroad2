package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISSpeedLimitDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.{adjustSideCodes, fillTopology}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.process.SpeedLimitValidator
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.util.LogUtils
import fi.liikennevirasto.digiroad2.util.assetUpdater.SpeedLimitUpdater
import org.joda.time.DateTime
import org.postgresql.util.PSQLException

import java.util.NoSuchElementException


class SpeedLimitService(eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) extends DynamicLinearAssetService(roadLinkService, eventbus) {
  val speedLimitDao: PostGISSpeedLimitDao = new PostGISSpeedLimitDao(roadLinkService)
  val inaccurateAssetDao: InaccurateAssetDAO = new InaccurateAssetDAO()
  val speedLimitUpdater = new SpeedLimitUpdater(this)
  private val RECORD_NUMBER = 4000

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, eventbus)
  }

  lazy val speedLimitValidator: SpeedLimitValidator = {
    new SpeedLimitValidator(trafficSignService)
  }

  def validateMunicipalities(id: Long,  municipalityValidation: (Int, AdministrativeClass) => Unit, newTransaction: Boolean = true): Unit = {
    getLinksWithLength(id, newTransaction).foreach(vvhLink => municipalityValidation(vvhLink._4, vvhLink._6))
  }

  def getSpeedLimitValue(optionalValue: Option[Value]): Option[SpeedLimitValue] = {
    optionalValue match {
      case Some(SpeedLimitValue(_,_)) => Option(optionalValue.get.asInstanceOf[SpeedLimitValue])
      case _ => None
    }
  }

  def getLinksWithLength(id: Long, newTransaction: Boolean = true): Seq[(String, Double, Seq[Point], Int, LinkGeomSource, AdministrativeClass)] = {
    if (newTransaction)
      withDynTransaction {
        speedLimitDao.getLinksWithLength(id)
      }
    else
      speedLimitDao.getLinksWithLength(id)
  }

  def getSpeedLimitAssetsByIds(ids: Set[Long], newTransaction: Boolean = true): Seq[PieceWiseLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        speedLimitDao.getSpeedLimitLinksByIds(ids)
      }
    else
      speedLimitDao.getSpeedLimitLinksByIds(ids)
  }

  def getSpeedLimitById(id: Long, newTransaction: Boolean = true): Option[PieceWiseLinearAsset] = {
    getSpeedLimitAssetsByIds(Set(id), newTransaction).headOption
  }

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true):  Seq[PersistedLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        speedLimitDao.getPersistedSpeedLimitByIds(ids)
      }
    else
      speedLimitDao.getPersistedSpeedLimitByIds(ids)
  }

  def getPersistedSpeedLimitById(id: Long, newTransaction: Boolean = true): Option[PersistedLinearAsset] = {
    getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), newTransaction).headOption
  }

  override def expireAsset(typeId: Int, id: Long, username: String, expired: Boolean, newTransaction: Boolean = true):Option[Long] = {
    if (newTransaction)
      withDynTransaction {
        speedLimitDao.updateExpiration(id, expired, username)
      }
    else
      speedLimitDao.updateExpiration(id, expired, username)
  }


  /**
    * Returns speed limits history for Digiroad2Api /history speedlimits GET endpoint.
    */
  def getHistory(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksHistory(bounds, municipalities)
    val filledTopology = getByRoadLinks(SpeedLimitAsset.typeId, roadLinks, true, true, {roadLinkFilter: RoadLink => roadLinkFilter.isCarTrafficRoad})
    LinearAssetPartitioner.partition(filledTopology, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  /**
    * This method returns speed limits that have been changed in OTH between given date values. It is used by TN-ITS ChangeApi.
    *
    * @param sinceDate
    * @param untilDate
    * @param withAdjust
    * @return Changed speed limits
    */
  override def getChanged(typeId: Int, sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean = false, token: Option[String] = None): Seq[ChangedLinearAsset] = {
    val persistedSpeedLimits = withDynTransaction {
      speedLimitDao.getSpeedLimitsChangedSince(sinceDate, untilDate, withAdjust, token)
    }
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(persistedSpeedLimits.map(_.linkId).toSet)
    val (walkWays, roadLinksWithoutWalkways) = roadLinks.partition(link => link.linkType == CycleOrPedestrianPath || link.linkType == TractorRoad)
    val historyRoadLinks = fetchMissingLinksFromHistory(persistedSpeedLimits, roadLinksWithoutWalkways)

    mapPersistedAssetChanges(persistedSpeedLimits, roadLinksWithoutWalkways, historyRoadLinks, walkWays)
  }

  /**
    * Returns unknown speed limits for Digiroad2Api /speedlimits/unknown GET endpoint.
    */
  def getUnknown(municipalities: Set[Int], administrativeClass: Option[AdministrativeClass]): Map[String, Map[String, Any]] = {
    withDynSession {
      speedLimitDao.getUnknownSpeedLimits(municipalities, administrativeClass)
    }
  }

  def hideUnknownSpeedLimits(linkIds: Set[String]): Set[String] = {
    withDynTransaction {
      speedLimitDao.hideUnknownSpeedLimits(linkIds)
    }
  }

  def getMunicipalitiesWithUnknown(administrativeClass: Option[AdministrativeClass]): Seq[(Long, String)] = {
    withDynSession {
      speedLimitDao.getMunicipalitiesWithUnknown(administrativeClass)
    }
  }

  /**
    * Removes speed limit from unknown speed limits list if speed limit exists. Used by SpeedLimitUpdater actor.
    */
  def purgeUnknown(linkIds: Set[String], expiredLinkIds: Seq[String]): Unit = {
    val roadLinks = roadLinkService.fetchRoadlinksByIds(linkIds)
    withDynTransaction {
      roadLinks.foreach { rl =>
        speedLimitDao.purgeFromUnknownSpeedLimits(rl.linkId, GeometryUtils.geometryLength(rl.geometry))
      }

      //To remove nonexistent road links of unknown speed limits list
      if (expiredLinkIds.nonEmpty)
        speedLimitDao.deleteUnknownSpeedLimits(expiredLinkIds)
    }
  }

  protected def createUnknownLimits(speedLimits: Seq[PieceWiseLinearAsset], roadLinksByLinkId: Map[String, RoadLink]): Seq[UnknownSpeedLimit] = {
    val generatedLimits = speedLimits.filter(speedLimit => speedLimit.id == 0 && speedLimit.value.isEmpty)
    generatedLimits.map { limit =>
      val roadLink = roadLinksByLinkId(limit.linkId)
      UnknownSpeedLimit(roadLink.linkId, roadLink.municipalityCode, roadLink.administrativeClass)
    }
  }

  def getExistingAssetByRoadLink(roadLink: RoadLink, newTransaction: Boolean = true): Seq[PieceWiseLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(Set(roadLink.linkId)))
      }
    else
      speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(Set(roadLink.linkId)))
  }

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], generateUnknownBoolean: Boolean = true, showHistory: Boolean,
                                        roadLinkFilter: RoadLink => Boolean = _ => true): Seq[PieceWiseLinearAsset] = {
    withDynTransaction {
      val roadLinksFiltered = roadLinks.filter(roadLinkFilter)
      val speedLimitLinks = speedLimitDao.getSpeedLimitLinksByRoadLinks(roadLinksFiltered, showHistory)
      val speedLimits = speedLimitLinks.groupBy(_.linkId)
      if (generateUnknownBoolean) generateUnknowns(roadLinksFiltered, speedLimits) else speedLimitLinks
    }
  }

  /**
    * Make sure operations are small and fast
    * Do not try to use methods which also use event bus, publishing will not work
    * @param linksIds
    * @param typeId asset type
    */
  override def adjustLinearAssetsAction(linksIds: Set[String], typeId: Int, newTransaction: Boolean = true,adjustSideCode: Boolean = false): Unit = {
    if (newTransaction)  withDynTransaction {action(false)} else action(newTransaction)
    def action( newTransaction: Boolean):Unit = {
      try {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksIds, newTransaction = newTransaction).filter({roadLinkFilter: RoadLink => roadLinkFilter.isCarTrafficRoad})
        val existingAssets = speedLimitDao.getSpeedLimitLinksByRoadLinks(roadLinks)
        val groupedAssets = existingAssets.groupBy(_.linkId)
        LogUtils.time(logger, s"Check for and adjust possible linearAsset adjustments on ${roadLinks.size} roadLinks. TypeID: ${SpeedLimitAsset.typeId}") {
          if (adjustSideCode) adjustSpeedLimitsSideCode(roadLinks, groupedAssets)
          else adjustSpeedLimitsAndGenerateUnknowns(roadLinks, groupedAssets, geometryChanged = false)
        }
      } catch {
        case e: PSQLException => logger.error(s"Database error happened on asset type ${typeId}, on links ${linksIds.mkString(",")} : ${e.getMessage}", e)
        case e: Throwable => logger.error(s"Unknown error happened on asset type ${typeId}, on links ${linksIds.mkString(",")} : ${e.getMessage}", e)
      }
    }
  }

  def adjustSpeedLimitsAndGenerateUnknowns(roadLinks: Seq[RoadLink], speedLimits: Map[String, Seq[PieceWiseLinearAsset]],
                        changeSet:Option[ChangeSet] = None, geometryChanged: Boolean, counter: Int = 1): Seq[PieceWiseLinearAsset] = {
    val (filledTopology, changedSet) = fillTopology(roadLinks, speedLimits, SpeedLimitAsset.typeId, changeSet, geometryChanged)
    val cleanedChangeSet = speedLimitUpdater.cleanRedundantMValueAdjustments(changedSet, speedLimits.values.flatten.toSeq).filterGeneratedAssets

    cleanedChangeSet.isEmpty match {
      case true => filledTopology
      case false if counter > 3 =>
        speedLimitUpdater.updateChangeSet(cleanedChangeSet)
        filledTopology
      case false if counter <= 3 =>
        speedLimitUpdater.updateChangeSet(cleanedChangeSet)
        val speedLimitsToAdjust = filledTopology.filterNot(speedLimit => speedLimit.id <= 0 && speedLimit.value.isEmpty).groupBy(_.linkId)
        adjustSpeedLimitsAndGenerateUnknowns(roadLinks, speedLimitsToAdjust, None, geometryChanged, counter + 1)
    }
  }

  def adjustSpeedLimitsSideCode(roadLinks: Seq[RoadLink], speedLimits: Map[String, Seq[PieceWiseLinearAsset]],
                                           changeSet: Option[ChangeSet] = None, counter: Int = 1): Seq[PieceWiseLinearAsset] = {
    val (filledTopology, changedSet) = adjustSideCodes(roadLinks, speedLimits, SpeedLimitAsset.typeId, changeSet)
    val cleanedChangeSet = speedLimitUpdater.cleanRedundantMValueAdjustments(changedSet, speedLimits.values.flatten.toSeq).filterGeneratedAssets

    cleanedChangeSet.isEmpty match {
      case true => filledTopology
      case false if counter > 3 =>
        speedLimitUpdater.updateChangeSet(cleanedChangeSet)
        filledTopology
      case false if counter <= 3 =>
        speedLimitUpdater.updateChangeSet(cleanedChangeSet)
        val speedLimitsToAdjust = filledTopology.filterNot(speedLimit => speedLimit.id <= 0 && speedLimit.value.isEmpty).groupBy(_.linkId)
        adjustSpeedLimitsSideCode(roadLinks, speedLimitsToAdjust, None, counter + 1)
    }
  }
  def generateUnknowns(roadLinks: Seq[RoadLink], speedLimits: Map[String, Seq[PieceWiseLinearAsset]]): Seq[PieceWiseLinearAsset] = {
     SpeedLimitFiller.generateUnknowns(roadLinks, speedLimits, SpeedLimitAsset.typeId)._1
  }

  /**
    * Saves speed limit value changes received from UI. Used by Digiroad2Api /speedlimits PUT endpoint.
    */
  def updateValues(ids: Seq[Long], value: SpeedLimitValue, username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, newTransaction: Boolean = true): Seq[Long] = {
    if (newTransaction) {
      withDynTransaction {
        ids.foreach(id => validateMunicipalities(id, municipalityValidation, newTransaction = false))
        ids.flatMap(speedLimitDao.updateSpeedLimitValue(_, value, username))
      }
    } else {
      ids.foreach(id => validateMunicipalities(id, municipalityValidation, newTransaction = false))
      ids.flatMap(speedLimitDao.updateSpeedLimitValue(_, value, username))
    }
  }


  def updateFromMunicipalityApi(id: Long, newLimits: Seq[NewLinearAsset], username: String, newTransaction: Boolean = true): Seq[Long] = {
    val oldSpeedLimit = getPersistedSpeedLimitById(id, newTransaction).map(toPieceWiseLinearAsset(_, newTransaction)).get

    newLimits.flatMap (limit =>  limit.value match {
      case SpeedLimitValue(suggestion, intValue) =>

        if ((validateMinDistance(limit.startMeasure, oldSpeedLimit.startMeasure) || validateMinDistance(limit.endMeasure, oldSpeedLimit.endMeasure)) || SideCode(limit.sideCode) != oldSpeedLimit.sideCode)
          updateSpeedLimitWithExpiration(id, SpeedLimitValue(suggestion, intValue), username, Some(Measures(limit.startMeasure, limit.endMeasure)), Some(limit.sideCode), (_, _) => Unit)
        else
          updateValues(Seq(id), SpeedLimitValue(suggestion, intValue), username, (_, _) => Unit, newTransaction)
      case _ => Seq.empty[Long]
    })
  }


  def updateSpeedLimitWithExpiration(id: Long, value: SpeedLimitValue, username: String, measures: Option[Measures] = None, sideCode: Option[Int] = None, municipalityValidation: (Int, AdministrativeClass) => Unit): Option[Long] = {
    validateMunicipalities(id, municipalityValidation, newTransaction = false)

    //Get all data from the speedLimit to update
    val speedLimit = speedLimitDao.getPersistedSpeedLimit(id).filterNot(_.expired).getOrElse(throw new IllegalStateException("Asset no longer available"))

    //Expire old speed limit
    speedLimitDao.updateExpiration(id)

    //Create New Asset copy by the old one with new value
    val newAssetId =
      speedLimitDao.createSpeedLimit(username, speedLimit.linkId, measures.getOrElse(Measures(speedLimit.startMeasure, speedLimit.endMeasure)),
        SideCode.apply(sideCode.getOrElse(speedLimit.sideCode)), value, None, None, None, None, speedLimit.linkSource)

    existOnInaccuratesList(id, newAssetId)
    newAssetId
  }

  private def existOnInaccuratesList(oldId: Long, newId: Option[Long] = None) = {
    (inaccurateAssetDao.getInaccurateAssetById(oldId), newId) match {
      case (Some(idOld), Some(idNew)) =>
        inaccurateAssetDao.deleteInaccurateAssetById(idOld)
        checkInaccurateSpeedLimit(newId)
      case _ => None
    }
  }

  private def checkInaccurateSpeedLimit(id: Option[Long] = None) = {
    getSpeedLimitById(id.head, false) match {
      case Some(speedLimit) =>
        val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(speedLimit.linkId, false)
          .find(roadLink => roadLink.administrativeClass == State || roadLink.administrativeClass == Municipality)
          .getOrElse(throw new NoSuchElementException("RoadLink Not Found"))

        val trafficSigns = trafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(roadLink.linkId)

        val inaccurateAssets =
          speedLimitValidator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, Seq(speedLimit)).map {
            inaccurateAsset =>
              println(s"Inaccurate asset ${inaccurateAsset.id} found ")
              (inaccurateAsset, roadLink.administrativeClass)
          }

        inaccurateAssets.foreach { case (speedLimit, administrativeClass) =>
          inaccurateAssetDao.createInaccurateAsset(speedLimit.id, SpeedLimitAsset.typeId, roadLink.municipalityCode, administrativeClass)
        }
      case _ => None
    }
  }

  /**
    * Saves speed limit values when speed limit is split to two parts in UI (scissors icon). Used by Digiroad2Api /speedlimits/:speedLimitId/split POST endpoint.
    */
  def splitSpeedLimit(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[PieceWiseLinearAsset] = {
   val speedLimits =  withDynTransaction {
      getPersistedSpeedLimitById(id, newTransaction = false) match {
        case Some(speedLimit) =>
          val roadLink = roadLinkService.fetchRoadlinkAndComplementary(speedLimit.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
          municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

          val (newId ,idUpdated) = split(speedLimit, roadLink, splitMeasure, existingValue, createdValue, username)

          val assets = getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(idUpdated, newId), newTransaction = false)

          val speedLimits = assets.map{ persisted =>
            val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, persisted.startMeasure, persisted.endMeasure)
            PieceWiseLinearAsset(persisted.id, persisted.linkId, SideCode(persisted.sideCode), persisted.value, geometry, persisted.expired,
              persisted.startMeasure, persisted.endMeasure, geometry.toSet, persisted.modifiedBy, persisted.modifiedDateTime, persisted.createdBy, persisted.createdDateTime,
              persisted.typeId, roadLink.trafficDirection, persisted.timeStamp, persisted.geomModifiedDate,
              persisted.linkSource, roadLink.administrativeClass, Map(), persisted.verifiedBy, persisted.verifiedDate, persisted.informationSource)

          }
         
          speedLimits.filter(asset => asset.id == idUpdated || asset.id == newId)
        case _ => Seq()
      }
    }
    adjustLinearAssetsAction(speedLimits.map(_.linkId).toSet,speedLimits.head.typeId)
    speedLimits
  }

  /**
    * Splits speed limit by given split measure.
    * Used by SpeedLimitService.split.
    */
  def split(speedLimit: PersistedLinearAsset, roadLinkFetched: RoadLinkFetched, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String): (Long, Long) = {
    val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (speedLimit.startMeasure, speedLimit.endMeasure))

    speedLimitDao.updateExpiration(speedLimit.id)

    val existingId = speedLimitDao.createSpeedLimit(speedLimit.createdBy.getOrElse(username), speedLimit.linkId, Measures(existingLinkMeasures._1, existingLinkMeasures._2),
      SideCode(speedLimit.sideCode), SpeedLimitValue(existingValue), Some(speedLimit.timeStamp), speedLimit.createdDateTime, Some(username), Some(DateTime.now()) , roadLinkFetched.linkSource).get

    val createdId = speedLimitDao.createSpeedLimit(speedLimit.createdBy.getOrElse(username), roadLinkFetched.linkId, Measures(createdLinkMeasures._1, createdLinkMeasures._2),
      SideCode(speedLimit.sideCode), SpeedLimitValue(createdValue), Option(speedLimit.timeStamp), speedLimit.createdDateTime, Some(username), Some(DateTime.now()), roadLinkFetched.linkSource).get
    (existingId, createdId)
  }

  private def toPieceWiseLinearAsset(persisted: PersistedLinearAsset, newTransaction: Boolean = true): PieceWiseLinearAsset = {
    val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(persisted.linkId, newTransaction).get
    val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, persisted.startMeasure, persisted.endMeasure)

    PieceWiseLinearAsset(persisted.id, persisted.linkId, SideCode(persisted.sideCode), persisted.value, geometry, persisted.expired,
      persisted.startMeasure, persisted.endMeasure, geometry.toSet, persisted.modifiedBy, persisted.modifiedDateTime, persisted.createdBy, persisted.createdDateTime,
      persisted.typeId, roadLink.trafficDirection, persisted.timeStamp, persisted.geomModifiedDate,
      persisted.linkSource, roadLink.administrativeClass, Map(), persisted.verifiedBy, persisted.verifiedDate, persisted.informationSource)

  }

  private def isSeparableValidation(speedLimit: PieceWiseLinearAsset): PieceWiseLinearAsset = {
    val separable = speedLimit.sideCode == SideCode.BothDirections && speedLimit.trafficDirection == TrafficDirection.BothDirections
    if (!separable) throw new IllegalArgumentException("You cannot divide speed limit")
    speedLimit
  }

  /**
    * Saves speed limit values when speed limit is separated to two sides in UI. Used by Digiroad2Api /speedlimits/:speedLimitId/separate POST endpoint.
    */
  def separateSpeedLimit(id: Long, valueTowardsDigitization: SpeedLimitValue, valueAgainstDigitization: SpeedLimitValue, username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[PieceWiseLinearAsset] = {
    val speedLimit = getPersistedSpeedLimitById(id)
      .map(toPieceWiseLinearAsset(_))
      .map(isSeparableValidation)
      .get

    validateMunicipalities(id, municipalityValidation)

    expireAsset(SpeedLimitAsset.typeId, id, username, expired = true)

    val(newId1, newId2) = withDynTransaction {
      (speedLimitDao.createSpeedLimit(speedLimit.createdBy.getOrElse(username), speedLimit.linkId, Measures(speedLimit.startMeasure, speedLimit.endMeasure), SideCode.TowardsDigitizing, valueTowardsDigitization, None, createdDate = speedLimit.createdDateTime , modifiedBy = Some(username), modifiedAt = Some(DateTime.now()), linkSource = speedLimit.linkSource).get,
       speedLimitDao.createSpeedLimit(speedLimit.createdBy.getOrElse(username), speedLimit.linkId, Measures(speedLimit.startMeasure, speedLimit.endMeasure), SideCode.AgainstDigitizing, valueAgainstDigitization, None, createdDate = speedLimit.createdDateTime, modifiedBy = Some(username), modifiedAt = Some(DateTime.now()),  linkSource = speedLimit.linkSource).get)
    }
    val assets = getSpeedLimitAssetsByIds(Set(newId1, newId2))
    adjustLinearAssetsAction(Set(speedLimit.linkId),speedLimit.typeId)
    
    Seq(assets.find(_.id == newId1).get, assets.find(_.id == newId2).get)
  }

  /**
    * This method was created for municipalityAPI, in future could be merge with the other create method.
    */
  def createMultiple(newLimits: Seq[NewLinearAsset], username: String, timeStamp: Long = createTimeStamp(), municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    val createdIds = newLimits.flatMap { limit =>
      limit.value match {
        case SpeedLimitValue(suggestion, intValue) => speedLimitDao.createSpeedLimit(username, limit.linkId, Measures(limit.startMeasure, limit.endMeasure), SideCode.apply(limit.sideCode), SpeedLimitValue(suggestion, intValue), timeStamp, municipalityValidation)
        case _ => None
      }
    }

    eventbus.publish("speedLimits:purgeUnknownLimits", (newLimits.map(_.linkId).toSet, Seq()))
    createdIds
  }

  /**
    * Saves new speed limit from UI. Used by Digiroad2Api /speedlimits PUT and /speedlimits POST endpoints.
    */
  def create(newLimits: Seq[NewLimit], value: SpeedLimitValue, username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val createdIds = newLimits.flatMap { limit =>
        speedLimitDao.createSpeedLimit(username, limit.linkId, Measures(limit.startMeasure, limit.endMeasure), SideCode.BothDirections, value, createTimeStamp(), municipalityValidation)
      }
      eventbus.publish("speedLimits:purgeUnknownLimits", (newLimits.map(_.linkId).toSet, Seq()))
      
      createdIds
    }
  }


  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /speedlimits POST endpoint.
    */
   def createOrUpdateSpeedLimit(newLimits: Seq[NewLimit], values: SpeedLimitValue, username: String, updateIds: Seq[Long],
                                municipalityValidationForUpdate: (Int, AdministrativeClass) => Unit,
                                municipalityValidationForCreate: (Int, AdministrativeClass) => Unit): Seq[Long] = {
     val ids = updateValues(updateIds, values, username, municipalityValidationForUpdate) ++ create(newLimits, values, username, municipalityValidationForCreate)
     adjustLinearAssetsAction(getSpeedLimitAssetsByIds(ids.toSet).map(_.linkId).toSet, SpeedLimitAsset.typeId)
     ids.distinct
  }
  

  def createWithoutTransaction(newLimits: Seq[NewLimit], value: SpeedLimitValue, username: String, sideCode: SideCode): Seq[Long] = {
    newLimits.flatMap { limit =>
      speedLimitDao.createSpeedLimit(username, limit.linkId, Measures(limit.startMeasure, limit.endMeasure), sideCode, value, createTimeStamp(), (_, _) => Unit)
    }
  }

  def getAssetsAndPoints(existingAssets: Seq[PieceWiseLinearAsset], roadLinks: Seq[RoadLink], changeInfo: (ChangeInfo, RoadLink)): Seq[(Point, PieceWiseLinearAsset)] = {
    existingAssets.filter { asset => asset.createdDateTime.get.isBefore(changeInfo._1.timeStamp)}
      .flatMap { asset =>
        val roadLink = roadLinks.find(_.linkId == asset.linkId)
        if (roadLink.nonEmpty && roadLink.get.administrativeClass == changeInfo._2.administrativeClass) {
          GeometryUtils.calculatePointFromLinearReference(roadLink.get.geometry, asset.endMeasure).map(point => (point, asset)) ++
            (if (asset.startMeasure == 0)
              GeometryUtils.calculatePointFromLinearReference(roadLink.get.geometry, asset.startMeasure).map(point => (point, asset))
            else
              Seq())
        } else
          Seq()
      }
  }

  def getAdjacentAssetByPoint(assets: Seq[(Point, PieceWiseLinearAsset)], point: Point) : Seq[PieceWiseLinearAsset] = {
    assets.filter{case (assetPt, _) => GeometryUtils.areAdjacent(assetPt, point)}.map(_._2)
  }
}
