package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, RoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, Measures, NewLinearAssetMassOperation}
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.{Configuration, Role, User}
import fi.liikennevirasto.digiroad2.util.RoadAddressRange
import fi.liikennevirasto.digiroad2.{DummyEventBus, GeometryUtils, Point}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, TrustAllStrategy}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, JArray, JDouble}

import java.util.Base64
import javax.net.ssl.{HostnameVerifier, SSLSession}

class VelhoClient {

  val vkmClient = new VKMClient
  val roadLinkClient = new RoadLinkClient
  val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus)
  val linearAssetService = new LinearAssetService(roadLinkService, new DummyEventBus)
  val trafficSignService = new TrafficSignService(roadLinkService, new DummyEventBus)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def toRoadLink(l: RoadLinkFetched): RoadLink = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

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

  private def fetchAssetsFromLatauspalvelu(apiUrl: String, token: String, paths: Seq[String]): String = {

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

  //TODO combine fetch methods to avoid duplicate code
  private def fetchAssetsFromHakupalvelu(apiUrl: String, token: String): String = {

    val url = apiUrl
    val request = new HttpPost(url)
    request.setHeader("Authorization", s"Bearer $token")

    val payload = """{
                    |    "asetukset": {
                    |        "samalla-kaistalla": false
                    |    },
                    |    "kohdeluokat": [
                    |        "varusteet/liikennemerkit"
                    |    ],
                    |    "lauseke": [
                    |        "kohdeluokka",
                    |        "varusteet/liikennemerkit",
                    |        [
                    |            "pvm-suurempi-kuin",
                    |            [
                    |                "varusteet/liikennemerkit",
                    |                "alkaen"
                    |            ],
                    |            "2024-06-09T11:16:40.000Z"
                    |        ]
                    |    ]
                    |}""".stripMargin

    val entity = new StringEntity(payload, ContentType.APPLICATION_JSON)
    request.setEntity(entity)
    request.setHeader("Content-Type", "application/json")

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
  case class VelhoAsset(oid: String, createdDate: DateTime, editedDateOpt: Option[DateTime], geometry: Seq[Double])

  private def savePointAsset(assets: String): Unit = {

    val specifiedDate = DateTime.now().minusMonths(100)
    val lines = assets.split("\n").filter(_.nonEmpty)
    val parsedAssets = lines.map { line =>
      val json = parse(line)
      val oid = (json \ "oid").extract[String]
      val createdDate = DateTime.parse((json \ "luotu").extract[String])
      val editedDateOpt = (json \ "muokattu").extractOpt[String].map(DateTime.parse)
      val geometryOpt = (json \ "keskilinjageometria" \ "coordinates").extractOpt[JArray]

      val geometry = geometryOpt match {
        case Some(JArray(items)) =>
          items.collect { case JDouble(num) => num }
        case _ => Seq.empty[Double]
      }
      VelhoAsset(
        oid,
        createdDate,
        editedDateOpt,
        geometry
      )
    }
    val existingAssets = withDynTransaction(trafficSignService.fetchPointAssets(query => query + "where a.municipality_code in (931, 182, 249, 179) and a.created_by = 'velhoTest'"))
    val (preserved, deleted) = existingAssets.partition(a => parsedAssets.map(_.oid).contains(a.externalId.get))
    val newAssets = parsedAssets.filterNot(a => preserved.map(_.externalId.get).contains(a.oid))
    val updatedAssets = preserved.filter { a =>
      parsedAssets.exists(p => p.oid == a.externalId.get && p.editedDateOpt.exists(_.isAfter(specifiedDate)))
    }
    deleted.map(_.id).foreach(id => trafficSignService.expire(id, "velhoExpire"))

    val simpleProperties = Set(
      SimplePointAssetProperty("location_specifier", List(PropertyValue("99", None))),
      SimplePointAssetProperty("height", List()),
      SimplePointAssetProperty("structure", List(PropertyValue("1", None))),
      SimplePointAssetProperty("sign_material", List(PropertyValue("2", None))),
      SimplePointAssetProperty("trafficSign_start_date", List()),
      SimplePointAssetProperty("lane_type", List(PropertyValue("99", None))),
      SimplePointAssetProperty("life_cycle", List(PropertyValue("3", None))),
      SimplePointAssetProperty("urgency_of_repair", List(PropertyValue("99", None))),
      SimplePointAssetProperty("trafficSigns_type", List(PropertyValue("36", None))),
      SimplePointAssetProperty("size", List(PropertyValue("2", None))),
      SimplePointAssetProperty("coating_type", List(PropertyValue("99", None))),
      SimplePointAssetProperty("suggest_box", List(PropertyValue("0", None))),
      SimplePointAssetProperty("old_traffic_code", List(PropertyValue("0", None))),
      SimplePointAssetProperty("type_of_damage", List(PropertyValue("99", None))),
      SimplePointAssetProperty("trafficSigns_info", List()),
      SimplePointAssetProperty("main_sign_text", List()),
      SimplePointAssetProperty("trafficSign_end_date", List()),
      SimplePointAssetProperty("terrain_coordinates_y", List()),
      SimplePointAssetProperty("condition", List(PropertyValue("99", None))),
      SimplePointAssetProperty("opposite_side_sign", List(PropertyValue("0", None))),
      SimplePointAssetProperty("municipality_id", List()),
      SimplePointAssetProperty("lane", List()),
      SimplePointAssetProperty("terrain_coordinates_x", List()),
      SimplePointAssetProperty("additional_panel", List()),
      SimplePointAssetProperty("lifespan_left", List()),
      SimplePointAssetProperty("trafficSigns_value", List())
    )

    newAssets.foreach { asset =>
      val roadLink = roadLinkService.getClosestRoadlink(User(1, "test", Configuration(roles = Set(Role.Operator))), Point(asset.geometry.head, asset.geometry(1)))
      if (roadLink.nonEmpty) {
        val incomingTrafficSign = IncomingTrafficSign(asset.geometry.head, asset.geometry(1), roadLink.get.linkId, simpleProperties, 2, None, None, Some(asset.oid))
        trafficSignService.create(incomingTrafficSign, "velhoTest", toRoadLink(roadLink.get))
      }
    }

    updatedAssets.foreach { asset =>
      val roadLink = roadLinkService.getClosestRoadlink(User(1, "test", Configuration(roles = Set(Role.Operator))), Point(asset.lon, asset.lat))
      val incomingTrafficSign = IncomingTrafficSign(asset.lon, asset.lat, asset.linkId, simpleProperties, 2, None, None, asset.externalId)
        withDynTransaction(trafficSignService.updateWithoutTransaction(asset.id, incomingTrafficSign, toRoadLink(roadLink.get), "velhoUpdate", None, None))
      }
  }

  def importAssetsFromLatauspalvelu(username: String, password: String, path: String): Unit = {
    val tokenUrl = ""
    val apiUrl = ""
    val token = fetchToken(tokenUrl, username, password)
    val paths = fetchPaths(apiUrl, token, path)
    val assets = fetchAssetsFromLatauspalvelu(apiUrl, token, paths)
    savePointAsset(assets)
  }


  def importAssetsFromHakupalvelu(username: String, password: String): Unit = {
    val tokenUrl = ""
    val apiUrl = ""
    val token = fetchToken(tokenUrl, username, password)
    val assets = fetchAssetsFromHakupalvelu(apiUrl, token)
    savePointAsset(assets)
  }
}
