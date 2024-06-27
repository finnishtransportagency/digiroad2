package fi.liikennevirasto.digiroad2.middleware

import java.sql.SQLIntegrityConstraintViolationException
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.ManoeuvreService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignInfo
import org.joda.time.DateTime
import org.json4s.jackson.Json
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JInt, JNull, JString}


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

  val roadWorkRelatedSigns : Seq[TrafficSignType] = Seq(RoadWorks)
  def belongsToRoadwork(intValue: Int) : Boolean = {
    roadWorkRelatedSigns.contains(TrafficSignType.applyOTHValue(intValue))
  }
}

case class TrafficSignManager(manoeuvreService: ManoeuvreService, roadLinkService: RoadLinkService) {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ({
    case JInt(lg) => LinkGeomSource.apply(lg.toInt)
    case JNull => LinkGeomSource.Unknown
  }, {
    case lg: LinkGeomSource => JInt(lg.value)
    case _ => JNull
  }))

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(DateParser.dateToString(d, DateParser.DateTimePropertyFormat))
    case JNull => null
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + LinkGeomSourceSerializer + DateTimeSerializer

  lazy val linearAssetDao: PostGISLinearAssetDao = {
    new PostGISLinearAssetDao()
  }
  
  def createAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true ): Unit = {
    trafficSignInfo match {
      case trSign if TrafficSignManager.belongsToManoeuvre(trSign.signType) =>
        manoeuvreService.createBasedOnTrafficSign(trSign, newTransaction)

      case trSign if TrafficSignManager.belongsToProhibition(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, Prohibition, newTransaction = newTransaction)

      case trSign if TrafficSignManager.belongsToHazmat(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition, newTransaction = newTransaction)

      case trSign if TrafficSignManager.belongsToParking(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, ParkingProhibition, newTransaction = newTransaction)

      case trSign if TrafficSignManager.belongsToRoadwork(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, RoadWorksAsset, newTransaction = newTransaction )

      case _ => None
    }
  }

  def deleteAssets(trafficSign: Seq[PersistedTrafficSign], newTransaction: Boolean = true): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    trafficSign.foreach { trSign =>
      val trafficSignType = trSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[PropertyValue]).head.propertyValue.toInt
      trafficSignType match {
        case signType if TrafficSignManager.belongsToManoeuvre(signType) =>
          manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(trSign.id)), username, newTransaction)

        case signType if TrafficSignManager.belongsToProhibition(signType) =>
          insertTrafficSignToProcess(trSign.id, Prohibition, Some(trSign), newTransaction)

        case signType if TrafficSignManager.belongsToHazmat(signType) =>
          insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition, Some(trSign), newTransaction)

        case signType if TrafficSignManager.belongsToParking(signType) =>
          insertTrafficSignToProcess(trSign.id, ParkingProhibition, Some(trSign), newTransaction)

        case signType if TrafficSignManager.belongsToRoadwork(signType) =>
          insertTrafficSignToProcess(trSign.id, RoadWorksAsset, Some(trSign), newTransaction)

        case _ => None
      }
    }
  }

  def insertTrafficSignToProcess(id: Long, assetInfo: AssetTypeInfo, persistedTrafficSign: Option[PersistedTrafficSign] = None, newTransaction: Boolean = false) : Unit = {
    try {
      if(newTransaction) {
        withDynTransaction {
          linearAssetDao.insertTrafficSignsToProcess(id, assetInfo.typeId, Json(jsonFormats).write(persistedTrafficSign.getOrElse("")))
        }
      } else {
        linearAssetDao.insertTrafficSignsToProcess(id, assetInfo.typeId, Json(jsonFormats).write(persistedTrafficSign.getOrElse("")))
      }
    } catch {
      case ex: SQLIntegrityConstraintViolationException => print("try insert duplicate key")
      case e: Exception => print("SQL Exception ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }
}
