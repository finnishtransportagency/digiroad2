package fi.liikennevirasto.digiroad2.middleware

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.csvDataExporter.AssetReportCsvExporter
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}


case class CsvDataExporterInfo(assetTypeList: List[Int], municipalitiesList: List[Int], fileName: String, user: User,  logId: Long)


class DataExportManager(roadLinkService: RoadLinkService, eventBus: DigiroadEventBus, userProvider: UserProvider) {

  lazy val assetReportCsvExporter: AssetReportCsvExporter = new AssetReportCsvExporter(roadLinkService, eventBus, userProvider)


  def processExport(data: CsvDataExporterInfo): Unit ={
    assetReportCsvExporter.exportAssetsByMunicipalityCSVGenerator(data.assetTypeList,data.municipalitiesList, data.logId);
  }

}
