package fi.liikennevirasto.digiroad2.util

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AdditionalPanel, Prohibition, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.tierekisteri.importer.TrafficSignGeneralWarningSignsTierekisteriImporter
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService, TrafficSignToGenerateLinear}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime
import org.json4s.ParserUtil.Segment

case class TrafficSingLinearAssetGeneratorProcess(roadLinkServiceImpl: RoadLinkService) {
  def roadLinkService: RoadLinkService = roadLinkServiceImpl
  def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

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

  lazy val trafficSignManager: TrafficSignManager = {
    new TrafficSignManager(manoeuvreService, prohibitionService, hazmatTransportProhibitionService)
  }

  val oracleLinearAssetDao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)

  def segmentsManager(roadLinks: Seq[VVHRoadlink], trafficSigns: Seq[PersistedTrafficSign], existingSegments : Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {
    val startEndRoadLinks = findStartEndRoadLinkOnChain(roadLinks)

    val newSegments = startEndRoadLinks.flatMap { case (roadLink, pointOfInterest )=>
      baseProcess(trafficSigns, roadLinks, roadLink, pointOfInterest, Seq())
    }.distinct

    val allSegments = splitSegments(roadLinks, newSegments, existingSegments, startEndRoadLinks.map(_._1))
    val (assetForBothSide, assetOneSide) = fuseSegments(allSegments)
    val otherSegments = convertOneSideCode(assetOneSide, startEndRoadLinks.map(_._1))

    combineSegments((assetForBothSide ++ otherSegments).toSeq)
  }

  def findStartEndRoadLinkOnChain(roadLinks: Seq[VVHRoadlink]) :  Seq[(VVHRoadlink, Point)] = {
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

    borderRoadLinks.map{ roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val isStart = roadLinks.diff(borderRoadLinks).exists { r3 =>
        val (first2, last2) = GeometryUtils.geometryEndpoints(r3.geometry)
        GeometryUtils.areAdjacent(first, first2) || GeometryUtils.areAdjacent(first, last2)}

      if(isStart) {(roadLink, first)} else(roadLink, last)
    }
  }

  def segmentsConverter(existingAssets: Seq[PersistedLinearAsset], roadLinks: Seq[VVHRoadlink]) : Seq[TrafficSignToGenerateLinear] = {
    val connectedTrafficSignIds =
      if (existingAssets.nonEmpty)
        oracleLinearAssetDao.getConnectedAssetFromLinearAsset(existingAssets.map(_.id))
      else
        Seq()

    existingAssets.filter(_.value.isDefined).flatMap { asset =>
      val trafficSignIds = connectedTrafficSignIds.filter(_._1 == asset.id).map(_._2).toSet
      if (asset.sideCode == SideCode.BothDirections.value)
        Seq(TrafficSignToGenerateLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.AgainstDigitizing, asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id)),
          TrafficSignToGenerateLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.TowardsDigitizing, asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id)))
      else
        Seq(TrafficSignToGenerateLinear(roadLinks.find(_.linkId == asset.linkId).get, asset.value.get, SideCode.apply(asset.sideCode), asset.startMeasure, asset.endMeasure, trafficSignIds, Some(asset.id)))
    }
  }

  def baseProcess(trafficSigns: Seq[PersistedTrafficSign], roadLinks: Seq[VVHRoadlink], actualRoadLink: VVHRoadlink, opositePoint: Point, result: Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {
    val filteredRoadLinks =  roadLinks.filterNot(_.linkId == actualRoadLink.linkId)
    val signsOnRoadLink = trafficSigns.filter(_.linkId == actualRoadLink.linkId)
    (if (signsOnRoadLink.nonEmpty) {
      signsOnRoadLink.flatMap { sign =>
        val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
        val pointOfInterest = trafficSignService.getPointOfInterest(first, last, SideCode(sign.validityDirection)).head

        createSegmentPieces(actualRoadLink, filteredRoadLinks, sign, trafficSigns, pointOfInterest, result).toSeq ++
        getAdjacents(pointOfInterest, filteredRoadLinks).flatMap { case (roadLink, (_, nextPoint)) =>
          baseProcess(trafficSigns, filteredRoadLinks, roadLink, nextPoint, result)
        }
      }
    } else {
      getAdjacents(opositePoint, filteredRoadLinks).flatMap { case (roadLink, (_, nextPoint)) =>
        baseProcess(trafficSigns, filteredRoadLinks, roadLink,nextPoint,  result)
      }
    }).toSet
  }

  def createSegmentPieces(actualRoadLink: VVHRoadlink, allRoadLinks: Seq[VVHRoadlink], sign: PersistedTrafficSign, signs: Seq[PersistedTrafficSign], pointOfInterest: Point, result: Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {

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

  def generateSegmentPieces(currentRoadLink: VVHRoadlink, sign: PersistedTrafficSign, pairedSign: Option[PersistedTrafficSign], pointOfInterest: Point): TrafficSignToGenerateLinear = {
    val value = prohibitionService.createValue(sign)
    pairedSign match {
      case Some(pair) =>
        if (pair.linkId == sign.linkId) {
          val orderedMValue = Seq(sign.mValue, pair.mValue).sorted

          TrafficSignToGenerateLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), orderedMValue.head, orderedMValue.last, Set(sign.id))
        } else {
          val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
          val (starMeasure, endMeasure) = if (!GeometryUtils.areAdjacent(pointOfInterest, first))
            (0.toDouble, pair.mValue)
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (pair.mValue, length)
          }
          TrafficSignToGenerateLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), starMeasure, endMeasure, Set(sign.id))
        }
      case _ =>
        if (currentRoadLink.linkId == sign.linkId) {
          val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
          val (starMeasure, endMeasure) = if (GeometryUtils.areAdjacent(pointOfInterest, first))
            (0L.toDouble, sign.mValue)
          else {
            val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
            (sign.mValue, length)
          }

          TrafficSignToGenerateLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), starMeasure, endMeasure, Set(sign.id))
        }
        else {
          val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
          TrafficSignToGenerateLinear(currentRoadLink, value, SideCode.apply(sign.validityDirection), 0, length, Set(sign.id))
        }
    }
  }

  def getPairSign(actualRoadLink: VVHRoadlink, mainSign: PersistedTrafficSign, allSignsRelated: Seq[PersistedTrafficSign], pointOfInterest: Point): Option[PersistedTrafficSign] = {
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

  def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign) : Unit = {
    val username = "automatic_trafficSign_deleted"
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val trProhibitionValue = prohibitionService.createValue(trafficSign)

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition{ asset =>
      asset.value.get.asInstanceOf[Prohibitions].equals(trProhibitionValue)
    }

    toDelete.map { asset =>
      prohibitionService.expireAsset(Prohibition.typeId, asset.id, username, true, false)
      oracleLinearAssetDao.expireConnectedAsset(asset.id)
    }

    val groupedAssetsToUpdate = toUpdate.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[Prohibitions].prohibitions.diff(trProhibitionValue.prohibitions))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.map { value =>
      prohibitionService.updateWithoutTransaction(value.map(_._1), Prohibitions(value.flatMap(_._2)), username)
    }
  }

  def getAdjacents(previousInfo: Point, roadLinks: Seq[VVHRoadlink]): Seq[(VVHRoadlink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousInfo)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo)) (first, last) else (last, first)

      (roadLink, points)
    }
  }

  def createLinearAssetAccordingSegmentsInfo(newSegment: TrafficSignToGenerateLinear, username: String): Unit = {
    println(s"Applying creation of new data at roadLink: ${newSegment.roadLink.linkId}")

    val newAssetId = prohibitionService.createWithoutTransaction(Prohibition.typeId, newSegment.roadLink.linkId, newSegment.value,
      newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username,
      vvhClient.roadLinkData.createVVHTimeStamp(), Some(newSegment.roadLink))

    if (newSegment.signId.nonEmpty)
      println(s"Values to insert: $newAssetId / ${newSegment.signId.mkString(",")}")

    newSegment.signId.foreach { signId =>
      oracleLinearAssetDao.insertConnectedAsset(newAssetId, signId)
    }
  }

  def splitSegments(roadLinks: Seq[VVHRoadlink], segments: Seq[TrafficSignToGenerateLinear], existingSegments: Seq[TrafficSignToGenerateLinear], finalRoadLinks: Seq[VVHRoadlink]) : Seq[TrafficSignToGenerateLinear] = {
    val allSegments : Seq[TrafficSignToGenerateLinear] = segments ++ existingSegments

    val allSegmentsByLinkId = allSegments.map(fl => (fl.roadLink.linkId, fl.startMeasure, fl.endMeasure)).groupBy(_._1)

    allSegmentsByLinkId.keys.flatMap { linkId =>
      val minLengthToZip = 0.01
      val segmentsPoints = allSegmentsByLinkId(linkId).flatMap(fl => Seq(fl._2, fl._3)).distinct.sorted
      val segments = segmentsPoints.zip(segmentsPoints.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }
      val assetOnRoadLink = allSegments.filter(_.roadLink.linkId == linkId)

     segments.flatMap { case (startMeasurePOI, endMeasurePOI) =>
        val (assetToward, assetAgainst) = assetOnRoadLink.filter(asset => asset.startMeasure <= startMeasurePOI && asset.endMeasure >= endMeasurePOI).partition(_.sideCode == SideCode.TowardsDigitizing)

       (if (assetToward.nonEmpty)
          Seq(TrafficSignToGenerateLinear(assetToward.head.roadLink, Prohibitions(assetToward.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions).distinct), assetToward.head.sideCode, startMeasurePOI, endMeasurePOI, assetToward.flatMap(_.signId).toSet, assetToward.head.oldAssetId))
         else
         Seq()) ++
         (if (assetAgainst.nonEmpty)
           Seq(TrafficSignToGenerateLinear(assetAgainst.head.roadLink, Prohibitions(assetAgainst.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions).distinct), assetAgainst.head.sideCode, startMeasurePOI, endMeasurePOI, assetAgainst.flatMap(_.signId).toSet, assetAgainst.head.oldAssetId))
         else
           Seq())
      }
    }.toSeq
  }

  def fuseSegments(allSegments: Seq[TrafficSignToGenerateLinear]): (Set[TrafficSignToGenerateLinear], Set[TrafficSignToGenerateLinear]) = {
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


  def findNextEndAssets (segment: TrafficSignToGenerateLinear, otherSegments: Seq[TrafficSignToGenerateLinear], result: Seq[TrafficSignToGenerateLinear] = Seq()) : Seq[TrafficSignToGenerateLinear] = {
    val segmentSameRoadLink = otherSegments.filter(other => other.roadLink == segment.roadLink && other.startMeasure == segment.startMeasure && other.endMeasure == segment.endMeasure && other.sideCode == segment.sideCode)

    if (segmentSameRoadLink.nonEmpty) {
     segmentSameRoadLink.flatMap { otherSegment => findNextEndAssets(otherSegment, otherSegments.diff(Seq(otherSegment)), result :+ segment.copy(sideCode = BothDirections))}
    } else {
      val adjacent = roadLinkService.getAdjacentTemp(segment.roadLink.linkId)
      if (adjacent.size == 1) {
        otherSegments.filter(_.roadLink.linkId == adjacent.head.linkId).flatMap { otherSegment =>
          findNextEndAssets(otherSegment, otherSegments.diff(Seq(otherSegment)), result :+ segment.copy(sideCode = BothDirections))}
      } else
        segment.copy(sideCode = BothDirections) +: result
    }
  }

  def convertOneSideCode(oneSideSegments: Set[TrafficSignToGenerateLinear], finalRoadLinks: Seq[VVHRoadlink]): Seq[TrafficSignToGenerateLinear] = {
    def compareWithTrafficDirection(segments: Set[TrafficSignToGenerateLinear]) : (Seq[TrafficSignToGenerateLinear], Seq[TrafficSignToGenerateLinear]) = {
      roadLinkService.enrichRoadLinksFromVVH(segments.map(_.roadLink).toSeq).flatMap { roadLink =>
        segments.map { seg =>
          if (seg.roadLink.linkId == roadLink.linkId && roadLink.trafficDirection != TrafficDirection.BothDirections)
            seg.copy(sideCode = BothDirections)
          else
            seg
        }
      }.partition(_.sideCode == BothDirections)
    }

    def convertEndRoadSegments(segments: Seq[TrafficSignToGenerateLinear]):  Seq[TrafficSignToGenerateLinear] = {
      val (endSegments, otherSegments) = segments.partition { a =>
        finalRoadLinks.exists(finalRoad => a.roadLink.linkId == finalRoad.linkId &&
          (Math.abs(a.endMeasure - GeometryUtils.geometryLength(finalRoad.geometry)) < 0.01 ||
            Math.abs(a.startMeasure - 0) < 0.01))
      }

      if (endSegments.nonEmpty) {
        endSegments.flatMap { asset =>
          findNextEndAssets(asset, otherSegments.toSeq)
        }.toSeq
      } else
        Seq(endSegments, otherSegments).flatten
    }

    val (assetInOneTrafficDirectionLink, possibleEndRoad) = compareWithTrafficDirection(oneSideSegments)

    convertEndRoadSegments(possibleEndRoad) ++ assetInOneTrafficDirectionLink
  }

  def combineSegments(allSegments: Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {
    val groupedSegments = allSegments.groupBy(_.roadLink)

    groupedSegments.keys.flatMap { roadLink =>
      val sortedSegments = groupedSegments(roadLink).sortBy(_.startMeasure)
        sortedSegments.tail.foldLeft(Seq(sortedSegments.head)) { case (result, row) =>

          if(Math.abs(result.last.endMeasure - row.startMeasure) < 0.001 && result.last.value.equals(row.value))
            result.last.copy(endMeasure = row.endMeasure) +: result.init
          else
            result :+ row
        }
    }.toSet
  }

  private def getAllRoadLinksWithSameName(signRoadLink: VVHRoadlink): Seq[VVHRoadlink] = {
    val (tsRoadNamePublicId, tsRroadName): (String, String) =
      signRoadLink.attributes.get("ROADNAME_FI") match {
        case Some(nameFi) =>
          ("ROADNAME_FI", nameFi.toString)
        case _ =>
          ("ROADNAME_SE", signRoadLink.attributes.getOrElse("ROADNAME_SE", "").toString)
      }

    //RoadLink with the same Finnish/Swedish name
    roadLinkService.fetchVVHRoadlinks(Set(tsRroadName), tsRoadNamePublicId)
  }

  def iterativeProcess(roadLinks: Seq[VVHRoadlink], roadLinksssss: Seq[VVHRoadlink]): Unit = {
    val roadLinkFor = roadLinks.diff(roadLinksssss)

    if (roadLinkFor.nonEmpty) {
      val roadLink = roadLinkFor.head
        println("")
        println(s"Working at RoadLink with link id: ${roadLink.linkId}")

        val allRoadLinksWithSameName = getAllRoadLinksWithSameName(roadLink)
        val trafficSignsOnRoadLinks = trafficSignService.getTrafficSign(roadLinks.map(_.linkId)).filter { trafficSign =>
          val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
          TrafficSignManager.belongsToProhibition(signType)
        }

        val existingAssets = prohibitionService.getPersistedAssetsByLinkIds(Prohibition.typeId, allRoadLinksWithSameName.map(_.linkId), false)
        val existingSegments = segmentsConverter(existingAssets, allRoadLinksWithSameName)
        val allSegments = segmentsManager(allRoadLinksWithSameName, trafficSignsOnRoadLinks, existingSegments)

        applyChangesBySegments(allSegments, existingSegments)

        iterativeProcess(roadLinkFor.filterNot(_.linkId == roadLink.linkId), allRoadLinksWithSameName)
    }
  }

  def applyChangesBySegments(allSegments: Set[TrafficSignToGenerateLinear], existingSegments: Seq[TrafficSignToGenerateLinear]) {
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
            }
            else {
              //delete old and create new
              existingSeg.foreach { asset =>
                prohibitionService.expireAsset(Prohibition.typeId, asset.oldAssetId.get, userUpdate, true, false)
                oracleLinearAssetDao.expireConnectedAsset(asset.oldAssetId.get)
              }
              createLinearAssetAccordingSegmentsInfo(newSegment, userUpdate)
              }
          }
        }
        else {
          //create news
          createLinearAssetAccordingSegmentsInfo(newSegment, userCreate)
        }
      }
    )
  }


  def createLinearAssetUsingTrafficSigns(): Unit = {
    println("\nStarting create Linear Assets using traffic signs")
    println(DateTime.now())
    println("")

    withDynTransaction {
      val lastExecutionDate = oracleLinearAssetDao.getLastExecutionDateOfConnectedAsset()
      println(s"Last Execution Date of the batch: ${lastExecutionDate.toString} ")
      println("")

      println(s"Obtaining created/modified/deleted traffic Signs after the date: ${lastExecutionDate.toString}")
      //Get Traffic Signs
      val trafficSigns = trafficSignService.getAfterDate(lastExecutionDate)
      println(s"Number of Traffic Signs to execute: ${trafficSigns.size} ")

      val trafficSignsToTransform =
        trafficSigns.filter { ts =>
          val signType = trafficSignService.getProperty(ts, trafficSignService.typePublicId).get.propertyValue.toInt
          TrafficSignManager.belongsToProhibition(signType)
        }

      if (trafficSignsToTransform.nonEmpty) {
        println("")
        println(s"Obtaining all Road Links for filtered Traffic Signs")
        val roadLinks = roadLinkService.fetchVVHRoadlinksAndComplementary(trafficSignsToTransform.map(_.linkId).toSet)
        println(s"End of roadLinks fetch for filtered Traffic Signs")

        println("")
        println("Start processing traffic signs")
        val tsToDelete = trafficSignsToTransform.filter(_.expired)

        tsToDelete.foreach { ts =>
          // Delete actions
          println(s"Start deleting prohibitions according the traffic sign with ID: ${ts.id}")

          deleteOrUpdateAssetBasedOnSign(ts)

          println(s"Prohibition related with traffic sign with ID: ${ts.id} deleted")
          println("")
        }

        //create and Modify actions
        println("Start creating/modifying prohibitions according the traffic sign")
        iterativeProcess(roadLinks, Seq())
      }
    }
    println("")
    println("Complete at time: " + DateTime.now())
  }
}
