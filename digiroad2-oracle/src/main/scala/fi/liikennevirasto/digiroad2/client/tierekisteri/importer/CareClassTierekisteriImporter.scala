package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriAssetData, TierekisteriAssetDataClient, TierekisteriGreenCareClassAssetClient, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, Queries}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.dao.Queries.insertSingleChoiceProperty
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, Track}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

case class AssetWithMeasures (roadLink: Option[VVHRoadlink], measures: Measures, assetData: TierekisteriAssetData)

class CareClassTierekisteriImporter extends TierekisteriImporterOperations {
  final private val DefaultEpsilon = 0.001

  lazy val service: DynamicLinearAssetService = new DynamicLinearAssetService(roadLinkService, eventbus)
  lazy val dao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  override def typeId: Int = CareClass.typeId
  override def assetName: String = "careClass"
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  lazy val greenCareTierekisteriClient: TierekisteriGreenCareClassAssetClient  =
    new TierekisteriGreenCareClassAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
      Digiroad2Properties.tierekisteriEnabled,
      HttpClientBuilder.create().build())


  lazy val winterCareTierekisteriClient: TierekisteriWinterCareClassAssetClient =
    new TierekisteriWinterCareClassAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
      Digiroad2Properties.tierekisteriEnabled,
      HttpClientBuilder.create().build())

  val trAssetTypeClients: Seq[TierekisteriAssetDataClient] = Seq(greenCareTierekisteriClient, winterCareTierekisteriClient)

  override def importAssets(): Unit = {
    val municipalities = getAllMunicipalities

    municipalities.foreach { municipality =>
      withDynTransaction{
        expireAssets(municipality, Some(State))
      }
    }

    val roadNumbers = getAllViiteRoadNumbers


    roadNumbers.foreach {
      roadNumber =>
        //Get all the existing road address for the road number
        val roadAddresses = roadAddressService.getAllByRoadNumber(roadNumber)
        val mappedRoadAddresses = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
        val sectionAssets = getAllTierekisteriAddressSections(roadNumber)
        val mappedRoadLinks  = getRoadLinks(roadAddresses.map(ra => ra.linkId).toSet, Some(State))
        val roadAddressInfo = getAllRoadAddressMeasures(sectionAssets, mappedRoadAddresses, mappedRoadLinks)
        val groupedByLinkId = roadAddressInfo.flatten.groupBy(_._1.linkId)
        withDynTransaction {
          groupedByLinkId.foreach{link => splitAndCreateAssets(link._2)}
        }
    }
  }

  def updateAssets(lastExecution: DateTime): Unit = {
    val roadNumbers = getAllViiteRoadNumbers

    roadNumbers.foreach {
      roadNumber =>
        val historySectionAssets = getAllTierekisteriHistoryAddressSection(roadNumber, lastExecution)
        //Get all the existing road address for the road number
        val roadAddresses = roadAddressService.getAllByRoadNumber(roadNumber)

        withDynTransaction {
          //Expire all the sections that have changes in tierekisteri
          val expiredSections = historySectionAssets.foldLeft(Seq.empty[Long]) {
            case (sections, (section, trAssetData)) =>
              //If the road part number was already process we ignore it
              if(sections.contains(section.roadPartNumber)) {
                sections
              } else {
                //Get all existing road address in viite and expire all the assets on top of this roads
                val roadAddressLink = filterRoadAddressByNumberAndRoadPart(roadAddresses, section.roadNumber, section.roadPartNumber)
                expireAssets(roadAddressLink.map(_._1.linkId))
                sections ++ Seq(section.roadPartNumber)
              }
          }

          val mappedRoadAddresses = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
          val mappedRoadLinks  = getRoadLinks(roadAddresses.map(ra => ra.linkId).toSet, Some(State))

          //Creates the assets on top of the expired sections
          expiredSections.foreach {
            roadPart =>
              val sectionAssets: Seq[(AddressSection, TierekisteriAssetData)] = {
                trAssetTypeClients.flatMap(client => client.fetchActiveAssetData(roadNumber, roadPart).map(_.asInstanceOf[TierekisteriAssetData])).flatMap(x => splitTrAssetsBySections(x))
              }
              val roadAddressInfo = getAllRoadAddressMeasures(sectionAssets, mappedRoadAddresses, mappedRoadLinks)
              val groupedByLinkId = roadAddressInfo.flatten.groupBy(_._1.linkId)
              groupedByLinkId.foreach{link => splitAndCreateAssets(link._2)}
          }
        }
    }
  }

  def getAllRoadAddressMeasures(sectionAssets: Seq[(AddressSection, TierekisteriAssetData)], existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]) = {
    sectionAssets.map { section =>
      val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section._1, mappedRoadLinks)
      roadAddressLink.flatMap { case (ra, roadlink) =>
        calculateMeasures(ra, section._1).map { measures =>
          (roadlink.get, measures, section._2)
        }
      }
    }
  }

  protected def getAllTierekisteriAddressSections(roadNumber: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = trAssetTypeClients.flatMap(client => client.fetchActiveAssetData(roadNumber).map(_.asInstanceOf[TierekisteriAssetData]))

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.flatMap(splitTrAssetsBySections)
  }

  protected def getAllTierekisteriHistoryAddressSection(roadNumber: Long, lastExecution: DateTime) = {
    println("\nFetch " + assetName + " History by Road Number " + roadNumber)
    val trAsset = trAssetTypeClients.flatMap(client => client.fetchHistoryAssetData(roadNumber, Some(lastExecution)).map(_.asInstanceOf[TierekisteriAssetData]))

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.flatMap(splitTrAssetsBySections)
  }

  def  roundMeasure(measure: Double) : Double = {
    Math.round(measure * 1000).toDouble/1000
  }

  def splitAndCreateAssets(roadAddressInfo: Seq[(VVHRoadlink, Measures, TierekisteriAssetData)]) = {

    val splitMeasures = roadAddressInfo.flatMap {
      case (_, measures, _) =>
        Seq(roundMeasure(measures.startMeasure), roundMeasure(measures.endMeasure))
    }.distinct.sorted

    val sectionMeasures = splitMeasures.zip(splitMeasures.tail).map(x => Measures(x._1, x._2))

    sectionMeasures.foreach{ segment =>
      val trAssets = roadAddressInfo.filter {
        case (_, assetMeasures, _) =>
          assetMeasures.startMeasure - DefaultEpsilon <= segment.startMeasure && assetMeasures.endMeasure + DefaultEpsilon >= segment.endMeasure && assetMeasures.endMeasure != assetMeasures.startMeasure
      }map{
        case(_, _, trAsset) =>
          getAssetValue(trAsset)
      }
      val assetId = service.dao.createLinearAsset(typeId, roadAddressInfo.head._1.linkId, false, SideCode.BothDirections.value, segment, "batch_process_" + assetName,
        vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadAddressInfo.head._1.linkSource.value))

      trAssets.distinct.foreach{
        asset =>
          val enumeratedId = Queries.getPropertyIdByPublicId(asset._2)
          println(s"Add property to asset $assetId with value ${asset._1}, public id ${asset._2} and enumerated Id $enumeratedId")
          insertSingleChoiceProperty(assetId, enumeratedId, asset._1).execute
      }
      println(s"Created OTH $assetName assets for ${roadAddressInfo.head._1.linkId} from TR data with assetId $assetId")
    }
  }

  def getAssetValue(trAsset: TierekisteriAssetData): (Int, String) = {
    if(trAsset.isInstanceOf[TierekisteriWinterCareClassAssetData])
      (trAsset.asInstanceOf[TierekisteriWinterCareClassAssetData].assetValue, trAsset.asInstanceOf[TierekisteriWinterCareClassAssetData].publicId)
    else
      (trAsset.asInstanceOf[TierekisteriGreenCareClassAssetData].assetValue, trAsset.asInstanceOf[TierekisteriGreenCareClassAssetData].publicId)
  }
}
