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

        trTrafficVolume.foreach { tr => println("\nTR: roadNumber, roadPartNumber, start, end and kvt " + tr.roadNumber + " " + tr.roadPartNumber + " " + tr.starMValue + " " + tr.endMValue + " " + tr.kvl) }

        val r = trTrafficVolume.groupBy(trTrafficVolume => (trTrafficVolume.roadNumber, trTrafficVolume.roadPartNumber, trTrafficVolume.starMValue, trTrafficVolume.endMValue)).map(_._2.head)

        r.foreach { tr =>
          OracleDatabase.withDynTransaction {

            println("\nFetch road addresses to link ids using Viite, trRoadNumber, roadPartNumber start and end " + tr.roadNumber + " " + tr.roadPartNumber + " " + tr.starMValue + " " + tr.endMValue)
            val roadAddresses = roadAddressDao.getRoadAddressesFiltered(tr.roadNumber, tr.roadPartNumber, tr.starMValue, tr.endMValue)

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

        trLighting.foreach { tr => println("\nTR: roadNumber, roadPartNumber, start and end: " + tr.roadNumber + " " + tr.roadPartNumber + " " + tr.starMValue + " " + tr.endMValue) }

        val newTRLighting = trLighting.groupBy(trLighting => (trLighting.roadNumber, trLighting.roadPartNumber, trLighting.starMValue, trLighting.endMValue)).map(_._2.head)

        newTRLighting.foreach { trl =>
          OracleDatabase.withDynTransaction {
            println("\nFetch road addresses to link ids using Viite, trRoadNumber, roadPartNumber start and end " + trl.roadNumber + " " + trl.roadPartNumber + " " + trl.starMValue + " " + trl.endMValue)
            val roadAddresses = roadAddressDao.getRoadAddressesFiltered(trl.roadNumber, trl.roadPartNumber, trl.starMValue, trl.endMValue)
            val roadAddressLinks = roadAddresses.map(ra => ra.linkId).toSet
            val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks).filter(_.administrativeClass == State)

            println("roadAddresses fetched: ")
            roadAddresses
              .filter(ra => vvhRoadlinks.exists(_.linkId == ra.linkId))
              .foreach { ra =>
                println(ra.linkId)
              }

            roadAddresses
              .filter(ra => vvhRoadlinks.exists(_.linkId == ra.linkId))
              .foreach { ra =>
                val newStartMValue =
                  if (ra.startAddrMValue >= trl.starMValue) {
                    ra.startMValue
                  } else {
                    (trl.starMValue - ra.startAddrMValue) + ra.startMValue
                  }

                val newEndMValue =
                  if (trl.endMValue <= ra.endAddrMValue) {
                    ra.endMValue
                  } else {
                    ra.endMValue - (ra.endAddrMValue - trl.endMValue)
                  }
                val assetId = linearAssetService.dao.createLinearAsset(lightingAssetId, ra.linkId, false, SideCode.BothDirections.value,
                  Measures(newStartMValue, newEndMValue), "batch_process_lighting", vvhClient.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))

                linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, 1)
                println("\nCreated OTH Lighting assets form TR data with assetId " + assetId)
              }
          }
        }
    }
    println("\nEnd of Lighting fetch")
    println("\nEnd of creation OTH Lighting assets form TR data")
  }

}
