package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.process.assetValidator.{TopologyValidator, ValidationResult}
import fi.liikennevirasto.digiroad2.service.AwsService
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

object ValidateAssets {
  val logger = LoggerFactory.getLogger(getClass)

  private lazy val awsService = new AwsService
  private lazy val s3Service: awsService.S3.type = awsService.S3
  private lazy val s3Bucket: String = ""
  
  
  def validateAll (filter: Set[String]= Set()): Unit = {
    logger.info(s"Validation started")
    new Parallel().operation(AssetTypeInfo.validate.grouped(10).toList.par,4){
      _.foreach(_.foreach(a1=>validate(a1.typeId,filter)))
    }
    logger.info(s"Validation ended")
  }
  
  def validate(typeId: Int, filter: Set[String]= Set()): Unit = {
    val result = TopologyValidator.validate(typeId, filter)
    val passSet = result.map(_.pass).toSet
      if (passSet.contains(false))
        reportInvalidAssets(typeId, result)
  }
  
  private def reportInvalidAssets( typeId: Int, result: Seq[ValidationResult]): Unit = {
    val report = TopologyValidator.createCSV(result)
    saveReportToS3(AssetTypeInfo.apply(typeId).label, report,result.length)
    logger.info(s"Validation returned invalid assets :$typeId")
  }
  
  private def saveReportToS3(assetName: String, body: String,count:Int): Unit = {
    val date = DateTime.now().toString("YYYY-MM-dd")
    val path = s"$date/${assetName}_invalidRows_${count}.csv"
    s3Service.saveFileToS3(s3Bucket, path, body, "csv")
  }

  private def saveReportToLocalFile(assetName: String, body: String): Unit = {
    val localReportDirectoryName = "validation-reports-local-test"
    val date = DateTime.now().toString("YYYY-MM-dd")
    Files.createDirectories(Paths.get(localReportDirectoryName, date))
    val path = s"$localReportDirectoryName/$date/${assetName}_invalidRows.csv"
    new PrintWriter(path) {
      write(body)
      close()
    }
  }
}
