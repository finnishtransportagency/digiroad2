package fi.liikennevirasto.digiroad2.masstransitstop.oracle

import java.sql.SQLIntegrityConstraintViolationException

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, Role, User}
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2._
import org.joda.time.LocalDate
import org.mockito.Mockito.verify
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

import scala.language.implicitConversions
import slick.jdbc.{StaticQuery => Q}
import scala.util.Random
import slick.jdbc.StaticQuery.interpolation


class OracleSpatialAssetProviderSpec extends FunSuite with Matchers with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val TestAssetTypeId = 10
  val userProvider = new OracleUserProvider

  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val provider = new AssetPropertyService(new DummyEventBus, userProvider, passThroughTransaction)
  val user = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  before {
    userProvider.setCurrentUser(user)
  }

  test("load enumerated values for asset type", Tag("db")) {
    runWithRollback {
      val values = provider.getEnumeratedPropertyValues(TestAssetTypeId)
      values shouldBe 'nonEmpty
      values.map(_.propertyName) should contain("Vaikutussuunta")
    }
  }
}
