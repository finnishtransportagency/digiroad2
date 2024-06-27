package fi.liikennevirasto.digiroad2.util

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{PointAssetValue, _}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{Value, _}
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{MissingMandatoryPropertyException, _}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime
import org.json4s.jackson.Json
import org.json4s.{CustomSerializer, DefaultFormats, Extraction, Formats, JInt, JNull, JObject}
import org.slf4j.LoggerFactory

import scala.util.Try


case class TrafficSignToLinear(roadLink: RoadLink, value: Value, sideCode: SideCode, startMeasure: Double, endMeasure: Double, signId: Set[Long], oldAssetId: Option[Long] = None)

trait TrafficSignLinearGenerator {
  def roadLinkService: RoadLinkService

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)

  def logger = LoggerFactory.getLogger(getClass)

  val assetType: Int

  protected def errorMessage(newSegment: TrafficSignToLinear): String = {
    s"Failed to save linear asset from these traffic sign:${newSegment.signId.toString()} on this link ${newSegment.roadLink.linkId}"
  }

  protected def assetMissingMandatoryProperty(newSegment: TrafficSignToLinear, e: MissingMandatoryPropertyException): String = {
    s"Failed to save linear asset from these traffic sign:${newSegment.signId.toString()} on this link ${newSegment.roadLink.linkId}, asset is missing these properties: ${e.missing.toString()}"
  }
  
  case object TrafficSignSerializer extends CustomSerializer[Property](format =>
    ({
      case jsonObj: JObject =>
        val id = (jsonObj \ "id").extract[Long]
        val publicId = (jsonObj \ "publicId").extract[String]
        val propertyType = (jsonObj \ "propertyType").extract[String]
        val values: Seq[PointAssetValue] = (jsonObj \ "values").extractOpt[Seq[PropertyValue]].getOrElse((jsonObj \ "values").extractOpt[Seq[AdditionalPanel]].getOrElse(Seq()))
        val required = (jsonObj \ "required").extract[Boolean]
        val numCharacterMax = (jsonObj \ "numCharacterMax").extractOpt[Int]

        Property(id, publicId, propertyType, required, values, numCharacterMax)
      case JNull => null
    },
      {
        case tv : Property =>
          Extraction.decompose(tv)
        case _ => JNull
      }))

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ({
    case JInt(lg) => LinkGeomSource.apply(lg.toInt)
    case JNull => LinkGeomSource.Unknown
  }, {
    case lg: LinkGeomSource => JInt(lg.value)
    case _ => JNull
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + TrafficSignSerializer + LinkGeomSourceSerializer

  final val userCreate = "automatic_trafficSign_created"
  final val userUpdate = "automatic_trafficSign_updated"
  final val debbuger = false

  lazy val userProvider: UserProvider = {
    Class.forName(Digiroad2Properties.userProvider).newInstance().asInstanceOf[UserProvider]
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val dynamicLinearAssetService: DynamicLinearAssetService = {
    new DynamicLinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val manoeuvreService: ManoeuvreService = {
    new ManoeuvreService(roadLinkService, new DummyEventBus)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, eventbus)
  }

  lazy val postGisLinearAssetDao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  lazy val dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  def createValue(trafficSigns: Seq[PersistedTrafficSign]): Option[Value]

  def getExistingSegments(roadLinks: Seq[RoadLink]): Seq[PersistedLinearAsset]

  def signBelongTo(trafficSign: PersistedTrafficSign): Boolean

  def updateLinearAsset(oldAssetId: Long, newValue: Value, username: String): Seq[Long]

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset]

  def assetToUpdate(assets: Seq[PersistedLinearAsset], trafficSign: PersistedTrafficSign, createdValue: Value, username: String): Unit

  def createLinearAsset(newSegment: TrafficSignToLinear, username: String): Long

  def mappingValue(segment: Seq[TrafficSignToLinear]): Value

  def compareValue(value1: Value, value2: Value): Boolean

  def withdraw(value1: Value, value2: Value): Value

  def mergeValue(values: Value): Value

  def filterTrafficSigns(trafficSigns: Seq[PersistedTrafficSign], actualRoadLink: RoadLink): Seq[PersistedTrafficSign] = {
    trafficSigns.filter(_.linkId == actualRoadLink.linkId)
  }

  def getPointOfInterest(first: Point, last: Point, sideCode: SideCode): (Option[Point], Option[Point], Option[Int]) = {
    sideCode match {
      case SideCode.TowardsDigitizing => (None, Some(last), Some(sideCode.value))
      case SideCode.AgainstDigitizing => (Some(first), None, Some(sideCode.value))
      case _ => (Some(first), Some(last), Some(sideCode.value))
    }
  }

  def createValidPeriod(trafficSignType: TrafficSignType, additionalPanel: AdditionalPanel): Set[ValidityPeriod] = {
    TimePeriodClass.fromTrafficSign(trafficSignType).filterNot(_ == TimePeriodClass.Unknown).flatMap { period =>
      val regexMatch = "[(]?\\d+\\s*[-]{1}\\s*\\d+[)]?".r
      val validPeriodsCount = regexMatch.findAllIn(additionalPanel.panelValue)
      val validPeriods = regexMatch.findAllMatchIn(additionalPanel.panelValue)

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

  def segmentsManager(roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign], existingSegments: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    logger.debug(s"segmentsManager : roadLinkSize = ${roadLinks.size}")

    val relevantLink = relevantLinkOnChain(roadLinks, trafficSigns)
    val newSegments = relevantLink.flatMap { case (roadLink, startPointOfInterest, lastPointOfInterest) =>
      baseProcess(trafficSigns, roadLinks, roadLink, (startPointOfInterest, lastPointOfInterest, None), Seq())
    }.distinct

    val groupedAssets = (newSegments ++ existingSegments).groupBy(_.roadLink)
    val assets = fillTopology(roadLinks, groupedAssets)

    convertRoadSegments(assets, findStartEndRoadLinkOnChain(roadLinks)).toSet
  }

  def fillTopology(topology: Seq[RoadLink], linearAssets: Map[RoadLink, Seq[TrafficSignToLinear]]): Seq[TrafficSignToLinear] = {
    logger.debug("fillTopology")
    val fillOperations: Seq[Seq[TrafficSignToLinear] => Seq[TrafficSignToLinear]] = Seq(
      combine,
      convertOneSideCode
    )

    topology.foldLeft(Seq.empty[TrafficSignToLinear]) { case (existingAssets, roadLink) =>
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink, Nil)
      val adjustedAssets = fillOperations.foldLeft(assetsOnRoadLink) { case (currentSegments, operation) =>
        operation(currentSegments)
      }
      existingAssets ++ adjustedAssets
    }
  }

  def findStartEndRoadLinkOnChain(roadLinks: Seq[RoadLink]): Seq[(RoadLink, Option[Point], Option[Point])] = {
    logger.debug("findStartEndRoadLinkOnChain")
    val borderRoadLinks = roadLinks.filterNot { r =>
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

    borderRoadLinks.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val isStart = roadLinks.diff(borderRoadLinks).exists { r3 =>
        val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
        GeometryUtils.areAdjacent(first, first2) || GeometryUtils.areAdjacent(first, last2)
      }

      if (isStart) {
        (roadLink, Some(first), None)
      } else
        (roadLink, None, Some(last))
    }
  }

  def relevantLinkOnChain(roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign]): Seq[(RoadLink, Option[Point], Option[Point])] = {
    logger.debug("relevantLinkOnChain")
    val filterRoadLinks = roadLinks.filter { road => trafficSigns.map(_.linkId).contains(road.linkId) }
    val groupedTrafficSigns = trafficSigns.groupBy(_.linkId)

    filterRoadLinks.flatMap { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      groupedTrafficSigns(roadLink.linkId).flatMap { sign =>
        SideCode(sign.validityDirection) match {
          case SideCode.TowardsDigitizing => Seq((roadLink, None, Some(last)))
          case SideCode.AgainstDigitizing => Seq((roadLink, Some(first), None))
          case _ => Seq()
        }
      }
    }
  }

  def segmentsConverter(existingAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink]): (Seq[TrafficSignToLinear], Seq[TrafficSignToLinear]) = {
    logger.debug("segmentsConverter")
    val connectedTrafficSignIds =
      if (existingAssets.nonEmpty)
        postGisLinearAssetDao.getConnectedAssetFromLinearAsset(existingAssets.map(_.id))
      else
        Seq()

    val signIdsGroupedByAssetId = connectedTrafficSignIds.groupBy(_._1)
    val trafficSigns = if (connectedTrafficSignIds.nonEmpty)
      postGisLinearAssetDao.getTrafficSignsToProcessById(connectedTrafficSignIds.map(_._2))
    else Seq()

    val existingWithoutSignsRelation = existingAssets.filter(_.value.isDefined).flatMap { asset =>
      val relevantSigns = trafficSigns.filter(sign => signIdsGroupedByAssetId.get(asset.id) match {
        case Some(values) => values.map(_._2).contains(sign._1)
        case _ => false
      })

      val persistedTrafficSign = relevantSigns.map{case (id, value) => Json(jsonFormats).read[PersistedTrafficSign](value)}
      val createdValue = createValue(persistedTrafficSign)
      if (createdValue.isEmpty)
        Some(TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.apply(asset.sideCode), asset.startMeasure, asset.endMeasure, Set(), None))
      else if (!compareValue(asset.value.get, createdValue.get))
        Some(TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, withdraw(asset.value.get, createdValue.get), SideCode.apply(asset.sideCode), asset.startMeasure, asset.endMeasure, Set(), None))
      else
        None
    }

    val allExistingSegments = existingAssets.filter(_.value.isDefined).map { asset =>
      val trafficSignIds = connectedTrafficSignIds.filter(_._1 == asset.id).map(_._2).toSet
      TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.apply(asset.sideCode), asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id))
    }

    (existingWithoutSignsRelation, allExistingSegments)
  }

  def baseProcess(trafficSigns: Seq[PersistedTrafficSign], roadLinks: Seq[RoadLink], actualRoadLink: RoadLink, previousInfo: (Option[Point], Option[Point], Option[Int]), result: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    logger.debug("baseProcess")
    val filteredRoadLinks = roadLinks.filterNot(_.linkId == actualRoadLink.linkId)
    val filteredTrafficSigns = filterTrafficSigns(trafficSigns, actualRoadLink)
    (if (filteredTrafficSigns.nonEmpty) {
      filteredTrafficSigns.flatMap { sign =>
        val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
        val pointOfInterest = getPointOfInterest(first, last, SideCode(sign.validityDirection))
        createSegmentPieces(actualRoadLink, filteredRoadLinks, sign, trafficSigns, pointOfInterest, result).toSeq ++
          getAdjacents(pointOfInterest, filteredRoadLinks).flatMap { case (roadLink, nextPoint) =>
            baseProcess(trafficSigns, filteredRoadLinks, roadLink, nextPoint, result)
          }
      }
    } else {
      getAdjacents(previousInfo, filteredRoadLinks).flatMap { case (roadLink, nextPoint) =>
        baseProcess(trafficSigns, filteredRoadLinks, roadLink, nextPoint, result)
      }
    }).toSet
  }

  def createSegmentPieces(actualRoadLink: RoadLink, allRoadLinks: Seq[RoadLink], sign: PersistedTrafficSign, signs: Seq[PersistedTrafficSign], pointOfInterest: (Option[Point], Option[Point], Option[Int]), result: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    logger.debug("createSegmentPieces")
    createValue(Seq(sign)) match {
      case Some(value) =>
        val pairSign = getPairSign(actualRoadLink, sign, signs.filter(_.linkId == actualRoadLink.linkId), pointOfInterest._3.get)
        val generatedSegmentPieces = generateSegmentPieces(actualRoadLink, sign, value, pairSign, pointOfInterest._3.get)

        (if (pairSign.isEmpty) {
          val adjRoadLinks = getAdjacents(pointOfInterest, allRoadLinks.filterNot(_.linkId == actualRoadLink.linkId))
          if (adjRoadLinks.nonEmpty) {
            adjRoadLinks.flatMap { case (newRoadLink, (nextFirst, nextLast, nextDirection)) =>
              createSegmentPieces(newRoadLink, allRoadLinks.filterNot(_.linkId == newRoadLink.linkId), sign, signs, (nextFirst, nextLast, nextDirection), generatedSegmentPieces +: result)
            }
          } else
            generatedSegmentPieces +: result
        } else
          generatedSegmentPieces +: result).toSet
      case _ => Set()
    }
  }

  def generateSegmentPieces(currentRoadLink: RoadLink, sign: PersistedTrafficSign, value: Value, pairedSign: Option[PersistedTrafficSign], direction: Int): TrafficSignToLinear = {
    logger.debug("generateSegmentPieces")
    pairedSign match {
      case Some(pair) =>
        if (pair.linkId == sign.linkId) {
          val orderedMValue = Seq(sign.mValue, pair.mValue).sorted

          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), orderedMValue.head, orderedMValue.last, Set(sign.id))
        } else {
          val (starMeasure, endMeasure) = if (SideCode.apply(direction) == TowardsDigitizing)
            (0.toDouble, pair.mValue)
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (pair.mValue, length)
          }
          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(direction), starMeasure, endMeasure, Set(sign.id))
        }
      case _ =>
        if (currentRoadLink.linkId == sign.linkId) {
          val (starMeasure, endMeasure) = if (SideCode.apply(direction) == AgainstDigitizing)
            (0L.toDouble, sign.mValue)
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (sign.mValue, length)
          }

          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(direction), starMeasure, endMeasure, Set(sign.id))
        }
        else {

          val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(direction), 0, length, Set(sign.id))
        }
    }
  }

  def getPairSign(actualRoadLink: RoadLink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], direction: Int): Option[PersistedTrafficSign] = {
    logger.debug("getPairSign")

    allSignsRelated.filterNot(_.id == mainSign.id).filter(_.linkId == actualRoadLink.linkId).find { sign =>
      compareValue(createValue(Seq(mainSign)).get, createValue(Seq(sign)).get) && sign.validityDirection != direction
    }
  }

  def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign): Unit = {
    val username = "automatic_trafficSign_deleted"
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val createdValue = createValue(Seq(trafficSign))

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition { asset =>
      if (createdValue.isEmpty) true else compareValue(asset.value.get, createdValue.get)
    }

    toDelete.foreach { asset =>
      linearAssetService.expireAsset(assetType, asset.id, username, true, false)
      postGisLinearAssetDao.expireConnectedByLinearAsset(asset.id)
    }

    if(createdValue.nonEmpty)
      assetToUpdate(toUpdate, trafficSign, createdValue.get, userUpdate)
  }

  def getAdjacents(previousInfo: (Option[Point], Option[Point], Option[Int]), roadLinks: Seq[RoadLink]): Seq[(RoadLink, (Option[Point], Option[Point], Option[Int]))] = {
    logger.debug("getAdjacents")
    val (prevFirst, prevLast, direction) = previousInfo
    val filter = roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, prevFirst.getOrElse(prevLast.get))
    }

    filter.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val switchDirection = direction match {
        case Some(value) => Some(SideCode.switch(SideCode.apply(value)).value)
        case _ => None
      }
      val complementaryInfo: (Option[Point], Option[Point], Option[Int]) = (prevFirst, prevLast) match {
        case (Some(prevPoint), None) => if (GeometryUtils.areAdjacent(first, prevPoint)) (None, Some(last), switchDirection) else (Some(first), None, direction)
        case _ => if (GeometryUtils.areAdjacent(last, prevLast.get)) (Some(first), None, switchDirection) else (None, Some(last), direction)
      }
      (roadLink, complementaryInfo)
    }
  }

  def createLinearAssetAccordingSegmentsInfo(newSegment: TrafficSignToLinear, username: String): Unit = {
    logger.debug("createLinearAssetAccordingSegmentsInfo")
    try {
      val newAssetId = createLinearAsset(newSegment, username)
      newSegment.signId.foreach { signId =>createAssetRelation(newAssetId, signId)}
    }catch {
      case e:MissingMandatoryPropertyException => logger.error(assetMissingMandatoryProperty(newSegment,e))
      case e: Throwable => logger.error(errorMessage(newSegment)); throw e
    }
  }

  protected def createAssetRelation(linearAssetId: Long, trafficSignId: Long): Unit = {
    logger.debug("createAssetRelation")
    try {
      postGisLinearAssetDao.insertConnectedAsset(linearAssetId, trafficSignId)
    } catch {
      case ex: SQLIntegrityConstraintViolationException => logger.warn("") //the key already exist with a valid date
      case e: Exception => logger.error("SQL Exception ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }

  def deleteLinearAssets(existingSeg: Seq[TrafficSignToLinear]): Unit = {
    logger.debug(s"deleteLinearAssets ${existingSeg.size}")
    existingSeg.foreach { asset =>
      linearAssetService.expireAsset(assetType, asset.oldAssetId.get, userUpdate, true, false)
      postGisLinearAssetDao.expireConnectedByLinearAsset(asset.oldAssetId.get)
    }
  }

  def updateRelation(newSeg: TrafficSignToLinear, oldSeg: TrafficSignToLinear): Unit = {
    oldSeg.signId.diff(newSeg.signId).foreach(sign => postGisLinearAssetDao.expireConnectedByPointAsset(sign))
    newSeg.signId.diff(oldSeg.signId).foreach(sign => createAssetRelation(oldSeg.oldAssetId.get, sign))
  }

  def combine(segments: Seq[TrafficSignToLinear]): Seq[TrafficSignToLinear] = {
    def squash(startM: Double, endM: Double, segments: Seq[TrafficSignToLinear]): Seq[TrafficSignToLinear] = {
      val sl = segments.filter(sl => (sl.startMeasure - 0.01) <= startM && (sl.endMeasure + 0.01) >= endM)
      val a = sl.filter(sl => sl.sideCode.equals(SideCode.AgainstDigitizing) || sl.sideCode.equals(SideCode.BothDirections))
      val t = sl.filter(sl => sl.sideCode.equals(SideCode.TowardsDigitizing) || sl.sideCode.equals(SideCode.BothDirections))

      (a.headOption, t.headOption) match {
        case (Some(x), Some(y)) => Seq(TrafficSignToLinear(x.roadLink, mappingValue(a), AgainstDigitizing, startM, endM, x.signId, x.oldAssetId), TrafficSignToLinear(y.roadLink, mappingValue(t), TowardsDigitizing, startM, endM, y.signId, y.oldAssetId))
        case (Some(x), None) => Seq(TrafficSignToLinear(x.roadLink, mappingValue(a), AgainstDigitizing, startM, endM, x.signId, x.oldAssetId))
        case (None, Some(y)) => Seq(TrafficSignToLinear(y.roadLink, mappingValue(t), TowardsDigitizing, startM, endM, y.signId, y.oldAssetId))
        case _ => Seq()
      }
    }

    def combineEqualValues(segmentPieces: Seq[TrafficSignToLinear]): Seq[TrafficSignToLinear] = {
      val seg1 = segmentPieces.head
      val seg2 = segmentPieces.last
      if (seg1.startMeasure.equals(seg2.startMeasure) && seg1.endMeasure.equals(seg2.endMeasure) && compareValue(seg1.value, seg2.value) && seg1.sideCode != seg2.sideCode) {
        val winnerSegment = if (seg1.oldAssetId.nonEmpty) seg1 else seg2
        Seq(winnerSegment.copy(sideCode = BothDirections, signId = seg1.signId ++ seg2.signId))
      } else
        segmentPieces
    }

    val pointsOfInterest = segments.map(_.startMeasure) ++ segments.map(_.endMeasure)
    val sortedPointsOfInterest =  pointsOfInterest.filterNot(a => pointsOfInterest.exists(x => Math.abs(x - a) <  0.01 && x > a)).distinct.sorted
    if (sortedPointsOfInterest.length < 2)
      return segments
    val pieces = sortedPointsOfInterest.zip(sortedPointsOfInterest.tail)
    val segmentPieces = pieces.flatMap(p => squash(p._1, p._2, segments))
    segmentPieces.groupBy(_.startMeasure).flatMap(n => combineEqualValues(n._2)).toSeq
  }

  def findNextEndAssets(segments: Seq[TrafficSignToLinear], baseSegment: TrafficSignToLinear, result: Seq[TrafficSignToLinear] = Seq(), numberOfAdjacent: Int = 0): Seq[TrafficSignToLinear] = {
    val adjacent = roadLinkService.getAdjacent(baseSegment.roadLink.linkId, false)
    val (start, end) = GeometryUtils.geometryEndpoints(baseSegment.roadLink.geometry)
    val allInSameSide = adjacent.forall { adj =>
      val (first, last) = GeometryUtils.geometryEndpoints(adj.geometry)
      GeometryUtils.areAdjacent(start, first) || GeometryUtils.areAdjacent(start, last)
    }

    if (adjacent.size == 1 || (numberOfAdjacent == 1 && adjacent.size > 1)) {
      val newResult = segments.filter(_ == baseSegment).map(_.copy(sideCode = SideCode.BothDirections)) ++ result
      val newBaseSegment = segments.filter{seg => seg.roadLink == adjacent.head && compareValue(seg.value, baseSegment.value)}

      if(newBaseSegment.nonEmpty)
        findNextEndAssets(segments.filterNot(_ == baseSegment), newBaseSegment.head, newResult, adjacent.size)
      else
        newResult
    } else if(adjacent.size > 1 && allInSameSide) {
      segments.filter(_ == baseSegment).map(_.copy(sideCode = SideCode.BothDirections)) ++ result
    } else
      result :+ baseSegment
  }

  def convertRoadSegments(segments: Seq[TrafficSignToLinear], endRoadLinksInfo: Seq[(RoadLink, Option[Point], Option[Point])]): Seq[TrafficSignToLinear] = {
    val segmentsOndEndRoads = segments.filter { seg =>
      endRoadLinksInfo.exists { case (endRoadLink, firstPoint, lastPoint) =>
        val (first, last) = GeometryUtils.geometryEndpoints(endRoadLink.geometry)
        //if is a lastRoaLink, the point of interest is the first point
        ((firstPoint, lastPoint) match {
          case (Some(firstPointDirection) , None) if !GeometryUtils.areAdjacent(last, firstPointDirection) =>  Math.abs(seg.endMeasure - GeometryUtils.geometryLength(endRoadLink.geometry)) < 0.01
          case (None, Some(lastPointDirection)) if !GeometryUtils.areAdjacent(first, lastPointDirection) => Math.abs(seg.startMeasure - 0) < 0.01
          case _ => false
        }) && seg.roadLink.linkId == endRoadLink.linkId
      }
    }

    val endSegments = segmentsOndEndRoads.flatMap { baseSegment =>
      findNextEndAssets(segments, baseSegment)
    }.distinct

    segments.filterNot(seg => endSegments.exists(endSeg => seg.startMeasure == endSeg.startMeasure && seg.endMeasure == endSeg.endMeasure && seg.roadLink.linkId == endSeg.roadLink.linkId)) ++ endSegments
  }

  def convertOneSideCode(segments: Seq[TrafficSignToLinear]): Seq[TrafficSignToLinear] = {
    segments.map { seg =>
      if (seg.roadLink.trafficDirection != TrafficDirection.BothDirections)
        seg.copy(sideCode = BothDirections)
      else
        seg
    }
  }

  def combineSegments(allSegments: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    val groupedSegments = allSegments.groupBy(_.roadLink)

    groupedSegments.keys.flatMap { RoadLink =>
      val sortedSegments = groupedSegments(RoadLink).sortBy(_.startMeasure)
      sortedSegments.tail.foldLeft(Seq(sortedSegments.head)) { case (result, row) =>
        if (Math.abs(result.last.endMeasure - row.startMeasure) < 0.001 && result.last.value.equals(row.value))
          result.last.copy(endMeasure = row.endMeasure) +: result.init
        else
          result :+ row
      }
    }.toSet
  }

  private def getAllRoadLinksWithSameName(signRoadLink: RoadLink): Seq[RoadLink] = {
    val tsRoadNameInfo =
      if (signRoadLink.attributes.get("ROADNAME_FI").exists(_.toString.trim.nonEmpty)) {
        Some("ROADNAME_FI", signRoadLink.attributes("ROADNAME_FI").toString)
      } else if (signRoadLink.attributes.get("ROADNAME_SE").exists(_.toString.trim.nonEmpty)) {
        Some("ROADNAME_SE", signRoadLink.attributes("ROADNAME_SE").toString)
      } else
        None

    //RoadLink with the same Finnish/Swedish name
    tsRoadNameInfo.map { case (roadNamePublicIds, roadNameSource) =>
      roadLinkService.getRoadLinksAndComplementaryByRoadName(roadNamePublicIds, Set(roadNameSource), false)
        .filter(_.administrativeClass != State)
    }.getOrElse(Seq(signRoadLink))
  }

  def isToUpdateRelation(newSeg: TrafficSignToLinear)(oldSeg: TrafficSignToLinear): Boolean = {
    oldSeg.roadLink.linkId == newSeg.roadLink.linkId && oldSeg.sideCode == newSeg.sideCode &&
      Math.abs(oldSeg.startMeasure - newSeg.startMeasure) < 0.01 && Math.abs(oldSeg.endMeasure - newSeg.endMeasure) < 0.01 &&
      compareValue(oldSeg.value, newSeg.value)
  }

  def isToUpdateValue(newSeg: TrafficSignToLinear)(oldSeg: TrafficSignToLinear): Boolean = {
    oldSeg.roadLink.linkId == newSeg.roadLink.linkId && oldSeg.sideCode == newSeg.sideCode &&
      Math.abs(oldSeg.startMeasure - newSeg.startMeasure) < 0.01 && Math.abs(oldSeg.endMeasure - newSeg.endMeasure) < 0.01
  }

  def applyChangesBySegments(allSegments: Set[TrafficSignToLinear], existingSegments: Seq[TrafficSignToLinear]) : Unit = {
    if (allSegments.isEmpty)
      deleteLinearAssets(existingSegments)
    else {
      val segment = allSegments.head

      val toUpdateRelation = existingSegments.filter(isToUpdateRelation(segment))
      if (toUpdateRelation.nonEmpty) {
        val head = toUpdateRelation.head

        updateRelation(segment, head)
        applyChangesBySegments(allSegments.filterNot(_ == segment), existingSegments.filterNot(_ == head))

      } else {
        val toUpdateValue = existingSegments.filter(isToUpdateValue(segment))
        if (toUpdateValue.nonEmpty) {
          val head = toUpdateValue.head

          updateLinearAsset(head.oldAssetId.get, mergeValue(segment.value), userUpdate)
          updateRelation(segment, head)

          applyChangesBySegments(allSegments.filterNot(_ == segment), existingSegments.filterNot(_ == head))
        } else {
          createLinearAssetAccordingSegmentsInfo(segment, userCreate)

          applyChangesBySegments(allSegments.filterNot(_ == segment), existingSegments)
        }
      }
    }
  }

  def iterativeProcess(roadLinks: Seq[RoadLink], processedRoadLinks: Seq[RoadLink]): Unit = {
    val roadLinkToBeProcessed = roadLinks.diff(processedRoadLinks)

    if (roadLinkToBeProcessed.nonEmpty) {
      val roadLink = roadLinkToBeProcessed.head
      logger.info(s"Processing roadLink linkId ${roadLink.linkId}")
      val allRoadLinksWithSameName = withDynTransaction {
        val roadLinksWithSameName = getAllRoadLinksWithSameName(roadLink)
        if(roadLinksWithSameName.nonEmpty){
          val trafficSigns = trafficSignService.getTrafficSign(roadLinksWithSameName.map(_.linkId))
          val filteredTrafficSigns = trafficSigns.filter(signBelongTo)

          val existingAssets = getExistingSegments(roadLinksWithSameName)
          val (relevantExistingSegments , existingSegments) = segmentsConverter(existingAssets, roadLinksWithSameName)
          logger.info(s"Processing: ${filteredTrafficSigns.size}")

          //create and Modify actions
          val allSegments = segmentsManager(roadLinksWithSameName, filteredTrafficSigns, relevantExistingSegments)
          applyChangesBySegments(allSegments, existingSegments)

          if (trafficSigns.nonEmpty)
            postGisLinearAssetDao.deleteTrafficSignsToProcess(trafficSigns.map(_.id), assetType)

          roadLinksWithSameName
        } else
          Seq()
      }
      iterativeProcess(roadLinkToBeProcessed.filterNot(_.linkId == roadLink.linkId), allRoadLinksWithSameName)
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  def createRoadWorkAssetUsingTrafficSign(): Unit = {
    logger.info(s"Starting create ${AssetTypeInfo.apply(assetType).layerName} using traffic signs")
    logger.info(DateTime.now() + "\n")

    val roadLinks = withDynTransaction {
      val trafficSignsToProcess = postGisLinearAssetDao.getTrafficSignsToProcess(assetType)

      val trafficSigns = if(trafficSignsToProcess.nonEmpty) trafficSignService.fetchPointAssetsWithExpired(withFilter(s"Where a.id in (${trafficSignsToProcess.mkString(",")}) ")) else Seq()
      val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIds(trafficSigns.map(_.linkId).toSet, false).filter(_.administrativeClass != State)
      val trafficSignsToTransform = trafficSigns.filter(asset => roadLinks.exists(_.linkId == asset.linkId))

      logger.info(s"Total of trafficSign to process: ${trafficSigns.size}")
      val tsToDelete = trafficSigns.filter(_.expired)
      tsToDelete.foreach { ts =>
        // Delete actions
        deleteOrUpdateAssetBasedOnSign(ts)
      }

      //Remove the table sign added on State Road
      val trafficSignsToDelete = trafficSigns.diff(trafficSignsToTransform) ++ trafficSigns.filter(_.expired)
      if (trafficSignsToDelete.nonEmpty)
        postGisLinearAssetDao.deleteTrafficSignsToProcess(trafficSignsToDelete.map(_.id), assetType)

      roadLinks
    }
    logger.info("Start processing traffic signs")
    iterativeProcess(roadLinks, Seq())

    logger.info("\nComplete at time: " + DateTime.now())

  }


  def createLinearAssetUsingTrafficSigns(): Unit = {
    logger.info(s"Starting create ${AssetTypeInfo.apply(assetType).layerName} using traffic signs")
    logger.info(DateTime.now().toString())
    logger.info("")

    val roadLinks = withDynTransaction {
      val trafficSignsToProcess = postGisLinearAssetDao.getTrafficSignsToProcess(assetType)

      val trafficSigns = if(trafficSignsToProcess.nonEmpty) trafficSignService.fetchByFilterWithExpiredByIds(trafficSignsToProcess.toSet) else Seq()
      val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIds(trafficSigns.map(_.linkId).toSet, false).filter(_.administrativeClass != State)
      val trafficSignsToTransform = trafficSigns.filter(asset => roadLinks.exists(_.linkId == asset.linkId))

      logger.info(s"Total of trafficSign to process: ${trafficSigns.size}")
      val tsToDelete = trafficSigns.filter(_.expired)
      tsToDelete.foreach { ts =>
        // Delete actions
        deleteOrUpdateAssetBasedOnSign(ts)
      }

      //Remove the table sign added on State Road
      val trafficSignsToDelete = trafficSigns.diff(trafficSignsToTransform) ++ trafficSigns.filter(_.expired)
      if (trafficSignsToDelete.nonEmpty)
        postGisLinearAssetDao.deleteTrafficSignsToProcess(trafficSignsToDelete.map(_.id), assetType)

      roadLinks
    }
    logger.info("Start processing traffic signs")
    iterativeProcess(roadLinks, Seq())
    
    logger.info("Complete at time: " + DateTime.now())
  }
}

//Prohibition
case class TrafficSignProhibitionGenerator(roadLinkServiceImpl: RoadLinkService) extends TrafficSignLinearGenerator  {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  override val assetType : Int = Prohibition.typeId

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  val signAllowException = Seq(2, 3, 23)

  override def createValue(trafficSigns: Seq[PersistedTrafficSign]): Option[Prohibitions] = {
    logger.debug("createValue")
    val value = trafficSigns.flatMap { trafficSign =>
      val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
      val additionalPanel = trafficSignService.getAllProperties(trafficSign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
      val types = ProhibitionClass.fromTrafficSign(TrafficSignType.applyOTHValue(signType))
      val additionalPanels = additionalPanel.sortBy(_.formPosition)

      val validityPeriods: Set[ValidityPeriod] =
        additionalPanels.flatMap { additionalPanel =>
          val trafficSignType = TrafficSignType.applyOTHValue(additionalPanel.panelType)
          createValidPeriod(trafficSignType, additionalPanel)
        }.toSet

        types.map{ typeId =>
          val exceptions = if(signAllowException.contains(typeId.value)) ProhibitionExceptionClass.fromTrafficSign(additionalPanel.map(panel => TrafficSignType.applyOTHValue(panel.panelType)))
          else Set.empty[Int]

          ProhibitionValue(typeId.value, validityPeriods, exceptions)
        }
    }
    if(value.nonEmpty) Some(Prohibitions(value)) else None
  }

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset] = {
    logger.debug("fetchTrafficSignRelatedAssets")
    if (withTransaction) {
      withDynTransaction {
        val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
        postGisLinearAssetDao.fetchProhibitionsByIds(assetType, assetIds.toSet)
      }
    } else {
      val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
      postGisLinearAssetDao.fetchProhibitionsByIds(assetType, assetIds.toSet)
    }
  }

  override def getExistingSegments(roadLinks : Seq[RoadLink]): Seq[PersistedLinearAsset] = {
    logger.debug("getExistingSegments")
    prohibitionService.getPersistedAssetsByLinkIds(assetType, roadLinks.map(_.linkId), false)
  }

  override def signBelongTo(trafficSign: PersistedTrafficSign): Boolean = {
    logger.debug("signBelongTo")
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    TrafficSignManager.belongsToProhibition(signType)
  }

  override def updateLinearAsset(oldAssetId: Long, newValue: Value, username: String): Seq[Long] = {
    logger.debug("updateLinearAsset")
    prohibitionService.updateWithoutTransaction(Seq(oldAssetId), newValue, username)
  }

  override def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long = {
    logger.debug("createLinearAsset")
    prohibitionService.createWithoutTransaction(assetType, newSegment.roadLink.linkId, newSegment.value,
      newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
      LinearAssetUtils.createTimeStamp(), Some(newSegment.roadLink))
  }

  override def assetToUpdate(assets: Seq[PersistedLinearAsset], trafficSign: PersistedTrafficSign, createdValue: Value, username: String) : Unit = {
    logger.debug("assetToUpdate")
    val groupedAssetsToUpdate = assets.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[Prohibitions].prohibitions.diff(createdValue.asInstanceOf[Prohibitions].prohibitions))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.foreach { value =>
      prohibitionService.updateWithoutTransaction(value.map(_._1), Prohibitions(value.flatMap(_._2)), username)
      postGisLinearAssetDao.expireConnectedByPointAsset(trafficSign.id)
    }
  }

  override def mappingValue(segment: Seq[TrafficSignToLinear]): Prohibitions = {
    Prohibitions(segment.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions).distinct)
  }

  override def compareValue(value1: Value, value2: Value) : Boolean = {
    value1.asInstanceOf[Prohibitions].equals(value2.asInstanceOf[Prohibitions])
  }

  override def withdraw(value1: Value, value2: Value): Value = {
     Prohibitions(value1.asInstanceOf[Prohibitions].prohibitions.diff(value2.asInstanceOf[Prohibitions].prohibitions))
  }

  override def mergeValue(values: Value): Value = {
    Prohibitions(
      values.asInstanceOf[Prohibitions].prohibitions.foldLeft(Seq.empty[ProhibitionValue] ) { case (res, value) =>
      res.find(_.typeId == value.typeId) match {
        case Some(result) =>
          res.filterNot(_.typeId == value.typeId) :+ result.copy(validityPeriods = value.validityPeriods ++ result.validityPeriods, exceptions = value.exceptions ++ result.exceptions )
        case None => res :+ value
      }
    })
  }
}

class TrafficSignHazmatTransportProhibitionGenerator(roadLinkServiceImpl: RoadLinkService) extends TrafficSignProhibitionGenerator(roadLinkServiceImpl: RoadLinkService)  {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override val assetType : Int = HazmatTransportProhibition.typeId

  lazy val hazmatTransportProhibitionService: HazmatTransportProhibitionService = {
    new HazmatTransportProhibitionService(roadLinkService, eventbus)
  }

  override def createValue(trafficSigns: Seq[PersistedTrafficSign]): Option[Prohibitions] = {
    logger.debug("createValue")
    val values = trafficSigns.flatMap{ trafficSign =>
    val additionalPanels = trafficSignService.getAllProperties(trafficSign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
    val types = additionalPanels.flatMap{ additionalPanel =>
      HazmatTransportProhibitionClass.fromTrafficSign(TrafficSignType.applyOTHValue(additionalPanel.panelType))
    }

    val validityPeriods: Set[ValidityPeriod] =
      additionalPanels.flatMap { additionalPanel =>
        val trafficSignType = TrafficSignType.applyOTHValue(additionalPanel.panelType)
        createValidPeriod(trafficSignType, additionalPanel)
      }.toSet

      types.map(typeId => ProhibitionValue(typeId.value, validityPeriods, Set()))
    }
    if(values.nonEmpty) Some(Prohibitions(values)) else None
  }

  override def signBelongTo(trafficSign: PersistedTrafficSign): Boolean = {
    logger.debug("signBelongTo")
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    TrafficSignManager.belongsToHazmat(signType)
  }

  override def updateLinearAsset(oldAssetId: Long, newValue: Value, username: String): Seq[Long] = {
    logger.debug("updateLinearAsset")
    hazmatTransportProhibitionService.updateWithoutTransaction(Seq(oldAssetId), newValue, username)
  }

  override def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long = {
    logger.debug("createLinearAsset")
    hazmatTransportProhibitionService.createWithoutTransaction(assetType, newSegment.roadLink.linkId, newSegment.value,
      newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
      LinearAssetUtils.createTimeStamp(), Some(newSegment.roadLink))
  }

  override def getExistingSegments(roadLinks : Seq[RoadLink]): Seq[PersistedLinearAsset] = {
    logger.debug("getExistingSegments")
    hazmatTransportProhibitionService.getPersistedAssetsByLinkIds(assetType, roadLinks.map(_.linkId), false)
  }
}


trait TrafficSignDynamicAssetGenerator extends TrafficSignLinearGenerator  {

  override def mappingValue(segment: Seq[TrafficSignToLinear]): DynamicValue = {
    DynamicValue(DynamicAssetValue(segment.flatMap(_.value.asInstanceOf[DynamicValue].value.properties).distinct))
  }

  override def compareValue(value1: Value, value2: Value) : Boolean = {
    value1.asInstanceOf[DynamicValue].equals(value2.asInstanceOf[DynamicValue])
  }

  override def withdraw(value1: Value, value2: Value): Value = {
    DynamicValue(DynamicAssetValue(value1.asInstanceOf[DynamicValue].value.properties.diff(value2.asInstanceOf[DynamicValue].value.properties)))
  }

  override def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset] = {
    logger.debug("fetchTrafficSignRelatedAssets")
    if (withTransaction) {
      withDynTransaction {
        val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
        dynamicLinearAssetService.getPersistedAssetsByIds(assetType, assetIds.toSet, false)
      }
    } else {
      val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
      dynamicLinearAssetService.getPersistedAssetsByIds(assetType, assetIds.toSet, false)
    }
  }

  override def filterTrafficSigns(trafficSigns: Seq[PersistedTrafficSign], actualRoadLink: RoadLink): Seq[PersistedTrafficSign] = {
    trafficSigns.filter(_.linkId == actualRoadLink.linkId).filterNot(sign =>
      trafficSignService.getAllProperties(sign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel]).exists(_.panelType == RegulationEndsToTheSign.OTHvalue))
  }

  override def mergeValue(values: Value): Value = {
    values
  }

  def getStopCondition(actualRoadLink: RoadLink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], direction: Int, result : Seq[TrafficSignToLinear]): Option[Double]

  override def createSegmentPieces(actualRoadLink: RoadLink, allRoadLinks: Seq[RoadLink], sign: PersistedTrafficSign, signs: Seq[PersistedTrafficSign], pointOfInterest: (Option[Point], Option[Point], Option[Int]), result: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    logger.debug("createSegmentPieces")
    createValue(Seq(sign)) match {
      case Some(value) =>
        val stopCondition = getStopCondition(actualRoadLink, sign, signs, pointOfInterest._3.get, result)
        val generatedSegmentPieces = generateSegmentPiece(actualRoadLink, sign, value, stopCondition, pointOfInterest._3.get)

        (if (stopCondition.isEmpty) {
          val adjRoadLinks = getAdjacents(pointOfInterest, allRoadLinks.filterNot(_.linkId == actualRoadLink.linkId))
          if (adjRoadLinks.nonEmpty) {
            adjRoadLinks.flatMap { case (newRoadLink, (nextFirst, nextLast, nextDirection)) =>
              createSegmentPieces(newRoadLink, allRoadLinks.filterNot(_.linkId == newRoadLink.linkId), sign, signs, (nextFirst, nextLast, nextDirection), generatedSegmentPieces +: result)
            }
          } else
            generatedSegmentPieces +: result
        } else
          generatedSegmentPieces +: result).toSet
      case _ => Set()
    }
  }

  def generateSegmentPiece(currentRoadLink: RoadLink, sign: PersistedTrafficSign, value: Value, endDistance: Option[Double], direction: Int): TrafficSignToLinear = {
    logger.debug("generateSegmentPiece")
    endDistance match {
      case Some(mValue) =>
        if (currentRoadLink.linkId == sign.linkId) {
          val orderedMValue = Seq(sign.mValue, mValue).sorted

          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), orderedMValue.head, orderedMValue.last, Set(sign.id))
        } else {
          val (starMeasure, endMeasure) = if (SideCode.apply(direction) == TowardsDigitizing)
            (0.toDouble, mValue)
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (length - mValue, "%.3f".formatLocal(java.util.Locale.US, length).toDouble)
          }
          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(direction), starMeasure, endMeasure, Set(sign.id))
        }
      case _ =>
        if (currentRoadLink.linkId == sign.linkId) {
          val (starMeasure, endMeasure) = if (SideCode.apply(direction) == AgainstDigitizing)
            (0L.toDouble, sign.mValue)
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (sign.mValue, "%.3f".formatLocal(java.util.Locale.US, length).toDouble)
          }

          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(direction), starMeasure, endMeasure, Set(sign.id))
        }
        else {

          val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(direction), 0, "%.3f".formatLocal(java.util.Locale.US, length).toDouble, Set(sign.id))
        }
    }
  }

}

/***************************************************************
                ParkingProhibitionGenerator
***************************************************************/
class TrafficSignParkingProhibitionGenerator(roadLinkServiceImpl: RoadLinkService) extends TrafficSignDynamicAssetGenerator {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  override val assetType : Int = ParkingProhibition.typeId

  lazy val parkingProhibitionService: ParkingProhibitionService = {
    new ParkingProhibitionService(roadLinkService, eventbus)
  }

  override def createValue(trafficSigns: Seq[PersistedTrafficSign]): Option[DynamicValue] = {
    logger.debug("createValue")
    val value = trafficSigns.flatMap { trafficSign =>
      val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
      val additionalPanel = trafficSignService.getAllProperties(trafficSign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
      val types = ParkingProhibitionClass.fromTrafficSign(TrafficSignType.applyOTHValue(signType))
      val additionalPanels = additionalPanel.sortBy(_.formPosition)

      val validityPeriods: Set[ValidityPeriod] =
        additionalPanels.flatMap { additionalPanel =>
          val trafficSignType = TrafficSignType.applyOTHValue(additionalPanel.panelType)
          createValidPeriod(trafficSignType, additionalPanel)
        }.toSet

      val additionalInfo = if (validityPeriods.nonEmpty)
        validityPeriods.map{validityPeriod =>DynamicPropertyValue(
          Map(
            "days" -> ValidityPeriodDayOfWeek.toTimeDomainValue(validityPeriod.days),
            "startHour" -> validityPeriod.startHour,
            "endHour" -> validityPeriod.endHour,
            "startMinute" -> validityPeriod.startMinute,
            "endMinute" -> validityPeriod.endMinute
          ))}
      else
        Seq()

      Seq(DynamicProperty("parking_validity_period", "time_period", false, additionalInfo.toSeq)) ++ types.map(typeId => DynamicProperty("parking_prohibition", "single_choice", true, Seq(DynamicPropertyValue(typeId.value))))

    }
    if(value.nonEmpty) Some(DynamicValue(DynamicAssetValue(value.toArray.toSeq))) else None
  }

  override def signBelongTo(trafficSign: PersistedTrafficSign): Boolean = {
    logger.debug("signBelongTo")
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    TrafficSignManager.belongsToParking(signType)
  }

  override def updateLinearAsset(oldAssetId: Long, newValue: Value, username: String): Seq[Long] = {
    logger.debug("updateLinearAsset")
    parkingProhibitionService.updateWithoutTransaction(Seq(oldAssetId), newValue, username)
  }

  override def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long = {
    logger.debug("createLinearAsset")
    parkingProhibitionService.createWithoutTransaction(assetType, newSegment.roadLink.linkId, newSegment.value,
      newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
      LinearAssetUtils.createTimeStamp(), Some(newSegment.roadLink))
  }

  override def assetToUpdate(assets: Seq[PersistedLinearAsset], trafficSign: PersistedTrafficSign, createdValue: Value, username: String) : Unit = {
    logger.debug("assetToUpdate")
    val groupedAssetsToUpdate = assets.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[DynamicValue].value.properties.diff(createdValue.asInstanceOf[DynamicValue].value.properties))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.foreach { value =>
      parkingProhibitionService.updateWithoutTransaction(value.map(_._1), DynamicValue(DynamicAssetValue(value.flatMap(_._2))), username)
      postGisLinearAssetDao.expireConnectedByPointAsset(trafficSign.id)
    }
  }

  override def mappingValue(segment: Seq[TrafficSignToLinear]): DynamicValue = {
    DynamicValue(DynamicAssetValue(segment.flatMap(_.value.asInstanceOf[DynamicValue].value.properties).distinct))
  }

  override def compareValue(value1: Value, value2: Value) : Boolean = {
    value1.asInstanceOf[DynamicValue].equals(value2.asInstanceOf[DynamicValue])
  }

  override def withdraw(value1: Value, value2: Value): Value = {
    DynamicValue(DynamicAssetValue(value1.asInstanceOf[DynamicValue].value.properties.diff(value2.asInstanceOf[DynamicValue].value.properties)))
  }

  override def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset] = {
    logger.debug("fetchTrafficSignRelatedAssets")
    if (withTransaction) {
      withDynTransaction {
        val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(assetIds.toSet)
      }
    } else {
      val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
      dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(assetIds.toSet)
    }
  }

  override def getExistingSegments(roadLinks : Seq[RoadLink]): Seq[PersistedLinearAsset] = {
    logger.debug("getExistingSegments")
    parkingProhibitionService.getPersistedAssetsByLinkIds(assetType, roadLinks.map(_.linkId), false)
  }

  override def getStopCondition(actualRoadLink: RoadLink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], direction: Int, result : Seq[TrafficSignToLinear]): Option[Double] = {
    //link length exceed the dimensions
    //if the additional panel length doesn't exist check the adjacent number
    //in same direction exist a different type
    //in same direction exist a same type with a arrow down
    val mainSignRoadLink = (result.map(_.roadLink) :+ actualRoadLink).find(_.linkId == mainSign.linkId).get
    val (start, end) = GeometryUtils.geometryEndpoints(mainSignRoadLink.geometry)
    val x = getPointOfInterest(start, end, SideCode.apply(mainSign.validityDirection))
    val distance = if(mainSign.linkId == actualRoadLink.linkId) 0 else (if (x._2.nonEmpty) GeometryUtils.geometryLength(mainSignRoadLink.geometry) - mainSign.mValue else mainSign.mValue) +
      result.filterNot(_.roadLink.linkId == mainSign.linkId).map(res => Math.abs(res.endMeasure - res.startMeasure)).sum

    val length = GeometryUtils.geometryLength(actualRoadLink.geometry)
    val distanceLeft = if(mainSign.linkId == actualRoadLink.linkId) if(x._2.nonEmpty) GeometryUtils.geometryLength(mainSignRoadLink.geometry) - mainSign.mValue else mainSign.mValue else length

    val mainType = trafficSignService.getProperty(mainSign, trafficSignService.typePublicId).get.propertyValue
    val exceedDistance = trafficSignService.getAllProperties(mainSign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel]).find(_.panelType == DistanceWhichSignApplies.OTHvalue).
      filter(distancePanel =>Try(distancePanel.panelValue.toDouble < distance + distanceLeft).getOrElse(false)).map( panel =>
      if(actualRoadLink.linkId == mainSign.linkId)
        if(x._1.nonEmpty) mainSign.mValue - panel.panelValue.toDouble else mainSign.mValue + panel.panelValue.toDouble
      else
        panel.panelValue.toDouble - distance)

    val existingSigns = allSignsRelated.filterNot(_.id == mainSign.id).filter(_.linkId == actualRoadLink.linkId).filter { sign =>
      trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue != mainType && sign.validityDirection == direction && (if(direction == TowardsDigitizing.value) mainSign.mValue <= sign.mValue else mainSign.mValue >= sign.mValue)||
        (trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue == mainType &&
          trafficSignService.getAllProperties(sign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel]).exists(_.panelType == RegulationEndsToTheSign.OTHvalue) && sign.validityDirection == direction)
    }.map(_.mValue)

    val position = exceedDistance ++ existingSigns

    if(position.nonEmpty)
      Some(position.min)
    else {
      val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
      val pointOfInterest = getPointOfInterest(first, last, SideCode.apply(direction))
      val getAdjacents = roadLinkService.getAdjacent(actualRoadLink.linkId, Seq(pointOfInterest._1.getOrElse(pointOfInterest._2.get)), false)
      if (getAdjacents.size > 1)
        if(pointOfInterest._1.nonEmpty) Some(0) else Some(length)
      else
        None
    }
  }

  override def filterTrafficSigns(trafficSigns: Seq[PersistedTrafficSign], actualRoadLink: RoadLink): Seq[PersistedTrafficSign] = {
    trafficSigns.filter(_.linkId == actualRoadLink.linkId).filterNot(sign =>
      trafficSignService.getAllProperties(sign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel]).exists(_.panelType == RegulationEndsToTheSign.OTHvalue))
  }

  override def mergeValue(values: Value): Value = {
    values
  }

}

/***************************************************************
                TrafficSignRoadWorkGenerator
  ***************************************************************/
class TrafficSignRoadWorkGenerator(roadLinkServiceImpl: RoadLinkService) extends TrafficSignDynamicAssetGenerator {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  override val assetType: Int = RoadWorksAsset.typeId
  private val MAX_DISTANCE: Int = 1000

  lazy val roadWorkService: RoadWorkService = {
    new RoadWorkService(roadLinkService, eventbus)
  }

  override def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset] = {
    logger.debug("fetchTrafficSignRelatedAssets")
    if (withTransaction) {
      withDynTransaction {
        val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
        roadWorkService.getPersistedAssetsByIds(assetType, assetIds.toSet, false)
      }
    } else {
      val assetIds = postGisLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
      roadWorkService.getPersistedAssetsByIds(assetType, assetIds.toSet, false)
    }
  }

  override def signBelongTo(trafficSign: PersistedTrafficSign): Boolean = {
    logger.debug("signBelongTo")
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    TrafficSignManager.belongsToRoadwork(signType)
  }

  override def createValue(trafficSigns: Seq[PersistedTrafficSign]): Option[DynamicValue] = {
    logger.debug("createValue")
    val value = trafficSigns.flatMap { trafficSign =>
      val startDate = trafficSignService.getProperty(trafficSign, trafficSignService.trafficSignStartDatePublicId).get.propertyValue
      val endDate = trafficSignService.getProperty(trafficSign, trafficSignService.trafficSignEndDatePublicId).get.propertyValue

      val additionalInfo = if (endDate.nonEmpty)
        Seq( DynamicPropertyValue(
               Map("startDate" -> startDate,
                   "endDate" -> endDate))
        )
      else
        Seq()

      Seq(DynamicProperty("arvioitu_kesto", "date_period", true, additionalInfo))

    }
    if(value.nonEmpty) Some(DynamicValue(DynamicAssetValue(value.toArray.toSeq))) else None
  }

  override def getExistingSegments(roadLinks : Seq[RoadLink]): Seq[PersistedLinearAsset] = {
    logger.debug("getExistingSegments")
    roadWorkService.getPersistedAssetsByLinkIds(assetType, roadLinks.map(_.linkId), false)
  }

  override def withdraw(value1: Value, value2: Value): Value = {
    val newProperties = value1.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "tyon_tunnus") ++ value2.asInstanceOf[DynamicValue].value.properties
    DynamicValue(DynamicAssetValue(newProperties.toSeq))
  }

  override def updateLinearAsset(oldAssetId: Long, newValue: Value, username: String): Seq[Long] = {
    logger.debug("updateLinearAsset")
    roadWorkService.updateWithoutTransaction(Seq(oldAssetId), newValue, username)
  }

  override def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign): Unit = {
    logger.debug("deleteOrUpdateAssetBasedOnSign")

    val username = "automatic_trafficSign_deleted"
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val createdValue = createValue(Seq(trafficSign))

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition { asset =>
      if (createdValue.isEmpty) true
      else if (trafficSign.expired) true
      else compareValue(asset.value.get, createdValue.get)
    }

    toDelete.foreach { asset =>
      linearAssetService.expireAsset(assetType, asset.id, username, true, false)
      postGisLinearAssetDao.expireConnectedByLinearAsset(asset.id)
    }

    assetToUpdate(toUpdate, trafficSign, createdValue.get, userUpdate)
  }

  override def assetToUpdate(assets: Seq[PersistedLinearAsset], trafficSign: PersistedTrafficSign, createdValue: Value, username: String) : Unit = {
    logger.debug("assetToUpdate")
    val groupedAssetsToUpdate = assets.map { asset =>
      val newProperties = asset.value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "tyon_tunnus") ++ createdValue.asInstanceOf[DynamicValue].value.properties

      (asset.id, newProperties)
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.foreach { value =>
      roadWorkService.updateWithoutTransaction(value.map(_._1), DynamicValue(DynamicAssetValue(value.flatMap(_._2))), username)
      postGisLinearAssetDao.expireConnectedByPointAsset(trafficSign.id)
    }
  }

  override def createLinearAsset(newSegment: TrafficSignToLinear, username: String): Long = {
    logger.debug("createLinearAsset")
      roadWorkService.createWithoutTransaction(assetType, newSegment.roadLink.linkId, newSegment.value,
        newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
        LinearAssetUtils.createTimeStamp(), Some(newSegment.roadLink))
  }


  override def getStopCondition(actualRoadLink: RoadLink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], direction: Int, result : Seq[TrafficSignToLinear]): Option[Double] = {
    // STOP CONDITIONS:
    //  a) Find another roadwork sign
    //  b) No signal found in a 1000m range

    val mainSignRoadLink = (result.map(_.roadLink) :+ actualRoadLink).find(_.linkId == mainSign.linkId).get
    val (start, end) = GeometryUtils.geometryEndpoints(mainSignRoadLink.geometry)
    val x = getPointOfInterest(start, end, SideCode.apply(mainSign.validityDirection))

    // Calculate the length of the effect the sign in the roadlink
    val mainSignLengthEffect = if (x._2.nonEmpty)
                                GeometryUtils.geometryLength(mainSignRoadLink.geometry) - mainSign.mValue
                              else
                                mainSign.mValue

    // calculate the distance of effect the main sign when
    // we are not in the same linkid as the main sign linkid
    val distance = if(mainSign.linkId == actualRoadLink.linkId) 0
                    else mainSignLengthEffect

    // sum all lengths of the roadliks except the roadlink with the main signal
    val sumPrevRoadlinks = result.filterNot(_.roadLink.linkId == mainSign.linkId)
                                .map(res => Math.abs(res.endMeasure - res.startMeasure))
                                .sum

    // Get the total length of the current roadlink
    val length = GeometryUtils.geometryLength(actualRoadLink.geometry)

    // Get the oposite sign of roadwork in the current linkid if exists
    val existingSigns = allSignsRelated.filterNot( _.id == mainSign.id )
                                    .filter( _.linkId == actualRoadLink.linkId )
                                    .filter( sign =>  sign.validityDirection == direction || compareValue(createValue(Seq(mainSign)).get, createValue(Seq(sign)).get) )

    // Calculate the length of the effect the oposite sign in the current roadlink
    val existingSignLengthEffect =  if (existingSigns.nonEmpty) {
                                      val auxSign = existingSigns.head

                                      if (auxSign.linkId == mainSign.linkId)  //signal in same linkid
                                        auxSign.mValue
                                      else { // validate the direction to do some math
                                         if (SideCode.apply(direction) == TowardsDigitizing)
                                           auxSign.mValue
                                        else
                                         Math.abs(length - auxSign.mValue)
                                      }
                                    } else 0 // In case we don't have sign


    // calculate the correct distance of effect for the current linkid
    val distanceLeft = if (existingSigns.nonEmpty)
                        existingSignLengthEffect
                      else if(mainSign.linkId == actualRoadLink.linkId)
                        mainSignLengthEffect
                      else
                        length

    // Sum all distances calculated above
    val totalDistance = distance + distanceLeft + sumPrevRoadlinks

    // if the sum of distances are higher than MAX_DISTANCE then cut to the MAX_DISTANCE allowed
    if(totalDistance > MAX_DISTANCE)
      Some(length - Math.abs(totalDistance - MAX_DISTANCE) )
    else if (existingSigns.nonEmpty) { // if we find a sign
        val auxSign = existingSigns.head
        val sameDirection = auxSign.validityDirection == mainSign.validityDirection

        if (sameDirection && auxSign.linkId == mainSign.linkId){
          if ( mainSign.validityDirection == direction  ) {
            if (SideCode.apply(direction) == TowardsDigitizing){
              if (mainSign.mValue > auxSign.mValue)
                None
              else
                Some(distanceLeft)
            } else {
              if (mainSign.mValue < auxSign.mValue)
                None
              else
                Some(distanceLeft)
            }
          }
          else{
            if (SideCode.apply(direction) == TowardsDigitizing){
              if (mainSign.mValue > auxSign.mValue)
                Some(distanceLeft)
              else
                None
            } else {
              if (mainSign.mValue < auxSign.mValue)
                Some(distanceLeft)
              else
                None
            }
          }
        }
        else
          Some(distanceLeft)
    } else
      None

  }

  override def segmentsManager(roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign], existingSegments: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    logger.debug(s"segmentsManager : roadLinkSize = ${roadLinks.size}")

    val relevantLink = relevantLinkOnChain(roadLinks, trafficSigns)
    val newSegments = relevantLink.flatMap { case (roadLink, startPointOfInterest, lastPointOfInterest) =>
      baseProcess(trafficSigns, roadLinks, roadLink, (startPointOfInterest, lastPointOfInterest, None), Seq())
    }.distinct

  val mergedSegments = newSegments.map { x =>
                          val newProps = if (existingSegments.nonEmpty) withdraw( existingSegments.head.value, x.value )
                                          else x.value

                          TrafficSignToLinear(x.roadLink, newProps, x.sideCode, x.startMeasure, x.endMeasure, x.signId, x.oldAssetId )
                      }

    val groupedAssets = mergedSegments.groupBy(_.roadLink)
    val assets = fillTopology(roadLinks, groupedAssets)

    convertRoadSegments(assets, findStartEndRoadLinkOnChain(roadLinks)).toSet
  }

  override def convertRoadSegments(segments: Seq[TrafficSignToLinear], endRoadLinksInfo: Seq[(RoadLink, Option[Point], Option[Point])]): Seq[TrafficSignToLinear] = {
    segments
  }

}
