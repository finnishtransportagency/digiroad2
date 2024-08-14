package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.DummyEventBus
import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, LitRoad, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, Measures, NewLinearAssetMassOperation}
import fi.liikennevirasto.digiroad2.util.RoadAddressRange
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, TrustAllStrategy}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

import java.util.Base64
import javax.net.ssl.{HostnameVerifier, SSLSession}

class VelhoClient {

  val vkmClient = new VKMClient
  val roadLinkClient = new RoadLinkClient
  val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus)
  val linearAssetService = new LinearAssetService(roadLinkService, new DummyEventBus)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  implicit val formats: DefaultFormats.type = DefaultFormats
  private def fetchToken(url: String, username: String, password:String): String = {
    val request = new HttpPost(url)
    val params = "grant_type=client_credentials"
    request.setEntity(new StringEntity(params, ContentType.APPLICATION_FORM_URLENCODED))
    val auth = s"$username:$password"
    val encodedAuth = Base64.getEncoder.encodeToString(auth.getBytes("UTF-8"))
    request.setHeader("Authorization", s"Basic $encodedAuth")

    val client: CloseableHttpClient = HttpClients.createDefault()

    try {
      val response = client.execute(request)
      val responseBody = EntityUtils.toString(response.getEntity)
      val json = parse(responseBody)
      (json \ "access_token").extract[String]
    } catch {
      case e: Exception =>
        throw new Exception("failed to fetch token")
    } finally {
      client.close()
    }
  }

  private def fetchPaths(apiUrl: String, token: String, path: String) = {
    val url = s"""${apiUrl}/kohdeluokka/${path}"""
    val request = new HttpGet(url)
    request.setHeader("Authorization", s"Bearer $token")

    val client: CloseableHttpClient = HttpClients.createDefault()

    try {
      val response = client.execute(request)
      val responseBody = EntityUtils.toString(response.getEntity)

      val json = parse(responseBody)

      (json \ "jaottelut" \ "alueet/ely").children.map(ely => (ely \ "polku").extract[String])

    } catch {
      case e: Exception =>
        throw new Exception("failed to fetch elys")
    } finally {
      client.close()
    }
  }

  private def fetchAssets(apiUrl: String, token: String, paths: Seq[String]): String = {

    val url = s"""${apiUrl}/${paths.head}"""
    val request = new HttpGet(url)
    request.setHeader("Authorization", s"Bearer $token")

    // Create a custom SSL context that trusts all certificates
    val sslContext = SSLContextBuilder.create()
      .loadTrustMaterial(new TrustAllStrategy())
      .build()

    // Disable hostname verification
    val allowAllHosts: HostnameVerifier = new HostnameVerifier {
      override def verify(s: String, sslSession: SSLSession): Boolean = true
    }

    val socketFactory = new SSLConnectionSocketFactory(sslContext, allowAllHosts)

    // Redirect has to be processed manually as it comes with signed url
    val requestConfig = RequestConfig.custom()
      .setRedirectsEnabled(false)
      .build()

    // Build the client with the custom SSL context and socket factory
    val client: CloseableHttpClient = HttpClients.custom()
      .setSSLSocketFactory(socketFactory)
      .setDefaultRequestConfig(requestConfig)
      .build()

    val fetchedAssets: String = try {
      val response = client.execute(request)
      val statusCode = response.getStatusLine.getStatusCode

      if (statusCode == 301 || statusCode == 302 || statusCode == 307) {
        val locationHeader = response.getFirstHeader("Location")
        val redirectUrl = locationHeader.getValue

        // Check if the redirect URL is a signed URL
        if (redirectUrl.contains("X-Amz-Signature")) {
          val redirectRequest = new HttpGet(redirectUrl)
          // Do not set Authorization header for signed URL
          val redirectResponse = client.execute(redirectRequest)
          try {
            EntityUtils.toString(redirectResponse.getEntity)
          } finally {
            redirectResponse.close()
          }
        } else {
          ""
        }
      } else {
        EntityUtils.toString(response.getEntity)
      }
    } catch {
      case e: Exception =>
        throw new Exception("failed to fetch assets")
        ""
    } finally {
      client.close()
    }
    fetchedAssets
  }

  private def saveAssets(assets: String): Unit = {
    val lines = assets.split("\n").filter(_.nonEmpty)
      lines.foreach { line =>
        val json = parse(line)
        val roadNumber = (json \ "alkusijainti" \ "tie").extract[Long]
        val range = RoadAddressRange(
          roadNumber,
          None,
          (json \ "alkusijainti" \ "osa").extract[Long],
          (json \ "loppusijainti" \ "osa").extract[Long],
          (json \ "alkusijainti" \ "etaisyys").extract[Long],
          (json \ "loppusijainti" \ "etaisyys").extract[Long]
        )
        val startAndEndLinkSet = vkmClient.fetchStartAndEndLinkIdForAddrRange(range)
        val massOperations = startAndEndLinkSet.flatMap { startAndEndLink =>
          val allLinks = vkmClient.fetchLinkIdsBetweenTwoRoadLinks(startAndEndLink._1, startAndEndLink._2, roadNumber)

          val roadLinks = roadLinkService.fetchRoadlinksAndComplementaries(allLinks)
          roadLinks.map { roadLink =>
            NewLinearAssetMassOperation(
              LitRoad.typeId, roadLink.linkId,
              DynamicValue(DynamicAssetValue(Seq(DynamicProperty("suggest_box", "checkbox", false, Seq())))),
              SideCode.BothDirections.value,
              Measures(0, roadLink.length),
              "velhoTest", 0L,
              Some(roadLink),
              geometry = roadLink.geometry,
              linkSource = None)
          }

        }.toSeq
        withDynTransaction(linearAssetService.createMultipleLinearAssets(massOperations))
      }
  }

  def importAssets(username: String, password: String, path: String) = {
    val tokenUrl = ""
    val apiUrl = ""
    val token = fetchToken(tokenUrl, username, password)
    val paths = fetchPaths(apiUrl, token, path)
    val assets = fetchAssets(apiUrl, token, paths)
    saveAssets(assets)
  }

}
