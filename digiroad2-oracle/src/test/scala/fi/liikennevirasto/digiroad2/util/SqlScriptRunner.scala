package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

import scala.io.{Codec, Source}
import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession

object SqlScriptRunner {
  def runScripts(filenames: Seq[String]) {
    executeStatements(filenames.flatMap(readScriptStatements))
  }

  def readScriptStatements(filename: String): Seq[String] = {
    val commentR = """\/\*.*\*\/""".r
    val withComments = Source.fromFile("./digiroad2-oracle/sql/" + filename)(Codec.UTF8).getLines.filterNot(_.trim.startsWith("--")).mkString
    commentR.replaceAllIn(withComments, "").split(";")
  }

  def executeStatements(stmts: Seq[String]) {
    println("Running " + stmts.length + " statements...")
    OracleDatabase.withDynTransaction {
      stmts.foreach { stmt =>
        try {
          println("executing: " + stmt.replaceAll("\\)", ")\n"))
          (Q.u + stmt).execute
        } catch {
          case e: Exception => {
            e.printStackTrace
            println("CONTINUING WITH NEXT STATEMENT...")
            return
          }
        }
      }
      println("DONE!")
    }
  }

  def executeStatement(statement: String) = executeStatements(List(statement))
}