package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{SpeedLimitTrafficSignClient, TierekisteriTrafficSignAssetClient, TierekisteriTrafficSignGroupClient}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISTrafficSignDao
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.{AdditionalPanelInfo, IncomingTrafficSign, TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, Track}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

class TrafficSignTierekisteriImporter extends TierekisteriAssetImporterOperations {

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, eventbus)
  lazy val manoeuvreService: ManoeuvreService = new ManoeuvreService(roadLinkService, eventbus)
  lazy val prohibitionService: ProhibitionService = new ProhibitionService(roadLinkService, eventbus)
  lazy val hazmatTransportProhibitionService: HazmatTransportProhibitionService = new HazmatTransportProhibitionService(roadLinkService, eventbus)
  lazy val trafficSignManager: TrafficSignManager = new TrafficSignManager(manoeuvreService, roadLinkService)

  override def typeId: Int = 300
  override def assetName = "trafficSigns"
  override type TierekisteriClientType = TierekisteriTrafficSignAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override val tierekisteriClient = new TierekisteriTrafficSignAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())

  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val signPlacementPublicId = "opposite_side_sign"
  private val mainSignText="main_sign_text"

  private val additionalInfoTypeGroups = Set(TrafficSignTypeGroup.GeneralWarningSigns, TrafficSignTypeGroup.ProhibitionsAndRestrictions, TrafficSignTypeGroup.AdditionalPanels, TrafficSignTypeGroup.InformationSigns)

  private def generateProperties(trAssetData: TierekisteriAssetData, additionalProperties: Set[AdditionalPanelInfo] = Set()) = {
    val trafficType = trAssetData.assetType
    val typeProperty = SimplePointAssetProperty(typePublicId, Seq(PropertyValue(trafficType.OTHvalue.toString)))
    val valueProperty = additionalInfoTypeGroups.exists(group => group == trafficType.group) match {
      case true => SimplePointAssetProperty(mainSignText, Seq(PropertyValue(trAssetData.assetValue)))
      case _ => SimplePointAssetProperty(valuePublicId, Seq(PropertyValue(trAssetData.assetValue)))
    }

    val additionalPanel = trafficSignService.additionalPanelProperties(additionalProperties)

    val defaultProperties =
      if (additionalPanel.nonEmpty)
        Set(typeProperty, valueProperty) ++ additionalPanel
      else
        Set(typeProperty, valueProperty)


    trAssetData.signSidePlacement match {
      case Some(signSidePlacement) =>
        defaultProperties ++ Set(SimplePointAssetProperty(signPlacementPublicId, Seq(PropertyValue(signSidePlacement))))
      case _ => defaultProperties
    }
  }

  def converter(trafficType: TrafficSignType, value: String): String = {
    val regexRemoveChar = "[a-zA-Z]".r

    val weightType : Seq[TrafficSignType] = Seq(MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding)
    val measuresType : Seq[TrafficSignType] = Seq(MaximumLength, NoWidthExceeding, MaxHeightExceeding)
    val speedLimitType : Seq[TrafficSignType] = Seq(SpeedLimitSign, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone)

    val trimValue = value.replaceAll("\\s", "").replaceAll(",", ".")
    try {
      trafficType match {
        case x if weightType.contains(trafficType) && Seq("""(?i)(\s*\d+\.?\d*t)""".r, """(?i)(\s*\d+\.?\d*tn)""".r).exists(regex => regex.findFirstMatchIn(trimValue).nonEmpty) =>
          val matched = """(?i)(\s*\d+\.?\d*t)""".r.findFirstMatchIn(trimValue)
          if(matched.nonEmpty)
            (regexRemoveChar.replaceAllIn(matched.get.toString, "").toDouble * 1000).toInt.toString
          else
            (regexRemoveChar.replaceAllIn("""(?i)(\s*\d+\.?\d*tn)""".r.findFirstMatchIn(trimValue).get.toString, "").toDouble * 1000).toInt.toString

        case x if measuresType.contains(trafficType) && """(?i)(\s*\d+?\.?\d*m)""".r.findFirstMatchIn(trimValue).nonEmpty =>
          val value = """(?i)(\s*\d+?\.?\d*m)""".r.findFirstMatchIn(trimValue).get.toString
          (regexRemoveChar.replaceAllIn(value, "").toDouble * 100).toInt.toString

        case x if speedLimitType.contains(trafficType) && Seq("""(?i)(\d+km\\h)""".r, """(?i)(\d+kmh)""".r).exists(regex => regex.findFirstMatchIn(trimValue).nonEmpty) =>
          val matched = """(?i)(\d+km\\h)""".r.findFirstMatchIn(trimValue)
          if(matched.nonEmpty)
              """[a-zA-Z|\\\/]""".r.replaceAllIn(matched.get.toString, "")
          else
            """[a-zA-Z|\\\/]""".r.replaceAllIn("""(?i)(\d+kmh)""".r.findFirstMatchIn(trimValue).get.toString, "")
        case _ => value
      }
    } catch {
      case _: Throwable =>
        println(s"Conversion fail for the following value -> value")
        value
      }
  }


  protected override def getAllTierekisteriHistoryAddressSection(roadNumber: Long, lastExecution: DateTime) = {
    println("\nFetch " + assetName + " History by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchHistoryAssetData(roadNumber, Some(lastExecution)).filter(_.assetType != TrafficSignType.Unknown)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.map(_.asInstanceOf[TierekisteriAssetData]).flatMap(getRoadAddressSections)
  }

  protected override def getAllTierekisteriAddressSections(roadNumber: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber).filter(_.assetType != Unknown)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.map(_.asInstanceOf[TierekisteriAssetData]).flatMap(getRoadAddressSections)
  }

  protected override def getAllTierekisteriAddressSections(roadNumber: Long, roadPart: Long): Seq[(AddressSection, TierekisteriAssetData)] = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber, roadPart).filter(_.assetType != TrafficSignType.Unknown)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.map(_.asInstanceOf[TierekisteriAssetData]).flatMap(getRoadAddressSections)
  }

  protected def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData, properties: Set[AdditionalPanelInfo]): Unit = {
      GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
        point =>
          val trafficSign = IncomingTrafficSign(point.x, point.y, vvhRoadlink.linkId, generateProperties(trAssetData, properties),
            getSideCode(roadAddress, trAssetData.track, trAssetData.roadSide).value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))

          val newId =  PostGISTrafficSignDao.create(trafficSign, mValue, "batch_process_trafficSigns", vvhRoadlink.municipalityCode,
            VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)

          val signType = trafficSignService.getProperty(trafficSign, typePublicId).get.propertyValue.toInt

          if (TrafficSignManager.belongsToManoeuvre(signType)) {
            roadLinkService.enrichRoadLinksFromVVH(Seq(vvhRoadlink))
                            .foreach{ roadLink =>
                                val trafficSignInfo = TrafficSignInfo(newId, roadLink.linkId,  trafficSign.validityDirection, signType, roadLink)
                                manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction = false, fromTierekisteriGenerator = true)
            }
          }

          newId
      }
    println(s"Created OTH $assetName asset on link ${vvhRoadlink.linkId} from TR data")
  }

  private def getAdditionalPanels(trAdditionalData: Seq[(AddressSection, TierekisteriAssetData)], existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]],  vvhRoadLinks: Seq[VVHRoadlink]): Seq[AdditionalPanelInfo] = {
    trAdditionalData.flatMap { case (section, properties) =>
      val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, vvhRoadLinks)

      roadAddressLink.flatMap { case (ra, roadlink) =>
        ra.addressMValueToLRM(section.startAddressMValue).map{
          mValue =>
            AdditionalPanelInfo(mValue, roadlink.get.linkId, generateProperties(properties), getSideCode(ra, properties.track, properties.roadSide).value)
        }
      }
    }
  }

//  def getAllAllTrAddressSections(roadNumber: Long, trAddressSections: Try[Seq[(AddressSection, TierekisteriAssetData)]]): Seq[(AddressSection, TierekisteriAssetData)] = {
//    if (!trAddressSections.isSuccess) {
//      roadAddressService.getAllByRoadNumber(roadNumber).flatMap { road =>
//        getAllTierekisteriAddressSections(road.roadNumber, road.roadPartNumber)
//      }
//    } else
//      trAddressSections.get
//  }

  override def importAssets(): Unit = {
    //Expire all asset in state roads in all the municipalities
    expireAssetsFromAllMunicipalitiesExcept()

    val roadNumbers = getAllViiteRoadNumbers

    roadNumbers.foreach {
      roadNumber =>
        //Fetch asset from Tierekisteri and then generates the sections foreach returned asset
        //For example if Tierekisteri returns
        //One asset with start part = 2, end part = 5, start address = 10, end address 20
        //We will generate the middle parts and return a AddressSection for each one

        //TODO revert this code when fixed it on TR side
//        val trAddressSections = getAllAllTrAddressSections(roadNumber, Try(getAllTierekisteriAddressSections(roadNumber)))
        val trAddressSections = getAllTierekisteriAddressSections(roadNumber)

        val (trProperties, trAssetsSections) = trAddressSections.partition(_._2.assetType.group == TrafficSignTypeGroup.AdditionalPanels)

        //Fetch all the existing road address from viite client
        //If in the future this process get slow we can start using the returned sections
        //from trAddressSections sequence so we reduce the amount returned
        val roadAddresses = roadAddressService.getAllByRoadNumber(roadNumber)
        val mappedRoadAddresses = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
        val mappedRoadLinks = getRoadLinks(roadAddresses.map(ra => ra.linkId).toSet, Some(State))

        val additionalProperties = getAdditionalPanels(trProperties, mappedRoadAddresses, mappedRoadLinks)

        //For each section creates a new OTH asset
        trAssetsSections.foreach {
          case (section, trAssetData) =>
            withDynTransaction {
              createAsset(section, trAssetData, mappedRoadAddresses, mappedRoadLinks, additionalProperties)
            }
        }
    }
  }

  protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], vvhRoadLinks: Seq[VVHRoadlink], trAdditionalData: Seq[AdditionalPanelInfo]): Unit = {
    println(s"Fetch Road Addresses from Viite: R:${section.roadNumber} P:${section.roadPartNumber} T:${section.track.value} ADDRM:${section.startAddressMValue}-${section.endAddressMValue.map(_.toString).getOrElse("")}")
    if(trAssetData.assetType.source.contains("TRimport")) {
      //Returns all the match Viite road address for the given section
      val roadAddressLink = filterRoadAddressBySection(existingRoadAddresses, section, vvhRoadLinks)
      roadAddressLink
        .foreach { case (ra, roadlink) =>
          ra.addressMValueToLRM(section.startAddressMValue).foreach{
            mValue =>
              val sideCode = getSideCode(ra, trAssetData.track, trAssetData.roadSide).value
              val trafficSignType = trAssetData.assetType.OTHvalue
              val allowedAdditionalPanels = trafficSignService.getAdditionalPanels(ra.linkId, mValue, sideCode, trafficSignType, roadlink.get.geometry, trAdditionalData.toSet, vvhRoadLinks)

              val allowedProperties = if(allowedAdditionalPanels.size > 3) {
                println(s"WARNING - additional panels excluded - ${allowedAdditionalPanels.size} additional Panel ${ allowedAdditionalPanels.map { panel =>
                  TrafficSignType.applyOTHValue(trafficSignService.getProperty(panel.propertyData, trafficSignService.typePublicId).get.propertyValue.toInt).toString}.mkString(",")}")
                Set()
              } else
                allowedAdditionalPanels

              //Remove additonal Panels with 'trafficSigns_type' with empty value
              val additionalPanelsFiltered = allowedProperties.filterNot{ panel =>
                                                                              val prop = trafficSignService.getProperty(panel.propertyData, trafficSignService.typePublicId).get
                                                                              prop.propertyValue.toString.trim.isEmpty }

              if ( trafficSignType.toString.trim.nonEmpty )
                createPointAsset(ra, roadlink.get, mValue, trAssetData, additionalPanelsFiltered.toSet)
          }
        }
    }
  }

  override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, sectionRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit = throw new UnsupportedOperationException("Not Supported Method")
}

trait TrafficSignByGroupTierekisteriImporter extends TrafficSignTierekisteriImporter {
  lazy val trafficSignGroup: TrafficSignTypeGroup = throw new IllegalArgumentException

  def trafficSignsInGroup(trafficSignGroup: TrafficSignTypeGroup) = TrafficSignType.apply(trafficSignGroup)
  def filterCondition(assetNumber : Int): Boolean = TrafficSignType.applyTRValue(assetNumber).group == TrafficSignTypeGroup.AdditionalPanels || TrafficSignType.applyTRValue(assetNumber).group == trafficSignGroup

  override val tierekisteriClient = new TierekisteriTrafficSignGroupClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())(filterCondition)

  override def expireAssetsFromAllMunicipalitiesExcept(municipalitiesToIgnore: Seq[Int] = Seq.empty[Int]) : Unit = {
    val municipalities = getAllMunicipalities.filterNot(municipalitiesToIgnore.contains)
    municipalities.foreach { municipality =>
      println("\nStart assets expiration in municipality %d".format(municipality))
      withDynTransaction {

        val roadLinksWithStateFilter = roadLinkService.getVVHRoadLinksF(municipality).filter(_.administrativeClass == State).map(_.linkId)
        val trafficSigns = trafficSignService.getTrafficSign(roadLinksWithStateFilter).to.filter {sign =>
          TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt).group == trafficSignGroup
        }

        trafficSignService.expireAssetsByLinkId(roadLinksWithStateFilter, trafficSignsInGroup(trafficSignGroup))
        trafficSignManager.deleteAssets(trafficSigns, false)
      }
      println("\nEnd assets expiration in municipality %d".format(municipality))
    }
  }

  override def getLastExecutionDate: Option[DateTime] = {
      trafficSignService.getLastExecutionDate(s"batch_process_$assetName", trafficSignsInGroup(trafficSignGroup))
  }
}

class TrafficSignSpeedLimitTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.SpeedLimits
  override val tierekisteriClient = new SpeedLimitTrafficSignClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())(filterCondition)
}

class TrafficSignRegulatorySignsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.RegulatorySigns
}

class TrafficSignMaximumRestrictionsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.MaximumRestrictions
}

class TrafficSignGeneralWarningSignsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.GeneralWarningSigns
}

class TrafficSignProhibitionsAndRestrictionsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.ProhibitionsAndRestrictions
}

class TrafficSignMandatorySignsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.MandatorySigns
}

class TrafficSignPriorityAndGiveWaySignsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.PriorityAndGiveWaySigns
}
class TrafficSignInformationSignsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.InformationSigns
}

class TrafficSignServiceSignsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.ServiceSigns
}

//TODO remove this code after merge US 1707
class TrafficSignAdditionalPanelsTierekisteriImporter extends TrafficSignByGroupTierekisteriImporter {
  override lazy val trafficSignGroup : TrafficSignTypeGroup = TrafficSignTypeGroup.AdditionalPanels
}