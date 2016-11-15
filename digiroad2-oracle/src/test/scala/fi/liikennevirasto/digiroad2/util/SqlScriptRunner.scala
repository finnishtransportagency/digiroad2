package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

import scala.io.{Codec, Source}
import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession

object SqlScriptRunner {
  def runScripts(filenames: Seq[String]) {
    executeStatements(filenames.flatMap(readScriptStatements("./digiroad2-oracle/sql/", _)))
  }

  def runViiteScripts(filenames: Seq[String]) {
    executeStatements(filenames.flatMap(readScriptStatements("./digiroad2-viite/sql/", _)))
  }

  def readScriptStatements(path: String, filename: String): Seq[String] = {
    val commentR = """\/\*.*\*\/""".r
    val withComments = Source.fromFile(path + filename)(Codec.UTF8).getLines.filterNot(_.trim.startsWith("--")).mkString
    commentR.replaceAllIn(withComments, "").split(";")
  }

  def executeStatements(stmts: Seq[String]) {
    println("Running " + stmts.length + " statements...")
    var i = 0
    OracleDatabase.withDynTransaction {
      stmts.foreach { stmt =>
        try {
          (Q.u + stmt).execute
          i = i+1
          if (i % 10 == 0)
            println("" + i + " / " + stmts.length)
        } catch {
          case e: Exception => {
            e.printStackTrace
            println("failed statement: " + stmt.replaceAll("\\)", ")\n"))
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