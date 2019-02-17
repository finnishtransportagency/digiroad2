package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AdditionalPanel, Prohibition, SideCode, TrafficDirection}
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

  def createLinearXXXX(sign: PersistedTrafficSign, roadLinks: Seq[VVHRoadlink]): Seq[TrafficSignToGenerateLinear] = {

    val trafficSignsOnRoadLinks = trafficSignService.getTrafficSign(roadLinks.map(_.linkId)).filter { trafficSign =>
      val signType = trafficSignService.getProperty(trafficSign, trafficSignService.typePublicId).get.propertyValue.toInt
      TrafficSignManager.belongsToProhibition(signType)
    }

    val (_, finalRoadLinks) = roadLinks.partition { r =>
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

    val newAsset = finalRoadLinks.flatMap { frl =>
      baseProcess(trafficSignsOnRoadLinks, roadLinks, frl, Set())
    }

    val allSegments = splitSegments(roadLinks, newAsset, finalRoadLinks)

    val (assetForBothSide, assetOneSide) = combineSegments(allSegments)

    convertOneSideCode(assetOneSide, finalRoadLinks) ++ assetForBothSide.toSeq
    Seq()
  }

  def baseProcess(trafficSigns: Seq[PersistedTrafficSign], roadLinks: Seq[VVHRoadlink], actualRoadLink: VVHRoadlink, generatedTrafficSigns: Set[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {
    val signsOnRoadLink = trafficSigns.filter(_.linkId == actualRoadLink.linkId)
    (if (signsOnRoadLink.nonEmpty) {
      signsOnRoadLink.map { sign =>
        val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)
        val pointOfInterest = trafficSignService.getPointOfInterest(first, last, SideCode(sign.validityDirection)).head

        val pairSign = getPairSign(actualRoadLink, sign, signsOnRoadLink, pointOfInterest)
        processing(actualRoadLink, roadLinks.filterNot(_.linkId == actualRoadLink.linkId), sign, signsOnRoadLink, pointOfInterest, Seq(generateLinear(actualRoadLink, sign, pairSign, pointOfInterest))) ++ generatedTrafficSigns
      }
    } else {
      val (first, last) = GeometryUtils.geometryEndpoints(actualRoadLink.geometry)

      Seq(first, last).flatMap { pointOfInterest =>
        getAdjacents((pointOfInterest, actualRoadLink), roadLinks.filterNot(_.linkId == actualRoadLink.linkId)).map { roadLink =>
          baseProcess(trafficSigns, roadLinks, roadLink._1, generatedTrafficSigns)
        }
      }
    }).flatten.toSet
  }

  def processing(actualRoadLink: VVHRoadlink, allRoadLinks: Seq[VVHRoadlink], sign: PersistedTrafficSign,
                 signsOnRoadLink: Seq[PersistedTrafficSign], pointOfInterest: Point, generatedLinear: Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {

    val pairSign = getPairSign(actualRoadLink, sign, signsOnRoadLink, pointOfInterest)
    val generated = generateLinear(actualRoadLink, sign, pairSign, pointOfInterest)

    (if (pairSign.isEmpty) {
      val adjRoadLinks = getAdjacents((pointOfInterest, actualRoadLink), allRoadLinks.filterNot(_.linkId == actualRoadLink.linkId))
      if (adjRoadLinks.nonEmpty)
        adjRoadLinks.flatMap { case (newRoadLink, (_, oppositePoint)) =>
          processing(newRoadLink, allRoadLinks.filterNot(_.linkId == newRoadLink.linkId), sign, signsOnRoadLink, oppositePoint, generated +: generatedLinear)
        }
      else
        generated +: generatedLinear
    } else
      generated +: generatedLinear).toSet
  }

  def generateLinear(currentRoadLink: VVHRoadlink, sign: PersistedTrafficSign, pairedSign: Option[PersistedTrafficSign], pointOfInterest: Point): TrafficSignToGenerateLinear = {
    val prohibitionValue = Seq(prohibitionService.createValue(sign))
    pairedSign match {
      case Some(pair) =>
        if (pair.linkId == sign.linkId) {
          val orderedMValue = Seq(sign.mValue, pair.mValue).sorted

          TrafficSignToGenerateLinear(currentRoadLink, Prohibitions(prohibitionValue), SideCode.apply(sign.validityDirection), orderedMValue.head, orderedMValue.last, Seq(sign.id))
        } else {
          val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
          val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
          val (starMeasure, endMeasure) = if (!GeometryUtils.areAdjacent(pointOfInterest, first)) (0.toDouble, pair.mValue) else (pair.mValue, length)

          TrafficSignToGenerateLinear(currentRoadLink, Prohibitions(prohibitionValue), SideCode.apply(sign.validityDirection), starMeasure, endMeasure, Seq(sign.id))
        }
      case _ =>
        val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
        if (currentRoadLink.linkId == sign.linkId) {
          val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
          val (starMeasure, endMeasure) = if (GeometryUtils.areAdjacent(pointOfInterest, first)) (0L.toDouble, sign.mValue) else (sign.mValue, length)

          TrafficSignToGenerateLinear(currentRoadLink, Prohibitions(prohibitionValue), SideCode.apply(sign.validityDirection), starMeasure, endMeasure, Seq(sign.id))
        }
        else
          TrafficSignToGenerateLinear(currentRoadLink, Prohibitions(prohibitionValue), SideCode.apply(sign.validityDirection), 0, length, Seq(sign.id))
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

  def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign, withTransaction: Boolean = true) : Unit = {
    val username = Some("automatic_trafficSign_deleted")
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val trProhibitionValue = Seq(prohibitionService.createValue(trafficSign))

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition{ asset =>
      asset.value.get.asInstanceOf[Prohibitions].equals(Prohibitions(trProhibitionValue))
    }

    if(toDelete.nonEmpty)
      prohibitionService.expire(toDelete.map(_.id), username.getOrElse(""))

    val groupedAssetsToUpdate = toUpdate.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[Prohibitions].prohibitions.diff(trProhibitionValue))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.map { value =>
      prohibitionService.update(value.map(_._1), Prohibitions(value.flatMap(_._2)), username.getOrElse(""))
    }
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

  def modifyOrDeleteAssetBasedOnSign(trafficSign: PersistedTrafficSign, roadLinksWithSameName: Seq[VVHRoadlink]) {
    val username = "automatic_trafficSign_modified"
    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)

    trafficSignRelatedAssets.foreach { asset =>
      prohibitionService.expire(Seq(asset.id), username)
      oracleLinearAssetDao.expireConnectedAsset(asset.id)
    }

    val assetsToReprocess = createLinearXXXX(trafficSign, roadLinksWithSameName)

    assetsToReprocess.foreach { updatedAsset =>
      val newAssetId = prohibitionService.createWithoutTransaction(Prohibition.typeId, updatedAsset.roadLink.linkId, updatedAsset.value,
        updatedAsset.sideCode.value, Measures(updatedAsset.startMeasure, updatedAsset.endMeasure), username,
        vvhClient.roadLinkData.createVVHTimeStamp(), Some(updatedAsset.roadLink))

      updatedAsset.signId.foreach { relatedSign =>
        oracleLinearAssetDao.insertConnectedAsset(newAssetId, relatedSign)
      }
    }

    println(s"Prohibition related with traffic sign with ID: ${trafficSign.id} on RoadLink: ${trafficSign.linkId} modified")
  }

  def splitSegments(roadLinks: Seq[VVHRoadlink], futureLinears: Seq[TrafficSignToGenerateLinear], finalRoadLinks: Seq[VVHRoadlink]) : Seq[TrafficSignToGenerateLinear] = {
    val oldAssets = prohibitionService.getPersistedAssetsByLinkIds(Prohibition.typeId, roadLinks.map(_.linkId))

    val possibleAssets: Seq[TrafficSignToGenerateLinear] = oldAssets.filter(_.value.isDefined).flatMap { oldAsset =>
      if (oldAsset.sideCode == SideCode.BothDirections.value)
        Seq(TrafficSignToGenerateLinear(roadLinks.find(_.linkId == oldAsset.linkId).get, oldAsset.value.get, SideCode.AgainstDigitizing, oldAsset.startMeasure, oldAsset.endMeasure, Seq()),
          TrafficSignToGenerateLinear(roadLinks.find(_.linkId == oldAsset.linkId).get, oldAsset.value.get, SideCode.TowardsDigitizing, oldAsset.startMeasure, oldAsset.endMeasure, Seq()))
      else
        Seq(TrafficSignToGenerateLinear(roadLinks.find(_.linkId == oldAsset.linkId).get, oldAsset.value.get, SideCode.apply(oldAsset.sideCode), oldAsset.startMeasure, oldAsset.endMeasure, Seq()))
    } ++ futureLinears

    val allSegmentsByLinkId = possibleAssets.map(fl => (fl.roadLink.linkId, fl.startMeasure, fl.endMeasure)).groupBy(_._1)

    allSegmentsByLinkId.keys.flatMap { linkId =>
      val minLengthToZip = 0.01
      val segmentsPoints = allSegmentsByLinkId(linkId).flatMap(fl => Seq(fl._2, fl._3)).distinct.sorted
      val segments = segmentsPoints.zip(segmentsPoints.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }
      val assetOnRoadLink = possibleAssets.filter(_.roadLink.linkId == linkId)

     segments.flatMap { case (startMeasurePOI, endMeasurePOI) =>
        val (assetToward, assetAgainst) = assetOnRoadLink.filter(asset => asset.startMeasure <= startMeasurePOI && asset.endMeasure >= endMeasurePOI).partition(_.sideCode == SideCode.TowardsDigitizing)

       (if (assetToward.nonEmpty)
          Seq(TrafficSignToGenerateLinear(assetToward.head.roadLink, Prohibitions(assetToward.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions)), assetToward.head.sideCode, startMeasurePOI, endMeasurePOI, assetToward.flatMap(_.signId)))
         else
         Seq()) ++
         (if (assetAgainst.nonEmpty)
           Seq(TrafficSignToGenerateLinear(assetAgainst.head.roadLink, Prohibitions(assetAgainst.flatMap(_.value.asInstanceOf[Prohibitions].prohibitions)), assetAgainst.head.sideCode, startMeasurePOI, endMeasurePOI, assetAgainst.flatMap(_.signId)))
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
      val adjacent = roadLinkService.getAdjacent(segment.roadLink.linkId)
      if (adjacent.size == 1) {
        otherSegments.filter(_.roadLink.linkId == adjacent.head.linkId).flatMap { otherSegment =>
          findNextEndAssets(otherSegment, otherSegments.diff(Seq(otherSegment)), result :+ segment.copy(sideCode = BothDirections))}
      } else
        segment +: result
    }
  }

  def convertOneSideCode(assetOneSide: Set[TrafficSignToGenerateLinear], finalRoadLinks: Seq[VVHRoadlink]): Seq[TrafficSignToGenerateLinear] = {
    val (assetInOneTrafficDirectionLink, possibleEndRoad) = roadLinkService.enrichRoadLinksFromVVH(assetOneSide.map(_.roadLink).toSeq).flatMap { roadLink =>
      assetOneSide.map { asset =>
        if (asset.roadLink.linkId == roadLink.linkId && roadLink.trafficDirection != TrafficDirection.BothDirections)
          asset.copy(sideCode = BothDirections)
        else
          asset
      }
    }.toSet.partition(_.sideCode == BothDirections)

    val (endAssets, otherAssets) = possibleEndRoad.partition { a =>
      finalRoadLinks.exists(finalRoad => a.roadLink.linkId == finalRoad.linkId &&
        (Math.abs(a.endMeasure - GeometryUtils.geometryLength(finalRoad.geometry)) < 0.01 ||
        Math.abs(a.startMeasure - 0) < 0.01))
    }

    (if (endAssets.nonEmpty) {
      endAssets.flatMap { asset =>
        findNextEndAssets(asset, otherAssets.toSeq)
      }.toSeq
    } else
      Seq(endAssets, otherAssets).flatten) ++ assetInOneTrafficDirectionLink
  }

  def combineSegments(allSegments: Seq[TrafficSignToGenerateLinear]): Set[TrafficSignToGenerateLinear] = {

    val groupedAssets = allSegments.groupBy(_.roadLink)

    groupedAssets.keys.flatMap { roadLink =>
      groupedAssets(roadLink).sortBy(_.startMeasure).foldLeft(Seq.empty[TrafficSignToGenerateLinear]) { case (result, row) =>
        if (result.isEmpty)
          result :+ row
        else {
          if(Math.abs(result.last.endMeasure - row.startMeasure) < 0.001)
            result.last.copy(endMeasure = row.endMeasure) +: result.init
          else
            result :+ row
        }
      }
    }.toSet
  }

//  private def getOpositePoint(geometry: Seq[Point], point: Point) = {
//    val (headPoint, lastPoint) = GeometryUtils.geometryEndpoints(geometry)
//    if (GeometryUtils.areAdjacent(headPoint, point))
//      lastPoint
//    else
//      headPoint
//  }

  private def getAllRoadLinksWithSameName(signRoadLink: RoadLink): Seq[VVHRoadlink] = {
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

  def getOldAssetsToTreat(): Unit = {

  }


  def createLinearAssetUsingTrafficSigns(): Unit = {
    println("\nStarting create Linear Assets using traffic signs")
    println(DateTime.now())
    println("")

    withDynSession {
      val lastExecutionDate = oracleLinearAssetDao.getLastExecutionDateOfConnectedAsset.getOrElse(DateTime.now().minusDays(2))
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
        val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(trafficSignsToTransform.map(_.linkId).toSet, false)
        println(s"End of roadLinks fetch for filtered Traffic Signs")

        println("")
        println("Start processing traffic signs")
        val(tsToDelete, tsToCreateOrUpdate) = trafficSignsToTransform.partition{_.expired}

        tsToDelete.foreach { ts =>
          // Delete actions
          println(s"Start deleting prohibitions according the traffic sign with ID: ${ts.id}")

          deleteOrUpdateAssetBasedOnSign(ts)

          println(s"Prohibition related with traffic sign with ID: ${ts.id} deleted")
          println("")
        }

        tsToCreateOrUpdate.foreach { ts2 =>
          val tsRoadLink = roadLinks.find(_.linkId == ts2.linkId).head
          val tsCreatedDate = ts2.createdBy
          val tsModifiedDate = ts2.modifiedAt
          val allRoadLinksWithSameName = getAllRoadLinksWithSameName(tsRoadLink)
          if (tsCreatedDate.nonEmpty && tsModifiedDate.isEmpty) {
            //Create actions
            println(s"Start creating prohibition according the traffic sign with ID: ${ts2.id}")

            //            createLinearXXXX(ts, roadLinks.find(_.linkId == ts.linkId).head)

            println(s"Prohibition related with traffic sign with ID: ${ts2.id} created")
            println("")

          } else {
            //Modify actions
            println(s"Start modifying prohibition according the traffic sign with ID: ${ts2.id}")

            modifyOrDeleteAssetBasedOnSign(ts2, allRoadLinksWithSameName)

            println(s"Prohibition related with traffic sign with ID: ${ts2.id} modified")
            println("")
          }
        }
      }
    }
    println("")
    println("Complete at time: " + DateTime.now())
  }
}
