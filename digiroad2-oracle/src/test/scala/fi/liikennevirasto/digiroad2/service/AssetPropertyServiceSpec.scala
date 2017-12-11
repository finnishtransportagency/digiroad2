package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.DummyEventBus
import fi.liikennevirasto.digiroad2.dao.OracleUserProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest._
import slick.jdbc.{StaticQuery => Q}

import scala.language.implicitConversions


class AssetPropertyServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val TestAssetTypeId = 10
  val userProvider = new OracleUserProvider

  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val service = new AssetPropertyService(new DummyEventBus, userProvider, passThroughTransaction)
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
      val values = service.getEnumeratedPropertyValues(TestAssetTypeId)
      values shouldBe 'nonEmpty
      values.map(_.propertyName) should contain("Vaikutussuunta")
    }
  }
}
