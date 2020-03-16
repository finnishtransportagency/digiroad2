package fi.liikennevirasto.digiroad2.service.linearasset

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, MunicipalityInfo, OracleAssetDao, Queries}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.{AssetFiller, _}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignInfo
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

object LinearAssetTypes {
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
  val VvhGenerated = "vvh_generated"
  val dr1Conversion = "dr1_conversion"
}

case class ChangedLinearAsset(linearAsset: PieceWiseLinearAsset, link: RoadLink)
case class Measures(startMeasure: Double, endMeasure: Double)

trait LinearAssetOperations {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def dao: OracleLinearAssetDao
  def municipalityDao: MunicipalityDao
  def eventBus: DigiroadEventBus
  def polygonTools : PolygonTools
  def assetDao: OracleAssetDao
  def assetFiller: AssetFiller = new AssetFiller

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  val logger = LoggerFactory.getLogger(getClass)
  val verifiableAssetType = Set(30, 40, 50, 60, 70, 80, 90, 100, 120, 140, 160, 190, 210)

  def getMunicipalityCodeByAssetId(assetId: Int): Int = {
    withDynTransaction {
      assetDao.getAssetMunicipalityCodeById(assetId)
    }
  }

  protected def getLinkSource(roadLink: Option[RoadLinkLike]): Option[Int] = {
    roadLink match {
      case Some(road) =>
        Some(road.linkSource.value)
      case _ => None
    }
  }

  protected def getGeometry(roadLink: Option[RoadLinkLike]): Seq[Point] = {
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
  def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks, change)
    val assetsWithAttributes = enrichLinearAssetAttributes(linearAssets, roadLinks)
    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  def getComplementaryByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks, change)
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

  def getByIntersectedBoundingBox(typeId: Int, serviceAreas : Set[Int], bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    getEitherByIntersectedBoundingBox(typeId, serviceAreas, bounds, municipalities, roadLinkService.getRoadLinksAndChangesFromVVHWithPolygon)
  }

  def getComplementaryByIntersectedBoundingBox(typeId: Int, serviceAreas : Set[Int], bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    getEitherByIntersectedBoundingBox(typeId, serviceAreas, bounds, municipalities, roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHWithPolygon)
  }

  def getEitherByIntersectedBoundingBox(typeId: Int, serviceAreas : Set[Int], bounds: BoundingRectangle, municipalities: Set[Int] = Set(), getRoadlinks: (Polygon) => (Seq[RoadLink], Seq[ChangeInfo])): Seq[Seq[PieceWiseLinearAsset]] = {
    val polygons = polygonTools.geometryInterceptorToBoundingBox(polygonTools.getAreasGeometries(serviceAreas),bounds)
    val vVHRoadLinksAndChanges = polygons.map(getRoadlinks)
    val roadLinks = vVHRoadLinksAndChanges.flatMap(_._1)
    val changes = vVHRoadLinksAndChanges.flatMap(_._2)
    val linearAssets = getByRoadLinks(typeId, roadLinks, changes)
    LinearAssetPartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  def getAssetsByMunicipality(typeId: Int, municipality: Int): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
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
  def getByMunicipality(typeId: Int, municipality: Int): Seq[PieceWiseLinearAsset] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
    getByRoadLinks(typeId, roadLinks, change)
  }

  def getByMunicipalityAndRoadLinks(typeId: Int, municipality: Int): Seq[(PieceWiseLinearAsset, RoadLink)] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
    val linearAssets = getByRoadLinks(typeId, roadLinks, change)
    linearAssets.map{ asset => (asset, roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new NoSuchElementException))}
  }

  def getLinearMiddlePointAndSourceById(typeId: Int, assetId: Long): (Long, Option[Point], Option[Int])  = {
    val optLrmInfo = withDynTransaction {
      dao.getAssetLrmPosition(typeId, assetId)
    }
    val roadLinks: Option[RoadLinkLike] = optLrmInfo.flatMap( x => roadLinkService.getRoadLinkAndComplementaryFromVVH(x._1))

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
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(unVerifiedAssets.map(_._2).toSet, false)

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

  protected def getVerifiedBy(userName: String, assetType: Int): Option[String] = {
    val notVerifiedUser = Set("vvh_generated", "dr1_conversion", "dr1conversion")

    if (!notVerifiedUser.contains(userName) && verifiableAssetType.contains(assetType)) Some(userName) else None
  }

  protected def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
         dao.fetchLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId)
      }.filterNot(_.expired)
    existingAssets
  }

  protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {

    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val existingAssets = fetchExistingAssetsByLinksIds(typeId, roadLinks, removedLinkIds)

    val timing = System.currentTimeMillis
    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

    val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
                               expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot( _ == 0L),
                               adjustedMValues = Seq.empty[MValueAdjustment],
                               adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
                               adjustedSideCodes = Seq.empty[SideCodeAdjustment],
                               valueAdjustments = Seq.empty[ValueAdjustment])

    val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      assetsOnChangedLinks, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

    val newAssets = projectedAssets ++ assetsWithoutChangedLinks

    if (newAssets.nonEmpty) {
      logger.info("Finnish transfer %d assets at %d ms after start".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (assetsOnChangedLinks.filterNot(a => projectedAssets.exists(_.linkId == a.linkId)) ++ projectedAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

    publish(eventBus, changeSet, projectedAssets)
    filledTopology
  }

  def publish(eventBus: DigiroadEventBus, changeSet: ChangeSet, projectedAssets: Seq[PersistedLinearAsset]) {
    eventBus.publish("linearAssets:update", changeSet)
    eventBus.publish("linearAssets:saveProjectedLinearAssets", projectedAssets.filter(_.id == 0L))
  }

  /**
    * Uses VVH ChangeInfo API to map OTH linear asset information from old road links to new road links after geometry changes.
    */
  protected def fillNewRoadLinksWithPreviousAssetsData(roadLinks: Seq[RoadLink], assetsToUpdate: Seq[PersistedLinearAsset],
                                                     currentAssets: Seq[PersistedLinearAsset], changes: Seq[ChangeInfo], changeSet: ChangeSet, existingAssets: Seq[PersistedLinearAsset]) : (Seq[PersistedLinearAsset], ChangeSet) ={

    val (replacementChanges, otherChanges) = changes.partition(isReplacementChange)
    val reverseLookupMap = replacementChanges.filterNot(c=>c.oldId.isEmpty || c.newId.isEmpty).map(c => c.newId.get -> c).groupBy(_._1).mapValues(_.map(_._2))

    val extensionChanges = otherChanges.filter(isExtensionChange).flatMap(
      ext => reverseLookupMap.getOrElse(ext.newId.getOrElse(0L), Seq()).flatMap(
        rep => addSourceRoadLinkToChangeInfo(ext, rep)))

    val fullChanges = extensionChanges ++ replacementChanges


    val linearAssetsAndChanges = mapReplacementProjections(assetsToUpdate, currentAssets, roadLinks, fullChanges).flatMap {
      case (asset, (Some(roadLink), Some(projection))) =>
        val (linearAsset, changes) = assetFiller.projectLinearAsset(asset, roadLink, projection, changeSet)
        Some((linearAsset, changes))
      case _ => None
    }

    val linearAssets = linearAssetsAndChanges.map(_._1)

    val generatedChangeSet = linearAssetsAndChanges.map(_._2)
    val changeSetF = if (generatedChangeSet.nonEmpty) { generatedChangeSet.last } else { changeSet }
    val newLinearAsset = if((linearAssets ++ existingAssets).nonEmpty) {
      newChangeAsset(roadLinks, linearAssets ++ existingAssets, changes)
    } else Seq()

    (linearAssets ++ newLinearAsset, changeSetF)
  }

  def newChangeAsset(roadLinks: Seq[RoadLink], existingAssets: Seq[PersistedLinearAsset], changes: Seq[ChangeInfo]): Seq[PersistedLinearAsset] = {
    def getAdjacentAssetByPoint(assets: Seq[(Point, PersistedLinearAsset)], point: Point) : Seq[PersistedLinearAsset] = {
      assets.filter{case (assetPt, _) => GeometryUtils.areAdjacent(assetPt, point)}.map(_._2)
    }

    def getAssetsAndPoints(existingAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], changeInfo: (ChangeInfo, RoadLink)): Seq[(Point, PersistedLinearAsset)] = {
      existingAssets.flatMap { asset =>
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

    val changesNew = changes.filter(_.changeType == New.value)
    val linksWithExpiredAssets = withDynTransaction {
      dao.getLinksWithExpiredAssets(changesNew.flatMap(_.newId.map(x => x)), existingAssets.head.typeId)
    }

    changesNew.filterNot(chg => existingAssets.exists(_.linkId == chg.newId.get) || linksWithExpiredAssets.contains(chg.newId.get)).flatMap { change =>
      roadLinks.find(_.linkId == change.newId.get).map { changeRoadLink =>
        val assetAndPoints : Seq[(Point, PersistedLinearAsset)] = getAssetsAndPoints(existingAssets, roadLinks, (change, changeRoadLink))
        val (first, last) = GeometryUtils.geometryEndpoints(changeRoadLink.geometry)

        if (assetAndPoints.nonEmpty) {
          val assetAdjFirst = getAdjacentAssetByPoint(assetAndPoints, first)
          val assetAdjLast = getAdjacentAssetByPoint(assetAndPoints, last)

          val groupBySideCodeFirst = assetAdjFirst.groupBy(_.sideCode)
          val groupBySideCodeLast = assetAdjLast.groupBy(_.sideCode)

          if (assetAdjFirst.nonEmpty && assetAdjLast.nonEmpty) {
            groupBySideCodeFirst.keys.flatMap { sideCode =>
              groupBySideCodeFirst(sideCode).find(asset => groupBySideCodeLast(sideCode).exists(_.value.equals(asset.value))).map { asset =>
                asset.copy(id = 0, linkId = changeRoadLink.linkId, startMeasure = 0L.toDouble, endMeasure = GeometryUtils.geometryLength(changeRoadLink.geometry))
              }
            }
          } else
            Seq()
        } else
          Seq()
      }
    }.flatten
  }

  private def mapReplacementProjections(oldLinearAssets: Seq[PersistedLinearAsset], currentLinearAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) : Seq[(PersistedLinearAsset, (Option[RoadLink], Option[Projection]))] = {

    val targetLinks = changes.flatMap(_.newId).toSet
    val newRoadLinks = roadLinks.filter(rl => targetLinks.contains(rl.linkId)).groupBy(_.linkId)
    val changeMap = changes.filterNot(c => c.newId.isEmpty || c.oldId.isEmpty).map(c => (c.oldId.get, c.newId.get)).groupBy(_._1)
    val targetRoadLinks = changeMap.mapValues(a => a.flatMap(b => newRoadLinks.getOrElse(b._2, Seq())).distinct)
    val groupedLinearAssets = currentLinearAssets.groupBy(_.linkId)
    val groupedOldLinearAssets = oldLinearAssets.groupBy(_.linkId)
    oldLinearAssets.flatMap{asset =>
      targetRoadLinks.getOrElse(asset.linkId, Seq()).map(newRoadLink =>
        (asset,
          getRoadLinkAndProjection(roadLinks, changes, asset.linkId, newRoadLink.linkId, groupedOldLinearAssets, groupedLinearAssets))
      )}
  }

  private def addSourceRoadLinkToChangeInfo(extensionChangeInfo: ChangeInfo, replacementChangeInfo: ChangeInfo) : Option[ChangeInfo] = {
    def givenAndEqualDoubles(v1: Option[Double], v2: Option[Double]): Boolean = {
      (v1, v2) match {
        case (Some(d1), Some(d2)) => d1 == d2
        case _ => false
      }
    }
    def givenAndEqualLongs(v1: Option[Long], v2: Option[Long]): Boolean = {
      (v1, v2) match {
        case (Some(l1), Some(l2)) => l1 == l2
        case _ => false
      }
    }
    // Test if these change infos extend each other. Then take the small little piece just after tolerance value to test if it is true there
    val (mStart, mEnd) = (givenAndEqualDoubles(replacementChangeInfo.newStartMeasure, extensionChangeInfo.newEndMeasure),
      givenAndEqualDoubles(replacementChangeInfo.newEndMeasure, extensionChangeInfo.newStartMeasure)) match {
      case (true, false) =>
        (replacementChangeInfo.oldStartMeasure.get + assetFiller.AllowedTolerance,
          replacementChangeInfo.oldStartMeasure.get + assetFiller.AllowedTolerance + assetFiller.MaxAllowedError)
      case (false, true) =>
        (Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - assetFiller.AllowedTolerance - assetFiller.MaxAllowedError),
          Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - assetFiller.AllowedTolerance))
      case (_, _) => (0.0, 0.0)
    }

    if (mStart != mEnd && extensionChangeInfo.vvhTimeStamp == replacementChangeInfo.vvhTimeStamp)
      Option(extensionChangeInfo.copy(oldId = replacementChangeInfo.oldId, oldStartMeasure = Option(mStart), oldEndMeasure = Option(mEnd)))
    else
      None
  }

  private def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: Long, newId: Long,
                                       linearAssetsToUpdate: Map[Long, Seq[PersistedLinearAsset]],
                                       currentLinearAssets: Map[Long, Seq[PersistedLinearAsset]]): (Option[RoadLink], Option[Projection]) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse(0) == oldId && c.newId.getOrElse(0) == newId)
    val projection = changeInfo match {
      case Some(changedPart) =>
        // ChangeInfo object related assets; either mentioned in oldId or in newId
        val linearAssets = (linearAssetsToUpdate.getOrElse(changedPart.oldId.getOrElse(0L), Seq()) ++
          currentLinearAssets.getOrElse(changedPart.newId.getOrElse(0L), Seq())).distinct
        mapChangeToProjection(changedPart, linearAssets)
      case _ => None
    }
    (roadLink,projection)
  }

  private def mapChangeToProjection(change: ChangeInfo, linearAssets: Seq[PersistedLinearAsset]): Option[Projection] = {
    val typed = ChangeType.apply(change.changeType)
    typed match {
      // cases 5, 6, 1, 2
      case ChangeType.DividedModifiedPart  | ChangeType.DividedNewPart | ChangeType.CombinedModifiedPart |
           ChangeType.CombinedRemovedPart => projectAssetsConditionally(change, linearAssets, testNoAssetExistsOnTarget, useOldId=false)
      // cases 3, 7, 13, 14
      case ChangeType.LengthenedCommonPart | ChangeType.ShortenedCommonPart | ChangeType.ReplacedCommonPart |
           ChangeType.ReplacedNewPart =>
        projectAssetsConditionally(change, linearAssets, testAssetOutdated, useOldId=false)
      //TODO check if it is OK, this ChangeType.ReplacedNewPart never used
      case ChangeType.LengthenedNewPart | ChangeType.ReplacedNewPart =>
        projectAssetsConditionally(change, linearAssets, testAssetsContainSegment, useOldId=true)
      case _ =>
        None
    }
  }

  private def testNoAssetExistsOnTarget(assets: Seq[PersistedLinearAsset], linkId: Long, mStart: Double, mEnd: Double,
                                        vvhTimeStamp: Long): Boolean = {
    !assets.exists(l => l.linkId == linkId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(mStart,mEnd)))
  }

  private def testAssetOutdated(assets: Seq[PersistedLinearAsset], linkId: Long, mStart: Double, mEnd: Double,
                                vvhTimeStamp: Long): Boolean = {
    val targetAssets = assets.filter(a => a.linkId == linkId)
    targetAssets.nonEmpty && !targetAssets.exists(a => a.vvhTimeStamp >= vvhTimeStamp)
  }

  private def projectAssetsConditionally(change: ChangeInfo, assets: Seq[PersistedLinearAsset],
                                         condition: (Seq[PersistedLinearAsset], Long, Double, Double, Long) => Boolean,
                                         useOldId: Boolean): Option[Projection] = {
    val id = useOldId match {
      case true => change.oldId
      case _ => change.newId
    }
    (id, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.vvhTimeStamp) match {
      case (Some(targetId), Some(oldStart:Double), Some(oldEnd:Double),
      Some(newStart:Double), Some(newEnd:Double), vvhTimeStamp) =>
        condition(assets, targetId, oldStart, oldEnd, vvhTimeStamp) match {
          case true => Some(Projection(oldStart, oldEnd, newStart, newEnd, vvhTimeStamp))
          case false =>
            None
        }
      case _ =>
        None
    }
  }

  private def testAssetsContainSegment(assets: Seq[PersistedLinearAsset], linkId: Long, mStart: Double, mEnd: Double,
                                       vvhTimeStamp: Long): Boolean = {
    val targetAssets = assets.filter(a => a.linkId == linkId)
    targetAssets.nonEmpty && !targetAssets.exists(a => a.vvhTimeStamp >= vvhTimeStamp) && targetAssets.exists(
      a => GeometryUtils.covered((a.startMeasure, a.endMeasure),(mStart,mEnd)))
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

  def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
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
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(persistedLinearAssets.map(_.linkId).toSet).filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)
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
            persistedLinearAsset.vvhTimeStamp, persistedLinearAsset.geomModifiedDate, persistedLinearAsset.linkSource, roadLink.administrativeClass,
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
  def update(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, vvhTimeStamp, sideCode, measures)
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

  /**
    * Sets the linear asset value to None for numeric value properies.
    * Used by Digiroad2Api /linearassets POST endpoint.
    */
  def clearValue(ids: Seq[Long], username: String): Seq[Long] = {
    withDynTransaction {
      ids.flatMap(id => dao.clearValue(id, LinearAssetTypes.numericValuePropertyId, username))
    }
  }

  /*
   * Creates new linear assets and updates existing. Used by the Digiroad2Context.LinearAssetSaveProjected actor.
   */
  def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit ={
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected linear assets")
    def getValuePropertyId(value: Option[Value], typeId: Int) = {
      value match {
        case Some(NumericValue(intValue)) =>
          LinearAssetTypes.numericValuePropertyId
        case Some(TextualValue(textValue)) =>
          LinearAssetTypes.getValuePropertyId(typeId)
        case _ => ""
      }
    }

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
      if(toUpdate.nonEmpty) {
        val toUpdateText = toUpdate.filter(a =>
          Set(EuropeanRoads.typeId, ExitNumbers.typeId).contains(a.typeId))

        val groupedNum = toUpdate.filterNot(a => toUpdateText.contains(a)).groupBy(a => getValuePropertyId(a.value, a.typeId)).filterKeys(!_.equals(""))
        val groupedText = toUpdateText.groupBy(a => getValuePropertyId(a.value, a.typeId)).filterKeys(!_.equals(""))

        val persisted = (groupedNum.flatMap(group => dao.fetchLinearAssetsByIds(group._2.map(_.id).toSet, group._1)).toSeq ++
          groupedText.flatMap(group => dao.fetchAssetsWithTextualValuesByIds(group._2.map(_.id).toSet, group._1)).toSeq).groupBy(_.id)

        updateProjected(toUpdate, persisted)
        if (newLinearAssets.nonEmpty)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
      }
      toInsert.foreach{ linearAsset =>
        val roadlink = roadLinks.find(_.linkId == linearAsset.linkId)
        val id =
          (linearAsset.createdBy, linearAsset.createdDateTime) match {
            case (Some(createdBy), Some(createdDateTime)) =>
              dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
                Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
                getLinkSource(roadlink), fromUpdate = true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate, geometry = getGeometry(roadlink))
            case _ =>
              dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
                Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
                getLinkSource(roadlink), geometry = getGeometry(roadlink))
          }

        linearAsset.value match {
          case Some(NumericValue(intValue)) =>
            dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
          case Some(TextualValue(textValue)) =>
            dao.insertValue(id, LinearAssetTypes.getValuePropertyId(linearAsset.typeId), textValue)
          case _ => None
        }
      }
      if (toInsert.nonEmpty)
        logger.info("Added assets for linkids " + newLinearAssets.map(_.linkId))
    }
  }

  protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]]) = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }
    toUpdate.foreach { linearAsset =>
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(NumericValue(intValue)) =>
            dao.updateValue(id, intValue, LinearAssetTypes.numericValuePropertyId, LinearAssetTypes.VvhGenerated)
          case _ => None
        }
      }
    }
  }

  /**
    * Mark VALID_TO field of old asset to sysdate and create a new asset.
    * Copy all the data from old asset except the properties that changed, modifiedBy and modifiedAt.
    */
  protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String,
                                        measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int], informationSource: Option[Int] = None): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case NumericValue(intValue) =>
          dao.fetchLinearAssetsByIds(Set(assetId), valuePropertyId).head
        case _ => return None
      }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
    //Create New Asset
    val newAssetIDcreate = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()),
      roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, getVerifiedBy(username, oldAsset.typeId), informationSource)

      Some(newAssetIDcreate)
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadlink = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhTimeStamp, roadlink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId))
      }
    }
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon). Used by Digiroad2Api /linearassets/:id POST endpoint.
    */
  def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink),fromUpdate = true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
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

  def updateChangeSet(changeSet: ChangeSet) : Unit = {
    withDynTransaction {
      dao.floatLinearAssets(changeSet.droppedAssetIds)

      if (changeSet.adjustedMValues.nonEmpty)
        logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

      changeSet.adjustedMValues.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
      }

      if (changeSet.adjustedVVHChanges.nonEmpty)
        logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

      changeSet.adjustedVVHChanges.foreach { adjustment =>
        dao.updateMValuesChangeInfo(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.vvhTimestamp, LinearAssetTypes.VvhGenerated)
      }

      val ids = changeSet.expiredAssetIds.toSeq
      if (ids.nonEmpty)
        logger.info("Expiring ids " + ids.mkString(", "))
      ids.foreach(dao.updateExpiration(_, expired = true, LinearAssetTypes.VvhGenerated))

      if (changeSet.adjustedSideCodes.nonEmpty)
        logger.info("Saving SideCode adjustments for asset/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.assetId).mkString(", "))

      changeSet.adjustedSideCodes.foreach { adjustment =>
        adjustedSideCode(adjustment)
      }

      if (changeSet.valueAdjustments.nonEmpty)
        logger.info("Saving value adjustments for assets: " + changeSet.valueAdjustments.map(a => "" + a.asset.id).mkString(", "))
      changeSet.valueAdjustments.foreach { adjustment =>
        updateWithoutTransaction(Seq(adjustment.asset.id), adjustment.asset.value.get, adjustment.asset.modifiedBy.get)
      }
    }
  }

  def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId), newTransaction = false).head
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))
    expireAsset(oldAsset.typeId, oldAsset.id, LinearAssetTypes.VvhGenerated, expired = true, newTransaction = false)
    createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAsset.value.get, adjustment.sideCode.value, Measures(oldAsset.startMeasure, oldAsset.endMeasure), LinearAssetTypes.VvhGenerated, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), false, Some(LinearAssetTypes.VvhGenerated), None, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))
  }

  /**
    * Saves linear assets when linear asset is separated to two sides in UI. Used by Digiroad2Api /linearassets/:id/separate POST endpoint.
    */
  def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val existing = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id)

      val (newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink), fromUpdate = true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)),
          valueAgainstDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.AgainstDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink), fromUpdate = true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)))

      Seq(newId1, newId2).flatten
    }
  }

  protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    ids.flatMap { id =>
      val oldLinearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val newMeasures = measures.getOrElse(Measures(oldLinearAsset.startMeasure, oldLinearAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldLinearAsset.sideCode)
      val roadLink = vvhClient.fetchRoadLinkByLinkId(oldLinearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      value match {
        case NumericValue(intValue) =>
          if ((validateMinDistance(newMeasures.startMeasure, oldLinearAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldLinearAsset.endMeasure)) || newSideCode != oldLinearAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, NumericValue.apply(intValue), newSideCode, newMeasures, username, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), fromUpdate = true, createdByFromUpdate = Some(username), verifiedBy = oldLinearAsset.verifiedBy, informationSource = informationSource))
          }
          else
            dao.updateValue(id, intValue, LinearAssetTypes.numericValuePropertyId, username)
        case _ => Some(id)
      }
    }
  }

  protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                       createdByFromUpdate: Option[String] = Some(""),
                                       createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy, informationSource = informationSource, geometry = getGeometry(roadLink))
    value match {
      case NumericValue(intValue) =>
        dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
      case _ => None
    }
    id
  }

  /**
    * Received a AssetTypeId and expire All RoadLinks for That AssetTypeId, create new assets based on VVH RoadLink data
    *
    * @param assetTypeId
    */
  def expireImportRoadLinksVVHtoOTH(assetTypeId: Int): Unit = {
    //Get all municipalities for search VVH Roadlinks
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    withDynTransaction {
      //Expire All RoadLinks
      dao.expireAllAssetsByTypeId(assetTypeId)

      //For each municipality get all VVH Roadlinks for pick link id and pavement data
      municipalities.foreach { municipality =>

        //Get All RoadLinks from VVH
        val roadLinks = roadLinkService.getVVHRoadLinksF(municipality)

        var count = 0
        if (roadLinks != null) {

          //Create new Assets for the RoadLinks from VVH
          val newAssets = roadLinks.
            filter(_.attributes.get("SURFACETYPE").contains(2)).
            map(roadLink => NewLinearAsset(roadLink.linkId, 0, GeometryUtils.geometryLength(roadLink.geometry), NumericValue(1), 1, 0, None))
          newAssets.foreach{ newAsset =>
              createWithoutTransaction(assetTypeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), LinearAssetTypes.VvhGenerated, VVHClient.createVVHTimeStamp(),
                roadLinks.find(_.linkId == newAsset.linkId))
            count = count + 1
          }
        }
      }
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
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools : PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = throw new UnsupportedOperationException("Not supported method")

}

class MissingMandatoryPropertyException(val missing: Set[String]) extends RuntimeException {
}

class AssetValueException(value: String) extends RuntimeException {override def getMessage: String = value
}
