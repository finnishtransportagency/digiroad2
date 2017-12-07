package fi.liikennevirasto.digiroad2.util

import java.sql.{BatchUpdateException, SQLSyntaxErrorException}

import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.Queries

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  case class TableSpec(name: String)

  def importLinkIdsFromVVH(vvhHost: String): Unit = {
    withDynTransaction {
      try { sqlu"""drop table mml_id_to_link_id""".execute } catch {
        case e: SQLSyntaxErrorException => // ok
      }
      sqlu"""create table mml_id_to_link_id (
          mml_id number(38, 0),
          link_id number(38, 0),
          primary key (mml_id)
        )""".execute
    }

    val tableNames = Seq(
      "lrm_position",
      "traffic_direction",
      "incomplete_link",
      "functional_class",
      "link_type",
      "manoeuvre_element",
      "unknown_speed_limit")

    tableNames.foreach { table =>
      updateTable(table, vvhHost)
    }
  }

  def updateTable(tableName: String, vvhHost: String): Unit = {
    val vvhClient = new VVHClient(vvhHost)

    withDynTransaction {
      sqlu"""delete from mml_id_to_link_id""".execute

      val tempPS = dynamicSession.prepareStatement(
        """
        insert into mml_id_to_link_id (mml_id, link_id) values (?, ?)
        """)

      val count = sql"""select count(distinct mml_id) from #$tableName""".as[Int].first

      val batches = getBatchDrivers(0, count, 20000)
      val total = batches.size

      print(s"[${DateTime.now}] Table $tableName: Fetching $total batches of links… ")

      val startTime = DateTime.now()

      batches.foreach { case (min, max) =>
        val mmlIds =
          sql"""
               select *
               from (select a.*, rownum rnum
                      from (select distinct mml_id from #$tableName) a
                      where rownum <= $max
                ) where rnum >= $min
          """.as[Long].list
        val links = vvhClient.roadLinkData.fetchByMmlIds(mmlIds.toSet)
        links.foreach { link =>
          val mmlId = link.attributes("MTKID").asInstanceOf[BigInt].longValue()
          tempPS.setLong(1, mmlId)
          tempPS.setLong(2, link.linkId)
          tempPS.addBatch()
        }
        try {
          tempPS.executeBatch()
        }
        catch {
          case e: BatchUpdateException => // ok, assume it was a duplicate MML ID
        }
      }

      sqlu"""
        update #$tableName pos
               set pos.link_id = (select t.link_id from mml_id_to_link_id t where t.mml_id = pos.mml_id)
      """.execute

      println(s"done in ${AssetDataImporter.humanReadableDurationSince(startTime)}.")
    }
  }

  def getBatchDrivers(n: Int, m: Int, step: Int): List[(Int, Int)] = {
    if ((m - n) < step) {
      List((n, m))
    } else {
      val x = (n to m by step).sliding(2).map(x => (x(0), x(1) - 1)).toList
      x :+ (x.last._2 + 1, m)
    }
  }
}