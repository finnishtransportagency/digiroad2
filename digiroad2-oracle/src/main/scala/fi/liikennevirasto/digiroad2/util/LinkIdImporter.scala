package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  def importLinkIdsFromVVH(vvhHost: String): Unit = {
    val vvhClient = new VVHClient(vvhHost)
    //    val municipalities = withDynSession { Queries.getMunicipalities }
    val municipalities = Seq(235)

    withDynTransaction {
      municipalities.foreach { municipalityCode =>
        val startTime = DateTime.now()

        val roadlinks = vvhClient.fetchByMunicipality(municipalityCode)

        roadlinks.foreach { link =>
          sqlu"""
             update lrm_position
             set link_id = ${link.linkId}
             where mml_id = ${link.mmlId}
          """.execute
        }

        println(s"Imported ${roadlinks.size} link IDs for municipality $municipalityCode in ${AssetDataImporter.humanReadableDurationSince(startTime)}")
      }
    }
  }
}
