package fi.liikennevirasto.digiroad2.util

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink, Value}
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime

case class TrafficSignToLinear(roadLink: RoadLink, value: Value, sideCode: SideCode, startMeasure: Double, endMeasure: Double, signId: Set[Long], oldAssetId: Option[Long] = None)

case class TrafficSingLinearAssetGeneratorProcess(RoadLinkServiceImpl: RoadLinkService) {
  def roadLinkService: RoadLinkService = RoadLinkServiceImpl

  def vvhClient: VVHClient = RoadLinkServiceImpl.vvhClient

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

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

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  lazy val hazmatTransportProhibitionService: HazmatTransportProhibitionService = {
    new HazmatTransportProhibitionService(roadLinkService, eventbus)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, userProvider, eventbus)
  }

  val oracleLinearAssetDao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)

  def segmentsManager(roadLinks: Seq[RoadLink], trafficSigns: Seq[PersistedTrafficSign], existingSegments: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    val startEndRoadLinks = findStartEndRoadLinkOnChain(roadLinks)

    val newSegments = startEndRoadLinks.flatMap { case (roadLink, pointOfInterest) =>
      baseProcess(trafficSigns, roadLinks, roadLink, pointOfInterest, Seq())
    }.distinct

    val allSegments = splitSegments(roadLinks, newSegments, existingSegments, startEndRoadLinks.map(_._1))
    val (assetForBothSide, assetOneSide) = fuseSegments(allSegments)

    val otherSegments = convertOneSideCode(assetOneSide, startEndRoadLinks)
    //
    //    combineSegments((assetForBothSide ++ otherSegments).toSeq)

    //assetForBothSide ++ otherSegments
    assetForBothSide ++ otherSegments
  }

  def findStartEndRoadLinkOnChain(RoadLinks: Seq[RoadLink]): Seq[(RoadLink, Point)] = {
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

    borderRoadLinks.map { RoadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(RoadLink.geometry)
      val isStart = RoadLinks.diff(borderRoadLinks).exists { r3 =>
        val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
        !(GeometryUtils.areAdjacent(first, first2) || GeometryUtils.areAdjacent(first, last2))
      }

      if (isStart) {
        (RoadLink, first)
      } else
        (RoadLink, last)
    }
  }

  def segmentsConverter(existingAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink]): Seq[TrafficSignToLinear] = {
    println("Converting old segments")
    val connectedTrafficSignIds =
      if (existingAssets.nonEmpty)
        oracleLinearAssetDao.getConnectedAssetFromLinearAsset(existingAssets.map(_.id))
      else
        Seq()

    existingAssets.filter(_.value.isDefined).flatMap { asset =>
      val trafficSignIds = connectedTrafficSignIds.filter(_._1 == asset.id).map(_._2).toSet
      if (asset.sideCode == SideCode.BothDirections.value)
        Seq(TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.AgainstDigitizing, asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id)),
          TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.TowardsDigitizing, asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id)))
      else
        Seq(TrafficSignToLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.apply(asset.sideCode), asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id)))
    }
  }

  def baseProcess(trafficSigns: Seq[PersistedTrafficSign], roadLinks: Seq[RoadLink], actualRoadLink: RoadLink, oppositePoint: Point, result: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    println(s"Base process for ${roadLinks.size}")
    val filteredRoadLinks = roadLinks.filterNot(_.linkId == actualRoadLink.linkId)
    val signsOnRoadLink = trafficSigns.filter(_.linkId == actualRoadLink.linkId)
    (if (signsOnRoadLink.nonEmpty) {
      signsOnRoadLink.flatMap { sign =>
        val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
        val pointOfInterest = trafficSignService.getPointOfInterest(first, last, SideCode(sign.validityDirection)).head
        println("Creating and getting new adjacent")
        createSegmentPieces(actualRoadLink, filteredRoadLinks, sign, trafficSigns, pointOfInterest, result).toSeq ++
          getAdjacents(pointOfInterest, filteredRoadLinks).flatMap { case (roadLink, (_, nextPoint)) =>
            baseProcess(trafficSigns, filteredRoadLinks, roadLink, nextPoint, result)
          }
      }
    } else {
      println("getting new adjacent")
      getAdjacents(oppositePoint, filteredRoadLinks).flatMap { case (roadLink, (_, nextPoint)) =>
        baseProcess(trafficSigns, filteredRoadLinks, roadLink, nextPoint, result)
      }
    }).toSet
  }

  def createSegmentPieces(actualRoadLink: RoadLink, allRoadLinks: Seq[RoadLink], sign: PersistedTrafficSign, signs: Seq[PersistedTrafficSign], pointOfInterest: Point, result: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    println("Create Segments")
    val pairSign = getPairSign(actualRoadLink, sign, signs.filter(_.linkId == actualRoadLink.linkId), pointOfInterest)
    val generatedSegmentPieces = generateSegmentPieces(actualRoadLink, sign, pairSign, pointOfInterest)

    (if (pairSign.isEmpty) {
      val adjRoadLinks = getAdjacents(pointOfInterest, allRoadLinks.filterNot(_.linkId == actualRoadLink.linkId))
      if (adjRoadLinks.nonEmpty) {
        adjRoadLinks.flatMap { case (newRoadLink, (_, oppositePoint)) =>
          createSegmentPieces(newRoadLink, allRoadLinks.filterNot(_.linkId == newRoadLink.linkId), sign, signs, oppositePoint, generatedSegmentPieces +: result)
        }
      } else
        generatedSegmentPieces +: result
    } else
      generatedSegmentPieces +: result).toSet
  }

  def generateSegmentPieces(currentRoadLink: RoadLink, sign: PersistedTrafficSign, pairedSign: Option[PersistedTrafficSign], pointOfInterest: Point): TrafficSignToLinear = {
    println("Generate pieces")
    val value = prohibitionService.createValue(sign)
    pairedSign match {
      case Some(pair) =>
        if (pair.linkId == sign.linkId) {
          val orderedMValue = Seq(sign.mValue, pair.mValue).sorted

          TrafficSignToLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), orderedMValue.head, orderedMValue.last, Set(sign.id))
        } else {
          val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
          val (starMeasure, endMeasure, sideCode) = if (!GeometryUtils.areAdjacent(pointOfInterest, first))
            (0.toDouble, pair.mValue, SideCode.apply(sign.validityDirection))
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (pair.mValue, length, SideCode.switch(SideCode.apply(sign.validityDirection)))
          }
          TrafficSignToLinear(currentRoadLink, value, sideCode , starMeasure, endMeasure, Set(sign.id))
        }
      case _ =>
        val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
        if (currentRoadLink.linkId == sign.linkId) {
          val (starMeasure, endMeasure, sideCode) = if (GeometryUtils.areAdjacent(pointOfInterest, first))
            (0L.toDouble, sign.mValue, SideCode.apply(sign.validityDirection))
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (sign.mValue, length, SideCode.switch(SideCode.apply(sign.validityDirection)))
          }

          TrafficSignToLinear(currentRoadLink, value, sideCode, starMeasure, endMeasure, Set(sign.id))
        }
        else {
          val sideCode = if (GeometryUtils.areAdjacent(pointOfInterest, first))
            SideCode.apply(sign.validityDirection)
          else
            SideCode.switch(SideCode.apply(sign.validityDirection))

          val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
          TrafficSignToLinear(currentRoadLink, value, sideCode, 0, length, Set(sign.id))
        }
    }
  }

  def getPairSign(actualRoadLink: RoadLink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], pointOfInterest: Point): Option[PersistedTrafficSign] = {
    println("Get pair sign")
    val mainSignType = trafficSignService.getProperty(mainSign, trafficSignService.typePublicId).get.propertyValue.toInt

    allSignsRelated.filterNot(_.id == mainSign.id).filter(_.linkId == actualRoadLink.linkId).find { sign =>
      val relatedSignType = trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt
      val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
      val pointOfInterestRelatedSign = trafficSignService.getPointOfInterest(first, last, SideCode(sign.validityDirection)).head
      //sign in opposite direction
      relatedSignType == mainSignType && !GeometryUtils.areAdjacent(pointOfInterestRelatedSign, pointOfInterest)
    }
  }

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = false): Seq[PersistedLinearAsset] = {
    if (withTransaction) {
      withDynTransaction {
        val assetIds = oracleLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
        oracleLinearAssetDao.fetchProhibitionsByIds(Prohibition.typeId, assetIds.toSet)
      }
    } else {
      val assetIds = oracleLinearAssetDao.getConnectedAssetFromTrafficSign(trafficSignId)
      oracleLinearAssetDao.fetchProhibitionsByIds(Prohibition.typeId, assetIds.toSet)
    }
  }

  def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign): Unit = {
    val username = "automatic_trafficSign_deleted"
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val trProhibitionValue = prohibitionService.createValue(trafficSign)

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition { asset =>
      asset.value.get.asInstanceOf[Prohibitions].equals(trProhibitionValue)
    }

    toDelete.foreach { asset =>
      prohibitionService.expireAsset(Prohibition.typeId, asset.id, username, true, false)
      oracleLinearAssetDao.expireConnectedByLinearAsset(asset.id)
    }

    val groupedAssetsToUpdate = toUpdate.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[Prohibitions].prohibitions.diff(trProhibitionValue.prohibitions))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.foreach { value =>
      prohibitionService.updateWithoutTransaction(value.map(_._1), Prohibitions(value.flatMap(_._2)), username)
      oracleLinearAssetDao.expireConnectedByPointAsset(trafficSign.id)
    }
  }

  def getAdjacents(previousInfo: Point, RoadLinks: Seq[RoadLink]): Seq[(RoadLink, (Point, Point))] = {
    RoadLinks.filter {
      RoadLink =>
        GeometryUtils.areAdjacent(RoadLink.geometry, previousInfo)
    }.map { RoadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(RoadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo)) (first, last) else (last, first)

      (RoadLink, points)
    }
  }

  def createLinearAssetAccordingSegmentsInfo(newSegment: TrafficSignToLinear, username: String): Unit = {
    println(s"Applying creation of new data at RoadLink: ${newSegment.roadLink.linkId}")

    val newAssetId = prohibitionService.createWithoutTransaction(Prohibition.typeId, newSegment.roadLink.linkId, newSegment.value,
      newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
      vvhClient.roadLinkData.createVVHTimeStamp(), Some(newSegment.roadLink))

    if (newSegment.signId.nonEmpty)
      println(s"Values to insert: $newAssetId / ${newSegment.signId.mkString(",")}")

    newSegment.signId.foreach { signId =>
      createAssetRelation(newAssetId, signId)
    }
  }

  private def createAssetRelation(linearAssetId: Long, trafficSignId: Long) {
    try {
      oracleLinearAssetDao.insertConnectedAsset(linearAssetId, trafficSignId)
    } catch {
      case ex: SQLIntegrityConstraintViolationException => print("") //the key already exist with a valid date
      case e: Exception => print("SQL Exception ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }


  def splitSegments(RoadLinks: Seq[RoadLink], segments: Seq[TrafficSignToLinear], existingSegments: Seq[TrafficSignToLinear], finalRoadLinks: Seq[RoadLink]) : Seq[TrafficSignToLinear] = {
    val allSegments: Seq[TrafficSignToLinear] = segments ++ existingSegments

    val allSegmentsByLinkId = allSegments.map(fl => (fl.roadLink.linkId, fl.startMeasure, fl.endMeasure)).distinct.groupBy(_._1)

    allSegmentsByLinkId.keys.flatMap { linkId =>
      val minLengthToZip = 0.01
      val segmentsPoints = allSegmentsByLinkId(linkId).flatMap(fl => Seq(fl._2, fl._3)).distinct.sorted
      val segments = segmentsPoints.zip(segmentsPoints.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }
      val assetOnRoadLink = allSegments.filter(_.roadLink.linkId == linkId)

      segments.flatMap { case (startMeasurePOI, endMeasurePOI) =>
        val (assetsToward, assetsAgainst) = assetOnRoadLink.filter(asset => asset.startMeasure <= startMeasurePOI && asset.endMeasure >= endMeasurePOI).partition(_.sideCode == SideCode.TowardsDigitizing)

        assetsToward.headOption.map { assetToward =>
          TrafficSignToLinear(assetToward.roadLink, Prohibitions(assetsToward.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions).distinct), assetToward.sideCode, startMeasurePOI, endMeasurePOI, assetsToward.flatMap(_.signId).toSet, assetToward.oldAssetId)
        } ++
          assetsAgainst.headOption.map { assetAgainst =>
            TrafficSignToLinear(assetAgainst.roadLink, Prohibitions(assetsAgainst.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions).distinct), assetAgainst.sideCode, startMeasurePOI, endMeasurePOI, assetsAgainst.flatMap(_.signId).toSet, assetAgainst.oldAssetId)
          }
      }
    }.toSeq
  }

  def fuseSegments(allSegments: Seq[TrafficSignToLinear]): (Set[TrafficSignToLinear], Set[TrafficSignToLinear]) = {
    val (assetToward, assetAgainst) = allSegments.partition(_.sideCode == SideCode.TowardsDigitizing)
    val (withoutMatch, bothSide) = (assetToward.map { toward =>
      if (assetAgainst.exists { against => toward.roadLink.linkId == against.roadLink.linkId && toward.startMeasure == against.startMeasure && toward.endMeasure == against.endMeasure && toward.value.equals(against.value) })
        toward.copy(sideCode = BothDirections)
      else
        toward
    } ++
      assetAgainst.filterNot { against =>
        assetToward.exists { toward => toward.roadLink.linkId == against.roadLink.linkId && toward.startMeasure == against.startMeasure && toward.endMeasure == against.endMeasure && toward.value.equals(against.value)}
    }).toSet.partition(_.sideCode != BothDirections)

    val (falseMatch, oneSide) = withoutMatch.partition{ asset =>
      withoutMatch.exists( seg => seg.roadLink.linkId == asset.roadLink.linkId && seg.startMeasure == asset.startMeasure && seg.endMeasure == asset.endMeasure && seg.sideCode != asset.sideCode )
    }
    (bothSide ++ falseMatch , oneSide)
  }


  def findNextEndAssets (segments: Seq[TrafficSignToLinear], baseSegment: TrafficSignToLinear, result: Seq[TrafficSignToLinear] = Seq(), numberOfAdjacent: Int = 0) : Seq[TrafficSignToLinear] = {
    if (segments.isEmpty) {
      result
    } else {
      val adjacent = roadLinkService.getAdjacentTemp(baseSegment.roadLink.linkId)
      if(numberOfAdjacent == 1 && adjacent.size > 1 ) {
        segments.filter(_ == baseSegment).map(_.copy(sideCode = SideCode.BothDirections)) ++ result
      }else if (adjacent.size == 1 || isEndRoadLink(baseSegment.roadLink, adjacent)) { //is ended
        val result = segments.filter(_.roadLink == baseSegment.roadLink).map(_.copy(sideCode = SideCode.BothDirections))
        val newBaseSegment = segments.filter(_.roadLink.linkId == adjacent.head.linkId)

        if (newBaseSegment.isEmpty)
          result
        else
          findNextEndAssets(segments.filterNot(_.roadLink == newBaseSegment.head.roadLink), newBaseSegment.head,  result, adjacent.size)
      } else
        segments ++ result
    }
  }

  def convertOneSideCode(oneSideSegments: Set[TrafficSignToLinear], endRoadLinksInfo: Seq[(RoadLink, Point)]): Seq[TrafficSignToLinear] = {
    def compareWithTrafficDirection(segments: Set[TrafficSignToLinear]) : (Set[TrafficSignToLinear], Set[TrafficSignToLinear]) = {
      segments.map { seg =>
        if (seg.roadLink.trafficDirection != TrafficDirection.BothDirections)
          seg.copy(sideCode = BothDirections)
        else
          seg
      }.partition(_.sideCode == BothDirections)
    }

    def convertEndRoadSegments(segments: Seq[TrafficSignToLinear]):  Seq[TrafficSignToLinear] = {
      val (segmentsOnEndRoadLink, otherSegments) = segments.partition { seg => endRoadLinksInfo.exists {case (endRoadLink, pointOfInterest) =>
        val (first, _) = GeometryUtils.geometryEndpoints(endRoadLink.geometry)
        (if(GeometryUtils.areAdjacent(first, pointOfInterest)) {
          Math.abs(seg.startMeasure - 0) < 0.01
        } else {
          Math.abs(seg.endMeasure - GeometryUtils.geometryLength(endRoadLink.geometry))  < 0.01
        })&& seg.roadLink.linkId == endRoadLink.linkId
        }
      }

      val endRoadLinks = segmentsOnEndRoadLink.flatMap { endedSegment =>
        findNextEndAssets(segments, endedSegment)
      }

      otherSegments.diff(endRoadLinks) ++ endRoadLinks
    }

    val (assetInOneTrafficDirectionLink, possibleEndRoad) = compareWithTrafficDirection(oneSideSegments)

    convertEndRoadSegments(possibleEndRoad.toSeq) ++ assetInOneTrafficDirectionLink
  }

  def isEndRoadLink(endRoadLink: RoadLink, adjacent: Seq[RoadLink]) : Boolean = {
    val (start, end) = GeometryUtils.geometryEndpoints(endRoadLink.geometry)
    !(adjacent.exists{ road =>
      val (first, last) = GeometryUtils.geometryEndpoints(road.geometry)
      GeometryUtils.areAdjacent(start, first) || GeometryUtils.areAdjacent(start, last)} &&
      adjacent.exists{ road =>  val (first, last) = GeometryUtils.geometryEndpoints(road.geometry)
        GeometryUtils.areAdjacent(end, first) || GeometryUtils.areAdjacent(end, last)
      })
  }

  def combineSegments(allSegments: Seq[TrafficSignToLinear]): Set[TrafficSignToLinear] = {
    val groupedSegments = allSegments.groupBy(_.roadLink)

    groupedSegments.keys.flatMap { RoadLink =>
      val sortedSegments = groupedSegments(RoadLink).sortBy(_.startMeasure)
        sortedSegments.tail.foldLeft(Seq(sortedSegments.head)) { case (result, row) =>

          if(Math.abs(result.last.endMeasure - row.startMeasure) < 0.001 && result.last.value.equals(row.value))
            result.last.copy(endMeasure = row.endMeasure) +: result.init
          else
            result :+ row
        }
    }.toSet
  }

  private def getAllRoadLinksWithSameName(signRoadLink: RoadLink): Seq[RoadLink] = {
    println("getAllRoadLinksWithSameName")
    val tsRoadNameInfo =
      if(signRoadLink.attributes("ROADNAME_FI").toString.trim.nonEmpty) {
        Some("ROADNAME_FI", signRoadLink.attributes("ROADNAME_FI").toString)
      } else if(signRoadLink.attributes("ROADNAME_SE").toString.trim.nonEmpty) {
        Some("ROADNAME_FI", signRoadLink.attributes("ROADNAME_SE").toString)
      } else
        None

    //RoadLink with the same Finnish/Swedish name
    if(tsRoadNameInfo.nonEmpty)
      roadLinkService.getRoadLinksAndComplementaryByRoadNameFromVVH(tsRoadNameInfo.get._1, Set(tsRoadNameInfo.get._2), false)
    else
      Seq()
  }

  def applyChangesBySegments(allSegments: Set[TrafficSignToLinear], existingSegments: Seq[TrafficSignToLinear]) {
    val userCreate = "automatic_trafficSign_created"
    val userUpdate = "automatic_trafficSign_updated"
    println(s"Total of segments:  ${allSegments.size} ")

    allSegments.groupBy(_.roadLink.linkId).values.foreach(newSegments =>
      newSegments.foreach { newSegment =>
        val existingSegOnRoadLink = existingSegments.filter(_.roadLink.linkId == newSegment.roadLink.linkId)

        if (existingSegOnRoadLink.nonEmpty) {

          val existingSeg = existingSegOnRoadLink.filter { existingSeg =>existingSeg.startMeasure == newSegment.startMeasure &&
            existingSeg.endMeasure == newSegment.endMeasure && newSegment.sideCode == existingSeg.sideCode}

          if (existingSeg.exists(_.value.equals(newSegment.value))) {
            if (newSegment.signId.toSeq.diff(existingSeg.flatMap(_.oldAssetId)).isEmpty) {
              println(s"Changes apply to Connected table ${newSegment.signId.mkString(",")}")
              newSegment.signId.foreach { signId =>
                try {
                  oracleLinearAssetDao.insertConnectedAsset(existingSeg.flatMap(_.oldAssetId).head, signId)
                } catch {
                  case ex: SQLIntegrityConstraintViolationException =>
                    print("duplicate key inserted ")
                  case e: Exception => print("SQL Exception ")
                    throw new RuntimeException("SQL exception " + e.getMessage)
                }}}
          }else {
            //same startMeasure, endMeasure amd SideCode, diff values
            if (existingSeg.nonEmpty) {
              //Update value
              println(s"Applying modifications at asset ID: ${existingSeg.flatMap(_.oldAssetId).mkString(",")} ")
              prohibitionService.updateWithoutTransaction(existingSeg.flatMap(_.oldAssetId), newSegment.value, userUpdate)
              newSegment.oldAssetId.foreach { oldId =>
                newSegment.signId.foreach { signId =>
                  createAssetRelation(oldId, signId)
                }
              }
            }else {
              //delete old and create new
              existingSeg.foreach { asset =>
                prohibitionService.expireAsset(Prohibition.typeId, asset.oldAssetId.get, userUpdate, true, false)
                oracleLinearAssetDao.expireConnectedByLinearAsset(asset.oldAssetId.get)}

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

  def iterativeProcess(roadLinks: Seq[RoadLink], processedRoadLinks: Seq[RoadLink]): Unit = {
    val roadLinkToBeProcessed = roadLinks.diff(processedRoadLinks)

    if (roadLinkToBeProcessed.nonEmpty) {
      val roadLink = roadLinkToBeProcessed.head
      val allRoadLinksWithSameName = withDynTransaction {
        val allRoadLinksWithSameName = getAllRoadLinksWithSameName(roadLink)
        val trafficSigns = trafficSignService.getTrafficSign(allRoadLinksWithSameName.map(_.linkId))
        val filteredTrafficSigns = trafficSigns.filter { trafficSign =>
          val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
          TrafficSignManager.belongsToProhibition(signType)
        }

        val existingAssets = prohibitionService.getPersistedAssetsByLinkIds(Prohibition.typeId, allRoadLinksWithSameName.map(_.linkId), false)
        val existingSegments = segmentsConverter(existingAssets, allRoadLinksWithSameName)
        println(s"Processing: ${filteredTrafficSigns.size}")

        //create and Modify actions
        println("Start creating/modifying prohibitions according the traffic sign")
        val allSegments = segmentsManager(roadLinks, filteredTrafficSigns, existingSegments)
        applyChangesBySegments(allSegments, existingSegments)

        if(trafficSigns.nonEmpty)
          oracleLinearAssetDao.deleteTrafficSignsToProcess(trafficSigns.map(_.id), Prohibition.typeId)

        allRoadLinksWithSameName
      }

      iterativeProcess(roadLinkToBeProcessed.filterNot(_.linkId == roadLink.linkId), allRoadLinksWithSameName)
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  def createLinearAssetUsingTrafficSigns(): Unit = {
    println("\nStarting create Linear Assets using traffic signs")
    println(DateTime.now())
    println("")

    val roadLinks = withDynSession {
      val trafficSignsToProcess = oracleLinearAssetDao.getTrafficSignsToProcess(Prohibition.typeId)
      val trafficSigns = trafficSignService.fetchPointAssetsWithExpired(withFilter(s"Where a.id in (${trafficSignsToProcess.mkString(",")}) "))
      val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIdsFromVVH(trafficSigns.map(_.linkId).toSet, false).filter(_.administrativeClass != State)
      val trafficSignsToTransform = trafficSigns.filter(asset => roadLinks.exists(_.linkId == asset.linkId))

      println(s"Total of trafficSign to process: ${trafficSigns.size}")

      val tsToDelete = trafficSigns.filter(_.expired)

      tsToDelete.foreach { ts =>
        // Delete actions
        println(s"Start deleting prohibitions according the traffic sign with ID: ${ts.id}")
        deleteOrUpdateAssetBasedOnSign(ts)
      }

      //Remove the table sign added on State Road
      val trafficSignsToDelete = trafficSigns.diff(trafficSignsToTransform) ++ tsToDelete
      if(trafficSignsToDelete.nonEmpty)
        oracleLinearAssetDao.deleteTrafficSignsToProcess(trafficSignsToDelete.map(_.id), Prohibition.typeId)

      roadLinks
    }
    println("Start processing traffic signs")
    iterativeProcess(roadLinks, Seq())

    println("")
    println("Complete at time: " + DateTime.now())
  }
}
