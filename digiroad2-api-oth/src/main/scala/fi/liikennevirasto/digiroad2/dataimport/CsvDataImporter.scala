package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}
import java.util.Properties

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, RoadLink, Properties => Props}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{Digiroad2Context, Point}
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, ManoeuvreService, Measures, ProhibitionService}
import fi.liikennevirasto.digiroad2.{Digiroad2Context, Point, TrafficSignType}
import fi.liikennevirasto.digiroad2.{PriorityAndGiveWaySigns, _}
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import org.apache.commons.lang3.StringUtils.isBlank
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.TrafficSignTypeGroup.{AdditionalPanels, PriorityAndGiveWaySigns}
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.service.pointasset.{AdditionalPanelInfo, IncomingTrafficSign, TrafficSignService}
import slick.util.iter.Empty

import scala.util.Try

class RoadLinkNotFoundException(linkId: Int) extends RuntimeException

trait CsvDataImporterOperations {

  lazy val vvhClient = { new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint")) }
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  protected def getProperty(name: String) = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }
}
class TrafficSignCsvImporter extends CsvDataImporterOperations {
  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"

  case class CsvTrafficSign(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimpleTrafficSignProperty], validityDirection: Int, bearing: Option[Int], mValue: Double, roadLink: RoadLink)

  type MalformedParameters = List[String]
  type ParsedProperties = List[AssetProperty]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)

  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class MalformedAsset(malformedParameters: List[String], csvRow: String)
  case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)
  case class NotImportedData(reason: String, csvRow: String)
  case class CsvAssetRowAndRoadLink(properties: CsvAssetRow, roadLink: Seq[VVHRoadlink])
  case class ImportResult(incompleteAssets: List[IncompleteAsset] = Nil,
                          malformedAssets: List[MalformedAsset] = Nil,
                          excludedAssets: List[ExcludedAsset] = Nil,
                          notImportedData: List[NotImportedData] = Nil,
                          createdData: List[CsvAssetRowAndRoadLink] = Nil)
  case class AssetProperty(columnName: String, value: Any)
  case class CsvAssetRow(properties: Seq[AssetProperty])

  type ParsedCsv = (MalformedParameters, Seq[CsvAssetRowAndRoadLink])


  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  val trafficSignService: TrafficSignService = Digiroad2Context.trafficSignService
  val roadLinkService: RoadLinkService = Digiroad2Context.roadLinkService

  lazy val manoeuvreService: ManoeuvreService = Digiroad2Context.manoeuvreService
  lazy val prohibitionService: ProhibitionService = Digiroad2Context.prohibitionService

  private val longValueFieldMappings = Map(
    "koordinaatti x" -> "lon",
    "koordinaatti y" -> "lat"
  )

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

  val mappings = longValueFieldMappings ++ nonMandatoryMappings ++ codeValueFieldMappings

  private val mandatoryFields = List("koordinaatti x", "koordinaatti y", "liikennemerkin tyyppi")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  private def verifyDoubleType(parameterName: String, parameterValue: String): ParsedAssetRow = {
    if(parameterValue.matches("[0-9.]*")) {
      (Nil, List(AssetProperty(columnName = longValueFieldMappings(parameterName), value = BigDecimal(parameterValue))))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def verifyValueCode(parameterName: String, parameterValue: String): ParsedAssetRow = {
    if(parameterValue.forall(_.isDigit) && TrafficSignType.applyTRValue(parameterValue.toInt).source.contains("CSVimport")){
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    }else{
      (List(parameterName), Nil)
    }
  }

  def tryToInt(propertyValue: String ) = {
    Try(propertyValue.toInt).toOption
  }

  private def verifyData(parsedRow: CsvAssetRow): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(userProvider.getCurrentUser(), Point(lon.toLong, lat.toLong))
        if(roadLinks.isEmpty) {
          (List(s"Try to create in an unauthorized Municipality"), Seq())
        } else
          (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
      case _ =>
        (Nil, Nil) //That condition is already checked on assetRowToProperties
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
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
  private def getPropertyValue(trafficSignAttributes: CsvAssetRow, propertyName: String) : Any = {
    trafficSignAttributes.properties.find (prop => prop.columnName == propertyName).map(_.value).get
  }

  private def getPropertyValueOption(trafficSignAttributes: CsvAssetRow, propertyName: String) : Option[Any] = {
    trafficSignAttributes.properties.find (prop => prop.columnName == propertyName).map(_.value)
  }

  private def generateBaseProperties(trafficSignAttributes: CsvAssetRow) : Set[SimpleTrafficSignProperty] = {
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

  def createTrafficSigns(trafficSignAttributes: Seq[CsvAssetRowAndRoadLink]): Unit = {
    val roadLinks = trafficSignAttributes.flatMap(_.roadLink)

    val signs = trafficSignAttributes.map { trafficSignAttribute =>
      val properties = trafficSignAttribute.properties
      val roadLinks = trafficSignAttribute.roadLink
      val optBearing = tryToInt(getPropertyValue(properties, "bearing").toString)
      val optTrafficDirection = tryToInt(getPropertyValue(properties, "trafficDirection").toString)
      val twoSided = getPropertyValue(properties, "twoSided").toString match {
        case "Kaksipuoleinen" => true
        case _ => false
      }
      val lon = getPropertyValue(properties, "lon").asInstanceOf[BigDecimal].toLong
      val lat = getPropertyValue(properties, "lat").asInstanceOf[BigDecimal].toLong

      val closestLink: VVHRoadlink = roadLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon.toLong, lat.toLong), r.geometry))

      val roadLink = roadLinkService.enrichRoadLinksFromVVH(Seq(closestLink)).head
      val validityDirection = trafficSignService.getValidityDirection(Point(lon, lat), roadLink, optBearing, twoSided)

      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(lon, lat), roadLinks.head.geometry)
      CsvTrafficSign(lon, lat, closestLink.linkId, generateBaseProperties(properties), validityDirection, Some(GeometryUtils.calculateBearing(roadLink.geometry)), mValue, roadLink)
    }

    val (additionalPanelInfo, trafficSignInfo) = signs.partition{ sign =>
      TrafficSignType.applyOTHValue(sign.propertyData.find(p => p.publicId == typePublicId).get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toString.toInt).group == AdditionalPanels}

    val additionalPanels = additionalPanelInfo.map {panel => AdditionalPanelInfo(panel.mValue, panel.roadLink.linkId, panel.propertyData, panel.validityDirection)}.toSet

    trafficSignInfo.foreach { sign =>
      val signType = sign.propertyData.find(p => p.publicId == typePublicId).get.values.headOption.get.asInstanceOf[TextPropertyValue].propertyValue.toString.toInt
      val filteredAdditionalPanel = trafficSignService.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, signType, sign.roadLink.geometry, additionalPanels, roadLinks)

      val propertyData = trafficSignService.additionalPanelProperties(filteredAdditionalPanel) ++ sign.propertyData
      trafficSignService.createFromCoordinates(IncomingTrafficSign(sign.lon, sign.lat, sign.roadLink.linkId, propertyData, sign.validityDirection, sign.bearing), sign.roadLink, roadLinks)
    }
  }

  def importTrafficSigns(inputStream: InputStream, municipalitiesToExpire: Set[Int]): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })

    withDynTransaction{
      trafficSignService.expireAssetsByMunicipalities(municipalitiesToExpire)
      val result = csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
        val csvRow = row.map(r => (r._1.toLowerCase, r._2))
        val missingParameters = findMissingParameters(csvRow)
        val (malformedParameters, properties) = assetRowToProperties(csvRow)
        val (notImportedParameters, parsedRowAndRoadLink) = verifyData(CsvAssetRow(properties))

        if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
          result.copy(
            incompleteAssets = missingParameters match {
              case Nil => result.incompleteAssets
              case parameters =>
                IncompleteAsset(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteAssets
            },
            malformedAssets = malformedParameters match {
              case Nil => result.malformedAssets
              case parameters =>
                MalformedAsset(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedAssets
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

      createTrafficSigns(result.createdData)
      result
      }
    }
}

class RoadLinkCsvImporter extends CsvDataImporterOperations {

  case class NonUpdatedLink(linkId: Long, csvRow: String)
  case class IncompleteLink(missingParameters: List[String], csvRow: String)
  case class MalformedLink(malformedParameters: List[String], csvRow: String)
  case class ExcludedLink(unauthorizedAdminClass: List[String], csvRow: String)
  case class ImportResult(nonUpdatedLinks: List[NonUpdatedLink] = Nil,
                          incompleteLinks: List[IncompleteLink] = Nil,
                          malformedLinks: List[MalformedLink] = Nil,
                          excludedLinks: List[ExcludedLink] = Nil)

  case class LinkProperty(columnName: String, value: Any)
  case class CsvRoadLinkRow(linkId: Int, objectID: Int = 0, properties: Seq[LinkProperty])

  type IncompleteParameters = List[String]
  type ParsedProperties = List[LinkProperty]
  type MalformedParameters = List[String]
  type ParsedLinkRow = (MalformedParameters, ParsedProperties)

  private val administrativeClassLimitations: List[AdministrativeClass] = List(State)
  val autorizedValues: List[Int] = List(-11, -1, 0, 1, 2, 3, 4, 5, 10)

  private val intFieldMappings = Map(
    "Hallinnollinen luokka" -> "ADMINCLASS",
    "Toiminnallinen luokka" -> "functional_Class",
    "Liikennevirran suunta" -> "DIRECTIONTYPE",
    "Tielinkin tyyppi" -> "link_Type",
    "Kuntanumero" -> "MUNICIPALITYCODE",
    "Osoitenumerot oikealla alku" -> "FROM_RIGHT",
    "Osoitenumerot oikealla loppu" -> "TO_RIGHT",
    "Osoitenumerot vasemmalla alku" -> "FROM_LEFT",
    "Osoitenumerot vasemmalla loppu" -> "TO_LEFT",
    "Linkin tila" -> "CONSTRUCTIONTYPE"
  )

  private val codeValueFieldMappings = Map(
    "Tasosijainti" -> "VERTICALLEVEL"
  )

  private val fieldInOTH = List("link_Type", "functional_Class")

  private val textFieldMappings = Map(
    "Tien nimi (suomi)" -> "ROADNAME_FI",
    "Tien nimi (ruotsi)" -> "ROADNAME_SE",
    "Tien nimi (saame)" -> "ROADNAME_SM"
  )

  private val mandatoryFields = "Linkin ID"


  val mappings = textFieldMappings ++ intFieldMappings ++ codeValueFieldMappings

  val MandatoryParameters: Set[String] = mappings.keySet + mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def updateRoadLinkOTH(roadLinkAttribute: CsvRoadLinkRow, username: Option[String], hasTrafficDirectionChange: Boolean): Option[Long] = {
    try {
      if (hasTrafficDirectionChange) {
        RoadLinkDAO.get(RoadLinkDAO.TrafficDirection, roadLinkAttribute.linkId) match {
          case Some(value) => RoadLinkDAO.delete(RoadLinkDAO.TrafficDirection, roadLinkAttribute.linkId)
          case _ => None
        }
      }

      roadLinkAttribute.properties.map { prop =>
        val optionalLinkTypeValue: Option[Int] = RoadLinkDAO.get(prop.columnName, roadLinkAttribute.linkId)
        optionalLinkTypeValue match {
          case Some(existingValue) =>
            RoadLinkDAO.update(prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt, existingValue)
          case None =>
            RoadLinkDAO.insert(prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt)
        }
      }
      None
    } catch {
      case ex: Exception => Some(roadLinkAttribute.linkId)
    }
  }

  def updateRoadLinkInVVH(roadLinkVVHAttribute: CsvRoadLinkRow): Option[Long] = {
    val timeStamps = new java.util.Date().getTime
    val mapProperties = roadLinkVVHAttribute.properties.map { prop => prop.columnName -> prop.value }.toMap ++ Map("LAST_EDITED_DATE" -> timeStamps) ++ Map("OBJECTID" -> roadLinkVVHAttribute.objectID)
    vvhClient.complementaryData.updateVVHFeatures(mapProperties) match {
      case Right(error) => Some(roadLinkVVHAttribute.linkId)
      case _ => None
    }
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedLinkRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(LinkProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  def verifyValueCode(parameterName: String, parameterValue: String): ParsedLinkRow = {
    if (parameterValue.forall(_.isDigit) && autorizedValues.contains(parameterValue.toInt)) {
      (Nil, List(LinkProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    } else {
      (List(parameterName), Nil)
    }
  }

  private def linkRowToProperties(csvRowWithHeaders: Map[String, Any]): ParsedLinkRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(mandatoryFields) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (textFieldMappings.contains(key)) {
          result.copy(_2 = LinkProperty(columnName = textFieldMappings(key), value = value) :: result._2)
        } else if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (codeValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueCode(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (mandatoryFields.contains(key) && !value.toString.forall(_.isDigit)) {
          result.copy(_1 = List(mandatoryFields) ::: result._1 , _2 = result._2)
        } else
          result
      }
    }
  }

  def validateAdministrativeClass(optionalAdminClassValue : Option[Any], dataLocation: String): List[String] = {
    optionalAdminClassValue match {
      case Some(adminClassValue) if (administrativeClassLimitations.contains(AdministrativeClass.apply(adminClassValue.toString.toInt))) =>
                List("AdminClass value State found on  " ++ dataLocation)
      case _ => List()
    }
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  def importLinkAttribute(inputStream: InputStream): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
      def getCompletaryVVHInfo(linkId: Long) = {
        vvhClient.complementaryData.fetchByLinkId(linkId) match {
          case Some(vvhRoadlink) => (vvhRoadlink.attributes.get("OBJECTID"), vvhRoadlink.administrativeClass.value)
          case _ => None
        }
      }

      val missingParameters = findMissingParameters(row)
      val (malformedParameters, properties) = linkRowToProperties(row)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty) {
        result.copy(
          incompleteLinks = missingParameters match {
            case Nil => result.incompleteLinks
            case parameters => IncompleteLink(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteLinks
          },
          malformedLinks = malformedParameters match {
            case Nil => result.malformedLinks
            case parameters => MalformedLink(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedLinks
          })
      } else {
        val (objectId, oldAdminClassValue) = getCompletaryVVHInfo(row("Linkin ID").toInt) match {
          case None => (None, None)
          case (Some(objId), adminClass) => (objId, adminClass)
        }

        val unauthorizedAdminClass = (oldAdminClassValue match {
            case None => List("AdminClass value Unknown found on VVH")
            case adminClassValue => validateAdministrativeClass(Some(adminClassValue) , "VVH")
        }) ++ validateAdministrativeClass( properties.filter (prop => prop.columnName == "ADMINCLASS").map(_.value).headOption, "CSV")

        if (unauthorizedAdminClass.isEmpty && objectId != None) {
          val (propertiesOTH, propertiesVVH) = properties.partition(a => fieldInOTH.contains(a.columnName))
          val hasDirectionType = propertiesVVH.exists(_.columnName == "DIRECTIONTYPE")

          withDynTransaction {
            if (propertiesOTH.nonEmpty || hasDirectionType) {
              val parsedRowOTH = CsvRoadLinkRow(row("Linkin ID").toInt, properties = propertiesOTH)
              updateRoadLinkOTH(parsedRowOTH, Some(userProvider.getCurrentUser().username), hasDirectionType) match {
                case None => result
                case Some(value) =>
                  result.copy(nonUpdatedLinks = NonUpdatedLink(linkId = value, csvRow = rowToString(row)) :: result.nonUpdatedLinks)
              }
            }
            if (propertiesVVH.nonEmpty) {
              val parsedRowVVH = CsvRoadLinkRow(row("Linkin ID").toInt, objectId.toString.toInt, properties = propertiesVVH)
              updateRoadLinkInVVH(parsedRowVVH) match {
                case None => result
                case Some(value) =>
                  dynamicSession.rollback()
                  result.copy(nonUpdatedLinks = NonUpdatedLink(linkId = value, csvRow = rowToString(row)) :: result.nonUpdatedLinks)
              }
            } else result
          }
        } else {
          result.copy(
            nonUpdatedLinks = objectId match {
              case None => NonUpdatedLink(linkId = row("Linkin ID").toInt, csvRow = rowToString(row)) :: result.nonUpdatedLinks
              case _ => result.nonUpdatedLinks
            },
            excludedLinks = unauthorizedAdminClass match {
              case Nil => result.excludedLinks
              case parameters => ExcludedLink(unauthorizedAdminClass = parameters, csvRow = rowToString(row)) :: result.excludedLinks
            }
          )
        }
      }
    }
  }
}

class MaintenanceRoadCsvImporter extends CsvDataImporterOperations {

  type MalformedParameters = List[String]
  type ParsedProperties = List[AssetProperty]
  type ParsedAssetRow = (MalformedParameters, ParsedProperties)

  case class IncompleteAsset(missingParameters: List[String], csvRow: String)
  case class MalformedAsset(malformedParameters: List[String], csvRow: String)
  case class ExcludedAsset(affectedRoadLinkType: String, csvRow: String)
  case class ImportResult(incompleteAssets: List[IncompleteAsset] = Nil,
                          malformedAssets: List[MalformedAsset] = Nil,
                          excludedAssets: List[ExcludedAsset] = Nil)
  case class AssetProperty(columnName: String, value: Any)
  case class CsvAssetRow(properties: Seq[AssetProperty])

  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  val maintenanceService: MaintenanceService = Digiroad2Context.maintenanceRoadService

  private val intFieldMappings = Map(
    "new_ko" -> "rightOfUse",
    "or_access" -> "maintenanceResponsibility",
    "linkid" -> "linkid"
  )

  val mappings = intFieldMappings

  private val mandatoryFields = List("linkid", "new_ko", "or_access")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
    csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedAssetRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else
          result
      }
    }
  }

  def getPropertyValue(maintenanceRoadAttributes: CsvAssetRow, propertyName: String) = {
    maintenanceRoadAttributes.properties.find (prop => prop.columnName == propertyName).map(_.value).get
  }

  def createMaintenanceRoads(maintenanceRoadAttributes: CsvAssetRow): Unit = {
    val linkId = getPropertyValue(maintenanceRoadAttributes, "linkid").asInstanceOf[Integer].toLong
    val newKoProperty = Props("huoltotie_kayttooikeus", "single_choice", getPropertyValue(maintenanceRoadAttributes, "rightOfUse").toString())
    val orAccessProperty = Props("huoltotie_huoltovastuu", "single_choice", getPropertyValue(maintenanceRoadAttributes, "maintenanceResponsibility").toString())

    Digiroad2Context.roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(linkId)).map { roadlink =>
      val values = MaintenanceRoad(Seq(newKoProperty, orAccessProperty))

      maintenanceService.createWithHistory(MaintenanceRoadAsset.typeId, linkId, values,
        SideCode.BothDirections.value, Measures(0, roadlink.length), userProvider.getCurrentUser().username, Some(roadlink))
    }
  }


  def importMaintenanceRoads(inputStream: InputStream): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
      val csvRow = row.map(r => (r._1.toLowerCase, r._2))
      val missingParameters = findMissingParameters(csvRow)
      val (malformedParameters, properties) = assetRowToProperties(csvRow)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty) {
        result.copy(
          incompleteAssets = missingParameters match {
            case Nil => result.incompleteAssets
            case parameters => IncompleteAsset(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteAssets
          },
          malformedAssets = malformedParameters match {
            case Nil => result.malformedAssets
            case parameters => MalformedAsset(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedAssets
          })

      } else {
        val parsedRow = CsvAssetRow(properties = properties)
        createMaintenanceRoads(parsedRow)
        result
      }

    }
  }
}