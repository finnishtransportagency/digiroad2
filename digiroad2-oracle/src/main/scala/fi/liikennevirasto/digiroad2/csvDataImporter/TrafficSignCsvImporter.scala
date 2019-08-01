package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.TrafficSignTypeGroup.AdditionalPanels
import fi.liikennevirasto.digiroad2.{AssetProperty, DigiroadEventBus, ExcludedRow, GeometryUtils, IncompleteRow, MalformedRow, Point, Status, TrafficSignType}
import fi.liikennevirasto.digiroad2.asset.{SideCode, SimpleTrafficSignProperty, State, TextPropertyValue}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{AdditionalPanelInfo, IncomingTrafficSign, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank

import scala.util.Try

class TrafficSignCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"
  override val logInfo = "traffic sign import"

  case class CsvTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleTrafficSignProperty], validityDirection: Int, bearing: Option[Int], mValue: Double, roadLink: RoadLink, isFloating: Boolean)

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, eventBusImpl)

  private val longValueFieldMappings = coordinateMappings

  private val nonMandatoryMappings = Map(
    "arvo" -> "value",
    "kaksipuolinen merkki" -> "twoSided",
    "liikennevirran suunta" -> "trafficDirection",
    "suuntima" -> "bearing",
    "lisatieto" -> "additionalInfo"
  )

  private val codeValueFieldMappings = Map(
    "liikennemerkin tyyppi" -> "trafficSignType"
  )
  val mappings : Map[String, String] = longValueFieldMappings ++ nonMandatoryMappings ++ codeValueFieldMappings

  override def mandatoryFields = longValueFieldMappings.keySet ++ codeValueFieldMappings.keySet

  val mandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def verifyValueCode(parameterName: String, parameterValue: String): ParsedRow = {
    if(parameterValue.forall(_.isDigit) && TrafficSignType.applyTRValue(parameterValue.toInt).source.contains("CSVimport")){
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    }else{
      (List(parameterName), Nil)
    }
  }

  def tryToInt(propertyValue: String ) : Option[Int] = {
    Try(propertyValue.toInt).toOption
  }


  override def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
        }else
          result
      } else {
        if (longValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (codeValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueCode(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
        } else
          result
      }
    }
  }


  private def generateBaseProperties(trafficSignAttributes: ParsedProperties) : Set[SimpleTrafficSignProperty] = {
    val valueProperty = tryToInt(getPropertyValue(trafficSignAttributes, "value").toString).map { value =>
      SimpleTrafficSignProperty(valuePublicId, Seq(TextPropertyValue(value.toString)))}

    val additionalInfo = getPropertyValue(trafficSignAttributes, "additionalInfo").toString
    val additionalProperty = if(additionalInfo.nonEmpty)
      Some(SimpleTrafficSignProperty(infoPublicId, Seq(TextPropertyValue(additionalInfo))))
    else
      None

    val typeProperty = SimpleTrafficSignProperty(typePublicId, Seq(TextPropertyValue(TrafficSignType.applyTRValue(getPropertyValue(trafficSignAttributes, "trafficSignType").toString.toInt).OTHvalue.toString)))

    Set(Some(typeProperty), valueProperty, additionalProperty).flatten
  }

  def recalculateBearing(bearing: Option[Int]): (Option[Int], Option[Int]) = {
    bearing match {
      case Some(assetBearing) =>
        val validityDirection = trafficSignService.getAssetValidityDirection(assetBearing)
        val readjustedBearing = if(validityDirection == SideCode.AgainstDigitizing.value) {
          if(assetBearing > 90 && assetBearing < 180)
            assetBearing + 180
          else
            Math.abs(assetBearing - 180)
        } else assetBearing

        (Some(readjustedBearing), Some(validityDirection))
      case _ =>
        (None, None)
    }
  }

  override def createAsset(trafficSignAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData ): ImportResultData = {

    val signs = trafficSignAttributes.map { trafficSignAttribute =>
      val props = trafficSignAttribute.properties
      val nearbyLinks = trafficSignAttribute.roadLink
      val optBearing = tryToInt(getPropertyValue(props, "bearing").toString)
      val twoSided = getPropertyValue(props, "twoSided") match {
        case "1" => true
        case _ => false
      }
      val point = getCoordinatesFromProperties(props)

      val (assetBearing, assetValidityDirection) = recalculateBearing(optBearing)

      val closestRoadLinks = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)

      val possibleRoadLinks = roadLinkService.filterRoadLinkByBearing(assetBearing, assetValidityDirection, point, closestRoadLinks)

      val roadLinks = possibleRoadLinks.filter(_.administrativeClass != State)
      val roadLink = if (roadLinks.nonEmpty) {
        possibleRoadLinks.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(point, r.geometry))
      } else
        closestRoadLinks.minBy(r => GeometryUtils.minimumDistance(point, r.geometry))

      val validityDirection = if(assetBearing.isEmpty) {
        trafficSignService.getValidityDirection(point, roadLink, assetBearing, twoSided)
      } else assetValidityDirection.get

      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)

      (props, CsvTrafficSign(point.x, point.y, roadLink.linkId, generateBaseProperties(props), validityDirection, assetBearing, mValue, roadLink, (roadLinks.isEmpty || roadLinks.size > 1) && assetBearing.isEmpty))
    }

    val (additionalPanelInfo, trafficSignInfo) = signs.partition{ case(_, sign) =>
      TrafficSignType.applyOTHValue(sign.propertyData.find(p => p.publicId == typePublicId).get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toString.toInt).group == AdditionalPanels}

    val additionalPanels = additionalPanelInfo.map {case (csvRow, panel) => (csvRow, AdditionalPanelInfo(panel.mValue, panel.linkId, panel.propertyData, panel.validityDirection, Some(Point(panel.lon, panel.lat))))}.toSet

    val usedAdditionalPanels = trafficSignInfo.flatMap { case (csvRow, sign) =>
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(sign.lon, sign.lat), sign.roadLink.geometry)
      val bearing = if(sign.bearing.isEmpty && !sign.isFloating)
        Some(GeometryUtils.calculateBearing(sign.roadLink.geometry, Some(mValue)))
      else
        sign.bearing

      val signType = sign.propertyData.find(p => p.publicId == typePublicId).get.values.headOption.get.asInstanceOf[TextPropertyValue].propertyValue.toString.toInt
      val filteredAdditionalPanel = trafficSignService.distinctPanels(trafficSignService.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, signType, sign.roadLink.geometry, additionalPanels.map(_._2)))

      if (filteredAdditionalPanel.size <= 3) {
        val propertyData = trafficSignService.additionalPanelProperties(filteredAdditionalPanel) ++ sign.propertyData
        try {
          trafficSignService.createFromCoordinates(IncomingTrafficSign(sign.lon, sign.lat, sign.roadLink.linkId, propertyData, sign.validityDirection, bearing), sign.roadLink, user.username, sign.isFloating)
        } catch {
          case ex: NoSuchElementException => NotImportedData(reason = "Additional Panel Without main Sign Type", csvRow = rowToString(csvRow.flatMap{x => Map(x.columnName -> x.value)}.toMap))
        }
        filteredAdditionalPanel
      } else Seq()
    }

    val unusedAdditionalPanels = additionalPanels.filterNot{ panel => usedAdditionalPanels.contains(panel._2)}.toSeq.map { notImportedAdditionalPanel =>
      NotImportedData(reason = "Additional Panel Without main Sign Type", csvRow = rowToString(notImportedAdditionalPanel._1.flatMap{x => Map(x.columnName -> x.value)}.toMap))
    }

    result.copy(notImportedData = unusedAdditionalPanels.toList ++ result.notImportedData)
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long, municipalitiesToExpire: Set[Int]) : Unit = {
    update(logId, logInfo)
    try {
      val result = processing(inputStream, municipalitiesToExpire, user)
      result match {
        case ImportResultPointAsset(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result)
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, municipalitiesToExpire: Set[Int], user: User): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    withDynTransaction{
      trafficSignService.expireAssetsByMunicipalities(municipalitiesToExpire)
      val result = csvReader.allWithHeaders().foldLeft(ImportResultPointAsset()) { (result, row) =>
        val csvRow = row.map(r => (r._1.toLowerCase, r._2))
        val missingParameters = findMissingParameters(csvRow)
        val (malformedParameters, properties) = assetRowToProperties(csvRow)
        val (notImportedParameters, parsedRowAndRoadLink) = verifyData(properties, user)

        if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
          result.copy(
            incompleteRows = missingParameters match {
              case Nil => result.incompleteRows
              case parameters =>
                IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
            },
            malformedRows = malformedParameters match {
              case Nil => result.malformedRows
              case parameters =>
                MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
            },
            notImportedData = notImportedParameters match {
              case Nil => result.notImportedData
              case parameters =>
                NotImportedData(reason = parameters.head, csvRow = rowToString(csvRow)) :: result.notImportedData
            })
        } else {
          result.copy(
            createdData = parsedRowAndRoadLink match {
              case Nil => result.createdData
              case parameters =>
                CsvAssetRowAndRoadLink(properties = parameters.head.properties, roadLink = parameters.head.roadLink) :: result.createdData
            })
        }
      }

      createAsset(result.createdData, user, result)
    }
  }
}