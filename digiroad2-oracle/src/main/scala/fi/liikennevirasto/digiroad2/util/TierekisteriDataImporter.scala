package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, State}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.roadaddress.oracle.RoadAddressDAO


class TierekisteriDataImporter(vvhClient: VVHClient, oracleLinearAssetDao: OracleLinearAssetDao,
                               roadAddressDao: RoadAddressDAO, linearAssetService: LinearAssetService) {
  val trafficVolumeTR = "tl201"
  val lightingTR = "tl167"
  val trafficVolumeId = 170
  val lightingAssetId = 100
  val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)


  def importTrafficVolumeAsset(tierekisteriTrafficVolumeAsset: TierekisteriTrafficVolumeAsset) = {
    println("\nExpiring Traffic Volume From OTH Database")
    OracleDatabase.withDynSession {
      oracleLinearAssetDao.expireAllAssetsByTypeId(trafficVolumeId)
    }
    println("\nTraffic Volume data Expired")

    println("\nFetch Road Numbers From Viite")
    val roadNumbers = OracleDatabase.withDynSession {
      roadAddressDao.getRoadNumbers()
    }
    println("\nEnd of Fetch ")

    println("roadNumbers: ")
    roadNumbers.foreach(ra => println(ra))

    roadNumbers.foreach {
      case roadNumber =>
        println("\nFetch Traffic Volume by Road Number " + roadNumber)
        val trTrafficVolume = tierekisteriTrafficVolumeAsset.fetchActiveAssetData(trafficVolumeTR, roadNumber)

        trTrafficVolume.foreach { tr => println("\nTR: roadNumber, roadPartNumber, start, end and kvt " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue + " " + tr.kvl) }

        val r = trTrafficVolume.groupBy(trTrafficVolume => (trTrafficVolume.roadNumber, trTrafficVolume.startRoadPartNumber, trTrafficVolume.startAddressMValue, trTrafficVolume.endAddressMValue)).map(_._2.head)

        r.foreach { tr =>
          OracleDatabase.withDynTransaction {

            println("\nFetch road addresses to link ids using Viite, trRoadNumber, roadPartNumber start and end " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue)
            val roadAddresses = roadAddressDao.getRoadAddressesFiltered(tr.roadNumber, tr.startRoadPartNumber, tr.startAddressMValue, tr.endAddressMValue)

            val roadAddressLinks = roadAddresses.map(ra => ra.linkId).toSet
            val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks)

            println("roadAddresses fetched: ")
            roadAddresses.filter(ra => vvhRoadlinks.exists(t => t.linkId == ra.linkId)).foreach(ra => println(ra.linkId))

            roadAddresses
              .filter(ra => vvhRoadlinks.exists(t => t.linkId == ra.linkId))
              .foreach { ra =>
                val assetId = linearAssetService.dao.createLinearAsset(trafficVolumeId, ra.linkId, false, SideCode.BothDirections.value,
                  Measures(ra.startMValue, ra.endMValue), "batch_process_trafficVolume", vvhClient.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))
                println("\nCreated OTH traffic volume assets form TR data with assetId " + assetId)

                linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, tr.kvl)
                println("\nCreated OTH property value with value " + tr.kvl + " and assetId " + assetId)
              }
          }
        }
    }
    println("\nEnd of Traffic Volume fetch")
    println("\nEnd of creation OTH traffic volume assets form TR data")
  }


  def importLitRoadAsset(tierekisteriLightingAsset: TierekisteriLightingAsset): Unit = {

    def createLinearAsset(linkId: Long, measures: Measures) = {
      val assetId = linearAssetService.dao.createLinearAsset(lightingAssetId, linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_lighting", vvhClient.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, 1)
      println(s"Created OTH Lighting assets for $linkId from TR data with assetId $assetId")
    }

    println("\nExpiring litRoad From OTH Database Only with administrativeClass == State")
    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nStart processing municipality %d".format(municipality))
      val roadLinksWithStateFilter = roadLinkService.getVVHRoadLinksF(municipality).filter(_.administrativeClass == State).map(_.linkId)

      OracleDatabase.withDynTransaction {
        oracleLinearAssetDao.fetchLinearAssetsByLinkIds(lightingAssetId, roadLinksWithStateFilter, LinearAssetTypes.numericValuePropertyId).foreach { persistedLinearAsset =>
          oracleLinearAssetDao.expireAssetsById(persistedLinearAsset.id)
          println("Asset with Id: " + persistedLinearAsset.id + " Expired.")
        }
      }
      println("\nEnd processing municipality %d".format(municipality))
    }
    println("\nLighting data Expired")

    println("\nFetch Road Numbers From Viite")
    val roadNumbers = OracleDatabase.withDynSession {
      roadAddressDao.getRoadNumbers()
    }
    println("\nEnd of Fetch ")

    println("roadNumbers: ")
    println(roadNumbers.mkString("\n"))

    roadNumbers.foreach {
      roadNumber =>
        println("\nFetch Lighting by Road Number " + roadNumber)
        val trLighting = tierekisteriLightingAsset.fetchActiveAssetData(lightingTR, roadNumber)

        trLighting.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }

        trLighting.flatMap(_.getRoadAddressSections).foreach { section =>
          OracleDatabase.withDynTransaction {
            println(s"Fetch road addresses to link ids using Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

            val road = section.roadNumber
            val roadPart = section.roadPartNumber
            val startAddr = section.startAddressMValue
            val endAddr = section.endAddressMValue
            val track = section.track

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
                createLinearAsset(ra.linkId, Measures(newStartMValue, newEndMValue))
              }
          }
        }
    }
    println("\nEnd of Lighting fetch")
    println("End of creation OTH Lighting assets form TR data")
  }

}
