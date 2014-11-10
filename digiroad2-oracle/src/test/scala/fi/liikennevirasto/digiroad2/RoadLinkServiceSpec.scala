package fi.liikennevirasto.digiroad2

import org.scalatest.{FunSuite, Matchers}

class RoadLinkServiceSpec extends FunSuite with Matchers {
  test("Get production road link with test id that maps to one production road link") {
    RoadLinkService.getMunicipalityAndPointOnRoadLinkByTestId(48l, 50.0).map(_._1) should be (Some(57))
    RoadLinkService.getMunicipalityAndPointOnRoadLinkByTestId(48l, 50.0).map(_._2) should be (Some(18))
  }

  test("Get production road link with test id that doesn't map to production") {
    RoadLinkService.getMunicipalityAndPointOnRoadLinkByTestId(1414l, 50.0) should be (None)
  }

  test("Get production road link with test id that maps to several links in production") {
    RoadLinkService.getMunicipalityAndPointOnRoadLinkByTestId(147298l, 50.0) should be (None)
  }
}