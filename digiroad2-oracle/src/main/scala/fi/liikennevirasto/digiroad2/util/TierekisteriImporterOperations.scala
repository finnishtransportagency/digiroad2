package fi.liikennevirasto.digiroad2.util


import java.util.Properties
import fi.liikennevirasto.digiroad2.{TierekisteriAssetDataClient, TierekisteriLightingAssetClient, TierekisteriRoadWidthAssetClient, _}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, State}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.roadaddress.oracle.RoadAddressDAO
import org.apache.http.impl.client.HttpClientBuilder

case class AddressSection(roadNumber: Long, roadPartNumber: Long, track: Track, assetValue: Int, startAddressMValue: Long, endAddressMValue: Option[Long])

trait TierekisteriImporterOperations {
  val eventbus = new DummyEventBus
  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  lazy val roadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = {
    new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint"))
  }
  lazy val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkService)
  lazy val roadAddressDao : RoadAddressDAO = new RoadAddressDAO()
  lazy val linearAssetService: LinearAssetService = new LinearAssetService(roadLinkService, eventbus)

  def getProperty(name: String) = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }

  def tierekisteriClient: TierekisteriClientType

  protected def assetName: String = ""

  type TierekisteriClientType <: TierekisteriAssetDataClient
  type TierekisteriAssetDataType <: TierekisteriAssetData

  def createLinearAsset(linkId: Long, typeId: Int, measures: Measures, assetValue: Int) = {
    if (measures.startMeasure != measures.endMeasure) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, assetValue)
      println(s"Created OTH " + assetName + " assets for " + linkId + " from TR data with assetId " + assetId)
    }
  }

  def getValue(tierekisteriAssetDataType: TierekisteriAssetDataType) : Int

  def getRoadAddressSections(tierekisteriAssetData: TierekisteriAssetDataType): Seq[AddressSection] = {
    Seq(AddressSection(tierekisteriAssetData.roadNumber, tierekisteriAssetData.startRoadPartNumber, tierekisteriAssetData.track, getValue(tierekisteriAssetData), tierekisteriAssetData.startAddressMValue,
      if (tierekisteriAssetData.endRoadPartNumber == tierekisteriAssetData.startRoadPartNumber)
        Some(tierekisteriAssetData.endAddressMValue)
      else
        None)) ++ {
      if (tierekisteriAssetData.startRoadPartNumber != tierekisteriAssetData.endRoadPartNumber) {
        val roadPartNumberSortedList = List(tierekisteriAssetData.startRoadPartNumber, tierekisteriAssetData.endRoadPartNumber).sorted
        (roadPartNumberSortedList.head until roadPartNumberSortedList.last).tail.map(part => AddressSection(tierekisteriAssetData.roadNumber, part, tierekisteriAssetData.track, getValue(tierekisteriAssetData), 0L, None)) ++
          Seq(AddressSection(tierekisteriAssetData.roadNumber, tierekisteriAssetData.endRoadPartNumber, tierekisteriAssetData.track, getValue(tierekisteriAssetData), 0L, Some(tierekisteriAssetData.endAddressMValue)))
      } else
        Seq[AddressSection]()
    }
  }

  def importAsset(typeId: Int): Unit = {
    println("\nExpiring " + assetName + " From OTH Database Only with administrativeClass == State")
    //Get All Municipalities
    val municipalities: Seq[Int] =
    OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      println("\nStart processing municipality %d".format(municipality))
      val roadLinksWithStateFilter = roadLinkService.getVVHRoadLinksF(municipality).filter(_.administrativeClass == State).map(_.linkId)

      OracleDatabase.withDynTransaction {
        dao.fetchLinearAssetsByLinkIds(typeId, roadLinksWithStateFilter, LinearAssetTypes.numericValuePropertyId).foreach { persistedLinearAsset =>
          dao.expireAssetsById(persistedLinearAsset.id)
          println("Asset with Id: " + persistedLinearAsset.id + " Expired.")
        }
      }
      println("\nEnd processing municipality %d".format(municipality))
    }
    println("\n" + assetName + " data Expired")

    println("\nFetch Road Numbers From Viite")
    val roadNumbers = OracleDatabase.withDynSession {
      roadAddressDao.getRoadNumbers()
    }
    println("\nEnd of Fetch ")

    println("roadNumbers: ")
    println(roadNumbers.mkString("\n"))

    roadNumbers.foreach {
      roadNumber =>
        println("\nFetch " + assetName + " by Road Number " + roadNumber)
        val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber)

        trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }

        trAsset.map(_.asInstanceOf[TierekisteriAssetDataType]).flatMap(getRoadAddressSections).foreach { section =>
          OracleDatabase.withDynTransaction {
            println(s"Fetch road addresses to link ids using Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

            val road = section.roadNumber
            val roadPart = section.roadPartNumber
            val startAddr = section.startAddressMValue
            val endAddr = section.endAddressMValue
            val track = section.track
            val assetValue = section.assetValue

            val addresses = roadAddressDao.getRoadAddress(roadAddressDao.withRoadAddressSinglePart(road, roadPart, track.value, startAddr, endAddr))
            val roadAddressLinks = addresses.map(ra => ra.linkId).toSet
            val vvhRoadLinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks).filter(_.administrativeClass == State)

            addresses
              .filter(ra => vvhRoadLinks.exists(_.linkId == ra.linkId))
              .foreach { ra =>

                val newStartMValue =
                  if (ra.startAddrMValue >= startAddr) {
                    ra.startMValue
                  } else {
                    ra.addressMValueToLRM(startAddr) match {
                      case Some(startValue) => startValue
                      case None => return
                    }
                  }

                val newEndMValue =
                  if (ra.endAddrMValue <= endAddr.getOrElse(ra.endAddrMValue)) {
                    ra.endMValue
                  } else {
                    ra.addressMValueToLRM(endAddr.get) match {
                      case Some(endValue) => endValue
                      case None => return
                    }
                  }
                createLinearAsset(ra.linkId, typeId, Measures(newStartMValue, newEndMValue), assetValue)
              }
          }
        }
    }
    println("\nEnd of " + assetName + " fetch")
    println("End of creation OTH " + assetName + " assets form TR data")
  }
}

class LitRoadImporterOperations extends TierekisteriImporterOperations {

  override def tierekisteriClient = new TierekisteriLightingAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  override def assetName = "lighting"
  override type TierekisteriClientType = TierekisteriLightingAssetClient
  override type TierekisteriAssetDataType = TierekisteriLightingData

  override def getValue(tierekisteriLightingData: TierekisteriAssetDataType): Int = {
    1
  }

}

class RoadWidthImporterOperations extends TierekisteriImporterOperations {

  override def tierekisteriClient = new TierekisteriRoadWidthAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  override def assetName = "roadWidth"

  override type TierekisteriClientType = TierekisteriRoadWidthAssetClient
  override type TierekisteriAssetDataType = TierekisteriRoadWidthData

  override def getValue(tierekisteriAssetData: TierekisteriAssetDataType): Int = {
    tierekisteriAssetData.assetValue
  }

}