package fi.liikennevirasto.digiroad2.csvDataImporter

import java.text.Normalizer
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.{ServicePointsClass, State}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{IncomingService, IncomingServicePoint}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{ServicePointException, ServicePointService}
import fi.liikennevirasto.digiroad2.user.User
import scala.util.{Try, Success, Failure}

class ServicePointCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val stringValueFieldsMapping: Map[String, String] = Map("palvelun tyyppi" -> "type")
  override val nonMandatoryFieldsMapping: Map[String, String] = Map(
    "tarkenne" -> "type extension",
    "palvelun nimi" -> "name",
    "palvelun lisätieto" -> "additional info",
    "viranomaisdataa" -> "is authority data",
    "pysäkköintipaikkojen lukumäärä" -> "parking place count",
    "painorajoitus" -> "weight limit"
  )
  override val mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ stringValueFieldsMapping

  lazy val servicePointService: ServicePointService = new ServicePointService

  val municipalityBorders = municipalityBorderClient.fetchAllMunicipalities()
  case class CsvServicePoint(position: Point, incomingService: IncomingService, municipalityCode: Int, importInformation: Seq[NotImportedData] = Seq.empty)

  private def serviceTypeConverter(serviceType: String): Int = {
    val value = Normalizer.normalize(serviceType, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("-|\\s", "").toLowerCase
    ServicePointsClass.apply(value)
  }

  private def authorityDataConverter(value: String): Boolean = {
    val authorityDataValue = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("-|\\s", "").toLowerCase
    authorityDataValue match {
      case "kylla" => true
      case _ => false
    }
  }

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val incomingServicePoint = pointAssetAttributes.map { servicePointAttribute =>
      val csvProperties = servicePointAttribute.properties
      val position = getCoordinatesFromProperties(csvProperties)
      val serviceType = getPropertyValue(csvProperties, "type").asInstanceOf[String]
      val typeExtension = getPropertyValueOption(csvProperties, "type extension").map(_.toString)
      val name = getPropertyValueOption(csvProperties, "name").map(_.toString)
      val additionalInfo = getPropertyValueOption(csvProperties, "additional info").map(_.toString)
      val isAuthorityData = getPropertyValue(csvProperties, "is authority data").asInstanceOf[String]
      val parkingPlaceCount = getPropertyValueOption(csvProperties, "parking place count").map(_.toString.toInt)
      val weightLimit = getPropertyValueOption(csvProperties, "weight limit").map(_.toString.toInt)

      val validatedServiceType = serviceTypeConverter(serviceType)
      val validatedTypeExtension = ServicePointsClass.getTypeExtensionValue(typeExtension.getOrElse(""), validatedServiceType)
      val validatedAuthorityData = authorityDataConverter(isAuthorityData)

      val incomingService = IncomingService(validatedServiceType, name, additionalInfo, validatedTypeExtension, parkingPlaceCount, validatedAuthorityData, weightLimit)

      val servicePointInfo =
        if(validatedServiceType == ServicePointsClass.Unknown.value)
          Seq(NotImportedData(reason = s"Service Point type $serviceType does not exist.", csvRow = rowToString(csvProperties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
        else
          Seq()
      val municipalityCode = servicePointAttribute.municipalityCode.get
      CsvServicePoint(position, incomingService, municipalityCode, servicePointInfo)
    }

    val (validServicePoints, nonValidServicePoints) = incomingServicePoint.partition(servicePoint => servicePoint.importInformation.isEmpty)
    val notImportedInfo = nonValidServicePoints.flatMap(_.importInformation)
    val groupedServicePoints = validServicePoints.groupBy(_.position)

    val incomingServicePoints = groupedServicePoints.map { servicePoint =>
      (IncomingServicePoint(servicePoint._1.x, servicePoint._1.y, servicePoint._2.map(_.incomingService).toSet, Set()), servicePoint._2.head.municipalityCode)
    }

    val unauthoredData = incomingServicePoints.flatMap { elem =>
      Try {
        servicePointService.create(elem._1, elem._2, user.username, false)
      } match {
        case Success(_) => None
        case Failure(e: ServicePointException) =>
          Some(NotImportedData(reason = e.getMessage, csvRow = ""))
      }
    }

    result.copy(notImportedData = notImportedInfo.toList ++ result.notImportedData ++ unauthoredData)
  }

  override def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val municipality = municipalityBorderClient.findMunicipalityForPoint(Point(lon.toDouble, lat.toDouble), municipalityBorders)
        municipality match {
          case Some(foundMunicipality) =>
            (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, Seq.empty[RoadLink])))
          case None =>
            (Nil, Nil)
        }
      case _ =>
        (Nil, Nil)
    }
  }
}

