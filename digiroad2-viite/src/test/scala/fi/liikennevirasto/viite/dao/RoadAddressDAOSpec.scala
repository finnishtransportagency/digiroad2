package fi.liikennevirasto.viite.dao

import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 12.9.2016.
  */
class RoadAddressDAOSpec extends FunSuite with Matchers {

  test("testFetchByLinkId") {

  }

  test("Get valid road numbers") {
    val numbers = RoadAddressDAO.getValidRoadNumbers
    numbers.size should be (">0")
  }
}
