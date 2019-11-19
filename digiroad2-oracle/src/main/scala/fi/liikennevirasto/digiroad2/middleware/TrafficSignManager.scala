package fi.liikennevirasto.digiroad2.middleware

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{AdditionalInformation, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset.ManoeuvreService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime
import org.json4s
import org.json4s.jackson.Json
import org.json4s.{CustomSerializer, DefaultFormats, Extraction, Formats, JInt, JObject, JString}

object TrafficSignManager {
  val manoeuvreRelatedSigns : Seq[TrafficSignType] =  Seq(NoLeftTurn, NoRightTurn, NoUTurn)
  def belongsToManoeuvre(intValue: Int) : Boolean = {
    manoeuvreRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }

  val prohibitionRelatedSigns : Seq[TrafficSignType] = Seq(ClosedToAllVehicles,  NoPowerDrivenVehicles,  NoLorriesAndVans,  NoVehicleCombinations, NoAgriculturalVehicles,
    NoMotorCycles,  NoMotorSledges, NoBuses,  NoMopeds,  NoCyclesOrMopeds,  NoPedestrians,  NoPedestriansCyclesMopeds,  NoRidersOnHorseback)
  def belongsToProhibition(intValue: Int) : Boolean = {
    prohibitionRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }

  val hazmatRelatedSigns : Seq[TrafficSignType] = Seq(NoVehiclesWithDangerGoods)
  def belongsToHazmat(intValue: Int) : Boolean = {
    hazmatRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }

  val parkingRelatedSigns : Seq[TrafficSignType] = Seq(StandingAndParkingProhibited, ParkingProhibited)
  def belongsToParking(intValue: Int) : Boolean = {
    parkingRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }
}

case class TrafficSignManager(manoeuvreService: ManoeuvreService, roadLinkService: RoadLinkService) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ({
    case JInt(lg) => LinkGeomSource.apply(lg.toInt)
  }, {
    case lg: LinkGeomSource => JInt(lg.value)
  }))

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(DateParser.dateToString(d, DateParser.DateTimePropertyFormat))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + LinkGeomSourceSerializer + DateTimeSerializer

  lazy val linearAssetDao: OracleLinearAssetDao = {
    new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)
  }

  def createAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true): Unit = {
    trafficSignInfo match {
      case trSign if TrafficSignManager.belongsToManoeuvre(trSign.signType) =>
        manoeuvreService.createBasedOnTrafficSign(trSign, newTransaction)

      case trSign if TrafficSignManager.belongsToProhibition(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, Prohibition)

      case trSign if TrafficSignManager.belongsToHazmat(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition)

      case trSign if TrafficSignManager.belongsToParking(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, ParkingProhibition)

      case _ => None
    }
  }

  def deleteAssets(trafficSign: Seq[PersistedTrafficSign], newTransaction: Boolean = true): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    trafficSign.foreach { trSign =>
      val trafficSignType = trSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt
      trafficSignType match {
        case signType if TrafficSignManager.belongsToManoeuvre(signType) =>
          manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(trSign.id)), username, newTransaction)

        case signType if TrafficSignManager.belongsToProhibition(signType) =>
          insertTrafficSignToProcess(trSign.id, Prohibition, Some(trSign))

        case signType if TrafficSignManager.belongsToHazmat(signType) =>
          insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition, Some(trSign))

        case signType if TrafficSignManager.belongsToParking(signType) =>
          insertTrafficSignToProcess(trSign.id, ParkingProhibition, Some(trSign))

        case _ => None
      }
    }
  }

  def insertTrafficSignToProcess(id: Long, assetInfo: AssetTypeInfo, persistedTrafficSign: Option[PersistedTrafficSign] = None) : Unit = {
    try {
      withDynTransaction {
        linearAssetDao.insertTrafficSignsToProcess(id, assetInfo.typeId, Json(jsonFormats).write(persistedTrafficSign.getOrElse("")))
      }
    } catch {
      case ex: SQLIntegrityConstraintViolationException => print("try insert duplicate key")
      case e: Exception => print("SQL Exception ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }
}
