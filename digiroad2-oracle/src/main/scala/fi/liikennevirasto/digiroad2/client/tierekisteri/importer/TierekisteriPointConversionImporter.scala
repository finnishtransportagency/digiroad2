package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriAssetDataClient, TierekisteriHeightLimitAssetClient, TierekisteriWeightLimitAssetClient, TierekisteriWeightLimitData}
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, LinearAssetService, LinearAssetTypes, Measures}
import org.apache.http.impl.client.HttpClientBuilder

trait TierekisteriPointConversionImporter extends TierekisteriAssetImporterOperations {

  lazy val linearAssetService: LinearAssetService = new LinearAssetService(roadLinkService, eventbus)
  lazy val dynamicLinearAssetService: DynamicLinearAssetService = new DynamicLinearAssetService(roadLinkService, eventbus)

  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val tierekisteriClient: TierekisteriClientType
  val radiusDistance = 50 //meter

  type TierekisteriClientType <: TierekisteriAssetDataClient

  val allowedVerticalLevel: Seq[Int]

  def checkVerticalLevel(vvhRoadlink: VVHRoadlink): Boolean = {
    vvhRoadlink.verticalLevel match {
      case Some(vL) => allowedVerticalLevel.exists {
        _.toString == vL
      }
      case _ => false
    }
  }

  def getAdjacents(previousInfo: (Point, VVHRoadlink), roadLinks: Seq[VVHRoadlink]): Seq[(VVHRoadlink, (Point, Point))] = {
    roadLinks.filter {
      roadLink =>
        GeometryUtils.areAdjacent(roadLink.geometry, previousInfo._1)
    }.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val points = if (GeometryUtils.areAdjacent(first, previousInfo._1)) (first, last) else (last, first)

      (roadLink, points)
    }
  }

  def findRelevantRoadLink(previousInfo: (Point, VVHRoadlink), roadLinks: Seq[VVHRoadlink], distance: Double): Seq[(VVHRoadlink, Double)] = {
    val filteredRoadLink = roadLinks.filterNot(_.linkId == previousInfo._2.linkId)
    val adjacents = getAdjacents(previousInfo, filteredRoadLink)
    if (adjacents.isEmpty || distance >= radiusDistance)
      Seq()
    else {
      adjacents.flatMap { case (newRoadLink, (adjacentPoint, oppositePoint)) =>
        if (checkVerticalLevel(newRoadLink))
          Some(newRoadLink, distance)
        else
          findRelevantRoadLink((oppositePoint, newRoadLink), filteredRoadLink, distance + GeometryUtils.geometryLength(newRoadLink.geometry))
      }
    }
  }

  def findNearesRoadLink(ra: ViiteRoadAddress, section: AddressSection, roadLink: VVHRoadlink, mappedRoadLinks: Seq[VVHRoadlink]): Option[VVHRoadlink] = {
    ra.addressMValueToLRM(section.startAddressMValue).flatMap{ mValue =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      val resultRoadLink : Seq[(VVHRoadlink, Double)] = Seq(first, last).flatMap { point =>
        var distance = if (GeometryUtils.areAdjacent(point, first)) mValue else GeometryUtils.geometryLength(roadLink.geometry) - mValue
        findRelevantRoadLink((point, roadLink), mappedRoadLinks, distance)
      }
      if(resultRoadLink.nonEmpty) Some(resultRoadLink.minBy(_._2)._1) else {
        println(s"Cannot find RoadLink with requested conditions ${ra.roadNumber} ${ra.roadPartNumber} ${ra.endAddrMValue}")
        None
      }
    }
  }

  protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData)

  protected override def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit = {
    println(s"Fetch Road Addresses from Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")

    val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, mappedRoadLinks)

    roadAddressLink
      .foreach { case (ra, roadLink) =>
        val measures = Measures(0, GeometryUtils.geometryLength(roadLink.get.geometry))
        if (measures.endMeasure - measures.startMeasure > 0.01)

          if (checkVerticalLevel(roadLink.get))
            createLinearAsset(roadLink.get, ra, section, measures, trAssetData)
          else {
            val nearestRoadLink = findNearesRoadLink(ra, section, roadLink.get, mappedRoadLinks)
            if (nearestRoadLink.nonEmpty)
              createLinearAsset(nearestRoadLink.get, ra, section, measures, trAssetData)
          }
      }
  }
}

trait WeightConversionTierekisteriImporter extends TierekisteriPointConversionImporter {
  override type TierekisteriClientType = TierekisteriWeightLimitAssetClient
  override val tierekisteriClient = new TierekisteriWeightLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  override val allowedVerticalLevel: Seq[Int] = Seq(1,2,3,4)
  override type TierekisteriAssetData <: TierekisteriWeightLimitData

  def getValue(trAssetData: TierekisteriAssetData): Option[Int] = None

  override def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    getValue(trAssetData).foreach { value =>
      val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

      linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, value)
      println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
    }
  }
}

class TotalWeightLimitImporter extends WeightConversionTierekisteriImporter {

  override def typeId : Int = TotalWeightLimit.typeId
  override def assetName : String = TotalWeightLimit.label

  override def getValue(trAssetData: TierekisteriAssetData): Option[Int] = {
    trAssetData.totalWeight match {
      case Some(value) => Some(value*1000)
      case _ => None
    }
  }
}

class AxleWeightLimitImporter extends WeightConversionTierekisteriImporter {

  override def typeId : Int = AxleWeightLimit.typeId
  override def assetName : String = AxleWeightLimit.label

  override def getValue(trAssetData: TierekisteriAssetData): Option[Int] = {
    trAssetData.axleWeight match {
      case Some(value) => Some(value*1000)
      case _ => None
    }
  }
}

class TruckWeightLimitImporter extends WeightConversionTierekisteriImporter {

  override def typeId : Int = TrailerTruckWeightLimit.typeId
  override def assetName : String = TrailerTruckWeightLimit.label

  override def getValue(trAssetData: TierekisteriAssetData): Option[Int] = {
    trAssetData.trailerTruckWeight match {
      case Some(value) => Some(value*1000)
      case _ => None
    }
  }
}

class BogieWeightLimitImporter  extends WeightConversionTierekisteriImporter {

  override def typeId : Int = BogieWeightLimit.typeId
  override def assetName : String = BogieWeightLimit.label


  override def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val properties: Seq[DynamicProperty] =
      Seq(trAssetData.bogieWeight.map { twoAxelValue =>
        DynamicProperty("bogie_weight_2_axel", "number", false, Seq(DynamicPropertyValue(twoAxelValue*1000)))
      }, trAssetData.threeBogieWeight.map { threeAxelValue =>
        DynamicProperty("bogie_weight_3_axel", "number", false, Seq(DynamicPropertyValue(threeAxelValue*1000)))
      }).flatten

    if (properties.nonEmpty) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

      dynamicLinearAssetService.dynamicLinearAssetDao.updateAssetProperties(assetId, properties)
      println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
    }
  }
}

class HeightLimitImporter extends TierekisteriPointConversionImporter {
  override type TierekisteriClientType = TierekisteriHeightLimitAssetClient
  override val tierekisteriClient = new TierekisteriHeightLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  override def typeId : Int = HeightLimit.typeId
  override def assetName : String = HeightLimit.label

  override val allowedVerticalLevel : Seq[Int] = Seq(-1, -2, -3)

  override def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
      measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

    linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, trAssetData.height )
    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}


