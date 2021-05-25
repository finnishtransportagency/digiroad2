package fi.liikennevirasto.digiroad2.authentication

import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}
import org.scalatest.FunSuite

class JWTReaderSpec extends FunSuite {
  test("Test parseUsernameFromJWTPayloadJSONString When valid JSON Then return username") {
    val json = """{"custom:rooli":"int_kayttajat,Extranet_Kayttaja,arn:aws:iam::117531223221:role/DigiroadAdmin\\,arn:aws:iam::117531223221:saml-provider/VaylaTestOAM","sub":"2b5a2b65-ca06-46e2-8a52-a5190b495d12","email_verified":"false","custom:uid":"K123456","email":"other.user@sitowise.com","username":"vaylatestoam_other.usern@sitowise.com","exp":1591117019,"iss":"https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_oNzPsiXEJ"}""".stripMargin
    JWTReader.parseUsernameFromJWTPayloadJSONString(json) should be("K123456")
  }
}
