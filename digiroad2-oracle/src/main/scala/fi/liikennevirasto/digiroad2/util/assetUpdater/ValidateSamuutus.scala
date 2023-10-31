package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeSet
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.process.assetValidator.{SamuutusValidator, ValidationResult}
import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.slf4j.Logger

case class SamuutusFailled(msg:String) extends Exception(msg)

object ValidateSamuutus {
  val failSamuutus = false
  private lazy val awsService = new AwsService
  private lazy val s3Service: awsService.S3.type = awsService.S3
  private lazy val s3Bucket: String = Digiroad2Properties.samuutusReportsBucketName
  def validate(logger:Logger,typeId: Int, changeSet: RoadLinkChangeSet): Unit = {
    val newLinks = changeSet.changes.flatMap(_.newLinks.map(_.linkId)).toSet
    val result = SamuutusValidator.validate(typeId, newLinks)
    val passSet = result.map(_.pass).toSet
   if (failSamuutus) {
     if (passSet.contains(false)) 
       reportInvalidAssets(logger, typeId, result) 
     Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
   }else {
     if (passSet.contains(false)) {
       reportInvalidAssets(logger, typeId, result)
       throw SamuutusFailled("")
     } else Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
   }
  }

  private def reportInvalidAssets(logger: Logger, typeId: Int, result: Seq[ValidationResult]): Unit = {
    SamuutusValidator.createCSV(result)
    saveReportToS3(AssetTypeInfo.apply(typeId).label, SamuutusValidator.createCSV(result))
    logger.error("Samuutus process failed")
  }
  private def saveReportToS3(assetName: String, body: String): Unit = {
    val date = DateTime.now().toString("YYYY-MM-dd")
    val path = s"$date/${assetName}_invalidRows.csv"
    s3Service.saveFileToS3(s3Bucket, path, body, "csv")
  }
}
