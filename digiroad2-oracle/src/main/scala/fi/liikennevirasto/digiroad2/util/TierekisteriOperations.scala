package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, State}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.roadaddress.oracle.RoadAddressDAO

trait TierekisteriOperations {
  def linearAssetService: LinearAssetService
  def vvhClient: VVHClient
  lazy val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  def oracleLinearAssetDao: OracleLinearAssetDao
  def roadAddressDao: RoadAddressDAO

  protected def typeId: Int = 0
  protected def assetName: String = ""
  protected def assetTableTR = ""

  type TierekisteriType

  def createLinearAsset(linkId: Long, measures: Measures, newValue: Int) = {
    if (measures.startMeasure != measures.endMeasure) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, newValue)
      println(s"Created OTH " + assetName + " assets for $linkId from TR data with assetId $assetId")
    }
  }

  def importAsset(tierekisteriType: TierekisteriType) = {
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
        oracleLinearAssetDao.fetchLinearAssetsByLinkIds(typeId, roadLinksWithStateFilter, LinearAssetTypes.numericValuePropertyId).foreach { persistedLinearAsset =>
          oracleLinearAssetDao.expireAssetsById(persistedLinearAsset.id)
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
        val trAsset = tierekisteriType.fetchActiveAssetData(assetTableTR, roadNumber)

        trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}/${tr.assetValue}") }

        trAsset.flatMap(_.getRoadAddressSections).foreach { section =>
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
                createLinearAsset(ra.linkId, Measures(newStartMValue, newEndMValue), assetValue)
              }
          }
        }
    }
    println("\nEnd of " + assetName + " fetch")
    println("End of creation OTH " + assetName + " assets form TR data")
  }
}

