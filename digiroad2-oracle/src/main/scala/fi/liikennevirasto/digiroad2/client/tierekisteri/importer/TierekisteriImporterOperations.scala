package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriAssetData, TierekisteriAssetDataClient}
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.service.{RoadAddressesService, RoadLinkService}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

case class AddressSection(roadNumber: Long, roadPartNumber: Long, track: Track, startAddressMValue: Long, endAddressMValue: Option[Long])
case class TrAssetInfo(trAsset: TierekisteriAssetData, roadLink: Option[VVHRoadlink], linkType: Option[LinkType] = None)

trait TierekisteriImporterOperations {

  val eventbus = new DummyEventBus
  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  lazy val roadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = { new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint")) }

  lazy val assetDao: OracleAssetDao = new OracleAssetDao
  lazy val roadAddressService : RoadAddressesService = new RoadAddressesService(viiteClient)
  lazy val viiteClient: SearchViiteClient = { new SearchViiteClient(getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build()) }
  lazy val municipalityDao: MunicipalityDao = new MunicipalityDao

  def typeId: Int

  def withDynSession[T](f: => T): T

  def withDynTransaction[T](f: => T): T

  def assetName: String

  protected def getProperty(name: String) = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }

  protected def getSideCode(roadAddress: ViiteRoadAddress, trAssetTrack: Track, trAssetRoadSide: RoadSide): SideCode = {
    val trTrack = trAssetTrack match {
      case Track.Combined =>
        trAssetRoadSide match {
          case RoadSide.Right => Track.RightSide
          case _ => Track.LeftSide
        }
      case _ =>
        trAssetTrack
    }

    trTrack match {
      case Track.RightSide => roadAddress.sideCode
      case Track.LeftSide => roadAddress.sideCode match {
        case TowardsDigitizing => SideCode.AgainstDigitizing
        case AgainstDigitizing => SideCode.TowardsDigitizing
        case _ => SideCode.BothDirections
      }
      case _ => SideCode.BothDirections
    }
  }

  protected def calculateMeasures(roadAddress: ViiteRoadAddress, section: AddressSection): Option[Measures] = {
    val startAddrMValueCandidate = calculateStartLrmByAddress(roadAddress, section)
    val endAddrMValueCandidate = calculateEndLrmByAddress(roadAddress, section)

    (startAddrMValueCandidate, endAddrMValueCandidate) match {
      case (Some(startAddrMValue), Some(endAddrMValue)) if(startAddrMValue <= endAddrMValue) => Some(Measures(startAddrMValue, endAddrMValue))
      case (Some(startAddrMValue), Some(endAddrMValue)) => Some(Measures(endAddrMValue, startAddrMValue))
      case _ => None
    }
  }

  protected def getAllMunicipalities: Seq[Int] = {
    withDynSession {
      municipalityDao.getMunicipalities
    }
  }

  protected def getAllViiteRoadNumbers: Seq[Long] = {
    println("\nFetch Road Numbers From Viite")
    val roadNumbers = roadAddressService.getAllRoadNumbers()

    println("Road Numbers Fetched:")
    println(roadNumbers.mkString("\n"))

    roadNumbers
  }

  protected def calculateStartLrmByAddress(startAddress:  ViiteRoadAddress, section: AddressSection): Option[Double] = {
    if (startAddress.startAddrMValue >= section.startAddressMValue)
      startAddress.sideCode match {
        case TowardsDigitizing => Some(startAddress.startMValue)
        case AgainstDigitizing => Some(startAddress.endMValue)
        case _ => None
      }
    else
      startAddress.addressMValueToLRM(section.startAddressMValue)

  }

  protected def calculateEndLrmByAddress(endAddress: ViiteRoadAddress, section: AddressSection) = {
    if (endAddress.endAddrMValue <= section.endAddressMValue.getOrElse(endAddress.endAddrMValue))
      endAddress.sideCode match {
        case TowardsDigitizing => Some(endAddress.endMValue)
        case AgainstDigitizing => Some(endAddress.startMValue)
        case _ => None
      }
    else
      endAddress.addressMValueToLRM(section.endAddressMValue.get)
  }

  protected def filterRoadAddressByNumberAndTracks(roadAddresses: Seq[ViiteRoadAddress], roadNumber: Long, tracks: Seq[Track]) = {
    val addresses = roadAddresses.filter(ra => ra.roadNumber == roadNumber && tracks.exists(t => t == ra.track))
    val vvhRoadLinks = roadLinkService.fetchVVHRoadlinks(addresses.map(ra => ra.linkId).toSet).filter(_.administrativeClass == State).filter(filterViiteRoadAddress)
    addresses.map(ra => (ra, vvhRoadLinks.find(_.linkId == ra.linkId))).filter(_._2.isDefined)
  }

  protected def filterRoadAddressByNumberAndRoadPart(roadAddresses: Seq[ViiteRoadAddress], roadNumber: Long, roadPartNumber: Long) = {
    val addresses = roadAddresses.filter(ra => ra.roadNumber == roadNumber && ra.roadPartNumber == roadPartNumber)
    val vvhRoadLinks = roadLinkService.fetchVVHRoadlinks(addresses.map(ra => ra.linkId).toSet).filter(_.administrativeClass == State).filter(filterViiteRoadAddress)
    addresses.map(ra => (ra, vvhRoadLinks.find(_.linkId == ra.linkId))).filter(_._2.isDefined)
  }

  protected def filterRoadAddressBySection(roadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], section: AddressSection, mappedRoadLinks: Seq[VVHRoadlink]) = {
    def filterAddressMeasures(ra: ViiteRoadAddress) = {
      section.endAddressMValue match {
        case Some(endAddrM) => ra.startAddrMValue <= endAddrM && ra.endAddrMValue >= section.startAddressMValue
        case _ => ra.endAddrMValue >= section.startAddressMValue
      }
    }

    val addresses = roadAddresses.getOrElse((section.roadNumber, section.roadPartNumber, section.track), Seq()).filter(ra => filterAddressMeasures(ra))

    addresses.map(ra => (ra, mappedRoadLinks.find(_.linkId == ra.linkId))).filter(_._2.isDefined)
  }

  protected def filterViiteRoadAddress(roadLink: VVHRoadlink): Boolean = {
    true
  }

  protected def expireAssets(linkIds: Seq[Long]): Unit = {
    assetDao.expireAssetByTypeAndLinkId(typeId, linkIds)
  }

  protected def expireAssets(municipality: Int, administrativeClass: Option[AdministrativeClass] = None): Unit = {
    println("\nStart assets expiration in municipality %d".format(municipality))
    val roadLinksWithStateFilter = administrativeClass match {
      case Some(state) => roadLinkService.getVVHRoadLinksF(municipality).filter(_.administrativeClass == state).map(_.linkId)
      case _ => roadLinkService.getVVHRoadLinksF(municipality).map(_.linkId)
    }

    expireAssets(roadLinksWithStateFilter)

    println("\nEnd assets expiration in municipality %d".format(municipality))
  }

  protected def splitTrAssetsBySections(trAssetData: TierekisteriAssetData): Seq[(AddressSection, TierekisteriAssetData)] = {
    Seq((AddressSection(trAssetData.roadNumber, trAssetData.startRoadPartNumber, trAssetData.track, trAssetData.startAddressMValue,
      if (trAssetData.endRoadPartNumber == trAssetData.startRoadPartNumber)
        Some(trAssetData.endAddressMValue)
      else
        None), trAssetData)) ++ {
      if (trAssetData.startRoadPartNumber != trAssetData.endRoadPartNumber) {
        val roadPartNumberSortedList = List(trAssetData.startRoadPartNumber, trAssetData.endRoadPartNumber).sorted
        (roadPartNumberSortedList.head until roadPartNumberSortedList.last).tail.map(part =>
          (AddressSection(trAssetData.roadNumber, part, trAssetData.track, 0L, None), trAssetData)) ++
          Seq((AddressSection(trAssetData.roadNumber, trAssetData.endRoadPartNumber, trAssetData.track, 0L, Some(trAssetData.endAddressMValue)), trAssetData))
      } else
        Seq[(AddressSection, TierekisteriAssetData)]()
    }
  }

  def getAssetTypeId() = {
    typeId
  }

  def getAssetName() = {
    assetName
  }

  def importAssets()

  def updateAssets(lastExecution: DateTime)
}

trait TierekisteriAssetImporterOperations extends TierekisteriImporterOperations {

  val tierekisteriClient: TierekisteriClientType

  type TierekisteriClientType <: TierekisteriAssetDataClient
  type TierekisteriAssetData = tierekisteriClient.TierekisteriType

  protected def getRoadAddressSections(trAssetData: TierekisteriAssetData): Seq[(AddressSection, TierekisteriAssetData)] = {
    super.splitTrAssetsBySections(trAssetData).map(_.asInstanceOf[(AddressSection, TierekisteriAssetData)])
  }

  protected def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    true
  }

  protected def getAllTierekisteriAddressSections(roadNumber: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber).map(_.asInstanceOf[TierekisteriAssetData]).filter(filterTierekisteriAssets)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.flatMap(getRoadAddressSections)
  }

  protected def getAllTierekisteriAssets(roadNumber: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAssets = tierekisteriClient.fetchActiveAssetData(roadNumber)

    trAssets.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAssets.map(_.asInstanceOf[TierekisteriAssetData])
  }

  protected def getAllTierekisteriAssets(roadNumber: Long, roadPart: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAssets = tierekisteriClient.fetchActiveAssetData(roadNumber, roadPart)

    trAssets.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAssets.map(_.asInstanceOf[TierekisteriAssetData])
  }

  protected def getAllTierekisteriHistoryAssets(roadNumber: Long, lastExecution: DateTime) = {
    println("\nFetch " + assetName + " History by Road Number " + roadNumber)
    val trAssets = tierekisteriClient.fetchHistoryAssetData(roadNumber, Some(lastExecution))

    trAssets.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAssets.map(_.asInstanceOf[TierekisteriAssetData])
  }

  protected def getAllTierekisteriHistoryAddressSection(roadNumber: Long, lastExecution: DateTime) = {
    println("\nFetch " + assetName + " History by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchHistoryAssetData(roadNumber, Some(lastExecution)).map(_.asInstanceOf[TierekisteriAssetData]).filter(filterTierekisteriAssets)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.flatMap(getRoadAddressSections)
  }

  protected def getAllTierekisteriAddressSections(roadNumber: Long, roadPart: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber, roadPart).map(_.asInstanceOf[TierekisteriAssetData]).filter(filterTierekisteriAssets)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.flatMap(getRoadAddressSections)
  }

  protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, sectionRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit

  def importAssets(): Unit = {
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
        //Fetch asset from Tierekisteri and then generates the sections foreach returned asset
        //For example if Tierekisteri returns
        //One asset with start part = 2, end part = 5, start address = 10, end address 20
        //We will generate the middle parts and return a AddressSection for each one
        val trAddressSections = getAllTierekisteriAddressSections(roadNumber)

        //Fetch all the existing road address from viite client
        //If in the future this process get slow we can start using the returned sections
        //from trAddressSections sequence so we reduce the amount returned
        val roadAddresses = roadAddressService.getAllByRoadNumber(roadNumber)
        val mappedRoadAddresses = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
        val mappedRoadLinks  = roadLinkService.fetchVVHRoadlinks(roadAddresses.map(ra => ra.linkId).toSet)

        //For each section creates a new OTH asset
        trAddressSections.foreach {
          case (section, trAssetData) =>
            withDynTransaction {
              createAsset(section, trAssetData, mappedRoadAddresses, mappedRoadLinks)
            }
        }
    }
  }

  def updateAssets(lastExecution: DateTime): Unit = {
    val roadNumbers = getAllViiteRoadNumbers

    roadNumbers.foreach {
      roadNumber =>
        //Fetch asset changes from Tierekisteri and then generates the sections foreach returned asset change
        //For example if Tierekisteri returns
        //One asset with start part = 2, end part = 5, start address = 10, end address 20
        //We will generate the middle parts and return a AddressSection for each one
        val trHistoryAddressSections = getAllTierekisteriHistoryAddressSection(roadNumber, lastExecution)

        if(trHistoryAddressSections.nonEmpty){
          withDynTransaction {

            val changedSections = trHistoryAddressSections
            val changedRoadParts = changedSections.map(_._1.roadPartNumber).distinct
            val changedRoadAddresses = roadAddressService.getAllByRoadNumberAndParts(roadNumber, changedRoadParts)

            //Expire all the sections that have changes in tierekisteri
            expireAssets(changedRoadAddresses.map(_.linkId))
            val mappedChangedRoadAddresses = changedRoadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
            val mappedRoadLinks  = roadLinkService.fetchVVHRoadlinks(changedRoadAddresses.map(ra => ra.linkId).toSet)
            //Creates the assets on top of the expired sections
            changedRoadParts.foreach{
              roadPart =>
                //Fetch asset from Tierekisteri and then generates the sections foreach returned asset
                val trAddressSections = getAllTierekisteriAddressSections(roadNumber, roadPart)
                trAddressSections.foreach {
                  case (section, trAssetData) =>
                    createAsset(section, trAssetData, mappedChangedRoadAddresses, mappedRoadLinks)
                }
            }
          }
        }
    }
  }
}

trait LinearAssetTierekisteriImporterOperations extends TierekisteriAssetImporterOperations{

  lazy val linearAssetService: LinearAssetService = new LinearAssetService(roadLinkService, eventbus)

  protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData)

  protected override def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit = {
    println(s"Fetch Road Addresses from Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

    val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, mappedRoadLinks)

    roadAddressLink
      .foreach { case (ra, roadlink) =>
        calculateMeasures(ra, section).foreach {
          measures =>
            if(measures.endMeasure-measures.startMeasure > 0.01 )
              createLinearAsset(roadlink.get, ra, section, measures, trAssetData)
        }
      }
  }
}

trait PointAssetTierekisteriImporterOperations extends TierekisteriAssetImporterOperations{

  protected def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData)

  protected override def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], vvhRoadLinks: Seq[VVHRoadlink]): Unit = {
    println(s"Fetch Road Addresses from Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

    //Returns all the match Viite road address for the given section
    val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, vvhRoadLinks)
    roadAddressLink
      .foreach { case (ra, roadlink) =>
        ra.addressMValueToLRM(section.startAddressMValue).foreach{
          mValue =>
            createPointAsset(ra, roadlink.get, mValue, trAssetData)
        }
      }
  }
}