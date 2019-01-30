package fi.liikennevirasto.digiroad2.middleware


import java.io.InputStream

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, MaintenanceRoadAsset, MassTransitStopAsset, TrafficSigns}
import fi.liikennevirasto.digiroad2.{MaintenanceRoadCsvImporter, MassTransitStopCsvImporter, RoadLinkCsvImporter, TrafficSignCsvImporter}

sealed trait AdditionalImportValue {
  def toJson: Any
}

case class AdministrativeValues(administrativeClasses: Set[AdministrativeClass]) extends AdditionalImportValue {
  override def toJson: Any = administrativeClasses
}

case class MunicipalitiesValue(municipalities: Set[Int]) extends AdditionalImportValue {
  override def toJson: Any = municipalities
}

case class CsvDataImporterInfo(assetTypeName: String, fileName: String, username: String, inputStream: InputStream, additionalImportInfo: Option[AdditionalImportValue] = None)

class DataImportManager(trafficSignCsvImporter: TrafficSignCsvImporter, maintenanceRoadCsvImporter: MaintenanceRoadCsvImporter, massTransitStopCsvImporter: MassTransitStopCsvImporter, roadLinkCsvImporter: RoadLinkCsvImporter) {

  def importer(dataImporterInfo: CsvDataImporterInfo) {

    dataImporterInfo.assetTypeName match {
      case TrafficSigns.layerName =>
        trafficSignCsvImporter.importAssets(dataImporterInfo.inputStream, dataImporterInfo.fileName, dataImporterInfo.username, dataImporterInfo.additionalImportInfo.asInstanceOf[MunicipalitiesValue].municipalities)
      case MaintenanceRoadAsset.layerName =>
        maintenanceRoadCsvImporter.importAssets(dataImporterInfo.inputStream, dataImporterInfo.fileName, dataImporterInfo.username)
      case "roadLinks" =>
        roadLinkCsvImporter.importAssets(dataImporterInfo.inputStream, dataImporterInfo.fileName, dataImporterInfo.username)
      case MassTransitStopAsset.layerName =>
        massTransitStopCsvImporter.importAssets(dataImporterInfo.inputStream, dataImporterInfo.fileName, dataImporterInfo.username, dataImporterInfo.additionalImportInfo.asInstanceOf[AdministrativeValues].administrativeClasses)
      case _ =>
    }

  }
}