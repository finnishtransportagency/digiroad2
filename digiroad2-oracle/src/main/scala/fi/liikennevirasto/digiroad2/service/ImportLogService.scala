//package fi.liikennevirasto.digiroad2.service
//
//import java.io.{InputStream, InputStreamReader}
//
//import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
//import fi.liikennevirasto.digiroad2.dao.ImportLogDAO
//import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
//import org.joda.time.DateTime
//
//
//sealed trait Status {
//  def value : Int
//  def description: String
//}
//
//object Status {
//  val values : Set[Status] = Set(InProgress, OK, NotOK, Abend)
//
//  def apply(value: Int) : Status = {
//    values.find(_.value == value).getOrElse(Unknown)
//  }
//
//  case object InProgress extends Status {def value = 1; def description = "In progress ..."}
//  case object OK extends Status {def value = 1; def description = "All records was treated "}
//  case object NotOK extends Status {def value = 1; def description = "Process Executed but some fail records"}
//  case object Abend extends Status {def value = 1; def description = "Process fail"}
//  case object Unknown extends Status {def value = 99; def description = "Unknown Status Type"}
//}
//
//case class ImportStatusInfo(id: Long, status: Status, fileName: String, createdBy: Option[String], createdDate: Option[DateTime], logType: String, content: Option[String])
//
//class ImportLogService {
//  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
//  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
//  def importLogDao: ImportLogDAO = new ImportLogDAO
//
//  val ROAD_LINK_LOG = "road link import"
//  val TRAFFIC_SIGN_LOG = "traffic sign import"
//  val DELETE_TRAFFIC_SIGN_LOG = "traffic sign delete"
//  val MAINTENANCE_ROAD_LOG = "maintenance import"
//  val BUS_STOP_LOG = "bus stop import"
//
//  def getImportById(id: Long) : Option[ImportStatusInfo]  = {
//    OracleDatabase.withDynTransaction {
//      importLogDao.get(id)
//    }
//  }
//
//  def getByUser(username: String, logTypes: Seq[String]) : Seq[ImportStatusInfo]  = {
//    OracleDatabase.withDynTransaction {
//      importLogDao.getByUser(username, logTypes)
//    }
//  }
//
//  def getById(id: Long) : Option[ImportStatusInfo]  = {
//    OracleDatabase.withDynTransaction {
//      importLogDao.get(id)
//    }
//  }
//
//  def update(id: Long, status: Status, content: Option[String] = None) : Long  = {
//    OracleDatabase.withDynTransaction {
//      importLogDao.update(id, status, content)
//    }
//  }
//
//  def create(username: String, logType: String, fileName: String) : Long  = {
//    OracleDatabase.withDynTransaction {
//      importLogDao.create(username, logType, fileName)
//    }
//  }
//
//}
//
////trait ImportCSVService extends ImportLogServiceOperation {
////  val logTypes : Seq[String] = Seq(ROAD_LINK_LOG, TRAFFIC_SIGN_LOG, DELETE_TRAFFIC_SIGN_LOG, MAINTENANCE_ROAD_LOG)
////
////
////  override def getByUser(username: String) : Seq[ImportStatusInfo]  = {
////    OracleDatabase.withDynTransaction {
////      importLogDao.getByUser(username, logTypes)
////    }
////  }
////
////  override def create(username: String, fileName: String) : Long  = {
////    OracleDatabase.withDynTransaction {
////      importLogDao.create(username, logType, fileName)
////    }
////  }
////}
////
////class ImportRoadLinkService extends ImportCSVService {
////  override val logType : String = ROAD_LINK_LOG
////}
////
////class ImportTrafficSignService extends ImportCSVService {
////  override val logType : String = TRAFFIC_SIGN_LOG
////}
////
////class ImportDeleteTrafficSignService extends ImportCSVService {
////  override val logType : String = DELETE_TRAFFIC_SIGN_LOG
////}
////
////class ImportMaintenanceRoadSignService extends ImportCSVService {
////  override val logType : String = MAINTENANCE_ROAD_LOG
////}
////
////class ImportMassTransitStopService extends ImportLogServiceOperation {
////  val logType : String = BUS_STOP_LOG
////
////  override def getByUser(username: String) : Seq[ImportStatusInfo]  = {
////    OracleDatabase.withDynTransaction {
////      importLogDao.getByUser(username, Seq(logType))
////    }
////  }
////}
//
////class ImportLogService extends ImportLogServiceOperation {
////  override def getByUser(username: String) : Seq[ImportStatusInfo] = throw new UnsupportedOperationException("Not supported method")
////  override val logType : String = ""
////}
