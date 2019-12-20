package fi.liikennevirasto.digiroad2.dao

import java.sql.Connection

import slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import slick.jdbc.{GetResult, PositionedResult, SetParameter, StaticQuery}
import Database.dynamicSession
import _root_.oracle.spatial.geometry.JGeometry
import _root_.oracle.sql.STRUCT
import com.jolbox.bonecp.ConnectionHandle

import scala.math.BigDecimal.RoundingMode
import java.text.{DecimalFormat, NumberFormat}

import org.joda.time.{DateTime, LocalDate}
import com.github.tototoshi.slick.MySQLJodaSupport._
import java.util.Locale

import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.read

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
    val query = """
      select distinct LINK_ID, KAPY_POHJA from KAPY_POHJADATA_GEOMETRY where LINK_ID IS NOT NULL and KAPY_POHJA IS NOT NULL
    """
    val queryWithFilter = query + s"and KUNTAKOODI = $municipalityCode"
    StaticQuery.queryNA[CyclingAndWalking](queryWithFilter).iterator.toSeq
  }

}
