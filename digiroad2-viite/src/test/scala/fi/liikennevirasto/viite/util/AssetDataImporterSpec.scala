package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.viite.util.DataFixture.dr2properties
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import fi.liikennevirasto.viite.dao.RoadAddressDAO
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import fi.liikennevirasto.digiroad2.asset.SideCode.Unknown


class AssetDataImporterSpec extends FunSuite with Matchers {

  test("Should not have missing road addresses") {
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
      val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))

      val roadAddressImporter = new RoadAddressImporter(null, vvhClient, importOptions) {
        override def fetchChunckLinkIdsFromConversionTable(chunk: Int): Seq[(Long, Long)] = {
          Seq((0, 6656730))
        }
        override def fetchRoadAddressFromConversionTable(minLinkId: Long, maxLinkId: Long, filter: String): Seq[ConversionRoadAddress] = {
          if (minLinkId < 6656730 && 6656730 < maxLinkId) roadsToBeConverted else List[ConversionRoadAddress]()
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
      assetDataImporter.importRoadAddressData(null, vvhClient, None, importOptions)

      val insertedRoadAddresses = RoadAddressDAO.fetchByLinkId(Set(6656730), true, true, true)

      insertedRoadAddresses.size should be(14)
    }
  }

  val dateTimeFormatter = DateTimeFormat.forPattern("dd.MM.yyyy")

  def d(date: String): DateTime = {DateTime.parse(date, dateTimeFormatter)}

}
