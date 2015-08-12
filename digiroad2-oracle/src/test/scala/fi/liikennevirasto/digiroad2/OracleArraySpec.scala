package fi.liikennevirasto.digiroad2
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

//import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries
import scala.collection.JavaConversions._
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
//import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray

class OracleArraySpec extends FunSuite with Matchers with BeforeAndAfter {
  val mmlIdWithNulls: Int = 354365322
  var links : Seq[(Long, Long, Int)] = _
  before {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      links = OracleArray.fetchRoadLinkDataByMmlIds(List(mmlIdWithNulls), Queries.bonecpToInternalConnection(dynamicSession.conn))
    }
  }
  test("Should get unknown administrative class if null in database") {
    links.get(0)._3 should equal(99)
  }
}
