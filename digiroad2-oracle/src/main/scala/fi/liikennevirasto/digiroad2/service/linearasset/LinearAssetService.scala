package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, MunicipalityInfo, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignInfo
import fi.liikennevirasto.digiroad2.util.assetUpdater.LinearAssetUpdateProcess.getAssetUpdater
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LogUtils, PolygonTools}
import org.joda.time.DateTime
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory

object LinearAssetTypes {
  val SpeedLimitAssetTypeId = 20
  val TotalWeightLimits = 30
  val TrailerTruckWeightLimits = 40
  val AxleWeightLimits = 50
  val BogieWeightLimits = 60
  val ProhibitionAssetTypeId = 190
  val PavedRoadAssetTypeId = 110
  val RoadWidthAssetTypeId = 120
  val HazmatTransportProhibitionAssetTypeId = 210
  val EuropeanRoadAssetTypeId = 260
  val ExitNumberAssetTypeId = 270
  val numericValuePropertyId: String = "mittarajoitus"
  val europeanRoadPropertyId: String = "eurooppatienumero"
  val exitNumberPropertyId: String = "liittymänumero"
  val damagedByThawPropertyId: String = "kelirikko"
  def getValuePropertyId(typeId: Int) = typeId match {
    case EuropeanRoadAssetTypeId => europeanRoadPropertyId
    case ExitNumberAssetTypeId => exitNumberPropertyId
    case _ => numericValuePropertyId
  }
}

case class ChangedLinearAsset(linearAsset: PieceWiseLinearAsset, link: RoadLink)

case class AssetUpdateActor(linksIds: Set[String], typeId: Int,roadLinkUpdate:Boolean = false)
case class Measures(startMeasure: Double, endMeasure: Double){
  def roundMeasures(): Measures = {
    val exponentOfTen = Math.pow(10, 3)
    Measures(Math.round(startMeasure * exponentOfTen).toDouble / exponentOfTen,Math.round(endMeasure * exponentOfTen).toDouble / exponentOfTen)
  }
  def length():Double  ={
    val exponentOfTen = Math.pow(10, 3)
    Math.round((endMeasure - startMeasure) * exponentOfTen).toDouble / exponentOfTen
  }
}

case class NewLinearAssetMassOperation(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                       createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()),
                                       modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), 
                                       verifiedBy: Option[String] = None ,verifiedDateFromUpdate : Option[DateTime] = None, informationSource: Option[Int] = None,
                                       geometry: Seq[Point] = Seq(),
                                       expired :Boolean=false,linkSource: Option[Int]
                                      )

trait LinearAssetOperations {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def dao: PostGISLinearAssetDao
  def municipalityDao: MunicipalityDao
  def eventBus: DigiroadEventBus
  def polygonTools : PolygonTools
  def assetDao: PostGISAssetDao
  def assetFiller: AssetFiller = new AssetFiller
  def createTimeStamp(offsetHours:Int=5): Long = LinearAssetUtils.createTimeStamp(offsetHours)

  val logger = LoggerFactory.getLogger(getClass)
  val verifiableAssetType = Set(30, 40, 50, 60, 70, 80, 90, 100, 120, 140, 160, 190, 210)

  def getMunicipalityCodeByAssetId(assetId: Int): Int = {
    withDynTransaction {
      assetDao.getAssetMunicipalityCodeById(assetId)
    }
  }

  def getLinkSource(roadLink: Option[RoadLinkLike]): Option[Int] = {
    roadLink match {
      case Some(road) =>
        Some(road.linkSource.value)
      case _ => None
    }
  }

  def getGeometry(roadLink: Option[RoadLinkLike]): Seq[Point] = {
    roadLink match {
      case Some(road) => road.geometry
      case _ => Seq()
    }
  }

  def validateMinDistance(measure1: Double, measure2: Double): Boolean = {
    val minDistanceAllow = 0.01
    val (maxMeasure, minMeasure) = (math.max(measure1, measure2), math.min(measure1, measure2))
    (maxMeasure - minMeasure) > minDistanceAllow
  }

  /**
    * Returns linear assets for Digiroad2Api /linearassets GET endpoint.
    *
    * @param typeId
    * @param bounds
    * @param municipalities
    * @return
    */
  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set(), showSpeedLimitsHistory: Boolean = false,
                       roadLinkFilter: RoadLink => Boolean = _ => true): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksByBoundsAndMunicipalities(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks, showHistory = showSpeedLimitsHistory, roadLinkFilter = roadLinkFilter)
    val assetsWithAttributes = enrichLinearAssetAttributes(linearAssets, roadLinks)
    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  def getComplementaryByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    val assetsWithAttributes = enrichLinearAssetAttributes(linearAssets, roadLinks)
    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  private def addMunicipalityCodeAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("municipality" -> roadLink.municipalityCode))
  }

  private def addConstructionTypeAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("constructionType" -> roadLink.constructionType.value))
  }

  private def addFunctionalClassAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("functionalClass" -> roadLink.functionalClass))
  }

  private def addLinkTypeAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("linkType" -> roadLink.linkType.value))
  }

  private def enrichLinearAssetAttributes(linearAssets: Seq[PieceWiseLinearAsset], roadLinks: Seq[RoadLink]): Seq[PieceWiseLinearAsset] = {
    val linearAssetAttributeOperations: Seq[(PieceWiseLinearAsset, RoadLink) => PieceWiseLinearAsset] = Seq(
      addMunicipalityCodeAttribute,
      addConstructionTypeAttribute,
      addFunctionalClassAttribute,
      addLinkTypeAttribute
      //In the future if we need to add more attributes just add a method here
    )

    val linkData = roadLinks.map(rl => (rl.linkId, rl)).toMap

    linearAssets.map(linearAsset =>
      linearAssetAttributeOperations.foldLeft(linearAsset) { case (asset, operation) =>
        linkData.get(asset.linkId).map{
          roadLink =>
            operation(asset, roadLink)
        }.getOrElse(asset)
      }
    )
  }

  def getAssetsByMunicipality(typeId: Int, municipality: Int): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChanges(municipality)
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    withDynTransaction {
      dao.fetchLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId)
    }.filterNot(_.expired)
  }

  /**
    * Returns linear assets by municipality. Used by all IntegrationApi linear asset endpoints (except speed limits).
    *
    * @param typeId
    * @param municipality
    * @return
    */
  def getByMunicipality(typeId: Int, municipality: Int, roadLinkFilter: RoadLink => Boolean = _ => true): Seq[PieceWiseLinearAsset] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(municipality)
    val linearAssets = getByRoadLinks(typeId, roadLinks, generateUnknownBoolean = false, roadLinkFilter = roadLinkFilter)
    linearAssets.map(asset => {
      val roadLink = roadLinks.find(_.linkId == asset.linkId).get
      val (startMeasure, endMeasure, geometry) = GeometryUtils.useRoadLinkMeasuresIfCloseEnough(asset.startMeasure, asset.endMeasure, roadLink)
      asset.copy(startMeasure = startMeasure, endMeasure = endMeasure, geometry = geometry)
    })
  }

  def getByMunicipalityAndRoadLinks(typeId: Int, municipality: Int): Seq[(PieceWiseLinearAsset, RoadLink)] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(municipality)
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    linearAssets.map{ asset => (asset, roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new NoSuchElementException))}
  }

  def getLinearMiddlePointAndSourceById(typeId: Int, assetId: Long): (Long, Option[Point], Option[Int])  = {
    val optLrmInfo = withDynTransaction {
      dao.getAssetLrmPosition(typeId, assetId)
    }
    val roadLinks: Option[RoadLinkLike] = optLrmInfo.flatMap( x => roadLinkService.getRoadLinkAndComplementaryByLinkId(x._1))

    val (middlePoint, source) = (optLrmInfo, roadLinks) match {
      case (Some(lrmInfo), Some(road)) =>
        (GeometryUtils.calculatePointFromLinearReference(road.geometry, lrmInfo._2 + (lrmInfo._3 - lrmInfo._2) / 2.0), Some(road.linkSource.value))
      case _ => (None, None)
    }
    (assetId, middlePoint, source)
  }

  protected def getUncheckedLinearAssets(areas: Option[Set[Int]]): Map[String, Map[String,List[Long]]]

  def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()):  Map[String, Map[String, Any]]

  def getUnverifiedLinearAssets(typeId: Int, municipalityCodes: Set[Int]): Map[String, Map[String,List[Long]]] = {
    withDynTransaction {
      if (!verifiableAssetType.contains(typeId)) throw new IllegalStateException("Asset type not allowed")

      val unVerifiedAssets = dao.getUnVerifiedLinearAsset(typeId)
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(unVerifiedAssets.map(_._2).toSet, false)

      val roads = if (municipalityCodes.nonEmpty) roadLinks.filter(road => municipalityCodes.contains(road.municipalityCode)).filterNot(_.administrativeClass == State)
                        else roadLinks.filterNot(_.administrativeClass == State)

      val unVerified =  unVerifiedAssets.flatMap {
        case (id, linkId) =>  roads.filter(_.linkId == linkId).map { road =>
            (road.municipalityCode, id, road.administrativeClass)
          }
    }
      val municipalityNames = if (unVerified.nonEmpty) municipalityDao.getMunicipalitiesNameAndIdByCode(unVerified.map(_._1).toSet).groupBy(_.id)
                                else  List[MunicipalityInfo]().groupBy(_.id)

      unVerified.groupBy(_._1).map{
        case (municipalityCode, grouped) => (municipalityNames(municipalityCode).map(_.name).head, grouped)}
        .mapValues(municipalityAssets => municipalityAssets
          .groupBy(_._3.toString)
          .mapValues(_.map(_._2)))
    }
  }

   def getVerifiedBy(userName: String, assetType: Int): Option[String] = {
    val notVerifiedUser = Set(AutoGeneratedUsername.generatedInUpdate, AutoGeneratedUsername.dr1Conversion)

    if (!notVerifiedUser.contains(userName) && verifiableAssetType.contains(assetType)) Some(userName) else None
  }

  def fetchExistingAssetsByLinksIdsString(typeId: Int, linksIds: Set[String], removedLinkIds: Set[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val existingAssets = if (newTransaction) {
      withDynTransaction {
        dao.fetchLinearAssetsByLinkIds(typeId, (linksIds ++ removedLinkIds).toSeq, LinearAssetTypes.numericValuePropertyId)
      }.filterNot(_.expired)
    } else {
      dao.fetchLinearAssetsByLinkIds(typeId, (linksIds ++ removedLinkIds).toSeq, LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)
    }
    existingAssets
  }
  
  def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
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

  protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], generateUnknownBoolean: Boolean = true, showHistory: Boolean = false,
                               roadLinkFilter: RoadLink => Boolean = _ => true): Seq[PieceWiseLinearAsset] = {

    val existingAssets = fetchExistingAssetsByLinksIds(typeId, roadLinks, Seq())
    val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(existingAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
    if (generateUnknownBoolean) generateUnknowns(roadLinks, linearAssets.groupBy(_.linkId), typeId) else linearAssets
  }
  /**
    * Make sure operations are small and fast.
    * Do not try to use methods which also use event bus, publishing will not work
    * @param linksIds
    * @param typeId asset type
    */
  def adjustLinearAssetsAction(linksIds: Set[String], typeId: Int, newTransaction: Boolean = true,adjustSideCode: Boolean = false): Unit = {
    if (newTransaction) withDynTransaction {action(false)} else action(newTransaction)
    def action(newTransaction: Boolean): Unit = {
      try {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksIds, newTransaction = newTransaction)
        val existingAssets = fetchExistingAssetsByLinksIds(typeId, roadLinks, Seq(), newTransaction = newTransaction)
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

  def adjustAssets(ids: Seq[Long]): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dao.fetchLinearAssetsByIds(ids.toSet, LinearAssetTypes.numericValuePropertyId)
      adjustLinearAssetsAction(linearAsset.map(_.linkId).toSet, linearAsset.head.typeId, newTransaction = false)
    }
    ids
  }
  
  def adjustLinearAssets(roadLinks: Seq[RoadLink], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                         typeId: Int, changeSet: Option[ChangeSet] = None, geometryChanged: Boolean, counter: Int = 1): Seq[PieceWiseLinearAsset] = {
    val assetUpdater = getAssetUpdater(typeId)
    val (filledTopology, changedSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), linearAssets,  typeId, changeSet, geometryChanged)
    val adjustmentsChangeSet = LinearAssetFiller.cleanRedundantMValueAdjustments(changedSet, linearAssets.values.flatten.toSeq)
    adjustmentsChangeSet.isEmpty match {
      case true => filledTopology
      case false if counter > 3 =>
        assetUpdater.updateChangeSet(adjustmentsChangeSet)
        filledTopology
      case false if counter <= 3 =>
        assetUpdater.updateChangeSet(adjustmentsChangeSet)
        val linearAssetsToAdjust = filledTopology.filterNot(asset => asset.id <= 0 && asset.value.isEmpty).groupBy(_.linkId)
        adjustLinearAssets(roadLinks, linearAssetsToAdjust, typeId, None, geometryChanged, counter + 1)
    }
  }
  def adjustLinearAssetsSideCode(roadLinks: Seq[RoadLink], linearAssets: Map[String, Seq[PieceWiseLinearAsset]],
                         typeId: Int, changeSet: Option[ChangeSet] = None, geometryChanged: Boolean, counter: Int = 1): Seq[PieceWiseLinearAsset] = {
    val assetUpdater = getAssetUpdater(typeId)
    val (filledTopology, changedSet) = assetFiller.adjustSideCodes(roadLinks.map(assetFiller.toRoadLinkForFillTopology), linearAssets, typeId, changeSet)
    val adjustmentsChangeSet = LinearAssetFiller.cleanRedundantMValueAdjustments(changedSet, linearAssets.values.flatten.toSeq)
    adjustmentsChangeSet.isEmpty match {
      case true => filledTopology
      case false if counter > 3 =>
        assetUpdater.updateChangeSet(adjustmentsChangeSet)
        filledTopology
      case false if counter <= 3 =>
        assetUpdater.updateChangeSet(adjustmentsChangeSet)
        val linearAssetsToAdjust = filledTopology.filterNot(asset => asset.id <= 0 && asset.value.isEmpty).groupBy(_.linkId)
        adjustLinearAssets(roadLinks, linearAssetsToAdjust, typeId, None, geometryChanged, counter + 1)
    }
  }
  def generateUnknowns(roadLinks: Seq[RoadLink], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int): Seq[PieceWiseLinearAsset] = {
     assetFiller.generateUnknowns(roadLinks.map(assetFiller.toRoadLinkForFillTopology), linearAssets, typeId)._1
  }

  /**
    * Returns linear assets by asset type and asset ids. Used by Digiroad2Api /linearassets POST and /linearassets DELETE endpoints.
    */
  def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction)
      withDynTransaction {
        dao.fetchLinearAssetsByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
      }
    else
      dao.fetchLinearAssetsByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
  }

  def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        dao.fetchLinearAssetsByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId))
      }
    else
      dao.fetchLinearAssetsByLinkIds(typeId, linkIds, LinearAssetTypes.getValuePropertyId(typeId))
  }

  /**
    * This method returns linear assets that have been changed in OTH between given date values. It is used by TN-ITS ChangeApi.
    *
    * @param typeId
    * @param since
    * @param until
    * @param withAutoAdjust
    * @return Changed linear assets
    */
  def getChanged(typeId: Int, since: DateTime, until: DateTime, withAutoAdjust: Boolean = false, token: Option[String] = None): Seq[ChangedLinearAsset] = {
    val persistedLinearAssets = withDynTransaction {
      dao.getLinearAssetsChangedSince(typeId, since, until, withAutoAdjust, token)
    }
    val roadLinks = roadLinkService.getRoadLinksByLinkIds(persistedLinearAssets.map(_.linkId).toSet).filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)
    mapPersistedAssetChanges(persistedLinearAssets, roadLinks)
  }

  def mapPersistedAssetChanges(persistedLinearAssets: Seq[PersistedLinearAsset], roadLinksWithoutWalkways: Seq[RoadLink]): Seq[ChangedLinearAsset] = {
    persistedLinearAssets.flatMap { persistedLinearAsset =>
      roadLinksWithoutWalkways.find(_.linkId == persistedLinearAsset.linkId).map { roadLink =>
        val points = GeometryUtils.truncateGeometry3D(roadLink.geometry, persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure)
        val endPoints: Set[Point] =
          try {
            val ep = GeometryUtils.geometryEndpoints(points)
            Set(ep._1, ep._2)
          } catch {
            case ex: NoSuchElementException =>
              logger.warn("Asset is outside of geometry, asset id " + persistedLinearAsset.id)
              val wholeLinkPoints = GeometryUtils.geometryEndpoints(roadLink.geometry)
              Set(wholeLinkPoints._1, wholeLinkPoints._2)
          }
        ChangedLinearAsset(
          linearAsset = PieceWiseLinearAsset(
            persistedLinearAsset.id, persistedLinearAsset.linkId, SideCode(persistedLinearAsset.sideCode), persistedLinearAsset.value, points, persistedLinearAsset.expired,
            persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure,
            endPoints, persistedLinearAsset.modifiedBy, persistedLinearAsset.modifiedDateTime,
            persistedLinearAsset.createdBy, persistedLinearAsset.createdDateTime, persistedLinearAsset.typeId, roadLink.trafficDirection,
            persistedLinearAsset.timeStamp, persistedLinearAsset.geomModifiedDate, persistedLinearAsset.linkSource, roadLink.administrativeClass,
            verifiedBy = persistedLinearAsset.verifiedBy, verifiedDate = persistedLinearAsset.verifiedDate, informationSource = persistedLinearAsset.informationSource)
          ,
          link = roadLink
        )
      }
    }
  }

  /**
    * Expires linear asset. Used by Digiroad2Api /linearassets DELETE endpoint and Digiroad2Context.LinearAssetUpdater actor.
    */
  def expire(ids: Seq[Long], username: String): Seq[Long] = {
    if (ids.nonEmpty)
      logger.info("Expiring ids " + ids.mkString(", "))
    withDynTransaction {
      ids.foreach(dao.updateExpiration(_, true, username))
      ids
    }
  }

  /**
    * Saves updated linear asset from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def update(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, timeStamp, sideCode, measures)
    }
  }

  def expireAsset(typeId: Int, id: Long, username: String, expired : Boolean, newTransaction: Boolean = true): Option[Long] = {
    if (newTransaction)
      withDynTransaction {
        dao.updateExpiration(id, expired, username)
      }
    else
      dao.updateExpiration(id, expired, username)
  }

  def expireAssets(ids: Seq[Long], expired: Boolean,username: String,newTransaction: Boolean = true):Unit = {
    if (newTransaction) withDynTransaction {dao.updateExpirations(ids, expired,username)}
    else dao.updateExpirations(ids, expired,username)
  }

  /**
    * Sets the linear asset value to None for numeric value properies.
    * Used by Digiroad2Api /linearassets POST endpoint.
    */
  def clearValue(ids: Seq[Long], username: String): Seq[Long] = {
    withDynTransaction {
      ids.flatMap(id => dao.clearValue(id, LinearAssetTypes.numericValuePropertyId, username))
    }
  }


  /**
    * Mark VALID_TO field of old asset to current_timestamp and create a new asset.
    * Copy all the data from old asset except the properties that changed, modifiedBy and modifiedAt.
    */
  protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String,
                                        measures: Option[Measures], timeStamp: Option[Long], sideCode: Option[Int], informationSource: Option[Int] = None): Option[Long] = {
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
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, timeStamp.getOrElse(createTimeStamp()),
      roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, verifiedBy =  getVerifiedBy(username, oldAsset.typeId), informationSource = informationSource)

      Some(newAssetIDcreate)
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, timeStamp: Long = createTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadLink = roadLinkService.getRoadLinksAndComplementariesByLinkIds(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, timeStamp, roadLink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId))
      }
    }
  }

  /**
    * Saves or update linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def createOrUpdate(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String,valueOption: Option[Value], existingAssetIds: Set[Long]): Seq[Long] = {
    val ids = create(newLinearAssets,typeId,username) ++ valueOption.map(update(existingAssetIds.toSeq, _, username)).getOrElse(Nil)
    adjustLinearAssetsAction(getPersistedAssetsByIds(typeId, ids.toSet).map(_.linkId).toSet, typeId)
    ids.distinct
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon). Used by Digiroad2Api /linearassets/:id POST endpoint.
    */
  def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
   val ids = withDynTransaction {
      val linearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
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


  def getByZoomLevel(typeId: Int, boundingRectangle: BoundingRectangle, linkGeomSource: Option[LinkGeomSource] = None) : Seq[Seq[LightLinearAsset]] = {
    withDynTransaction {
      val assets = dao.fetchLinearAssets(typeId, LinearAssetTypes.getValuePropertyId(typeId), boundingRectangle, linkGeomSource)
      Seq(assets)
    }
  }

  /**
    * Sets linear assets with no geometry as floating. Used by Used by Digiroad2Context.LinearAssetUpdater actor.
    */
  def drop(ids: Set[Long]): Unit = {
    withDynTransaction {
      dao.floatLinearAssets(ids)
    }
  }


  /**
    * Saves linear assets when linear asset is separated to two sides in UI. Used by Digiroad2Api /linearassets/:id/separate POST endpoint.
    */
  def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
   val ids = withDynTransaction {
      val existing = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id)

      val (newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink), fromUpdate = true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)),
          valueAgainstDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.AgainstDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink), fromUpdate = true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)))
      Seq(newId1, newId2).flatten
    }

    if (adjust) adjustAssets(ids)else ids
  }
  def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None, fromAssetUpdater: Boolean = false): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    ids.flatMap { id =>
      val oldLinearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val newMeasures = measures.getOrElse(Measures(oldLinearAsset.startMeasure, oldLinearAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldLinearAsset.sideCode)
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(oldLinearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      value match {
        case NumericValue(intValue) =>
          if (((validateMinDistance(newMeasures.startMeasure, oldLinearAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldLinearAsset.endMeasure)) || newSideCode != oldLinearAsset.sideCode) && !fromAssetUpdater) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, NumericValue.apply(intValue), newSideCode, newMeasures, username, createTimeStamp(), Some(roadLink), fromUpdate = true, createdByFromUpdate = Some(username), verifiedBy = oldLinearAsset.verifiedBy, informationSource = informationSource))
          }
          else
            dao.updateValue(id, intValue, LinearAssetTypes.numericValuePropertyId, username)
        case _ => Some(id)
      }
    }
  }

  def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                               createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()),
                               modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      timeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, modifiedByFromUpdate, modifiedDateTimeFromUpdate, verifiedBy, informationSource = informationSource, geometry = getGeometry(roadLink))
    value match {
      case NumericValue(intValue) =>
        dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
      case _ => None
    }
    id
  }
  def createMultipleLinearAssets(list: Seq[NewLinearAssetMassOperation]): Unit = {
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


  def updateVerifiedInfo(ids: Set[Long], userName: String, type_id: Int): Set[Long] = {
    withDynTransaction {
      getVerifiedBy(userName, type_id) match {
        case Some(user) => dao.updateVerifiedInfo(ids, user)
        case _ =>
      }
    }
    ids
  }

  def validateCondition(asset: NewLinearAsset): Unit = {}

  protected def createLinearAssetFromTrafficSign(trafficSignInfo: TrafficSignInfo): Seq[Long] = {Seq()}

  def deleteOrUpdateAssetBasedOnSign(id: Long, additionalPanel: Seq[AdditionalPanel] = Seq(), username: Option[String] = None, withTransaction: Boolean = true) : Unit = {
    logger.info("expiring asset")
    if (withTransaction) {
      withDynTransaction {
        dao.deleteByTrafficSign(withId(id), username)
      }
    }
  }

  def withId(id: Long)(query: String): String = {
    query + s" and a.id = $id"
  }

  def withIds(ids: Set[Long])(query: String): String = {
    query + s" and a.id in (${ids.mkString(",")})"
  }

  def withMunicipalities(municipalities: Set[Int])(query: String): String = {
    query + s" and a.municipality_code in (${municipalities.mkString(",")}) and a.created_by != 'batch_process_trafficSigns'"
  }

  def getAutomaticGeneratedAssets(municipalities: Set[Int], assetTypeId: Int): Seq[(String, Seq[Long])] = {
    withDynTransaction {
      val lastCreationDate = dao.getLastExecutionDateOfConnectedAsset(assetTypeId)
      if (lastCreationDate.nonEmpty) {
        val automaticGeneratedAssets = dao.getAutomaticGeneratedAssets(municipalities.toSeq, assetTypeId, lastCreationDate).groupBy(_._2)
        val municipalityNames = municipalityDao.getMunicipalitiesNameAndIdByCode(automaticGeneratedAssets.keys.toSet)

        automaticGeneratedAssets.map {
          generatedAsset =>
            (municipalityNames.find(_.id == generatedAsset._1).get.name, generatedAsset._2.map(_._1))
        }.toSeq
      }
      else Seq()
    }
  }
}

class LinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools : PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = throw new UnsupportedOperationException("Not supported method")

}

class MissingMandatoryPropertyException(val missing: Set[String]) extends RuntimeException {
}

class AssetValueException(value: String) extends RuntimeException {override def getMessage: String = value
}
