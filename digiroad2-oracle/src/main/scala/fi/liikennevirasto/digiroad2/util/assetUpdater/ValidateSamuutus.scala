package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeSet
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.process.assetValidator.{SamuutusValidator, ValidationResult}
import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.slf4j.Logger

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

case class SamuraisFailed(msg:String) extends Exception(msg)

object ValidateSamuutus {
  private lazy val awsService = new AwsService
  private lazy val s3Service: awsService.S3.type = awsService.S3
  private lazy val s3Bucket: String = Digiroad2Properties.samuutusReportsBucketName
  def validate(logger:Logger,typeId: Int, changeSet: RoadLinkChangeSet): Unit = {
    val newLinks = changeSet.changes.flatMap(_.newLinks.map(_.linkId)).toSet
    val result = SamuutusValidator.validate(typeId, newLinks)
    val passSet = result.map(_.pass).toSet
   if (Digiroad2Properties.failSamuutusOnFailedValidation) {
     if (passSet.contains(false)) {
       reportInvalidAssets(logger, typeId, result)
       throw SamuraisFailed(s"Validation error happened, see report in bucket ${s3Bucket}")
     } else Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
   }else {
     if (passSet.contains(false)) reportInvalidAssets(logger, typeId, result)
     Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
   }
  }

  private def reportInvalidAssets(logger: Logger, typeId: Int, result: Seq[ValidationResult]): Unit = {
    val report = SamuutusValidator.createCSV(typeId,result)
    saveReportToS3(AssetTypeInfo.apply(typeId).label,report._1,report._2)
  }
  private def saveReportToS3(assetName: String, body: String,count:Int): Unit = {
    val date = DateTime.now().toString("YYYY-MM-dd")
    val path = s"$date/${assetName}_invalidRow_${count}.csv"
    s3Service.saveFileToS3(s3Bucket, path, body, "csv")
  }

  private def saveReportToLocalFile(assetName: String, body: String,count:Int): Unit = {
    val localReportDirectoryName = "samuutus-reports-local-test"
    val date = DateTime.now().toString("YYYY-MM-dd")
    Files.createDirectories(Paths.get(localReportDirectoryName, date))
    val path = s"$localReportDirectoryName/$date/${assetName}_invalidRows_${count}.csv"
    new PrintWriter(path) {
      write(body)
      close()
    }
  }
}
