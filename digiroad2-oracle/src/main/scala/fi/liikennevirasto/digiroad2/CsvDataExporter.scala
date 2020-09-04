package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.ExportReportDAO
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime


case class ExportStatusInfo(id: Long, status: Int, statusDescription: String, fileName: String,
                            createdBy: Option[String], createdDate: Option[DateTime], exportedAssets: String,
                            municipalities: String, content: Option[String])


trait CsvDataExporterOperations {

    def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
    def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
    def eventBus: DigiroadEventBus
    val exportReportDAO: ExportReportDAO = new ExportReportDAO

    def insertData(username: String, fileName: String, exportType: String, municipalities: String, withTransaction: Boolean = true): Long = {
      if (withTransaction)
        withDynTransaction {
          exportReportDAO.create(username, fileName, exportType, municipalities )
        }
      else
        exportReportDAO.create(username, fileName, exportType, municipalities )

    }

    def getByUser(username: String, withTransaction: Boolean = true) : Seq[ExportStatusInfo]  = {
      if (withTransaction)
        withDynTransaction {
          exportReportDAO.getByUser(username)
        }
      else
        exportReportDAO.getByUser(username)
    }

    def getInfoById(id: Long, withTransaction: Boolean = true) : Option[ExportStatusInfo]  = {
      if (withTransaction)
        withDynTransaction {
          exportReportDAO.get(id)
        }
      else
        exportReportDAO.get(id)
    }

    def getByIds(ids: Set[Long], withTransaction: Boolean = true) : Seq[ExportStatusInfo]  = {
      if (withTransaction)
        withDynTransaction {
          exportReportDAO.getByIds(ids)
        }
      else
        exportReportDAO.getByIds(ids)
    }

    def update(id: Long, status: Status, content: Option[String] = None, withTransaction: Boolean = true) : Long  = {
      if (withTransaction)
        withDynTransaction {
          exportReportDAO.update(id, status, content)
        }
      else
        exportReportDAO.update(id, status, content)
    }

    def createStandardCSVData(headers: Seq[String], values: Seq[Map[String, String]]): String = {

      if (headers.isEmpty && values.isEmpty)
        return ""

      val headerLine = if(headers.nonEmpty) headers.toList.mkString(";").concat("\r\n")
                        else ""

      val rows = values.map { mapValues =>
                    if (headers.isEmpty) {
                      mapValues.values.toList
                    }
                    else {
                      headers.foldLeft(Seq[String]()) { case (elems, h) =>
                        elems :+ mapValues(h)
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