package fi.liikennevirasto.digiroad2.process.assetValidator

import com.github.tototoshi.csv.CSVWriter
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Lanes}
import fi.liikennevirasto.digiroad2.dao.LinearReferenceAsset
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

import java.io.StringWriter

case class ValidationResult(rule: String, pass: Boolean, invalidRows: Seq[LinearReferenceAsset])

trait ValidatorProcess {
  protected def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  protected def runValidation(steps: Seq[Validators.ValidatorFunction], assetType: Int, linkFilter: Set[String]): Seq[ValidationResult] = {
    Validators.getDefined(steps.map(validator => validator(assetType, linkFilter)))
  }
  
  def validate(assetType: Int, linkFilter: Set[String] = Set(), newTransaction: Boolean = true): Seq[ValidationResult] = {
    if (newTransaction) withDynTransaction {process(assetType, linkFilter)} else process(assetType, linkFilter)
  }
  protected def process(assetType: Int, linkFilter: Set[String]): Seq[ValidationResult] = ???
  def createCSV(assetType: Int,rows: Seq[ValidationResult]): (String,Int) = {
    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    csvWriter.writeRow(Seq("sep=,"))
    val labels = Seq("invalidReason","assetType", "assetId", "laneCode", "linkId", "startMValue", "endMValue", "sideCode")
    csvWriter.writeRow(labels)
    val sumRows = rows.map(a => {
      val rule = a.rule
      val rows = a.invalidRows
      rows.foreach(asset => {
        val lrm = asset.lrm
        val row = Seq(rule,assetType, asset.assetId, asset.laneCode.getOrElse(null), lrm.linkId, lrm.startMValue, lrm.endMValue.getOrElse(null), lrm.sideCode.getOrElse(null))
        csvWriter.writeRow(row)
      })
      rows.size
    }).sum
    (stringWriter.toString,sumRows)
  }
}

object SamuutusValidator extends ValidatorProcess {
  override protected def process(assetType: Int, linkFilter: Set[String]): Seq[ValidationResult] = {
    AssetTypeInfo.apply(assetType) match {
      case Lanes => runValidation(LaneValidators.forSamuutus, assetType, linkFilter)
      case a if a.geometryType == "point" => runValidation(PointAssetValidators.forSamuutus, assetType, linkFilter)
      case _ => runValidation(LinearAssetValidators.forSamuutus, assetType, linkFilter)
    }
  }
}

object TopologyValidator extends ValidatorProcess {
  override protected def process(assetType: Int, linkFilter: Set[String]): Seq[ValidationResult] = {
    AssetTypeInfo.apply(assetType) match {
      case Lanes => runValidation(LaneValidators.forTopology, assetType, linkFilter)
      case a if a.geometryType == "point" => runValidation(PointAssetValidators.forTopology, assetType, linkFilter)
      case _ => runValidation(LinearAssetValidators.forTopology, assetType, linkFilter)
    }
  }
}

trait Validators {
  private type assetType = Int
  private type linkIds = Set[String]
  type returnResult = Option[ValidationResult]
  type ValidatorFunction = (assetType, linkIds) => returnResult
  def forTopology:Seq[ValidatorFunction] = ???
  def forSamuutus:Seq[ValidatorFunction] = ???
}

object Validators extends Validators{
  def getDefined(runValidation: Seq[returnResult]): Seq[ValidationResult] = {
    runValidation.filter(_.isDefined).map(_.get)
  }
  def returnValidationResult(rule: String, invalidRows: Seq[LinearReferenceAsset]): Option[ValidationResult] = {
    if (invalidRows.nonEmpty) Some(ValidationResult(rule, false, invalidRows)) else Some(ValidationResult(rule, true, Seq()))
  }
}

