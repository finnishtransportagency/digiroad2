package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{HazmatTransportProhibitionClass, TimePeriodClass, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, MunicipalityDao, OracleAssetDao, OracleUserProvider}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.util.PolygonTools

class HazmatTransportProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends ProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao

  def inaccurateDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  lazy val trafficSignService = new TrafficSignService(roadLinkService, new OracleUserProvider, eventBus)

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected prohibition assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
      if (toUpdate.nonEmpty) {
        val prohibitions = toUpdate.filter(a => Set(LinearAssetTypes.HazmatTransportProhibitionAssetTypeId).contains(a.typeId))
        val persisted = dao.fetchProhibitionsByIds(LinearAssetTypes.HazmatTransportProhibitionAssetTypeId, prohibitions.map(_.id).toSet).groupBy(_.id)
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

  override def update(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    val outputIds = withDynTransaction {
      updateWithoutTransaction(ids, value, username, vvhTimeStamp, sideCode, measures)
    }

    eventBus.publish("hazmatTransportProhibition:Validator",AssetValidatorInfo((ids ++ outputIds).toSet))
    outputIds
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    val newIds = withDynTransaction {
      val roadLink = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhTimeStamp, roadLink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId))
      }
    }
    eventBus.publish("hazmatTransportProhibition:Validator",AssetValidatorInfo(newIds.toSet))
    newIds
  }

  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = {
    withDynTransaction {
      inaccurateDAO.getInaccurateAsset(typeId, municipalities, adminClass)
        .groupBy(_.municipality)
        .mapValues {
          _.groupBy(_.administrativeClass)
            .mapValues(_.map{values => Map("assetId" -> values.assetId, "linkId" -> values.linkId)})
        }
    }
  }

  def createValidPeriod(trafficSignType: TrafficSignType, additionalPanel: AdditionalPanel) : Set[ValidityPeriod] = {
    TimePeriodClass.fromTrafficSign(trafficSignType).filterNot(_ == TimePeriodClass.Unknown).flatMap { period =>
      val regexMatch = "[(]?\\d+\\s*[-]{1}\\s*\\d+[)]?".r
      val validPeriodsCount = regexMatch.findAllIn(additionalPanel.panelInfo)
      val validPeriods =  regexMatch.findAllMatchIn(additionalPanel.panelInfo)

      if(validPeriodsCount.length == 3 && ValidityPeriodDayOfWeek.fromTimeDomainValue(period.value) == ValidityPeriodDayOfWeek.Sunday) {
        val convertPeriod = Map(0 -> ValidityPeriodDayOfWeek.Weekday, 1 -> ValidityPeriodDayOfWeek.Saturday, 2 -> ValidityPeriodDayOfWeek.Sunday)
        validPeriods.zipWithIndex.map { case (timePeriod, index) =>
          val splitTime = timePeriod.toString.replaceAll("[\\(\\)]|\\s", "").split("-")
          ValidityPeriod(splitTime.head.toInt, splitTime.last.toInt, convertPeriod(index))
        }.toSet

      }else
        validPeriods.map { timePeriod =>
          val splitTime = timePeriod.toString.replaceAll("[\\(\\)]|\\s", "").split("-")
          ValidityPeriod(splitTime.head.toInt, splitTime.last.toInt, ValidityPeriodDayOfWeek.fromTimeDomainValue(period.value))
        }
    }
  }


  def createValue(additionalPanels: Seq[AdditionalPanel]) :Seq[ProhibitionValue] = {
    additionalPanels.foldLeft(Seq.empty[ProhibitionValue]) { case (result, additionalPanel) =>
      val trafficSignType = TrafficSignType.applyOTHValue(additionalPanel.panelType)

      val typeId = HazmatTransportProhibitionClass.fromTrafficSign(trafficSignType)

      if (typeId.nonEmpty) {
        result ++ Seq(ProhibitionValue(typeId.head.value, Set(), Set()))
      }else {
        val validityPeriod: Set[ValidityPeriod] = createValidPeriod(trafficSignType, additionalPanel)
        result.init :+ result.last.copy(validityPeriods = validityPeriod)
      }
    }
  }

  override def createProhibitionFromTrafficSign(trafficSignInfo: TrafficSignInfo): Seq[Long] = {
    logger.info("Creating prohibition from traffic sign")
    val tsLinkId = trafficSignInfo.linkId
    val tsDirection = trafficSignInfo.validityDirection

    if (tsLinkId != trafficSignInfo.roadLink.linkId)
      throw new ProhibitionCreationException(Set("Wrong roadlink"))

    if (SideCode(tsDirection) == SideCode.BothDirections)
      throw new ProhibitionCreationException(Set("Isn't possible to create a prohibition based on a traffic sign with BothDirections"))

    val connectionPoint = roadLinkService.getRoadLinkEndDirectionPoints(trafficSignInfo.roadLink, Some(tsDirection)).headOption.getOrElse(throw new ProhibitionCreationException(Set("Connection Point not valid")))

    val roadLinks = roadLinkService.recursiveGetAdjacent(trafficSignInfo.roadLink, connectionPoint)
    logger.info("End of fetch for adjacents")

    val orderedPanel = trafficSignInfo.additionalPanel.sortBy(_.formPosition)

    val prohibitionValue = createValue(orderedPanel).groupBy(_.typeId).values.flatten.toSeq

    if (prohibitionValue.nonEmpty) {
      val ids = (roadLinks ++ Seq(trafficSignInfo.roadLink)).map { roadLink =>
        val (startMeasure, endMeasure): (Double, Double) = SideCode(tsDirection) match {
          case SideCode.TowardsDigitizing if roadLink.linkId == trafficSignInfo.roadLink.linkId => (trafficSignInfo.mValue, roadLink.length)
          case SideCode.AgainstDigitizing if roadLink.linkId == trafficSignInfo.roadLink.linkId => (0, trafficSignInfo.mValue)
          case _ => (0, roadLink.length)

        }
        val assetId = createWithoutTransaction(HazmatTransportProhibition.typeId, roadLink.linkId, Prohibitions(prohibitionValue), BothDirections.value, Measures(startMeasure, endMeasure),
          "automatic_process_hazmatTransportProhibition", vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), trafficSignId = Some(trafficSignInfo.id))

        dao.insertConnectedAsset(assetId, trafficSignInfo.id)

        logger.info(s"HazmatTransportProhibition created with id: $assetId")
        assetId
      }
      ids
    }
    else Seq()
  }

  def deleteByTrafficSign(ids: Set[Long], username: Option[String] = None, withTransaction: Boolean = true) : Unit = {
    if (withTransaction) {
      withDynTransaction {
        dao.deleteByTrafficSign(withIds(ids), username)
      }
    }
    else
      dao.deleteByTrafficSign(withIds(ids), username)
  }

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val assets = if(withTransaction) {
      withDynTransaction {
        val assetIds = dao.getConnectedAssetFromTrafficSign(trafficSignId)
        dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, assetIds.toSet)
      }
    } else {
      val assetIds = dao.getConnectedAssetFromTrafficSign(trafficSignId)
      dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, assetIds.toSet)
    }
    assets
  }

  def deleteOrUpdateAssetBasedOnSign(id: Long, propertyData: Seq[TrafficSignProperty] = Seq(), username: Option[String] = None, withTransaction: Boolean = true) : Unit = {
    logger.info("expiring asset")

    // Obter todos os sinais que contem o respetivo traffic sign
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(id)

    // Mapear valores do traffic sign numa propriedade que permita comparar com o asset

    // Compara valores do traffic sign com os do asset e separa pelos que precisam de update ou de ser eliminados

    // fazer update ou eliminar



    //    propertyData.map(_.)




//    val orderedPanel = trafficSignInfo.additionalPanel.sortBy(_.formPosition)
//    val prohibitionValue = createValue(orderedPanel).groupBy(_.typeId).values.flatten.toSeq
//
//    updadeAssetBasedOnSign
//    trafficSignInfo: TrafficSignInfo




  }



}
