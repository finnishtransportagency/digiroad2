package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{BogieWeightLimit, SideCode, State}
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriAssetDataClient, TierekisteriHeightLimitAssetClient, TierekisteriWeightLimitAssetClient}
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, LinearAssetTypes, Measures}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

trait PointConversionTierekisteriImporter extends TierekisteriImporterOperations {

  lazy val linearAssetService: LinearAssetService = new LinearAssetService(roadLinkService, eventbus)

  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

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

  protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData)

  protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit

  def importAssets(): Unit = {
    //Expire all asset in state roads in all the municipalities
    //    val municipalities = getAllMunicipalities
    //    municipalities.foreach { municipality =>
    //      withDynTransaction{
    //        expireAssets(municipality, Some(State))
    //      }
    //    }

    val roadNumbers = Seq(140)

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

trait WeightConversionTierekisteriImporter extends PointConversionTierekisteriImporter {
  override type TierekisteriClientType = TierekisteriWeightLimitAssetClient
  override val tierekisteriClient = new TierekisteriWeightLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit = {
    println(s"Fetch Road Addresses from Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

    val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, mappedRoadLinks)

    //search for nearby bridges (level +1)

    roadAddressLink
      .foreach { case (ra, roadlink) =>
        calculateMeasures(ra, section).foreach {
          measures =>
            //            if (measures.endMeasure - measures.startMeasure > 0.01)
            createLinearAsset(roadlink.get, ra, section, measures, trAssetData)
        }
      }
  }
}

trait HeightConversionTierekisteriImporter extends PointConversionTierekisteriImporter {
  override type TierekisteriClientType = TierekisteriHeightLimitAssetClient
  override val tierekisteriClient = new TierekisteriHeightLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit = {
    println(s"Fetch Road Addresses from Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

    val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, mappedRoadLinks)

    //search for nearby underpasses (level -1)

    roadAddressLink
      .foreach { case (ra, roadlink) =>
        calculateMeasures(ra, section).foreach {
          measures =>
            if (measures.endMeasure - measures.startMeasure > 0.01)
              createLinearAsset(roadlink.get, ra, section, measures, trAssetData)
        }
      }
  }
}

class BogieWeightImporter extends WeightConversionTierekisteriImporter {

  override def typeId: Int = BogieWeightLimit.typeId
  override def assetName = BogieWeightLimit.layerName

  override protected def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.bogieWeight.isDefined
  }

  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, trAssetData.bogieWeight.get)
      println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}
