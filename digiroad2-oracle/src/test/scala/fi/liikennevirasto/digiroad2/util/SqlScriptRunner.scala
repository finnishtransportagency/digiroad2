package fi.liikennevirasto.digiroad2.util

import scala.io.{Codec, Source}
import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

object SqlScriptRunner {
  def runScript(filename: String) {
    executeStatements(readScriptStatements(filename))
  }

  def runScripts(filenames: Seq[String]) {
    executeStatements(filenames.flatMap(readScriptStatements(_)))
  }

  def readScriptStatements(filename: String): Seq[String] = {
    val commentR = """\/\*.*\*\/""".r
    val withComments = Source.fromFile("./digiroad2-oracle/sql/" + filename)(Codec.UTF8).getLines.filterNot(_.startsWith("--")).mkString
    commentR.replaceAllIn(withComments, "").split(";")
  }

  def executeStatements(stmts: Seq[String]) {
    Database.forDataSource(ds).withDynTransaction {
      stmts.foreach { stmt =>
        println("Executing: " + stmt)
        try {
          (Q.u + stmt).execute()
        } catch {
          case e: Exception => {
            e.printStackTrace
            println("CONTINUING...")
          }
        }
      }
    }
  }

  def executeStatement(statement: String) = executeStatements(List(statement))
}