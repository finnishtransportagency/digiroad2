package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.dao.ExportReportDAO
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime

// myabe change ImportStatusInfo and ExportStatusInfo to something like FileStatusInfo ?
// and use it on import and export files
case class ExportStatusInfo(id: Long, status: Int, statusDescription: String, fileName: String, createdBy: Option[String], createdDate: Option[DateTime], exportedAssets: String, content: Option[String])


trait CsvDataExporterOperations {

    def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
    def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
    def eventBus: DigiroadEventBus
    val exportReportDAO: ExportReportDAO = new ExportReportDAO


    def getImportById(id: Long) : Option[ExportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        exportReportDAO.get(id)
      }
    }

    def getByUser(username: String) : Seq[ExportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        exportReportDAO.getByUser(username)
      }
    }

    def getById(id: Long) : Option[ExportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        exportReportDAO.get(id)
      }
    }

    def getByIds(ids: Set[Long]) : Seq[ExportStatusInfo]  = {
      OracleDatabase.withDynTransaction {
        exportReportDAO.getByIds(ids)
      }
    }

    def update(id: Long, status: Status, content: Option[String] = None) : Long  = {
      OracleDatabase.withDynTransaction {
        exportReportDAO.update(id, status, content)
      }
    }

    def createStandardCSV(headers: Seq[String], values: Seq[Map[String, String]]): String = {

      if (headers.isEmpty && values.isEmpty)
        return ""

      val headerLine = if(headers.nonEmpty) headers.toList.mkString(";").concat("\r\n")
                        else ""

      val rows = if (values.isEmpty) {
                  Seq()
                }
                else{
                  values.map { mapValues =>
                    if (headers.isEmpty) {
                      mapValues.values.toList
                    }
                    else {
                      var elems = Seq[String]()
                      headers.foreach{h => elems = elems ++ Seq(mapValues(h)) }
                      elems
                    }
                  }
                }

      headerLine ++ rows.map( _.mkString(";")).mkString("\r\n")
    }

}

class CsvDataExporter(eventBusImpl: DigiroadEventBus) extends CsvDataExporterOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def eventBus: DigiroadEventBus = eventBusImpl
}