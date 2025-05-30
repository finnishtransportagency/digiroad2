package fi.liikennevirasto.digiroad2.middleware

import java.io.InputStream
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.csvDataImporter._
import fi.liikennevirasto.digiroad2.user.User

sealed trait AdditionalImportValue {
  def toJson: Any
}

case class UpdateOnlyStartDates(onlyStartDates: Boolean) extends AdditionalImportValue {
  override def toJson: Any = onlyStartDates
}

case class AdministrativeValues(administrativeClasses: AdministrativeClass) extends AdditionalImportValue {
  override def toJson: Any = administrativeClasses
}

case class NumericValues(values: Int) extends  AdditionalImportValue {
  override def toJson: Any = values
}

case class CsvDataImporterInfo(assetTypeName: String, fileName: String, user: User, inputStream: InputStream, logId: Long, additionalImportInfo: Set[AdditionalImportValue] = Set())

class DataImportManager(roadLinkClient: RoadLinkClient, roadLinkService: RoadLinkService, eventBus: DigiroadEventBus) {

  lazy val trafficSignCsvImporter: TrafficSignCsvImporter = new TrafficSignCsvImporter(roadLinkService, eventBus)
  lazy val maintenanceRoadCsvImporter: MaintenanceRoadCsvImporter = new MaintenanceRoadCsvImporter(roadLinkService, eventBus)
  lazy val massTransitStopCsvImporter: MassTransitStopCsvOperation = new MassTransitStopCsvOperation(roadLinkClient, roadLinkService, eventBus)
  lazy val roadLinkCsvImporter: RoadLinkCsvImporter = new RoadLinkCsvImporter(roadLinkService, eventBus)
  lazy val obstaclesCsvImporter: ObstaclesCsvImporter = new ObstaclesCsvImporter(roadLinkService, eventBus)
  lazy val trafficLightsCsvImporter: TrafficLightsCsvImporter = new TrafficLightsCsvImporter(roadLinkService, eventBus)
  lazy val pedestrianCrossingCsvImporter: PedestrianCrossingCsvImporter = new PedestrianCrossingCsvImporter(roadLinkService, eventBus)
  lazy val railwayCrossingCsvImporter: RailwayCrossingCsvImporter = new RailwayCrossingCsvImporter(roadLinkService, eventBus)
  lazy val servicePointCsvImporter: ServicePointCsvImporter = new ServicePointCsvImporter(roadLinkService, eventBus)

  def importer(importedInfo: CsvDataImporterInfo) {
    importedInfo.assetTypeName match {
      case TrafficSigns.layerName =>
        trafficSignCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId, importedInfo.additionalImportInfo.map(_.asInstanceOf[NumericValues].values))
      case MaintenanceRoadAsset.layerName =>
        maintenanceRoadCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId)
      case "roadLinks" =>
        roadLinkCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user.username, importedInfo.logId)
      case MassTransitStopAsset.layerName =>
        massTransitStopCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId, importedInfo.additionalImportInfo.map(_.asInstanceOf[AdministrativeValues].administrativeClasses))
      case Obstacles.layerName =>
        obstaclesCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId)
      case TrafficLights.layerName =>
        trafficLightsCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId)
      case RailwayCrossings.layerName =>
        railwayCrossingCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId)
      case PedestrianCrossings.layerName =>
        pedestrianCrossingCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId)
      case ServicePoints.layerName =>
        servicePointCsvImporter.importAssets(importedInfo.inputStream, importedInfo.fileName, importedInfo.user, importedInfo.logId)
      case _ =>
    }
  }
}