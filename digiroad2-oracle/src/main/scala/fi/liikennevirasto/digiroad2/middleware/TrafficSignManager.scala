package fi.liikennevirasto.digiroad2.middleware

import java.sql.SQLIntegrityConstraintViolationException
import java.util.Properties

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.Asset.DateTimePropertyFormat
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
}

case class TrafficSignManager(manoeuvreService: ManoeuvreService, roadLinkService: RoadLinkService) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  case object TrafficSignSerializer extends CustomSerializer[TrafficSignProperty](format =>
    ({
      case jsonObj: JObject =>
        val id = (jsonObj \ "id").extract[Long]
        val publicId = (jsonObj \ "publicId").extract[String]
        val propertyType = (jsonObj \ "propertyType").extract[String]
        val values: Seq[PointAssetValue] = (jsonObj \ "values").extractOpt[Seq[TextPropertyValue]].getOrElse((jsonObj \ "values").extractOpt[Seq[AdditionalPanel]].getOrElse(Seq()))
        val required = (jsonObj \ "required").extract[Boolean]
        val numCharacterMax = (jsonObj \ "numCharacterMax").extractOpt[Int]

        TrafficSignProperty(id, publicId, propertyType, required, values, numCharacterMax)
    },
      {
        case tv : SimpleTrafficSignProperty => Extraction.decompose(tv)

      }))

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ({
    case JInt(lg) => LinkGeomSource.apply(lg.toInt)
  }, {
    case lg: LinkGeomSource => JInt(lg.value)
  }))

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + TrafficSignSerializer + LinkGeomSourceSerializer + DateTimeSerializer

  lazy val linearAssetDao: OracleLinearAssetDao = {
    new OracleLinearAssetDao(roadLinkService.vvhClient, roadLinkService)
  }

    def createAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true): Unit = {

      if (TrafficSignManager.belongsToManoeuvre(trafficSignInfo.signType))
        manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)

      else if (TrafficSignManager.belongsToProhibition(trafficSignInfo.signType))
        insertTrafficSignToProcess(trafficSignInfo.id, Prohibition)

      else if (TrafficSignManager.belongsToHazmat(trafficSignInfo.signType))
        insertTrafficSignToProcess(trafficSignInfo.id, HazmatTransportProhibition)
  }

  def deleteAssets(trafficSign: Seq[PersistedTrafficSign]): Unit = {
    val username = Some("automatic_trafficSign_deleted")

    trafficSign.foreach { trSign =>
      val trafficSignType = trSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.map(_.asInstanceOf[TextPropertyValue]).head.propertyValue.toInt

      if (TrafficSignManager.belongsToManoeuvre(trafficSignType))
        manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(trSign.id)), username)

       else if (TrafficSignManager.belongsToProhibition(trafficSignType))
        insertTrafficSignToProcess(trSign.id, Prohibition, Some(trSign))

      else if(TrafficSignManager.belongsToHazmat(trafficSignType))
        insertTrafficSignToProcess(trSign.id, HazmatTransportProhibition, Some(trSign))

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
