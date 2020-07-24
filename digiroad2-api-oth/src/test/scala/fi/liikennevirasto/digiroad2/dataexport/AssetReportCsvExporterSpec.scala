package fi.liikennevirasto.digiroad2.dataexport

import java.util.Properties
import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.csvDataExporter.AssetReportCsvExporter
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, MunicipalityInfo, OracleUserProvider}
import fi.liikennevirasto.digiroad2.dao.csvexporter.{AssetReport, AssetReporterDAO}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User, UserProvider}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class AssetReportCsvExporterSpec extends FunSuite with Matchers {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  val csvSeparator = ";"
  val newLine = "\r\n"

  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockAssetReporterDAO = MockitoSugar.mock[AssetReporterDAO]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]

  val assetReportCsvExporter = new AssetReportCsvExporter(mockRoadLinkService, mockEventBus, mockUserProvider){
    override val assetReporterDAO: AssetReporterDAO = mockAssetReporterDAO
    override val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override val userProvider: UserProvider = mockUserProvider
  }

  val LupaMunicipalityInfo = MunicipalityInfo(408, 3, "Lapua")
  val SavitaipaleMunicipalityInfo = MunicipalityInfo(739, 8, "Savitaipale")

  val vvhRoadlinks1 = Seq(VVHRoadlink(388562360L, 408, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))
  val vvhRoadlinks2 = Seq(VVHRoadlink(38856690L, 739, Seq(Point(10, 0), Point(65, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))

  val users = List(User(26867454, "k647320", Configuration(None, None, None, None, None, Set(), Set(), Set("operator"), Some("2018-11-27"), None), None),
                  User(642513, "l485693", Configuration(None, None, None, None, None, Set(408, 739), Set(), Set(), None, None), None),
                  User(123456, "lx775599", Configuration(None, None, None, None, None, Set(), Set(), Set("elyMaintainer"), Some("2019-12-07"), None), None) )


  test("test function exportAssetsByMunicipality"){

    val municipalitiesList = List(LupaMunicipalityInfo, SavitaipaleMunicipalityInfo)

    val assetReportsList1 = List ( AssetReport(130, "Liikennemerkki", "linear", "k647320", DateTime.now().minusDays(2)) )
    val assetReportsList2 = List ( AssetReport(130, "Liikennemerkki", "linear", "l485693", DateTime.now().minusDays(5)) )

    when(mockUserProvider.getUsers()).thenReturn(users)
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(municipalitiesList)

    when(mockRoadLinkService.getVVHRoadLinksF(408)).thenReturn(vvhRoadlinks1)
    when(mockRoadLinkService.getVVHRoadLinksF(739)).thenReturn(vvhRoadlinks2)

    when(mockAssetReporterDAO.linearAssetQuery(Seq(388562360L), Seq(130))).thenReturn(assetReportsList1)
    when(mockAssetReporterDAO.linearAssetQuery(Seq(38856690L), Seq(130))).thenReturn(assetReportsList2)

    val result = assetReportCsvExporter.generateCSVContent(List(130), List(408, 739))

    result.contains("Lapua") should be (true)
    result.contains("lx775599") should be (false)

    val lasLine = result.split(newLine).last
    val lastValueInLastLine = lasLine.split(csvSeparator).last

    lastValueInLastLine should be("k647320")
  }


  test("test function exportAssetsByMunicipality with Points"){

    val municipalitiesList = List(LupaMunicipalityInfo, SavitaipaleMunicipalityInfo)

    val assetReportsList1 = List(AssetReport(300, "Liikennemerkki", "point", "k647320", DateTime.now().minusDays(2)))
    val assetReportsList2 = List(AssetReport(300, "Liikennemerkki", "point", "l485693", DateTime.now().minusDays(5)))

    when(mockUserProvider.getUsers()).thenReturn(users)
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(municipalitiesList)

    when(mockRoadLinkService.getVVHRoadLinksF(408)).thenReturn(vvhRoadlinks1)
    when(mockRoadLinkService.getVVHRoadLinksF(739)).thenReturn(vvhRoadlinks2)

    when(mockAssetReporterDAO.pointAssetQuery(Seq(408), Seq(300))).thenReturn(assetReportsList1)
    when(mockAssetReporterDAO.pointAssetQuery(Seq(739), Seq(300))).thenReturn(assetReportsList2)

    when(mockAssetReporterDAO.getTotalTrafficSignNewLaw(408)).thenReturn(0)
    when(mockAssetReporterDAO.getTotalTrafficSignNewLaw(739)).thenReturn(2)

    when(mockMunicipalityDao.getMunicipalityIdByName("Lapua")).thenReturn(List(LupaMunicipalityInfo))
    when(mockMunicipalityDao.getMunicipalityIdByName("Savitaipale")).thenReturn(List(SavitaipaleMunicipalityInfo))


    val result = assetReportCsvExporter.generateCSVContent(List(300), List(408, 739), true)

    result.contains("Savitaipale") should be(true)
    result.contains("lx775599") should be(false)

    val line2 = result.split(newLine)(2)
    val lastCharOfLine2 = line2.split(csvSeparator).last

    lastCharOfLine2 should be("2")
  }


}
