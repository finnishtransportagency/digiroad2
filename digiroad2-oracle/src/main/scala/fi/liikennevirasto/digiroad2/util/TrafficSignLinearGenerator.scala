package fi.liikennevirasto.digiroad2.util

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{Value, _}
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime

case class TrafficSignToLinear(roadLink: RoadLink, value: Value, sideCode: SideCode, startMeasure: Double, endMeasure: Double, signId: Set[Long], oldAssetId: Option[Long] = None)

trait TrafficSignLinearGenerator {
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  type AssetValue <: Value
  val assetType : Int

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/bonecp.properties"))
    props
  }

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val userProvider: UserProvider = {
    Class.forName(dr2properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val manoeuvreService: ManoeuvreService = {
    new ManoeuvreService(roadLinkService, new DummyEventBus)
  }

  lazy val hazmatTransportProhibitionService: HazmatTransportProhibitionService = {
    new HazmatTransportProhibitionService(roadLinkService, eventbus)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, userProvider, eventbus)
  }

  lazy val oracleLinearAssetDao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)

  def createValue(trafficSign: PersistedTrafficSign): AssetValue

  def getExistingSegments(roadLinks : Seq[RoadLink]): Seq[PersistedLinearAsset]

  def signBelongTo(trafficSign: PersistedTrafficSign) : Boolean

  def updateLinearAsset(newSegment: TrafficSignToLinear, username: String) : Seq[Long]

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset]

  def assetToUpdate(assets: Seq[PersistedLinearAsset], trafficSign: PersistedTrafficSign,  createdValue: AssetValue, username: String) : Unit

  def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long

  def mappingValue(segment: Seq[TrafficSignToLinear]): AssetValue

  def compareValue(value1: Value, value2: Value) : Boolean

  def getPointOfInterest(first: Point, last: Point, sideCode: SideCode): (Option[Point], Option[Point], Option[Int]) = {
    sideCode match {
      case SideCode.TowardsDigitizing => (None, Some(last), Some(sideCode.value))
      case SideCode.AgainstDigitizing => (Some(first), None, Some(sideCode.value))
      case _ => (Some(first), Some(last), Some(sideCode.value))
    }
  }

  def segmentsManager(roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign], existingSegments: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    val startEndRoadLinks = findStartEndRoadLinkOnChain(roadLinks)

    val newSegments = startEndRoadLinks.flatMap { case (roadLink, startPointOfInterest, lastPointOfInterest) =>
      baseProcess(trafficSigns, roadLinks, roadLink, (startPointOfInterest, lastPointOfInterest, None), Seq())
    }.distinct

    val groupedAssets = (newSegments ++ existingSegments).groupBy(_.roadLink)
    val assets = fillTopology(roadLinks, groupedAssets)

    convertEndRoadSegments(assets, startEndRoadLinks).toSet
  }

  def fillTopology(topology: Seq[RoadLink], linearAssets: Map[RoadLink, Seq[TrafficSignToLinear]]): Seq[TrafficSignToLinear] = {
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

  def findStartEndRoadLinkOnChain(RoadLinks: Seq[RoadLink]): Seq[(RoadLink, Option[Point], Option[Point])] = {
    val borderRoadLinks = RoadLinks.filterNot { r =>
      val (first, last) = GeometryUtils.geometryEndpoints(r.geometry)
      val RoadLinksFiltered = RoadLinks.filterNot(_.linkId == r.linkId)

      RoadLinksFiltered.exists { r3 =>
        val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
        GeometryUtils.areAdjacent(first, first2) || GeometryUtils.areAdjacent(first, last2)
      } &&
        RoadLinksFiltered.exists { r3 =>
          val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
          GeometryUtils.areAdjacent(last, first2) || GeometryUtils.areAdjacent(last, last2)
        }
    }

    borderRoadLinks.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val isStart = RoadLinks.diff(borderRoadLinks).exists { r3 =>
        val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
        !(GeometryUtils.areAdjacent(first, first2) || GeometryUtils.areAdjacent(first, last2))
      }

      if (isStart) {
        (roadLink, None, Some(last))
      } else
        (roadLink, Some(first), None)
    }
  }

  def segmentsConverter(existingAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink]): Seq[TrafficSignToLinear] = {
    val connectedTrafficSignIds =
      if (existingAssets.nonEmpty)
        oracleLinearAssetDao.getConnectedAssetFromLinearAsset(existingAssets.map(_.id))
      else
        Seq()

    existingAssets.filter(_.value.isDefined).map { asset =>
      val trafficSignIds = connectedTrafficSignIds.filter(_._1 == asset.id).map(_._2).toSet
      TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.apply(asset.sideCode), asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id))
    }
  }

  def baseProcess(trafficSigns: Seq[PersistedTrafficSign], roadLinks: Seq[RoadLink], actualRoadLink: RoadLink, previousInfo: (Option[Point], Option[Point], Option[Int]), result: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    val filteredRoadLinks = roadLinks.filterNot(_.linkId == actualRoadLink.linkId)
    val signsOnRoadLink = trafficSigns.filter(_.linkId == actualRoadLink.linkId)
    (if (signsOnRoadLink.nonEmpty) {
      signsOnRoadLink.flatMap { sign =>
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
    val pairSign = getPairSign(actualRoadLink, sign, signs.filter(_.linkId == actualRoadLink.linkId), pointOfInterest._3.get)
    val generatedSegmentPieces = generateSegmentPieces(actualRoadLink, sign, pairSign, pointOfInterest._3.get)

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
  }

  def generateSegmentPieces(currentRoadLink: RoadLink, sign: PersistedTrafficSign, pairedSign: Option[PersistedTrafficSign], direction: Int): TrafficSignToLinear = {
    val value = createValue(sign)
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
    val mainSignType = trafficSignService.getProperty(mainSign, trafficSignService.typePublicId).get.propertyValue.toInt

    allSignsRelated.filterNot(_.id == mainSign.id).filter(_.linkId == actualRoadLink.linkId).find { sign =>
      val relatedSignType = trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt
      //sign in opposite direction
      relatedSignType == mainSignType && sign.validityDirection != direction
    }
  }

  def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign): Unit = {
    val username = "automatic_trafficSign_deleted"
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val createdValue = createValue(trafficSign)

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition { asset =>
      asset.value.get.equals(createdValue)
    }

    toDelete.foreach { asset =>
      linearAssetService.expireAsset(assetType, asset.id, username, true, false)
      oracleLinearAssetDao.expireConnectedByLinearAsset(asset.id)
    }

    assetToUpdate(toUpdate, trafficSign, createdValue, username)
  }

  def getAdjacents(previousInfo: (Option[Point], Option[Point], Option[Int]), roadLinks: Seq[RoadLink]): Seq[(RoadLink, (Option[Point], Option[Point], Option[Int]))] = {
    val (prevFirst, prevLast, direction) = previousInfo
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, prevFirst.getOrElse(prevLast.get))
    }.map { roadLink =>
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
    println(s"Applying creation of new data at RoadLink: ${newSegment.roadLink.linkId}")

    val newAssetId = createLinearAsset(newSegment, username)

    if (newSegment.signId.nonEmpty)
      println(s"Values to insert: $newAssetId / ${newSegment.signId.mkString(",")}")

    newSegment.signId.foreach { signId =>
      createAssetRelation(newAssetId, signId)
    }
  }

  def createAssetRelation(linearAssetId: Long, trafficSignId: Long) : Unit = {
    try {
      oracleLinearAssetDao.insertConnectedAsset(linearAssetId, trafficSignId)
    } catch {
      case ex: SQLIntegrityConstraintViolationException => print("") //the key already exist with a valid date
      case e: Exception => print("SQL Exception ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }

  def combine(segments: Seq[TrafficSignToLinear]/*, endRoadLinksInfo: Seq[(RoadLink, Option[Point], Option[Point])]*/): Seq[TrafficSignToLinear] = {
    def squash(startM: Double, endM: Double, segments: Seq[TrafficSignToLinear]): Seq[TrafficSignToLinear] = {
      val sl = segments.filter(sl => sl.startMeasure <= startM && sl.endMeasure >= endM)
      val a = sl.filter(sl => sl.sideCode.equals(SideCode.AgainstDigitizing) || sl.sideCode.equals(SideCode.BothDirections))
      val t = sl.filter(sl => sl.sideCode.equals(SideCode.TowardsDigitizing) || sl.sideCode.equals(SideCode.BothDirections))

      (a.headOption, t.headOption) match {
        case (Some(x),Some(y)) => Seq(TrafficSignToLinear(x.roadLink, mappingValue(a), AgainstDigitizing, startM, endM, x.signId, x.oldAssetId), TrafficSignToLinear(y.roadLink, mappingValue(t), TowardsDigitizing, startM, endM, y.signId, y.oldAssetId))
        case (Some(x),None) => Seq(TrafficSignToLinear(x.roadLink, mappingValue(a), AgainstDigitizing, startM, endM, x.signId, x.oldAssetId))
        case (None,Some(y)) => Seq(TrafficSignToLinear(y.roadLink, mappingValue(t), TowardsDigitizing, startM, endM, y.signId, y.oldAssetId))
        case _ => Seq()
      }
    }

    def combineEqualValues(segmentPieces: Seq[TrafficSignToLinear]/*, segments : Seq[TrafficSignToLinear]*/): Seq[TrafficSignToLinear] = {
      val seg1 = segmentPieces.head
      val seg2 = segmentPieces.last
      if (seg1.startMeasure.equals(seg2.startMeasure) && seg1.endMeasure.equals(seg2.endMeasure) && compareValue(seg1.value, seg2.value) && seg1.sideCode != seg2.sideCode) {
        val winnerSegment = if(seg1.oldAssetId.nonEmpty) seg1 else seg2
        Seq(winnerSegment.copy(sideCode = BothDirections, signId = seg1.signId ++ seg2.signId))
      } else
        segmentPieces
    }

    val pointsOfInterest = (segments.map(_.startMeasure) ++ segments.map(_.endMeasure)).distinct.sorted
    if (pointsOfInterest.length < 2)
      return segments
    val pieces = pointsOfInterest.zip(pointsOfInterest.tail)
    val segmentPieces = pieces.flatMap(p => squash(p._1, p._2, segments))
    segmentPieces.groupBy(_.startMeasure).flatMap(n => combineEqualValues(n._2/*, segments*/)).toSeq
  }

  def findNextEndAssets(segments: Seq[TrafficSignToLinear], baseSegment: TrafficSignToLinear, result: Seq[TrafficSignToLinear] = Seq(), numberOfAdjacent: Int = 0): Seq[TrafficSignToLinear] = {
    val adjacent = roadLinkService.getAdjacentTemp(baseSegment.roadLink.linkId)

    if (numberOfAdjacent == 1 && adjacent.size > 1) {
      segments.filter(_ == baseSegment).map(_.copy(sideCode = SideCode.BothDirections)) ++ result
    } else if (adjacent.size == 1 || (adjacent.nonEmpty && isEndRoadLink(baseSegment.roadLink, adjacent))) { //is ended
      val newResult = segments.filter(_.roadLink == baseSegment.roadLink).map(_.copy(sideCode = SideCode.BothDirections)) ++ result
      val newBaseSegment = segments.filterNot(_.roadLink == baseSegment.roadLink)
      if (newBaseSegment.isEmpty)
        newResult
      else
        newBaseSegment.flatMap { baseSegment => findNextEndAssets(newBaseSegment, baseSegment, newResult, adjacent.size) }
    } else
      result
  }



  def convertEndRoadSegments(segments: Seq[TrafficSignToLinear], endRoadLinksInfo: Seq[(RoadLink, Option[Point], Option[Point])]): Seq[TrafficSignToLinear] = {
    val segmentsOndEndRoads =  segments.filter { seg =>
      endRoadLinksInfo.exists { case (endRoadLink, firstPoint, lastPoint) =>
        val (first, _) = GeometryUtils.geometryEndpoints(endRoadLink.geometry)
        //if is a lastRoaLink, the point of interest is the first point
        (if (GeometryUtils.areAdjacent(first, firstPoint.getOrElse(lastPoint.get))) {
          Math.abs(seg.startMeasure - 0) < 0.01
        } else {
          Math.abs(seg.endMeasure - GeometryUtils.geometryLength(endRoadLink.geometry)) < 0.01
        }) && seg.roadLink.linkId == endRoadLink.linkId
      }
    }

      val endSegments = segmentsOndEndRoads.flatMap{ baseSegment =>
        findNextEndAssets(segments, baseSegment)
    }.distinct

    segments.filterNot(seg => endSegments.exists(endSeg => seg.startMeasure == endSeg.startMeasure && seg.endMeasure == endSeg.endMeasure && seg.roadLink.linkId == endSeg.roadLink.linkId)) ++ endSegments
  }

  def convertOneSideCode(segments: Seq[TrafficSignToLinear]): Seq[TrafficSignToLinear] = {
//    def compareWithTrafficDirection(segments: Set[TrafficSignToLinear]): (Set[TrafficSignToLinear], Set[TrafficSignToLinear]) = {
      segments.map { seg =>
        if (seg.roadLink.trafficDirection != TrafficDirection.BothDirections)
          seg.copy(sideCode = BothDirections)
        else
          seg
      }
//    }
//    val (assetInOneTrafficDirectionLink, possibleEndRoad) = compareWithTrafficDirection(oneSideSegments)
//    convertEndRoadSegments(possibleEndRoad.toSeq, endRoadLinksInfo) ++ assetInOneTrafficDirectionLink
  }


  def isEndRoadLink(endRoadLink: RoadLink, adjacent: Seq[RoadLink]): Boolean = {
    val (start, end) = GeometryUtils.geometryEndpoints(endRoadLink.geometry)
    !(adjacent.exists { road =>
      val (first, last) = GeometryUtils.geometryEndpoints(road.geometry)
      GeometryUtils.areAdjacent(start, first) || GeometryUtils.areAdjacent(start, last)
    } &&
      adjacent.exists { road =>
        val (first, last) = GeometryUtils.geometryEndpoints(road.geometry)
        GeometryUtils.areAdjacent(end, first) || GeometryUtils.areAdjacent(end, last)
      })
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
      if (signRoadLink.attributes.get("ROADNAME_FI").map(_.toString.trim).nonEmpty) {
        Some("ROADNAME_FI", signRoadLink.attributes("ROADNAME_FI").toString)
      } else if (signRoadLink.attributes.get("ROADNAME_SE").map(_.toString.trim).nonEmpty) {
        Some("ROADNAME_FI", signRoadLink.attributes("ROADNAME_SE").toString)
      } else
        None

    //RoadLink with the same Finnish/Swedish name
    tsRoadNameInfo.map { case (roadNamePublicIds, roadNameSource) =>
      roadLinkService.getRoadLinksAndComplementaryByRoadNameFromVVH(roadNamePublicIds, Set(roadNameSource), false).filter(_.administrativeClass != State)
    }.getOrElse(Seq())
  }

  def applyChangesBySegments(allSegments: Set[TrafficSignToLinear], existingSegments: Seq[TrafficSignToLinear]) {
    val userCreate = "automatic_trafficSign_created"
    val userUpdate = "automatic_trafficSign_updated"
    println(s"Total of segments:  ${allSegments.size} ")

    allSegments.groupBy(_.roadLink.linkId).values.foreach(newSegments =>
      newSegments.foreach { newSegment =>
        val existingSegOnRoadLink = existingSegments.filter(_.roadLink.linkId == newSegment.roadLink.linkId)

        if (existingSegOnRoadLink.nonEmpty) {
          val existingSeg = existingSegOnRoadLink.filter { existingSeg =>
            existingSeg.startMeasure == newSegment.startMeasure &&
              existingSeg.endMeasure == newSegment.endMeasure && newSegment.sideCode == existingSeg.sideCode
          }

          if (existingSeg.exists(_.value.equals(newSegment.value))) {
            newSegment.signId.toSeq.diff(existingSeg.flatMap(_.oldAssetId)).foreach(
              createAssetRelation(existingSeg.flatMap(_.oldAssetId).head, _))
            println("change relation")
          } else {
            //same startMeasure, endMeasure amd SideCode, diff values
            if (existingSeg.nonEmpty) {
              //Update value
              println(s"Applying modifications at asset ID: ${newSegment.oldAssetId.mkString(",")} ")
              updateLinearAsset(newSegment, userUpdate)
              println("update asset")
              newSegment.oldAssetId.foreach (oldId => newSegment.signId.foreach(createAssetRelation(oldId, _)))
            } else {
              //delete old and create new
              println("delete old")
              existingSeg.foreach { asset =>
                expire(asset.oldAssetId.get, userUpdate)
              }
              println("create asset")
              createLinearAssetAccordingSegmentsInfo(newSegment, userUpdate)
            }
          }
        } else {
          //create news
          createLinearAssetAccordingSegmentsInfo(newSegment, userCreate)
        }
      }
    )
  }

  def expire(id: Long, username: String) = {
    linearAssetService.expireAsset(assetType, id, username, true, false)
    oracleLinearAssetDao.expireConnectedByLinearAsset(id)
  }

  def iterativeProcess(roadLinks: Seq[RoadLink], processedRoadLinks: Seq[RoadLink]): Unit = {
    val roadLinkToBeProcessed = roadLinks.diff(processedRoadLinks)

    if (roadLinkToBeProcessed.nonEmpty) {
      val roadLink = roadLinkToBeProcessed.head
      val allRoadLinksWithSameName = withDynTransaction {
        val allRoadLinksWithSameName = getAllRoadLinksWithSameName(roadLink)
        if(allRoadLinksWithSameName.nonEmpty){
          val trafficSigns = trafficSignService.getTrafficSign(allRoadLinksWithSameName.map(_.linkId))
          val filteredTrafficSigns = trafficSigns.filter(signBelongTo)

          val existingAssets = getExistingSegments(allRoadLinksWithSameName)
          val existingSegments = segmentsConverter(existingAssets, allRoadLinksWithSameName)
          println(s"Processing: ${filteredTrafficSigns.size}")

          //create and Modify actions
          println(s"Start creating/modifying ${AssetTypeInfo.apply(assetType).layerName} according the traffic sign")
          val allSegments = segmentsManager(allRoadLinksWithSameName, filteredTrafficSigns, existingSegments)
          applyChangesBySegments(allSegments, existingSegments)

          if (trafficSigns.nonEmpty)
            oracleLinearAssetDao.deleteTrafficSignsToProcess(trafficSigns.map(_.id), assetType)

          allRoadLinksWithSameName
        } else
          Seq()
      }
      iterativeProcess(roadLinkToBeProcessed.filterNot(_.linkId == roadLink.linkId), allRoadLinksWithSameName)
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  def createLinearAssetUsingTrafficSigns(): Unit = {
    println(s"Starting create ${AssetTypeInfo.apply(assetType).layerName} using traffic signs")
    println(DateTime.now())
    println("")

    val roadLinks = withDynTransaction {
      val trafficSignsToProcess = oracleLinearAssetDao.getTrafficSignsToProcess(assetType)

      val trafficSigns = if(trafficSignsToProcess.nonEmpty) trafficSignService.fetchPointAssetsWithExpired(withFilter(s"Where a.id in (${trafficSignsToProcess.mkString(",")}) ")) else Seq()
      val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIdsFromVVH(trafficSigns.map(_.linkId).toSet, false).filter(_.administrativeClass != State)
      val trafficSignsToTransform = trafficSigns.filter(asset => roadLinks.exists(_.linkId == asset.linkId))

      println(s"Total of trafficSign to process: ${trafficSigns.size}")

      val tsToDelete = trafficSigns.filter(_.expired)

      tsToDelete.foreach { ts =>
        // Delete actions
        println(s"Start deleting ${AssetTypeInfo.apply(assetType).layerName} according the traffic sign with ID: ${ts.id}")
        deleteOrUpdateAssetBasedOnSign(ts)
      }

      //Remove the table sign added on State Road
      val trafficSignsToDelete = trafficSigns.diff(trafficSignsToTransform) ++ tsToDelete
      if (trafficSignsToDelete.nonEmpty)
        oracleLinearAssetDao.deleteTrafficSignsToProcess(trafficSignsToDelete.map(_.id), assetType)

      roadLinks
    }
    println("Start processing traffic signs")
    iterativeProcess(roadLinks, Seq())

    println("")
    println("Complete at time: " + DateTime.now())
  }
}

//Prohibition
class TrafficSignProhibitionGenerator(roadLinkServiceImpl: RoadLinkService) extends TrafficSignLinearGenerator  {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

  override type AssetValue = Prohibitions
  override val assetType : Int = Prohibition.typeId

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  override def createValue(trafficSign: PersistedTrafficSign): Prohibitions = {
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    val additionalPanel = trafficSignService.getAllProperties(trafficSign, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
    val types = ProhibitionClass.fromTrafficSign(TrafficSignType.applyOTHValue(signType))
    val additionalPanels = additionalPanel.sortBy(_.formPosition)

    val validityPeriods: Set[ValidityPeriod] =
      additionalPanels.flatMap { additionalPanel =>
        val trafficSignType = TrafficSignType.applyOTHValue(additionalPanel.panelType)
        createValidPeriod(trafficSignType, additionalPanel)
      }.toSet

    Prohibitions(types.map(typeId => ProhibitionValue(typeId.value, validityPeriods, Set())).toSeq)
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

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset] = {
    if (withTransaction) {
      withDynTransaction {
        val assetIds = oracleLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
        oracleLinearAssetDao.fetchProhibitionsByIds(assetType, assetIds.toSet)
      }
    } else {
      val assetIds = oracleLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
      oracleLinearAssetDao.fetchProhibitionsByIds(assetType, assetIds.toSet)
    }
  }

  override def getExistingSegments(roadLinks : Seq[RoadLink]): Seq[PersistedLinearAsset] = {
    prohibitionService.getPersistedAssetsByLinkIds(Prohibition.typeId, roadLinks.map(_.linkId), false)
  }

  override def signBelongTo(trafficSign: PersistedTrafficSign): Boolean = {
    val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
    TrafficSignManager.belongsToProhibition(signType)
  }

  override def updateLinearAsset(newSegment: TrafficSignToLinear, username: String): Seq[Long] = {
    prohibitionService.updateWithoutTransaction(Seq(newSegment.oldAssetId.get), newSegment.value, username)
  }

  override def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long = {
    prohibitionService.createWithoutTransaction(Prohibition.typeId, newSegment.roadLink.linkId, newSegment.value,
      newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
      vvhClient.roadLinkData.createVVHTimeStamp(), Some(newSegment.roadLink))
  }

  override def assetToUpdate(assets: Seq[PersistedLinearAsset], trafficSign: PersistedTrafficSign,  createdValue: Prohibitions,username: String) : Unit = {
    val groupedAssetsToUpdate = assets.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[Prohibitions].prohibitions.diff(createdValue.prohibitions))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.foreach { value =>
      prohibitionService.updateWithoutTransaction(value.map(_._1), Prohibitions(value.flatMap(_._2)), username)
      oracleLinearAssetDao.expireConnectedByPointAsset(trafficSign.id)
    }
  }

  override def mappingValue(segment: Seq[TrafficSignToLinear]): Prohibitions = {
    Prohibitions(segment.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions).distinct)
  }

  override def compareValue(value1: Value, value2: Value) : Boolean = {
    value1.asInstanceOf[Prohibitions].equals(value2.asInstanceOf[Prohibitions])
  }
}
