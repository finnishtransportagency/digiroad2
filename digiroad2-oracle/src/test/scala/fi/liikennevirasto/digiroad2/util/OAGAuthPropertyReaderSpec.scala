package fi.liikennevirasto.digiroad2.util

import org.scalatest.FunSuite
import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}

class OAGAuthPropertyReaderSpec extends FunSuite {
  val reader = new OAGAuthPropertyReader

  test("Test reader.getAuthInBase64 When asking for the Basic64 authentication string for OAG Then return said string.") {
    val authenticate = reader.getAuthInBase64
    // when string below is decoded it will have values from env.properties presented as: <oag.username>:<oag.password>
    // "c3ZjX2Nsb3VkZGlnaXJvYWQ6c3ZjX2Nsb3VkZGlnaXJvYWQ=" == svc_clouddigiroad:svc_clouddigiroad
    authenticate should be ("c3ZjX2Nsb3VkZGlnaXJvYWQ6c3ZjX2Nsb3VkZGlnaXJvYWQ=")
  }
}
