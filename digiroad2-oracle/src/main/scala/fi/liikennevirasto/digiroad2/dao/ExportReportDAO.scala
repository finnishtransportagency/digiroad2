package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.{ExportStatusInfo, Status}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation


class ExportReportDAO {
  val logger = LoggerFactory.getLogger(getClass)

  lazy val municipalityDao = new MunicipalityDao

  implicit val getResult = new GetResult[ExportStatusInfo] {
    def apply(r: PositionedResult) : ExportStatusInfo = {
      val id = r.nextLong()
      val fileName = r.nextString()
      val status = r.nextInt()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val createdBy = r.nextStringOption()
      val exportedAssets = r.nextString()
      val municipalities = r.nextString()
      val content = r.nextStringOption()

      val assetsName = exportedAssets.split(",")
                                      .map( asset => AssetTypeInfo(asset.toInt).nameFI )
                                      .mkString(",")

      val municipalitiesAsSet = municipalities.split(",").map(_.toInt).toSet
      val municipalitiesName = municipalityDao.getMunicipalitiesNameAndIdByCode( municipalitiesAsSet )
                                              .map(_.name)
                                              .mkString(",")


      ExportStatusInfo(id, status, Status(status).descriptionFi, fileName, createdBy, createdDate, assetsName, municipalitiesName, content)
    }
  }


  def create(username: String, fileName: String, exportType: String, municipalities: String): Long = {
    val id = sql"""SELECT primary_key_seq.nextval FROM dual""".as[Long].first

    sqlu"""INSERT INTO export_report(id, file_name, exported_assets, municipalities, created_by)
           VALUES ($id, $fileName, $exportType, $municipalities, $username)
      """.execute

    id
  }

  def update(id: Long, status: Status, content: Option[String]): Long = {
    sqlu"""UPDATE export_report
        SET content = $content,
            status = ${status.value}
        WHERE id = $id
      """.execute
    id
  }

  def get(logId: Long): Option[ExportStatusInfo] = {
    sql"""SELECT ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, EXPORTED_ASSETS, MUNICIPALITIES, CONTENT
          FROM export_report
          WHERE id = $logId
      """.as[ExportStatusInfo].firstOption
  }

  def getByIds(logIds: Set[Long]): Seq[ExportStatusInfo] = {
    MassQuery.withIds(logIds) { idTableName =>
      sql"""SELECT er.ID, er.FILE_NAME, er.STATUS, er.CREATED_DATE, er.CREATED_BY, er.EXPORTED_ASSETS, er.MUNICIPALITIES, NULL
            FROM export_report er
              JOIN  #$idTableName i ON i.id = er.id
         """.as[ExportStatusInfo].list
    }
  }

  def getByUser(username: String): Seq[ExportStatusInfo] = {
    sql"""SELECT ID, FILE_NAME, STATUS, CREATED_DATE, CREATED_BY, EXPORTED_ASSETS, MUNICIPALITIES, NULL
          FROM export_report
          WHERE created_by = $username
          ORDER BY created_date DESC
      """.as[ExportStatusInfo].list
  }

}
