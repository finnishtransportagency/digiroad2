package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}
import java.util.Properties

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.apache.commons.lang3.StringUtils.isBlank
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.client.tierekisteri.TRTrafficSignType
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService

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
  val trafficSignService: TrafficSignService = Digiroad2Context.trafficSignService

  private val supportedTrafficSigns = Set[TRTrafficSignType](TRTrafficSignType.SpeedLimit, TRTrafficSignType.EndSpeedLimit, TRTrafficSignType.SpeedLimitZone, TRTrafficSignType.EndSpeedLimitZone,
    TRTrafficSignType.UrbanArea, TRTrafficSignType.EndUrbanArea, TRTrafficSignType.PedestrianCrossing, TRTrafficSignType.MaximumLength, TRTrafficSignType.Warning,
    TRTrafficSignType.NoLeftTurn, TRTrafficSignType.NoRightTurn, TRTrafficSignType.NoUTurn, TRTrafficSignType.ClosedToAllVehicles, TRTrafficSignType.NoPowerDrivenVehicles,
    TRTrafficSignType.NoLorriesAndVans, TRTrafficSignType.NoVehicleCombinations, TRTrafficSignType.NoAgriculturalVehicles, TRTrafficSignType.NoMotorCycles, TRTrafficSignType.NoMotorSledges,
    TRTrafficSignType.NoVehiclesWithDangerGoods, TRTrafficSignType.NoBuses, TRTrafficSignType.NoMopeds, TRTrafficSignType.NoCyclesOrMopeds, TRTrafficSignType.NoPedestrians,
    TRTrafficSignType.NoPedestriansCyclesMopeds, TRTrafficSignType.NoRidersOnHorseback, TRTrafficSignType.NoEntry, TRTrafficSignType.OvertakingProhibited, TRTrafficSignType.EndProhibitionOfOvertaking,
    TRTrafficSignType.MaxWidthExceeding, TRTrafficSignType.MaxHeightExceeding, TRTrafficSignType.MaxLadenExceeding, TRTrafficSignType.MaxMassCombineVehiclesExceeding, TRTrafficSignType.MaxTonsOneAxleExceeding,
    TRTrafficSignType.MaxTonsOnBogieExceeding, TRTrafficSignType.WRightBend, TRTrafficSignType.WLeftBend, TRTrafficSignType.WSeveralBendsRight, TRTrafficSignType.WSeveralBendsLeft,
    TRTrafficSignType.WDangerousDescent, TRTrafficSignType.WSteepAscent, TRTrafficSignType.WUnevenRoad, TRTrafficSignType.WChildren)

  private val longValueFieldMappings = Map(
    "koordinaatti x" -> "lon",
    "koordinaatti y" -> "lat"
  )

  private val nonMandatoryMappings = Map(
    "arvo" -> "value",
    "kaksipuolinen merkki" -> "twoSided",
    "liikennevirran suunta" -> "trafficDirection",
    "suuntima" -> "bearing"
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
    if(parameterValue.forall(_.isDigit) && supportedTrafficSigns.contains(TRTrafficSignType.apply(parameterValue.toInt))){
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue.toInt)))
    }else{
      (List(parameterName), Nil)
    }
  }

  def tryToInt(propertyValue: String ) = {
    Try(propertyValue.toInt).toOption
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedAssetRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else if ((nonMandatoryMappings.contains(key))) {
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
  def getPropertyValue(trafficSignAttributes: CsvAssetRow, propertyName: String) = {
    trafficSignAttributes.properties.find (prop => prop.columnName == propertyName).map(_.value).get
  }
  def createTrafficSigns(trafficSignAttributes: CsvAssetRow): Unit ={
    val value = tryToInt(getPropertyValue(trafficSignAttributes, "value").toString)
    val trafficSignType = getPropertyValue(trafficSignAttributes, "trafficSignType").toString.toInt
    val bearing = tryToInt(getPropertyValue(trafficSignAttributes, "bearing").toString)
    val trafficDirection = tryToInt(getPropertyValue(trafficSignAttributes, "trafficDirection").toString)
    val twoSided = getPropertyValue(trafficSignAttributes, "twoSided").toString match { case "Kaksipuoleinen" => true case _ => false }
    val lon = getPropertyValue(trafficSignAttributes, "lon").asInstanceOf[BigDecimal].toLong
    val lat = getPropertyValue(trafficSignAttributes, "lat").asInstanceOf[BigDecimal].toLong

    trafficSignService.createFromCoordinates(lon, lat, TRTrafficSignType.apply(trafficSignType), value, Some(twoSided), TrafficDirection.apply(trafficDirection), bearing)
  }


  def importTrafficSigns(inputStream: InputStream): ImportResult = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportResult()) { (result, row) =>
      val csvRow = row.map( r =>(r._1.toLowerCase, r._2))
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
          createTrafficSigns(parsedRow)
          result
      }

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
        RoadLinkServiceDAO.getTrafficDirectionValue(roadLinkAttribute.linkId) match {
          case Some(value) => RoadLinkServiceDAO.deleteTrafficDirection(roadLinkAttribute.linkId)
          case _ => None
        }
      }

      roadLinkAttribute.properties.map { prop =>
        val optionalLinkTypeValue: Option[Int] = RoadLinkServiceDAO.getLinkProperty(prop.columnName, prop.columnName, roadLinkAttribute.linkId)
        optionalLinkTypeValue match {
          case Some(existingValue) =>
            RoadLinkServiceDAO.updateExistingLinkPropertyRow(prop.columnName, prop.columnName, roadLinkAttribute.linkId, username, existingValue, prop.value.toString.toInt)
          case None =>
            RoadLinkServiceDAO.insertNewLinkProperty(prop.columnName, prop.columnName, roadLinkAttribute.linkId, username, prop.value.toString.toInt)
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