package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}
import java.text.NumberFormat
import java.util.Locale

case class CyclingAndWalking(linkId: Long, value: Int)

object ImportShapeFileDAO {

  implicit val getCyclingAndWalking= new GetResult[CyclingAndWalking] {
    val numberFormat = NumberFormat.getNumberInstance(Locale.FRANCE)

    def apply(r: PositionedResult) = {
      val linkId = r.nextLong()
      val value = numberFormat.parse(r.nextString()).intValue()

      CyclingAndWalking(linkId, value)
    }
  }

  def getCyclingAndWalkingInfo(municipalityCode: Int): Seq[CyclingAndWalking] = {
    sql"""
      select distinct LINK_ID, KAPY_POHJA from KAPY_POHJADATA_GEOMETRY where KUNTAKOODI = $municipalityCode and KAPY_POHJA IS NOT NULL
    """.as[CyclingAndWalking].list
  }

  def getPrivateRoadExternalInfo(municipalityCode: Int): Seq[(Long, Long, Int, Option[String], Option[String])] = {
    sql"""
      select distinct LINK_ID, LINK_MMLID, KUNTAKOODI, KAYTTOOIKE, NIMI from external_road_private_info where KUNTAKOODI = $municipalityCode
    """.as[(Long, Long, Int, Option[String], Option[String])].list
  }
}
