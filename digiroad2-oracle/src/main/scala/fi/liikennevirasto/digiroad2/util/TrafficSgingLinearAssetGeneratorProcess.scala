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
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.withDynSession
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService, TrafficSignToGenerateLinear}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime


object TrafficSgingLinearAssetGeneratorProcess {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

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

  val dataImporter = new AssetDataImporter
  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  }

  lazy val userProvider: UserProvider = {
    Class.forName(dr2properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
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

  lazy val oracleLinearAssetDao : OracleLinearAssetDao = {
    new OracleLinearAssetDao(vvhClient, roadLinkService)
  }


  val dao : OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)

  def createLinearXXXX(sign: PersistedTrafficSign, roadLink: RoadLink): Seq[TrafficSignToGenerateLinear] = {
    val (tsRoadNamePublicId, tsRroadName): (String, String) =
      roadLink.attributes.get("ROADNAME_FI") match {
        case Some(nameFi) =>
          ("ROADNAME_FI", nameFi.toString)
        case _ =>
          ("ROADNAME_SE", roadLink.attributes.getOrElse("ROADNAME_SE", "").toString)
      }

    //RoadLink with the same Finnish name
    val roadLinks = roadLinkService.fetchVVHRoadlinks(Set(tsRroadName), tsRoadNamePublicId)

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

    createLinearAssetAccordingTrafficSigns(roadLinks, newAsset, finalRoadLinks)
    Seq()
  }

  def baseProcess(trafficSigns: Seq[PersistedTrafficSign], roadLinks: Seq[VVHRoadlink], actualRoadLink: VVHRoadlink, generatedTrafficSigns: Set[TrafficSignToGenerateLinear] ) : Set[TrafficSignToGenerateLinear] = {
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
      if(adjRoadLinks.nonEmpty)
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
      case Some(pair)  =>
        if (pair.linkId == sign.linkId) {
          val orderedMValue = Seq(sign.mValue, pair.mValue).sorted

          TrafficSignToGenerateLinear(currentRoadLink, Prohibitions(prohibitionValue), SideCode.apply(sign.validityDirection), orderedMValue.head, orderedMValue.last, Seq(sign.id))
        } else {
          val (first, _) = GeometryUtils.geometryEndpoints(currentRoadLink.geometry)
          val length = GeometryUtils.geometryLength(currentRoadLink.geometry)
          val (starMeasure, endMeasure) = if (!GeometryUtils.areAdjacent(pointOfInterest, first)) (0.toDouble, pair.mValue) else (pair.mValue, length)

          TrafficSignToGenerateLinear(currentRoadLink, Prohibitions(prohibitionValue),  SideCode.apply(sign.validityDirection), starMeasure, endMeasure, Seq(sign.id))
        }
      case _=>
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

  def fetchTrafficSignRelatedAssets(trafficSignId: Long, withTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val assets = if(withTransaction) {
      withDynTransaction {
        val assetIds = dao.getConnectedAssetFromTrafficSign(trafficSignId)
        dao.fetchProhibitionsByIds(Prohibition.typeId, assetIds.toSet)
      }
    } else {
      val assetIds = dao.getConnectedAssetFromTrafficSign(trafficSignId)
      dao.fetchProhibitionsByIds(Prohibition.typeId, assetIds.toSet)
    }
    assets
  }

  def deleteOrUpdateAssetBasedOnSign(trafficSign: PersistedTrafficSign, username: Option[String] = None, withTransaction: Boolean = true) : Unit = {


    val trafficSignRelatedAssets = fetchTrafficSignRelatedAssets(trafficSign.id)
    val trProhibitionValue = Seq(prohibitionService.createValue(trafficSign))

    val (toDelete, toUpdate) = trafficSignRelatedAssets.partition{ asset =>
      asset.value.get.asInstanceOf[Prohibitions].equals(Prohibitions(trProhibitionValue))
    }

    if(toDelete.nonEmpty)
      expire(toDelete.map(_.id), username.getOrElse(""))

    val groupedAssetsToUpdate = toUpdate.map { asset =>
      (asset.id, asset.value.get.asInstanceOf[Prohibitions].prohibitions.diff(trProhibitionValue))
    }.groupBy(_._2)

    groupedAssetsToUpdate.values.map { value =>
      update(value.map(_._1), Prohibitions(value.flatMap(_._2)), username.getOrElse(""))
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

  def createLinearAssetAccordingTrafficSigns(roadLinks: Seq[VVHRoadlink], futureLinears: Seq[TrafficSignToGenerateLinear], finalRoadLinks: Seq[VVHRoadlink]) {
    val oldAssets = Seq.empty[PersistedLinearAsset]
    //    val oldAssets = getPersistedAssetsByLinkIds(Prohibition.typeId, roadLinksWithSameRoadName.map(_.linkId))

    val possibleAssets: Seq[TrafficSignToGenerateLinear] = oldAssets.filter(_.value.isDefined).flatMap { oldAsset =>
      if (oldAsset.sideCode == SideCode.BothDirections.value)
        Seq(TrafficSignToGenerateLinear(roadLinks.find(_.linkId == oldAsset.linkId).get, oldAsset.value.get, SideCode.apply(oldAsset.sideCode), oldAsset.startMeasure, oldAsset.endMeasure, Seq()),
          TrafficSignToGenerateLinear(roadLinks.find(_.linkId == oldAsset.linkId).get, oldAsset.value.get, SideCode.apply(oldAsset.sideCode), oldAsset.startMeasure, oldAsset.endMeasure, Seq()))
      else
        Seq(TrafficSignToGenerateLinear(roadLinks.find(_.linkId == oldAsset.linkId).get, oldAsset.value.get, SideCode.apply(oldAsset.sideCode), oldAsset.startMeasure, oldAsset.endMeasure, Seq()))
    } ++ futureLinears

    val allSegmentsByLinkId = possibleAssets.map(fl => (fl.roadLink.linkId, fl.startMeasure, fl.endMeasure)).groupBy(_._1)

    val allSegments = allSegmentsByLinkId.keys.flatMap { linkId =>
      val minLengthToZip = 0.01
      val segmentsPoints = allSegmentsByLinkId(linkId).flatMap(fl => Seq(fl._2, fl._3)).distinct.sorted
      val segments = segmentsPoints.zip(segmentsPoints.tail).filterNot { piece => (piece._2 - piece._1) < minLengthToZip }

      segments.map { case (startMeasurePOI, endMeasurePOI) =>
        val assetOnRoadLink = possibleAssets.filter(_.roadLink.linkId == linkId)
        val info = assetOnRoadLink.filter(asset => asset.startMeasure <= startMeasurePOI && asset.endMeasure >= endMeasurePOI).map(info => (info.value.asInstanceOf[Prohibitions].prohibitions, info.signId))
        val asset = assetOnRoadLink.find(asset => asset.startMeasure <= startMeasurePOI && asset.endMeasure >= endMeasurePOI).get

        TrafficSignToGenerateLinear(asset.roadLink, Prohibitions(info.flatMap(_._1)), asset.sideCode, startMeasurePOI, endMeasurePOI, info.flatMap(_._2))
      }
    }

    val (assetBothSide, assetOneSide) = combineSegments(allSegments.toSeq)

    convertOneSideCode(assetOneSide, finalRoadLinks) ++ assetBothSide.toSeq
  }

  def combineSegments(allSegments: Seq[TrafficSignToGenerateLinear]): (Set[TrafficSignToGenerateLinear], Set[TrafficSignToGenerateLinear]) = {
    /*    val (assetToward, assetAgainst) = allSegments.partition(_.sideCode == SideCode.TowardsDigitizing)
        (assetToward.map { toward =>
          if (assetAgainst.exists { against => toward.startMeasure == against.startMeasure && toward.endMeasure == against.endMeasure && toward.value.equals(against.value) })
            toward.copy(sideCode = BothDirections)
          else
            toward
        } ++ assetAgainst.map { against =>
          if (assetAgainst.exists { toward => toward.startMeasure == against.startMeasure && toward.endMeasure == against.endMeasure && toward.value.equals(against.value) })
            against.copy(sideCode = BothDirections)
          else
            against
        }).toSet.partition(_.sideCode == BothDirections)*/

    val (assetToward, assetAgainst) = allSegments.partition(_.sideCode == SideCode.TowardsDigitizing)
    (assetToward.map { toward =>
      if (assetAgainst.exists { against => toward.startMeasure == against.startMeasure && toward.endMeasure == against.endMeasure && toward.value.equals(against.value) })
        toward.copy(sideCode = BothDirections)
      else
        toward
    } ++ assetAgainst.filterNot { against =>
      assetAgainst.exists { toward => toward.startMeasure == against.startMeasure && toward.endMeasure == against.endMeasure && toward.value.equals(against.value)}
    }).toSet.partition(_.sideCode == BothDirections)
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

    val lastAssets = possibleEndRoad.partition { a =>
      finalRoadLinks.exists(finalRoad => a.roadLink.linkId == finalRoad.linkId &&
        Math.abs(a.endMeasure - GeometryUtils.geometryLength(finalRoad.geometry)) < 0.01 &&
        Math.abs(a.startMeasure - 0) < 0.01)
    }

    if (lastAssets._1.nonEmpty) {
      Seq(lastAssets._1, lastAssets._2).flatten.map { asset =>
        if (roadLinkService.getAdjacent(asset.roadLink.linkId).size < 2)
          asset.copy(sideCode = BothDirections)
        else
          asset
      }
    } else
      Seq(lastAssets._1, lastAssets._2).flatten ++ assetInOneTrafficDirectionLink
  }

  private def getOpositePoint(geometry: Seq[Point], point: Point) = {
    val (headPoint, lastPoint) = GeometryUtils.geometryEndpoints(geometry)
    if (GeometryUtils.areAdjacent(headPoint, point))
      lastPoint
    else
      headPoint
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
        trafficSignsToTransform.foreach { t =>
          val tsCreatedDate = t.createdBy
          val tsModifiedDate = t.modifiedAt

          if (t.expired) {
            // Delete actions
            println("********************************************")
            println("DETECTED AS A DELETED")
            println("********************************************")

          } else if (tsCreatedDate.nonEmpty && tsModifiedDate.isEmpty) {
            //Create actions
            println(s"Start creating prohibition according the traffic sign with ID: ${t.id}")

            val signType = trafficSignService.getProperty(t, trafficSignService.typePublicId).get.propertyValue.toInt
            val additionalPanel = trafficSignService.getAllProperties(t, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
            trafficSignManager.createAssets(TrafficSignInfo(t.id, t.linkId, t.validityDirection, signType, t.mValue, roadLinks.find(_.linkId == t.linkId).head, additionalPanel), false)

            println(s"Prohibition related with traffic sign with ID: ${t.id} created")
            println("")

          } else {
            //Modify actions
            println("********************************************")
            println("DETECTED AS A MODIFIED")
            println("********************************************")
          }

        }
      }
    }
    println("")
    println("Complete at time: " + DateTime.now())
  }

  def main(args: Array[String]): Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("*************************************************************************************")
      println("YOU ARE RUNNING FIXTURE RESET AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    createLinearAssetUsingTrafficSigns()
  }

}
