package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode.Unknown
import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.viite.dao.RoadAddressDAO
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.DatabaseDef
import sun.reflect.generics.reflectiveObjects.NotImplementedException


class AssetDataImporterSpec extends FunSuite with Matchers {

  val vvhRoadLinks = List(
    VVHRoadlink(6656730L, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
  )


  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
  val mockVVHSuravageClient = MockitoSugar.mock[VVHSuravageClient]
  val mockVVHHistoryClient = MockitoSugar.mock[VVHHistoryClient]
  val mockVVHFrozenTimeRoadLinkClient = MockitoSugar.mock[VVHFrozenTimeRoadLinkClientServicePoint]

  test("Should not have missing road addresses") {
    when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHClient.suravageData).thenReturn(mockVVHSuravageClient)
    when(mockVVHClient.historyData).thenReturn(mockVVHHistoryClient)
    when(mockVVHClient.frozenTimeRoadLinkData)thenReturn(mockVVHFrozenTimeRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(vvhRoadLinks)
    when(mockVVHComplementaryClient.fetchByLinkIds(any[Set[Long]])).thenReturn(vvhRoadLinks)
    when(mockVVHFrozenTimeRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(vvhRoadLinks)
    when(mockVVHSuravageClient.fetchSuravageByLinkIds(any[Set[Long]])).thenReturn(Seq())
    when(mockVVHHistoryClient.fetchVVHRoadLinkByLinkIds(any[Set[Long]])).thenReturn(Seq())

    TestTransactions.runWithRollback() {
      val roadsToBeConverted = Seq(
        //                    TIE AOSA  AJR JATKUU AET LET   ALKU LOPPU ALKUPVM                LOPPUPVM               MUUTOSPVM              -     ELY TIETYYPPI -, LINKID    KAYTTAJA      ALKUX             ALKUY              LOPPUX            LOPPUY             (LRMID)        AJORATAID  SIDE_CODE
        ConversionRoadAddress(25, 756,  22, 5,     1,  765,  62,  71,   Some(d("01.03.2016")), None,                  Some(d("30.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74032756001L,  7465456,   Unknown),
        ConversionRoadAddress(25, 765,  22, 5,     1,  810,  71,  116,  Some(d("01.03.2016")), None,                  Some(d("30.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 214689473001L, 148122173, Unknown),
        ConversionRoadAddress(25, 694,  22, 5,     1,  756,  0,   62,   Some(d("01.03.2016")), None,                  Some(d("30.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74033231001L,  7465931,   Unknown),
        ConversionRoadAddress(25, 694,  22, 5,     0,  756,  0,   62,   Some(d("29.10.2008")), Some(d("29.02.2016")), Some(d("08.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74033231000L,  7465931,   Unknown),
        ConversionRoadAddress(25, 694,  22, 5,     1,  756,  0,   62,   Some(d("31.10.2006")), Some(d("28.10.2008")), Some(d("29.10.2008")), None, 1,  1,        0, 6656730L, "TR",         Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74033231001L,  7465931,   Unknown),
        ConversionRoadAddress(25, 694,  22, 5,     0,  756,  0,   62,   Some(d("15.12.2005")), Some(d("30.10.2006")), Some(d("29.10.2008")), None, 1,  1,        0, 6656730L, "TR",         Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74033231000L,  7465931,   Unknown),
        ConversionRoadAddress(25, 756,  22, 5,     0,  765,  62,  71,   Some(d("29.10.2008")), Some(d("29.02.2016")), Some(d("08.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74032756000L,  7465456,   Unknown),
        ConversionRoadAddress(25, 756,  22, 5,     1,  765,  62,  71,   Some(d("31.10.2006")), Some(d("28.10.2008")), Some(d("29.10.2008")), None, 1,  1,        0, 6656730L, "TR",         Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74032756001L,  7465456,   Unknown),
        ConversionRoadAddress(53, 6221, 22, 5,     0,  6230, 62,  71,   Some(d("01.11.1963")), Some(d("31.12.1995")), Some(d("29.10.2008")), None, 1,  1,        0, 6656730L, "TR",         Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74032756000L,  7465456,   Unknown),
        ConversionRoadAddress(25, 6221, 22, 5,     0,  6230, 62,  71,   Some(d("01.01.1996")), Some(d("14.12.2005")), Some(d("29.10.2008")), None, 1,  1,        0, 6656730L, "TR",         Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74032756000L,  7465456,   Unknown),
        ConversionRoadAddress(25, 756,  22, 5,     0,  765,  62,  71,   Some(d("15.12.2005")), Some(d("30.10.2006")), Some(d("29.10.2008")), None, 1,  1,        0, 6656730L, "TR",         Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 74032756000L,  7465456,   Unknown),
        ConversionRoadAddress(25, 765,  22, 5,     0,  810,  71,  116,  Some(d("15.12.2005")), Some(d("29.02.2016")), Some(d("08.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 214689473000L, 148122173, Unknown),
        ConversionRoadAddress(53, 6230, 22, 5,     0,  6275, 71,  116,  Some(d("01.11.1963")), Some(d("31.12.1995")), Some(d("08.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 214689473000L, 148122173, Unknown),
        ConversionRoadAddress(25, 6230, 22, 5,     0,  6275, 71,  116,  Some(d("01.01.1996")), Some(d("14.12.2005")), Some(d("08.03.2016")), None, 1,  1,        0, 6656730L, "ajrpilkont", Some(346769.646), Some(6688615.011), Some(346862.556), Some(6688687.082), 214689473000L, 148122173, Unknown)
      )

      val importOptions = ImportOptions(false, false, 1510790400000L, "MOCK_CONVERSION", "2017-11-19", false)

      val roadAddressImporter = new RoadAddressImporter(null, mockVVHClient, importOptions) {
        override def fetchChunckLinkIdsFromConversionTable(chunk: Int): Seq[(Long, Long)] = {
          Seq((0, 6656730))
        }
        override def fetchRoadAddressFromConversionTable(minLinkId: Long, maxLinkId: Long, filter: String): Seq[ConversionRoadAddress] = {
          roadsToBeConverted
        }
      }

      val assetDataImporter = new AssetDataImporter {
        override def withDynTransaction(f: => Unit): Unit = f
        override def withDynSession[T](f: => T): T = f
        override def fetchRoadAddressHistory(conversionDatabase: DatabaseDef, ely: Int, importOptions: ImportOptions): List[RoadAddressHistory] = {
          throw new NotImplementedException()
        }
        override def getRoadAddressImporter(conversionDatabase: DatabaseDef, vvhClient: VVHClient, importOptions: ImportOptions) = {
          roadAddressImporter
        }
      }
      assetDataImporter.importRoadAddressData(null, mockVVHClient, None, importOptions)

      val insertedRoadAddresses = RoadAddressDAO.fetchByLinkId(Set(6656730), true, true, true)

      insertedRoadAddresses.size should be(14)
    }
  }

  val dateTimeFormatter = DateTimeFormat.forPattern("dd.MM.yyyy")

  def d(date: String): DateTime = {DateTime.parse(date, dateTimeFormatter)}

}
