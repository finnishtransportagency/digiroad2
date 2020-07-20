package fi.liikennevirasto.digiroad2.csvDataExport

import fi.liikennevirasto.digiroad2.{CsvDataExporter, DigiroadEventBus, sTestTransactions}
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mockito.MockitoSugar


class CsvDataExporterSupport extends FunSuite with Matchers {
  private val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback()(test)

  object PassThroughCSVDataExport extends CsvDataExporter(mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def eventBus: DigiroadEventBus = mockEventBus
  }

}

class CsvDataExporterSpec extends CsvDataExporterSupport {
  test("straight csv file") {
    val headers = Seq("id","name", "age")
    val values = Seq(
      Map("id" -> "1", "name" -> "Me1", "age" -> "Me20"),
      Map("id" -> "2", "name" -> "Me2", "age" -> "Me30")
    )
    val headersOutput = headers.mkString(";").concat("\r\n")
    val valuesOutput = values.map( m => m.values.mkString(";")).mkString("\r\n")

    val result = PassThroughCSVDataExport.createStandardCSVData(headers, values)

    result should be (headersOutput + valuesOutput)
  }

  test("straight csv file with empty headers") {
    val headers = Seq()
    val values = Seq(
      Map("id" -> "1", "name" -> "Me1", "age" -> "Me20"),
      Map("id" -> "2", "name" -> "Me2", "age" -> "Me30")
    )

    val output = values.map( m => m.values.mkString(";")).mkString("\r\n")
    val result = PassThroughCSVDataExport.createStandardCSVData(headers, values)

    result should be (output)
  }

  test("straight csv file with empty values") {
    val headers = Seq("id","name", "age")
    val values = Seq()

    val headersOutput = headers.mkString(";").concat("\r\n")
    val result = PassThroughCSVDataExport.createStandardCSVData(headers, values)

    result should be (headersOutput)
  }

  test("straight csv file with empty headers and values") {
    val headers = Seq()
    val values = Seq()

    val result = PassThroughCSVDataExport.createStandardCSVData(headers, values)

    result should be (empty)
  }
}



