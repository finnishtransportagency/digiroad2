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


  def importLitRoadAsset(tierekisteriLightingAsset: TierekisteriLightingAsset) = {

    def createLinearAsset(linkId: Long, measures: Measures) = {
      val assetId = linearAssetService.dao.createLinearAsset(lightingAssetId, linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_lighting", vvhClient.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, 1)
      println("\nCreated OTH Lighting assets form TR data with assetId " + assetId)
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
        oracleLinearAssetDao.fetchLinearAssetsByLinkIds(lightingAssetId, roadLinksWithStateFilter, LinearAssetTypes.numericValuePropertyId).map { persistedLinearAsset =>
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
    roadNumbers.foreach(ra => println(ra))

    roadNumbers.foreach {
      case roadNumber =>
        println("\nFetch Lighting by Road Number " + roadNumber)
        val trLighting = tierekisteriLightingAsset.fetchActiveAssetData(lightingTR, roadNumber)

        trLighting.foreach { tr => println("\nTR: roadNumber, roadPartNumber, start and end: " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue) }

        val newTRLighting = trLighting.groupBy(trLighting => (trLighting.roadNumber, trLighting.startRoadPartNumber, trLighting.startAddressMValue, trLighting.endAddressMValue)).map(_._2.head)

        newTRLighting.foreach { trl =>
          OracleDatabase.withDynTransaction {
            println("\nFetch road addresses to link ids using Viite, trRoadNumber, roadPartNumber, start, end and endRoadPartNumber " + trl.roadNumber + " " + trl.startRoadPartNumber + " " + trl.endRoadPartNumber + " " + trl.startAddressMValue + " " + trl.endAddressMValue)

            val roadAddressSections = trl.getRoadAddressSections
            val roadNumberTRL = roadAddressSections.head.roadNumber
            val startRoadPartNumberTRL = roadAddressSections.head.roadPartNumber
            val endRoadPartNumberTRL = roadAddressSections.last.roadPartNumber
            val startAddressMValueTRL = roadAddressSections.head.startAddressMValue
            val endAddressMValueTRL_LAST = roadAddressSections.last.endAddressMValue.getOrElse(trl.endAddressMValue)
            val endAddressMValueTRL_HEAD = roadAddressSections.head.endAddressMValue.getOrElse(trl.endAddressMValue)
            val trackTRL = roadAddressSections.head.track

            if (roadAddressSections.size == 1) {
              //Only One Single Part
              val roadAddressesWithSinglePart = roadAddressDao.getRoadAddress(roadAddressDao.withRoadAddressSinglePart(roadNumberTRL, startRoadPartNumberTRL, trackTRL.value, startAddressMValueTRL, endAddressMValueTRL_HEAD))
              val roadAddressLinks = roadAddressesWithSinglePart.map(ra => ra.linkId).toSet
              val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks).filter(_.administrativeClass == State)

              roadAddressesWithSinglePart
                .filter(ra => vvhRoadlinks.exists(_.linkId == ra.linkId))
                .foreach { ra =>

                  val newStartMValue =
                    if (ra.startAddrMValue >= startAddressMValueTRL) {
                      ra.startMValue
                    } else {
                      (startAddressMValueTRL - ra.startAddrMValue) + ra.startMValue
                    }

                  if (ra.endAddrMValue <= endAddressMValueTRL_HEAD) {
                    createLinearAsset(ra.linkId, Measures(newStartMValue, ra.endMValue))
                  } else {
                    val newEndMValue = ra.endMValue - (ra.endAddrMValue - endAddressMValueTRL_HEAD)
                    createLinearAsset(ra.linkId, Measures(newStartMValue, newEndMValue))
                  }
                }

            } else if (roadAddressSections.size == 2) {
              //Only two parts
              val roadAddressesWithEndPart = roadAddressDao.getRoadAddress(roadAddressDao.withRoadAddressTwoParts(roadNumberTRL, startRoadPartNumberTRL, endRoadPartNumberTRL, trackTRL.value, startAddressMValueTRL, endAddressMValueTRL_LAST))
              val roadAddressLinks = roadAddressesWithEndPart.map(ra => ra.linkId).toSet
              val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks).filter(_.administrativeClass == State)

              roadAddressesWithEndPart
                .filter(rawep => vvhRoadlinks.exists(_.linkId == rawep.linkId))
                .foreach { rawep =>

                  if ((rawep.startAddrMValue < startAddressMValueTRL) && (rawep.roadPartNumber == startRoadPartNumberTRL)) {
                    val newStartMValue = (startAddressMValueTRL - rawep.startAddrMValue) + rawep.startMValue
                    createLinearAsset(rawep.linkId, Measures(newStartMValue, rawep.endMValue))

                  } else if ((rawep.endAddrMValue > endAddressMValueTRL_LAST) && (rawep.roadPartNumber == trl.endRoadPartNumber)) {
                    val newEndMValue = rawep.endMValue - (rawep.endAddrMValue - endAddressMValueTRL_LAST)
                    createLinearAsset(rawep.linkId, Measures(rawep.startMValue, newEndMValue))

                  } else {
                    createLinearAsset(rawep.linkId, Measures(rawep.startMValue, rawep.endMValue))
                  }
                }

            } else {
              //Multiple Parts Between First and Last Parts
              val roadAddressesWithMediumEndPart = roadAddressDao.getRoadAddress(roadAddressDao.withRoadAddressMultiParts(roadNumberTRL, startRoadPartNumberTRL, endRoadPartNumberTRL, trackTRL.value, startAddressMValueTRL, endAddressMValueTRL_LAST))
              val roadAddressLinks = roadAddressesWithMediumEndPart.map(ra => ra.linkId).toSet
              val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks).filter(_.administrativeClass == State)

              roadAddressesWithMediumEndPart
                .filter(rawep => vvhRoadlinks.exists(_.linkId == rawep.linkId))
                .foreach { rawmep =>
                  if ((rawmep.startAddrMValue < startAddressMValueTRL) && (rawmep.roadPartNumber == startRoadPartNumberTRL)) {
                    val newStartMValue = (startAddressMValueTRL - rawmep.startAddrMValue) + rawmep.startMValue
                    createLinearAsset(rawmep.linkId, Measures(newStartMValue, rawmep.endMValue))

                  } else if ((rawmep.endAddrMValue > endAddressMValueTRL_LAST) && (rawmep.roadPartNumber == endRoadPartNumberTRL)) {
                    val newEndMValue = rawmep.endMValue - (rawmep.endAddrMValue - endAddressMValueTRL_LAST)
                    createLinearAsset(rawmep.linkId, Measures(rawmep.startMValue, newEndMValue))

                  } else {
                    createLinearAsset(rawmep.linkId, Measures(rawmep.startMValue, rawmep.endMValue))
                  }
                }
            }
          }
        }
    }
    println("\nEnd of Lighting fetch")
    println("\nEnd of creation OTH Lighting assets form TR data")
  }

}
