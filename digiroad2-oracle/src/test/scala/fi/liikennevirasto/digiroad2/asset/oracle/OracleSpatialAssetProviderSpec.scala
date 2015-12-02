package fi.liikennevirasto.digiroad2.asset.oracle

import java.sql.SQLIntegrityConstraintViolationException

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, Role, User}
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, EventBusMassTransitStop, Point}
import org.joda.time.LocalDate
import org.mockito.Mockito.verify
import org.scalatest._
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

import scala.language.implicitConversions
import slick.jdbc.{StaticQuery => Q}
import scala.util.Random
import slick.jdbc.StaticQuery.interpolation


class OracleSpatialAssetProviderSpec extends FunSuite with Matchers with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val MunicipalityEspoo = 49
  val TestAssetTypeId = 10
  val AssetCreator = "integration_test_add_asset"
  val userProvider = new OracleUserProvider

  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val provider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider, passThroughTransaction)
  val user = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))
  val espooUser = User(
    id = 2,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityEspoo)))
  val espooKauniainenUser = User(
    id = 3,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(MunicipalityEspoo, MunicipalityKauniainen)))
  val unauthorizedUser =
    user.copy(configuration = Configuration(authorizedMunicipalities = Set(666999)))
  val creatingUser = user.copy(username = AssetCreator)
  val operatorUser = user.copy(configuration = user.configuration.copy(authorizedMunicipalities = Set(1), roles = Set(Role.Operator)))

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
