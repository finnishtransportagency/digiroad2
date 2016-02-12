package fi.liikennevirasto.digiroad2.util

import java.sql.{SQLSyntaxErrorException, BatchUpdateException}

import fi.liikennevirasto.digiroad2.VVHClient
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc._
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef}
import Database.dynamicSession

object LinkIdImporter {
  def withDynTransaction(f: => Unit): Unit = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  case class TableSpec(name: String, idColumn: String)
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
      TableSpec(name = "lrm_position", idColumn = "id"),
      TableSpec(name = "traffic_direction", idColumn = "mml_id"),
      TableSpec(name = "incomplete_link", idColumn = "mml_id"),
      TableSpec(name = "functional_class", idColumn = "mml_id"),
      TableSpec(name = "link_type", idColumn = "mml_id"),
      TableSpec(name = "manoeuvre_element", idColumn = "manoeuvre_id"),
      TableSpec(name = "unknown_speed_limit", idColumn = "mml_id"))

    tableNames.foreach { table =>
      updateTable(table, vvhHost)
    }
  }

  def updateTable(table: TableSpec, vvhHost: String): Unit = {
    val tableName = table.name
    val idColumn = table.idColumn

    val vvhClient = new VVHClient(vvhHost)

    withDynTransaction {
      sqlu"""delete from mml_id_to_link_id""".execute

      val tempPS = dynamicSession.prepareStatement(
        """
        insert into mml_id_to_link_id (mml_id, link_id) values (?, ?)
        """)

      val (min, max) = sql"""select min(#$idColumn), max(#$idColumn) from #$tableName""".as[(Int, Int)].first

      val batches = getBatchDrivers(min, max, 10000)
      val total = batches.size

      print(s"Table $tableName: Fetching $total batches of links… ")

      val startTime = DateTime.now()

      batches.zipWithIndex.foreach { case ((min, max), i) =>
        val mmlIds = sql"""select distinct mml_id from #$tableName where #$idColumn between $min and $max""".as[Long].list
        val links = vvhClient.fetchVVHRoadlinks(mmlIds.toSet)
        links.foreach { link =>
          tempPS.setLong(1, link.mmlId)
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
