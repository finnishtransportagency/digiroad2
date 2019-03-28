package fi.liikennevirasto.digiroad2.middleware

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrafficSignDao, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{AdditionalInformation, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset.ManoeuvreService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.json4s.jackson.Json
import org.json4s.{CustomSerializer, DefaultFormats, Extraction, Formats, JObject, JString}

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
}

case class TrafficSignManager(manoeuvreService: ManoeuvreService, roadLinkService: RoadLinkService) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  case object TrafficSignSerializer extends CustomSerializer[SimpleTrafficSignProperty](format =>
    ({
      case jsonObj: JObject =>
        val publicId = (jsonObj \ "publicId").extract[String]
        val propertyValue: Seq[PointAssetValue] = (jsonObj \ "values").extractOpt[Seq[TextPropertyValue]].getOrElse((jsonObj \ "values").extractOpt[Seq[AdditionalPanel]].getOrElse(Seq()))

        SimpleTrafficSignProperty(publicId, propertyValue)
    },
      {
        case tv : SimpleTrafficSignProperty =>
          Extraction.decompose(tv)
      }))

  case object AdditionalInfoClassSerializer extends CustomSerializer[AdditionalInformation](format => ( {
    case JString(additionalInfo) => AdditionalInformation(additionalInfo)
  }, {
    case ai: AdditionalInformation => JString(ai.toString)
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats

  lazy val linearAssetDao: OracleLinearAssetDao = {
    new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)
  }
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, userProvider, new DummyEventBus)
  }


    def createAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true): Unit = {
    trafficSignInfo match {
      case trSign if TrafficSignManager.belongsToManoeuvre(trSign.signType) =>
        manoeuvreService.createBasedOnTrafficSign(trSign, newTransaction)

      case trSign if TrafficSignManager.belongsToProhibition(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, Prohibition, trSign.propertyData)

      case trSign if TrafficSignManager.belongsToHazmat(trSign.signType) =>
        insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition, trafficSignInfo.propertyData)

      case _ => None
    }
  }

  def deleteAssets(trafficSign: Seq[PersistedTrafficSign]): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    trafficSign.foreach { trSign =>
      val trafficSignType = trSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt

      trafficSignType match {
        case signType if TrafficSignManager.belongsToManoeuvre(signType) =>
          manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(trSign.id)), username)

        case signType  if TrafficSignManager.belongsToProhibition(signType) =>
            insertTrafficSignToProcess(trSign.id, Prohibition, trSign.propertyData.map(_.asInstanceOf[SimpleTrafficSignProperty]).toSet)

        case signType if TrafficSignManager.belongsToHazmat(signType) =>
            insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition, trSign.propertyData.map(_.asInstanceOf[SimpleTrafficSignProperty]).toSet)

      }
    }
  }

  def insertTrafficSignToProcess(id: Long, assetInfo: AssetTypeInfo, properties: Set[SimpleTrafficSignProperty]) : Unit = {
    val persist = trafficSignService.getById(id)
    try {
      withDynTransaction {

        linearAssetDao.insertTrafficSignsToProcess(id, assetInfo.typeId, Json(jsonFormats).write(properties))
      }
    } catch {
      case ex: SQLIntegrityConstraintViolationException => print("try insert duplicate key")
      case e: Exception => print("SQL Exception ")
        throw new RuntimeException("SQL exception " + e.getMessage)
    }
  }
}
