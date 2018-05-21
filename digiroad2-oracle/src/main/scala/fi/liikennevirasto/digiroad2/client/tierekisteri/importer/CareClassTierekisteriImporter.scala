package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriAssetData, TierekisteriAssetDataClient, TierekisteriGreenCareClassAssetClient, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, DynamicLinearAssetService}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

case class AssetWithMeasures (roadLink: Option[VVHRoadlink], measures: Measures, assetData: TierekisteriAssetData)

class CareClassTierekisteriImporter extends TierekisteriImporterOperations {

  lazy val service: DynamicLinearAssetService = new DynamicLinearAssetService(roadLinkService, eventbus)
  lazy val dao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  override def typeId: Int = CareClass.typeId
  override def assetName: String = "careClass"
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  lazy val greenCareTierekisteriClient: TierekisteriGreenCareClassAssetClient  =
    new TierekisteriGreenCareClassAssetClient(dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      dr2properties.getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())


  lazy val winterCareTierekisteriClient: TierekisteriWinterCareClassAssetClient =
    new TierekisteriWinterCareClassAssetClient(dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      dr2properties.getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())

  val trAssetTypeClients: Seq[TierekisteriAssetDataClient] = Seq(greenCareTierekisteriClient, winterCareTierekisteriClient)

  override def importAssets(): Unit = {
    val municipalities = Seq(851)

    municipalities.foreach { municipality =>
      withDynTransaction{
        expireAssets(municipality, Some(State))
      }
    }

    val roadNumbers = Seq(29)

    withDynTransaction {

      roadNumbers.foreach {
        roadNumber =>
          val sectionAssets = getAllTierekisteriAddressSections(roadNumber)
          val roadAddressInfo = getAllRoadAddressMeasures(sectionAssets)
          val groupedByLinkId = roadAddressInfo.flatten.groupBy(_._1.linkId)
          groupedByLinkId.foreach{link => splitAndCreateAssets(link._2)}
      }
    }
  }

  def updateAssets(lastExecution: DateTime): Unit = {
    val roadNumbers = getAllViiteRoadNumbers

    roadNumbers.foreach {
      roadNumber =>
        val historySectionAssets = getAllTierekisteriHistoryAddressSection(roadNumber, lastExecution)

        withDynTransaction {
          //Expire all the sections that have changes in tierekisteri
          val expiredSections = historySectionAssets.foldLeft(Seq.empty[Long]) {
            case (sections, (section, trAssetData)) =>
              //If the road part number was already process we ignore it
              if(sections.contains(section.roadPartNumber)) {
                sections
              } else {
                //Get all existing road address in viite and expire all the assets on top of this roads
                val roadAddressLink = getAllViiteRoadAddress(section.roadNumber, section.roadPartNumber)
                expireAssets(roadAddressLink.map(_._1.linkId))
                sections ++ Seq(section.roadPartNumber)
              }
          }

          //Creates the assets on top of the expired sections
          expiredSections.foreach {
            roadPart =>
              val sectionAssets: Seq[(AddressSection, TierekisteriAssetData)] = {
                trAssetTypeClients.flatMap(client => client.fetchActiveAssetData(roadNumber, roadPart).map(_.asInstanceOf[TierekisteriAssetData])).flatMap(x => splitTrAssetsBySections(x))
              }
              val roadAddressInfo = getAllRoadAddressMeasures(sectionAssets)
              val groupedByLinkId = roadAddressInfo.flatten.groupBy(_._1.linkId)
              groupedByLinkId.foreach{link => splitAndCreateAssets(link._2)}
          }
        }
    }
  }

  def getAllRoadAddressMeasures(sectionAssets: Seq[(AddressSection, TierekisteriAssetData)]) = {
    sectionAssets.map { section =>
      val roadAddressLink = getAllViiteRoadAddress(section._1)
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

  def splitAndCreateAssets(roadAddressInfo: Seq[(VVHRoadlink, Measures, TierekisteriAssetData)]) = {

    val splitMeasures = roadAddressInfo.flatMap {
      case (_, measures, _) =>
        Seq(measures.startMeasure, measures.endMeasure)
    }.distinct.sorted

    val sectionMeasures = splitMeasures.zip(splitMeasures.tail).map(x => Measures(x._1, x._2))

    sectionMeasures.foreach{ segment =>
      val trAssets = roadAddressInfo.filter {
        case (_, assetMeasures, _) =>
          assetMeasures.startMeasure <= segment.startMeasure && assetMeasures.endMeasure >= segment.endMeasure && assetMeasures.endMeasure != assetMeasures.startMeasure//check values which are equal
      }map{
        case(_, _, trAsset) =>
          getAssetValue(trAsset)
      }
      val assetId = service.dao.createLinearAsset(typeId, roadAddressInfo.head._1.linkId, false, SideCode.BothDirections.value, segment, "batch_process_" + assetName,
        vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadAddressInfo.head._1.linkSource.value))
      dao.updateAssetProperties(assetId, trAssets)
      println(s"Created OTH $assetName assets for ${roadAddressInfo.head._1.linkId} from TR data with assetId $assetId")
    }
  }

  def getAssetValue(trAsset: TierekisteriAssetData): DynamicProperty = {
    if(trAsset.isInstanceOf[TierekisteriWinterCareClassAssetData])
      trAsset.asInstanceOf[TierekisteriWinterCareClassAssetData].assetValue
    else
      trAsset.asInstanceOf[TierekisteriGreenCareClassAssetData].assetValue
  }
}
