package fi.liikennevirasto.digiroad2.vallu

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{StringEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.nio.charset.Charset
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

object ValluSender {

  // TODO: read from config
  val httpPost = new HttpPost("http://localhost:9002")
  val httpClient = HttpClients.createDefault()

  def postToVallu(asset: AssetWithProperties) {
    val payload =
      """
        <Stops>
          <Stop>
            <StopId>144948</StopId>
            <Coordinate>
              <xCoordinate>340920</xCoordinate>
              <yCoordinate>6987560</yCoordinate>
            </Coordinate>
            <Bearing>135</Bearing>
            <StopAttribute>
              <StopType name="LOCAL_BUS">0</StopType>
              <StopType name="EXPRESS_BUS">1</StopType>
              <StopType name="NON_STOP_EXPRESS_BUS">0</StopType>
              <StopType name="VIRTUAL_STOP">0</StopType>
            </StopAttribute>
            <Equipment/>
            <ModifiedTimestamp>2014-04-29T09:30:47</ModifiedTimestamp>
            <ModifiedBy>CGI</ModifiedBy>
            <AdministratorCode>Livi117347</AdministratorCode>
            <MunicipalityName>Alajärvi</MunicipalityName>
            <Comments>pysäkin koordinaatit pyöristetty</Comments>
            <ContactEmails>
              <Contact>pysakit@liikennevirasto.fi</Contact>
            </ContactEmails>
          </Stop>
        </Stops>"""

    postToVallu(payload)
  }

  private def postToVallu(payload: String) = {
    val entity = new StringEntity(payload, ContentType.create("text/xml", "UTF-8"))
    httpPost.setEntity(entity)
    val response = httpClient.execute(httpPost)
    try {
      // TODO: println "handling" out
      println(EntityUtils.toString(response.getEntity , Charset.forName("UTF-8")))
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }
}
