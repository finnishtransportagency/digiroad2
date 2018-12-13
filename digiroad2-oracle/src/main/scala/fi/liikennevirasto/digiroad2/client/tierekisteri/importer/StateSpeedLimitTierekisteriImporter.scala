package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.{Motorway => _, _}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

class StateSpeedLimitTierekisteriImporter extends TierekisteriAssetImporterOperations {
  override def typeId: Int = StateSpeedLimit.typeId
  override def assetName: String = "stateSpeedLimit"
  override type TierekisteriClientType = TierekisteriTrafficSignSpeedLimitClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]) = throw new UnsupportedOperationException("Not supported method")

  override val tierekisteriClient = new TierekisteriTrafficSignSpeedLimitClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
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

  private def getSpeedLimitValue(assetInfo: TrAssetInfo, linkType:  Option[LinkType]): Option[Int] = {
    def getSpeedLimitValueByLinkType(linkType:  Option[LinkType]): Option[Int] = {
      linkType.getOrElse(UnknownLinkType) match {
        case Motorway =>
          Some(defaultMotorwaySpeedLimit)
        case MultipleCarriageway | SingleCarriageway | Freeway =>
          Some(defaultCarriageOrFreewaySpeedLimit)
        case _ =>
          None
      }
    }
    val trAsset = assetInfo.trAsset.asInstanceOf[TierekisteriAssetData]
    trAsset.assetType match {
      case SpeedLimitSign  =>
        toInt(trAsset.assetValue)
      case EndSpeedLimit =>
        Some(defaultSpeedLimit)
      case SpeedLimitZone  =>
        toInt(trAsset.assetValue)
      case EndSpeedLimitZone =>
        Some(defaultSpeedLimit)
      case UrbanArea =>
        Some(urbanAreaSpeedLimit)
      case EndUrbanArea =>
        Some(defaultSpeedLimit)
      case TelematicSpeedLimit =>
        getSpeedLimitValueByLinkType(linkType) match {
          case None => getSpeedLimitValueByLinkType(assetInfo.linkType)
          case value => value
        }
      case _ =>
        None
    }
  }

  private def filterSectionTrafficSigns(trafficSigns: Seq[TierekisteriAssetData], roadAddress: ViiteRoadAddress, roadSide: RoadSide): Seq[TierekisteriAssetData] ={
    val signs = trafficSigns.filter(trSign => trSign.assetType.group == TrafficSignTypeGroup.SpeedLimits &&
      trSign.endRoadPartNumber == roadAddress.roadPartNumber && trSign.startAddressMValue >= roadAddress.startAddrMValue &&
      trSign.startAddressMValue <= roadAddress.endAddrMValue && (trSign.roadSide == RoadSide.Left || trSign.roadSide == RoadSide.Right))

    val currentTrack = roadSide match {
      case RoadSide.Left => Track.LeftSide
      case RoadSide.Right => Track.RightSide
      case _ => Track.Unknown
    }

    roadAddress.track match {
      case Track.Combined =>
        signs.filter(trSign => (trSign.track == Track.Combined && roadSide == trSign.roadSide) || trSign.track == currentTrack )
      case Track.LeftSide =>
        signs.filter(trSign => trSign.track == Track.LeftSide)
      case Track.RightSide =>
        signs.filter(trSign => trSign.track == Track.RightSide)
      case _ =>
        Seq()
    }
  }

  def generateUrbanTrafficSign(trAsset: TierekisteriUrbanAreaData, roadSide: RoadSide): Seq[TierekisteriTrafficSignData]= {

    val (startAddress, endAddress) = roadSide match{
      case RoadSide.Right => (trAsset.startAddressMValue, trAsset.endAddressMValue)
      case _ => (trAsset.endAddressMValue, trAsset.startAddressMValue)
    }

    if (trAsset.assetValue == notUrbanArea) {
      Seq(
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, startAddress, startAddress, roadSide, EndUrbanArea, defaultSpeedLimit.toString),
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, endAddress, endAddress, roadSide, EndSpeedLimit, defaultSpeedLimit.toString)
      )
    } else {
      Seq(
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, startAddress, startAddress, roadSide, UrbanArea, urbanAreaSpeedLimit.toString),
        TierekisteriTrafficSignData(trAsset.roadNumber, trAsset.startRoadPartNumber, trAsset.endRoadPartNumber, trAsset.track, endAddress, endAddress, roadSide, EndUrbanArea, defaultSpeedLimit.toString)
      )
    }
  }


  protected def createUrbanTrafficSign(roadLink: Option[VVHRoadlink], trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData],
                                       addressSection: AddressSection, roadAddress: ViiteRoadAddress,
                                       roadSide: RoadSide): Option[TrAssetInfo] = {

    def getDefaultTrTrafficSign(addressSection: AddressSection, roadLink: Option[VVHRoadlink]): Option[TrAssetInfo] ={
      Some(TrAssetInfo(TierekisteriTrafficSignData(addressSection.roadNumber, addressSection.roadPartNumber, addressSection.roadPartNumber, addressSection.track, addressSection.startAddressMValue, addressSection.startAddressMValue, roadSide, EndUrbanArea, defaultSpeedLimit.toString), roadLink))
    }

    val trUrbanAssets = trUrbanAreaAssets.filter( ua => ua.startRoadPartNumber == addressSection.roadPartNumber && ( ua.track == addressSection.track || ua.track == Track.Combined))
    val assets = trUrbanAssets.flatMap{ trAsset => generateUrbanTrafficSign(trAsset, roadSide) }

    splitRoadAddressSectionBySigns(assets, roadAddress, roadSide).foldLeft(None: Option[TrAssetInfo]){
      case (previousTrAsset, (addressSection: AddressSection, beginTrAsset)) =>
        val currentTrAssetSign = (beginTrAsset match {
          case Some(info) => Some(TrAssetInfo(info, roadLink))
          case _ => None
        }).orElse(previousTrAsset) match {
          case Some(asset) => Some(asset)
          case None => getDefaultTrTrafficSign(addressSection, roadLink)
        }
        createSpeedLimit(roadAddress, addressSection, currentTrAssetSign, roadLink, None)
        currentTrAssetSign
    }
  }

  protected def createSpeedLimit(roadAddress: ViiteRoadAddress, addressSection: AddressSection, trAssetOption: Option[TrAssetInfo]
                                 ,roadLinkOption: Option[VVHRoadlink], linkType: Option[LinkType]): Unit = {
    roadLinkOption.foreach {
      roadLink =>
        calculateMeasures(roadAddress, addressSection).foreach {
          measures =>
            trAssetOption.foreach {
              assetInfo =>
                createSpeedLimitAsset(roadLink, roadAddress, addressSection, measures, assetInfo, linkType)
            }
        }
    }
  }
  protected def createSpeedLimitAsset(roadLink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TrAssetInfo, linkType: Option[LinkType]) = {
    val speedLimit = getSpeedLimitValue(trAssetData, linkType)
    val trAsset = trAssetData.trAsset.asInstanceOf[TierekisteriAssetData]
    if (measures.startMeasure != measures.endMeasure) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, roadLink.linkId, false, getSideCode(roadAddress, trAsset.track, trAsset.roadSide).value,
        measures, s"batch_process_$assetName", vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink.linkSource.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, speedLimit.getOrElse(-1))
      println(s"Created OTH Speed Limit assets for road ${roadLink.linkId} from TR data with assetId $assetId")
    }
  }

  protected def generateOneSideSpeedLimits(roadNumber: Long, roadSide: RoadSide, trAssets : Seq[TierekisteriAssetData], trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData], existingRoadAddresses: Seq[ViiteRoadAddress]): Unit = {
    def getViiteRoadAddress(roadSide: RoadSide) = {
      val allViiteRoadAddress = roadSide match {
        case RoadSide.Left =>
          filterRoadAddressByNumberAndTracks(existingRoadAddresses, roadNumber, Seq(Track.LeftSide, Track.Combined)).sortBy(r => (-r._1.roadPartNumber, -r._1.startAddrMValue))
        case _ =>
          filterRoadAddressByNumberAndTracks(existingRoadAddresses, roadNumber, Seq(Track.RightSide, Track.Combined)).sortBy(r => (r._1.roadPartNumber, r._1.startAddrMValue))
      }
      val linkTypes = roadLinkService.getAllLinkType(allViiteRoadAddress.flatMap(_._2).map(_.linkId))

      allViiteRoadAddress.map {
        case (roadAddress: ViiteRoadAddress, roadLink: Option[VVHRoadlink]) =>
          val linkType = roadLink match {
            case Some(road) => linkTypes.get(road.linkId).map(_.head._2)
            case _ => None
          }
          (roadAddress, roadLink, linkType)
      }
    }

    getViiteRoadAddress(roadSide).foldLeft[Option[TrAssetInfo]](None){
      case (trAsset, (roadAddress: ViiteRoadAddress, roadLink: Option[VVHRoadlink], linkType)) =>
        splitRoadAddressSectionBySigns(trAssets, roadAddress, roadSide).foldLeft(trAsset){
          case (previousTrAsset, (addressSection: AddressSection, beginTrAsset)) =>

            (beginTrAsset match {
              case Some(info) => Some(TrAssetInfo(info, roadLink, linkType))
              case _ => None
            }).orElse(previousTrAsset) match {
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
    val municipalities = getAllMunicipalities

    municipalities.foreach { municipality =>
      withDynTransaction{
        expireAssets(municipality, Some(State))
      }
    }

    val roadNumbers = getAllViiteRoadNumbers

    roadNumbers.foreach {
      roadNumber =>
        withDynTransaction{
          //Get Urban Areas from Tierekisteri
          val trUrbanAreaAssets = tierekisteriClientUA.fetchActiveAssetData(roadNumber)
          //Get all TelematicSpeedLimit
          val trTelematicSpeedLimitAssets = tierekisteriClientTelematicSpeedLimit.fetchActiveAssetData(roadNumber)
          val trAssets = getAllTierekisteriAssets(roadNumber).filter(_.assetType.group == TrafficSignTypeGroup.SpeedLimits) ++ trTelematicSpeedLimitAssets
          //Get all the existing road address for the road number
          val roadAddresses = roadAddressService.getAllByRoadNumber(roadNumber)
          //Generate all speed limits of the right side of the road
          generateOneSideSpeedLimits(roadNumber, RoadSide.Right, trAssets, trUrbanAreaAssets, roadAddresses)
          //Generate all speed limits of the left side of the road
          generateOneSideSpeedLimits(roadNumber, RoadSide.Left, trAssets, trUrbanAreaAssets, roadAddresses)
        }
    }
  }

  override def updateAssets(lastExecution: DateTime): Unit = {
    val roadNumbers = getAllViiteRoadNumbers

    roadNumbers.foreach {
      roadNumber =>
        withDynTransaction {
          println("\nExpiring Speed Limits at Road Number: " + roadNumber)

          //Fetch asset changes from Tierekister
          val trHistoryAssets = getAllTierekisteriHistoryAssets(roadNumber, lastExecution) ++
            tierekisteriClientTelematicSpeedLimit.fetchHistoryAssetData(roadNumber, Some(lastExecution))
          val trHistoryAssetsUA = tierekisteriClientUA.fetchHistoryAssetData(roadNumber, Some(lastExecution))

          if (trHistoryAssets.nonEmpty || trHistoryAssetsUA.nonEmpty) {
            val expiredAssetTracks = (trHistoryAssets.map(_.track) ++ (if (trHistoryAssetsUA.nonEmpty) Seq(Track.RightSide, Track.LeftSide) else Seq())).distinct
            val roadSideProcessed = (trHistoryAssets.map(_.roadSide) ++ (if (trHistoryAssetsUA.nonEmpty) Seq(RoadSide.Right, RoadSide.Left) else Seq())).distinct

            //Expire all the assets that have changes in tierekisteri
            val roadAddress = roadAddressService.getAllByRoadNumber(roadNumber)
            val roadAddressLink = filterRoadAddressByNumberAndTracks(roadAddress, roadNumber, expiredAssetTracks)
            expireAssets(roadAddressLink.map(_._1.linkId))

            //Get Telematic Speed Screen from Tierekisteri
            val trTelematicSpeedAssets = tierekisteriClientTelematicSpeedLimit.fetchActiveAssetData(roadNumber)
            //Get Urban Areas from Tierekisteri
            val trUrbanAreaAssets = tierekisteriClientUA.fetchActiveAssetData(roadNumber)
            val trAssets = getAllTierekisteriAssets(roadNumber) ++ trTelematicSpeedAssets

            //Creates assets on side Expired
            roadSideProcessed.foreach {
              expiredAssetSide =>
                val trAssetBySide = trAssets.filter(_.track.value == expiredAssetSide.value)
                val trUrbanAreaAssetsBySide = trUrbanAreaAssets.filter(_.track.value == expiredAssetSide.value)
                expiredAssetSide match {
                  case RoadSide.Right =>
                    println("\nCreate Speed Limits at Road Number: " + roadNumber + ", on Side: " + RoadSide.Right.toString())
                    //Generate all speed limits of the right side of the road
                    generateOneSideSpeedLimits(roadNumber, RoadSide.Right, trAssetBySide, trUrbanAreaAssetsBySide, roadAddress)
                  case RoadSide.Left =>
                    println("\nCreate Speed Limits at Road Number: " + roadNumber + ", on Side: " + RoadSide.Left.toString())
                    //Generate all speed limits of the left side of the road
                    generateOneSideSpeedLimits(roadNumber, RoadSide.Left, trAssetBySide, trUrbanAreaAssetsBySide, roadAddress)
                  case _ => None
                }
            }
          }
        }
    }
  }
}