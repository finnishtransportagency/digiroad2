package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignType, TrafficSignTypeGroup}
import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

class StateSpeedLimitTierekisteriImporter extends TierekisteriAssetImporterOperations {
  override def typeId: Int = 310
  override def assetName: String = "speedLimitState"
  override type TierekisteriClientType = TierekisteriTrafficSignAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData) = throw new UnsupportedOperationException("Not supported method")

  override val tierekisteriClient = new TierekisteriTrafficSignAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  lazy val linearAssetService: LinearAssetService = new LinearAssetService(roadLinkService, eventbus)

  lazy val tierekisteriClientUA: TierekisteriUrbanAreaClient = {
    new TierekisteriUrbanAreaClient(dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      dr2properties.getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  lazy val tierekisteriClientTelematicSpeedLimit: TierekisteriTelematicSpeedLimitClient = {
    new TierekisteriTelematicSpeedLimitClient(dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      dr2properties.getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  private val urbanAreaSpeedLimit = 50
  private val defaultSpeedLimit = 80
  private val defaultMotorwaySpeedLimit = 120
  private val defaultCarriageOrFreewaySpeedLimit = 100
  private val notUrbanArea = "9"

  protected override def filterViiteRoadAddress(roadLink: VVHRoadlink): Boolean = {
    roadLink.featureClass != FeatureClass.CycleOrPedestrianPath
  }

  private def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

  private def getSpeedLimitValue(trAsset: TierekisteriAssetData, linkType: LinkType) = {
    trAsset.assetType.trafficSignType match {
      case TrafficSignType.SpeedLimit  =>
        toInt(trAsset.assetValue)
      case TrafficSignType.EndSpeedLimit =>
        Some(defaultSpeedLimit)
      case TrafficSignType.SpeedLimitZone  =>
        toInt(trAsset.assetValue)
      case TrafficSignType.EndSpeedLimitZone =>
        Some(defaultSpeedLimit)
      case TrafficSignType.UrbanArea =>
        Some(urbanAreaSpeedLimit)
      case TrafficSignType.EndUrbanArea =>
        Some(defaultSpeedLimit)
      case TrafficSignType.TelematicSpeedLimit =>
        linkType match {
          case Motorway =>
            Some(defaultMotorwaySpeedLimit)
          case MultipleCarriageway | SingleCarriageway | Freeway =>
            Some(defaultCarriageOrFreewaySpeedLimit)
          case _ =>
            None
        }
      case _ =>
        None
    }
  }

  private def filterSectionTrafficSigns(trafficSigns: Seq[TierekisteriAssetData], roadAddress: ViiteRoadAddress, roadSide: RoadSide): Seq[TierekisteriAssetData] ={
    trafficSigns.filter(trSign => trSign.assetType.trafficSignType.group == TrafficSignTypeGroup.SpeedLimits &&
      trSign.endRoadPartNumber == roadAddress.roadPartNumber && trSign.startAddressMValue >= roadAddress.startAddrMValue &&
      trSign.startAddressMValue <= roadAddress.endAddrMValue && roadSide == trSign.roadSide)
  }

  def generateUrbanTrafficSign(trAsset: TierekisteriUrbanAreaData, roadSide: RoadSide): Seq[TierekisteriTrafficSignData]= {

    val (startAddress, endAddress) = roadSide match{
      case RoadSide.Right => (trAsset.startAddressMValue, trAsset.endAddressMValue)
      case _ => (trAsset.endAddressMValue, trAsset.startAddressMValue)
    }

    if (trAsset.assetValue == notUrbanArea) {
      Seq(
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, startAddress, startAddress, roadSide, TRTrafficSignType.EndUrbanArea, defaultSpeedLimit.toString),
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, endAddress, endAddress, roadSide, TRTrafficSignType.EndSpeedLimit, defaultSpeedLimit.toString)
      )
    } else {
      Seq(
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, startAddress, startAddress, roadSide, TRTrafficSignType.UrbanArea, urbanAreaSpeedLimit.toString),
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, endAddress, endAddress, roadSide, TRTrafficSignType.EndUrbanArea, defaultSpeedLimit.toString)
      )
    }
  }


  protected def createUrbanTrafficSign(roadLink: Option[VVHRoadlink], trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData],
                                       addressSection: AddressSection, roadAddress: ViiteRoadAddress,
                                       roadSide: RoadSide): Option[TierekisteriAssetData] = {

    def getDefaultTrTrafficSign(addressSection: AddressSection): Option[TierekisteriAssetData] ={
      Some(TierekisteriTrafficSignData(addressSection.roadNumber, addressSection.roadPartNumber, addressSection.roadPartNumber, addressSection.track, addressSection.startAddressMValue, addressSection.startAddressMValue, roadSide, TRTrafficSignType.EndUrbanArea, defaultSpeedLimit.toString))
    }

    val trUrbanAssets = trUrbanAreaAssets.filter( ua => ua.startRoadPartNumber == addressSection.roadPartNumber && ( ua.track == addressSection.track || ua.track == Track.Combined))
    val assets = trUrbanAssets.flatMap{ trAsset => generateUrbanTrafficSign(trAsset, roadSide) }

    splitRoadAddressSectionBySigns(assets, roadAddress, roadSide).foldLeft(None: Option[TierekisteriAssetData]){
      case (previousTrAsset, (addressSection: AddressSection, beginTrAsset)) =>
        val currentTrAssetSign = beginTrAsset.orElse(previousTrAsset) match {
          case Some(asset) => Some(asset)
          case None => getDefaultTrTrafficSign(addressSection)
        }
        createSpeedLimit(roadAddress, addressSection, currentTrAssetSign, roadLink, UnknownLinkType)
        currentTrAssetSign
    }
  }

  protected def createSpeedLimit(roadAddress: ViiteRoadAddress, addressSection: AddressSection, trAssetOption: Option[TierekisteriAssetData],
                                 roadLinkOption: Option[VVHRoadlink], linkType: LinkType): Unit = {
    roadLinkOption.foreach {
      roadLink =>
        calculateMeasures(roadAddress, addressSection).foreach {
          measures =>
            trAssetOption.foreach {
              trAsset =>
                createSpeedLimitAsset(roadLink, roadAddress, addressSection, measures, trAsset, linkType)
            }
        }
    }
  }

  private def generateOneSideSpeedLimits(roadNumber: Long, roadSide: RoadSide, trAssets : Seq[TierekisteriAssetData], trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData]): Unit = {
    def getViiteRoadAddress(roadSide: RoadSide) = {
      roadSide match {
        case RoadSide.Left =>
          val allViiteRoadAddress = getAllViiteRoadAddress(roadNumber, Seq(Track.LeftSide, Track.Combined)).sortBy(r => (-r._1.roadPartNumber, -r._1.startAddrMValue))
          val linkTypes = roadLinkService.getAllLinkType(allViiteRoadAddress.flatMap(_._2).map(_.linkId))

          allViiteRoadAddress.map {
            case (roadAddress: ViiteRoadAddress, roadLink: Option[VVHRoadlink]) =>
              (roadAddress, roadLink, roadLink match {
                case Some(road) => LinkType.apply(linkTypes.get(road.linkId).map(_.head._2).getOrElse(99))
                case _ => UnknownLinkType
              }
              )
          }
        case _ =>
          val allViiteRoadAddress = getAllViiteRoadAddress(roadNumber, Seq(Track.RightSide, Track.Combined)).sortBy(r => (r._1.roadPartNumber, r._1.startAddrMValue))
          val linkTypes = roadLinkService.getAllLinkType(allViiteRoadAddress.flatMap(_._2).map(_.linkId))

          allViiteRoadAddress.map {
            case (roadAddress: ViiteRoadAddress, roadLink: Option[VVHRoadlink]) =>
              (roadAddress, roadLink, roadLink match {
                case Some(road) => LinkType.apply(linkTypes.get(road.linkId).map(_.head._2).getOrElse(99))
                case _ => UnknownLinkType
              }
              )
          }
      }
    }

    getViiteRoadAddress(roadSide).foldLeft[Option[tierekisteriClient.TierekisteriType]](None){
      case (trAsset, (roadAddress: ViiteRoadAddress, roadLink: Option[VVHRoadlink], linkType)) =>
        splitRoadAddressSectionBySigns(trAssets, roadAddress, roadSide).foldLeft(trAsset){
          case (previousTrAsset, (addressSection: AddressSection, beginTrAsset)) =>
            beginTrAsset.orElse(previousTrAsset) match {
              case Some(asset) => createSpeedLimit(roadAddress, addressSection, Some(asset), roadLink, linkType)
                Some(asset)
              case None => createUrbanTrafficSign(roadLink, trUrbanAreaAssets, addressSection, roadAddress, roadSide)
            }
        }
    }
  }

  protected def splitRoadAddressSectionBySigns(trAssets: Seq[TierekisteriAssetData], ra: ViiteRoadAddress, roadSide: RoadSide): Seq[(AddressSection, Option[TierekisteriAssetData])] = {
    val sectionAssets = filterSectionTrafficSigns(trAssets, ra, roadSide)
    if(sectionAssets.isEmpty) {
      Seq((AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, ra.startAddrMValue, Some(ra.endAddrMValue)), None))
    }
    else{
      roadSide match {
        case RoadSide.Right =>
          val sortedAssets = sectionAssets.sortBy(_.startAddressMValue)
          val first = Seq((AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, ra.startAddrMValue, Some(sortedAssets.head.startAddressMValue)), None))
          val last = Seq((AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, sortedAssets.last.startAddressMValue, Some(ra.endAddrMValue)), Some(sortedAssets.last)))
          val intermediate = sortedAssets.zip(sortedAssets.tail).map{
            case (firstAsset, lastAsset) =>
              (AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, firstAsset.startAddressMValue, Some(lastAsset.startAddressMValue)), Some(firstAsset))
          }
          first ++ intermediate ++ last
        case _ =>
          val sortedAssets = sectionAssets.sortBy(- _.startAddressMValue)
          val first = Seq((AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, sortedAssets.head.startAddressMValue, Some(ra.endAddrMValue)), None))
          val last = Seq((AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, ra.startAddrMValue, Some(sortedAssets.last.startAddressMValue)), Some(sortedAssets.last)))
          val intermediate = sortedAssets.zip(sortedAssets.tail).map{
            case (firstAsset, lastAsset) =>
              (AddressSection(ra.roadNumber, ra.roadPartNumber, ra.track, lastAsset.startAddressMValue, Some(firstAsset.startAddressMValue)), Some(firstAsset))
          }
          first ++ intermediate ++ last
      }
    }
  }

  override def importAssets(): Unit = {
    //Expire all asset in state roads in all the municipalities
    val municipalities = getAllMunicipalities()

    municipalities.foreach { municipality =>
      withDynTransaction{
        expireAssets(municipality, Some(State))
      }
    }

    val roadNumbers = getAllViiteRoadNumbers()

    roadNumbers.foreach {
      roadNumber =>
        withDynTransaction{
          //Get Urban Areas from Tierekisteri
          val trUrbanAreaAssets = tierekisteriClientUA.fetchActiveAssetData(roadNumber)
          //Get all TelematicSpeedLimit
          val trTelematicSpeedLimitAssets = tierekisteriClientTelematicSpeedLimit.fetchActiveAssetData(roadNumber)
          val trAssets = getAllTierekisteriAssets(roadNumber) ++ trTelematicSpeedLimitAssets
          //Generate all speed limits of the right side of the road
          generateOneSideSpeedLimits(roadNumber, RoadSide.Right, trAssets, trUrbanAreaAssets)
          //Generate all speed limits of the left side of the road
          generateOneSideSpeedLimits(roadNumber, RoadSide.Left, trAssets, trUrbanAreaAssets)
        }
    }
  }

  protected def createSpeedLimitAsset(roadLink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData, linkType: LinkType) = {
    val speedLimit = getSpeedLimitValue(trAssetData, linkType)
    if (measures.startMeasure != measures.endMeasure) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, roadLink.linkId, false, getSideCode(roadAddress, trAssetData.track, trAssetData.roadSide).value,
        measures, s"batch_process_$assetName", vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink.linkSource.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, speedLimit.getOrElse(-1))
      println(s"Created OTH Speed Limit assets for road ${roadLink.linkId} from TR data with assetId $assetId")
    }
  }

  override def updateAssets(lastExecution: DateTime): Unit = {
    val roadNumbers = getAllViiteRoadNumbers()

    roadNumbers.foreach {
      roadNumber =>
        withDynTransaction {
          println("\nExpiring Speed Limits at Road Number: " + roadNumber)

          //Fetch asset changes from Tierekisteri
          val trHistoryAssets = getAllTierekisteriHistoryAssets(roadNumber, lastExecution)

          //Expire all the assets that have changes in tierekisteri
          val expiredAssetSides = trHistoryAssets.foldLeft(Seq.empty[RoadSide]) {
            case (roadSideProcessed, trAssetData) =>
              //If the road side was already process we ignore it
              if (roadSideProcessed.contains(trAssetData.roadSide)) {
                roadSideProcessed
              } else {
                //Get all existing road address in viite and expire all the assets on top of this roads
                val roadAddressLink = getAllViiteRoadAddress(trAssetData.roadNumber, Seq(trAssetData.track))
                expireAssets(roadAddressLink.map(_._1.linkId))
                roadSideProcessed ++ Seq(trAssetData.roadSide)
              }
          }

          //Creates assets on side Expired
          expiredAssetSides.foreach {
            expiredAssetSide =>
              val trTelematicSpeedAssets = tierekisteriClientTelematicSpeedLimit.fetchActiveAssetData(roadNumber).filter(_.track.value == expiredAssetSide.value)
              //Get Urban Areas from Tierekisteri
              val trUrbanAreaAssets = tierekisteriClientUA.fetchActiveAssetData(roadNumber).filter(_.track.value == expiredAssetSide.value)
              val trAssets = getAllTierekisteriAssets(roadNumber).filter(_.roadSide == expiredAssetSide) ++ trTelematicSpeedAssets
              expiredAssetSide match {
                case RoadSide.Right =>
                  println("\nCreate Speed Limits at Road Number: " + roadNumber + ", on Side: " + RoadSide.Right.toString())
                  //Generate all speed limits of the right side of the road
                  generateOneSideSpeedLimits(roadNumber, RoadSide.Right, trAssets, trUrbanAreaAssets)
                case RoadSide.Left =>
                  println("\nCreate Speed Limits at Road Number: " + roadNumber + ", on Side: " + RoadSide.Left.toString())
                  //Generate all speed limits of the left side of the road
                  generateOneSideSpeedLimits(roadNumber, RoadSide.Left, trAssets, trUrbanAreaAssets)
                case _ => None
              }
          }
        }
    }
  }
}

