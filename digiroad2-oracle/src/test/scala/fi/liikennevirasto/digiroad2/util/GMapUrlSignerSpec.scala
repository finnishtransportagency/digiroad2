package fi.liikennevirasto.digiroad2.util

import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 14.10.2016.
  */
class GMapUrlSignerSpec extends FunSuite with Matchers {

  test("testSignRequest") {
    val signer = new GMapUrlSigner
    val request = signer.signRequest("60.0", "24.0", "180")
    request should be ("https://maps.googleapis.com/maps/api/streetview?location=60.0,24.0&" +
      "size=360x180&client=XYZ123&fov=110&heading=180&pitch=-10&sensor=false&signature=WPIDogYrT3qM1p5yBuvdT-oa96k")
  }

}
