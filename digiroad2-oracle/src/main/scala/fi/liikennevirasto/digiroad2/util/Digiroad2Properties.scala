package fi.liikennevirasto.digiroad2.util

import org.slf4j.LoggerFactory

import java.util.Properties

trait Digiroad2Properties {
  val speedLimitProvider: String
  val userProvider: String
  val municipalityProvider: String
  val eventBus: String
  val useVVHGeometry: String
  val vvhServiceHost: String
  val vvhRestApiEndPoint: String
  val vvhRoadlinkFrozen: Boolean
  val viiteRestApiEndPoint: String
  val tierekisteriRestApiEndPoint: String
  val vkmUrl: String
  val valluServerSengindEnabled: Boolean
  val valluServerAddress: String
  val cacheDirectory: String
  val feedbackAssetsEndPoint: String
  val tierekisteriViiteRestApiEndPoint: String
  val tierekisteriEnabled: Boolean
  val httpProxySet: Boolean
  val httpProxyHost: String
  val httpProxyPort: String
  val httpNonProxyHosts: String
  val authenticationTestMode: Boolean
  val bonecpJdbcUrl: String
  val bonecpUsername: String
  val bonecpPassword: String
  val revision: String
  val latestDeploy: String
  val tierekisteriUsername: String
  val tierekisteriPassword: String
  val viiteApiKey: String
  val sesName: String
  val sesPassword: String
  val tierekisteriOldUsername: String
  val tierekisteriOldPassword: String
  val oagUsername: String
  val oagPassword: String
  val emailTo: String
  val emailHost: String
  val emailPort: String
  val env: String
  val featureProvider: String
  val googleMapApiClientId: String
  val googleMapApiCryptoKey: String
  val rasterServiceUrl: String
  val bonecpProperties: Properties
  val batchMode:Boolean
}

class Digiroad2PropertiesFromEnv extends Digiroad2Properties {
  val speedLimitProvider: String = scala.util.Properties.envOrElse("speedLimitProvider", null)
  val userProvider: String = scala.util.Properties.envOrElse("userProvider", null)
  val municipalityProvider: String = scala.util.Properties.envOrElse("municipalityProvider", null)
  val eventBus: String = scala.util.Properties.envOrElse("eventBus", null)
  val useVVHGeometry: String = scala.util.Properties.envOrElse("useVVHGeometry", null)
  val vvhServiceHost: String = scala.util.Properties.envOrElse("vvhServiceHost", null)
  val vvhRestApiEndPoint: String = scala.util.Properties.envOrElse("vvhRestApiEndPoint", null)
  val vvhRoadlinkFrozen: Boolean = scala.util.Properties.envOrElse("vvhRoadlink.frozen", "false").toBoolean
  val viiteRestApiEndPoint: String = scala.util.Properties.envOrElse("viiteRestApiEndPoint", null)
  val viiteApiKey: String = scala.util.Properties.envOrElse("viite.apikey", null)
  val sesName: String = scala.util.Properties.envOrElse("ses.name", null)
  val sesPassword: String = scala.util.Properties.envOrElse("ses.password", null)
  val tierekisteriRestApiEndPoint: String = scala.util.Properties.envOrElse("tierekisteriRestApiEndPoint", null)
  val vkmUrl: String = scala.util.Properties.envOrElse("vkmUrl", null)
  val valluServerSengindEnabled: Boolean = scala.util.Properties.envOrElse("vallu.server.sending_enabled", "true").toBoolean
  val valluServerAddress: String = scala.util.Properties.envOrElse("vallu.server.address", null)
  val feedbackAssetsEndPoint: String = scala.util.Properties.envOrElse("feedbackAssetsEndPoint", null)
  val tierekisteriViiteRestApiEndPoint: String = scala.util.Properties.envOrElse("tierekisteriViiteRestApiEndPoint", null)
  val tierekisteriEnabled: Boolean = scala.util.Properties.envOrElse("tierekisteri.enabled", "true").toBoolean
  val httpProxySet: Boolean = scala.util.Properties.envOrElse("http.proxySet", "false").toBoolean
  val httpProxyHost: String = scala.util.Properties.envOrElse("http.proxyHost", null)
  val httpProxyPort: String = scala.util.Properties.envOrElse("http.proxyPort", null)
  val httpNonProxyHosts: String = scala.util.Properties.envOrElse("http.nonProxyHosts", null)
  val authenticationTestMode: Boolean = scala.util.Properties.envOrElse("authenticationTestMode", "true").toBoolean
  val revision: String = scala.util.Properties.envOrElse("revision", null)
  val latestDeploy: String = scala.util.Properties.envOrElse("latestDeploy", null)
  val emailTo: String = scala.util.Properties.envOrElse("emailTo", null)
  val emailHost = scala.util.Properties.envOrElse("emailHost", null)
  val emailPort = scala.util.Properties.envOrElse("emailPort", null)
  val env: String = scala.util.Properties.envOrElse("env", "Unknown")
  val featureProvider: String = scala.util.Properties.envOrElse("featureProvider", null)
  val rasterServiceUrl: String = scala.util.Properties.envOrElse("rasterServiceUrl", null)
  val batchMode: Boolean = scala.util.Properties.envOrElse("batchMode", "false").toBoolean

  // Get build id to check if executing in aws CodeBuild environment.
  val awsBuildId: String = scala.util.Properties.envOrElse("CODEBUILD_BUILD_ID", null)
  private def selectEnvType(codebuildVersion: String, normal: String): String = {
    awsBuildId match {
      case null =>
        normal
      case _ =>
        codebuildVersion
    }
  }
  val googleMapApiClientId: String = selectEnvType(scala.util.Properties.envOrElse("googlemapapi_client_id", null), scala.util.Properties.envOrElse("googlemapapi.client_id", null))
  val googleMapApiCryptoKey: String = selectEnvType(scala.util.Properties.envOrElse("googlemapapi_crypto_key", null), scala.util.Properties.envOrElse("googlemapapi.crypto_key", null))
  val bonecpJdbcUrl: String = selectEnvType(scala.util.Properties.envOrElse("bonecp_jdbcUrl", null), scala.util.Properties.envOrElse("bonecp.jdbcUrl", null))
  val bonecpUsername: String = selectEnvType(scala.util.Properties.envOrElse("bonecp_username", null), scala.util.Properties.envOrElse("bonecp.username", null))
  val bonecpPassword: String = selectEnvType(scala.util.Properties.envOrElse("bonecp_password", null), scala.util.Properties.envOrElse("bonecp.password", null))
  val cacheDirectory: String = selectEnvType(scala.util.Properties.envOrElse("cache_directory", null), scala.util.Properties.envOrElse("cache.directory", null))
  val oagUsername: String = selectEnvType(scala.util.Properties.envOrElse("oag_username", null),scala.util.Properties.envOrElse("oag.username", null))
  val oagPassword: String =  selectEnvType(scala.util.Properties.envOrElse("oag_password", null),scala.util.Properties.envOrElse("oag.password", null))
  val tierekisteriOldUsername: String = selectEnvType(scala.util.Properties.envOrElse("tierekisteriOldUsername", null),scala.util.Properties.envOrElse("tierekisteri.old.username", null))
  val tierekisteriOldPassword: String = selectEnvType(scala.util.Properties.envOrElse("tierekisteriOldPassword", null),scala.util.Properties.envOrElse("tierekisteri.old.password", null))
  val tierekisteriUsername: String = selectEnvType(scala.util.Properties.envOrElse("tierekisteriUsername", null),scala.util.Properties.envOrElse("tierekisteri.username", null))
  val tierekisteriPassword: String = selectEnvType(scala.util.Properties.envOrElse("tierekisteriPassword", null),scala.util.Properties.envOrElse("tierekisteri.password", null))
  
  lazy val bonecpProperties: Properties = {
    val props = new Properties()
    try {
      props.setProperty("bonecp.jdbcUrl", bonecpJdbcUrl)
      props.setProperty("bonecp.username", bonecpUsername)
      props.setProperty("bonecp.password", bonecpPassword)
    } catch {
      case e: Exception => throw new RuntimeException("Can't load bonecp properties for env: " + env, e)
    }
    props
  }
}

class Digiroad2PropertiesFromFile extends Digiroad2Properties {

  private lazy val envProps: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/env.properties"))
    props
  }

  override val speedLimitProvider: String = envProps.getProperty("speedLimitProvider")
  override val userProvider: String = envProps.getProperty("userProvider")
  override val municipalityProvider: String = envProps.getProperty("municipalityProvider")
  override val eventBus: String = envProps.getProperty("eventBus")
  override val useVVHGeometry: String = envProps.getProperty("useVVHGeometry")
  override val vvhServiceHost: String = envProps.getProperty("vvhServiceHost")
  override val vvhRestApiEndPoint: String = envProps.getProperty("vvhRestApiEndPoint")
  override val vvhRoadlinkFrozen: Boolean = envProps.getProperty("vvhRoadlink.frozen", "false").toBoolean
  override val viiteRestApiEndPoint: String = envProps.getProperty("viiteRestApiEndPoint")
  override val tierekisteriRestApiEndPoint: String = envProps.getProperty("tierekisteriRestApiEndPoint")
  override val vkmUrl: String = envProps.getProperty("vkmUrl")
  override val valluServerSengindEnabled: Boolean = envProps.getProperty("vallu.server.sending_enabled", "true").toBoolean
  override val valluServerAddress: String = envProps.getProperty("vallu.server.address")
  override val cacheDirectory: String = envProps.getProperty("cache.directory")
  override val feedbackAssetsEndPoint: String = envProps.getProperty("feedbackAssetsEndPoint")
  override val tierekisteriViiteRestApiEndPoint: String = envProps.getProperty("tierekisteriViiteRestApiEndPoint")
  override val tierekisteriEnabled: Boolean = envProps.getProperty("tierekisteri.enabled", "true").toBoolean
  override val httpProxySet: Boolean = envProps.getProperty("http.proxySet", "false").toBoolean
  override val httpProxyHost: String = envProps.getProperty("http.proxyHost")
  override val httpProxyPort: String = envProps.getProperty("http.proxyPort")
  override val httpNonProxyHosts: String = envProps.getProperty("http.nonProxyHosts", "")
  override val authenticationTestMode: Boolean = envProps.getProperty("authenticationTestMode", "true").toBoolean
  override val bonecpJdbcUrl: String = envProps.getProperty("bonecp.jdbcUrl")
  override val bonecpUsername: String = envProps.getProperty("bonecp.username")
  override val bonecpPassword: String = envProps.getProperty("bonecp.password")
  override val revision: String = envProps.getProperty("revision")
  override val latestDeploy: String = envProps.getProperty("latestDeploy")
  override val tierekisteriUsername: String = envProps.getProperty("tierekisteri.username")
  override val tierekisteriPassword: String = envProps.getProperty("tierekisteri.password")
  override val viiteApiKey: String = envProps.getProperty("viite.apikey")
  override val sesName: String = envProps.getProperty("ses.name")
  override val sesPassword: String = envProps.getProperty("ses.password")
  override val tierekisteriOldUsername: String = envProps.getProperty("tierekisteri.old.username")
  override val tierekisteriOldPassword: String = envProps.getProperty("tierekisteri.old.password")
  override val oagUsername: String = envProps.getProperty("oag.username")
  override val oagPassword: String = envProps.getProperty("oag.password")
  override val emailTo: String = envProps.getProperty("email.to")
  override val emailHost: String = envProps.getProperty("email.host")
  override val emailPort: String = envProps.getProperty("email.port")
  override val env: String = envProps.getProperty("env")
  override val featureProvider: String = envProps.getProperty("featureProvider")
  override val googleMapApiClientId: String = envProps.getProperty("googlemapapi.client_id")
  override val googleMapApiCryptoKey: String = envProps.getProperty("googlemapapi.crypto_key")
  override val rasterServiceUrl: String = envProps.getProperty("rasterServiceUrl")
  override val batchMode: Boolean =  envProps.getProperty("batchMode", "false").toBoolean

  override lazy val bonecpProperties: Properties = {
    val props = new Properties()
    try {
      props.setProperty("bonecp.jdbcUrl", bonecpJdbcUrl)
      props.setProperty("bonecp.username", bonecpUsername)
      props.setProperty("bonecp.password", bonecpPassword)
    } catch {
      case e: Exception => throw new RuntimeException("Can't load bonecp properties for env: " + env, e)
    }
    props
  }
}

/**
  * Digiroad2Properties will get the properties from the environment variables by default.
  * If env.properties is found in classpath, then the properties are read from that property file.
  */
object Digiroad2Properties {
  private val logger = LoggerFactory.getLogger(getClass)
  lazy val properties: Digiroad2Properties = {
    val properties = getClass.getResource("/env.properties")
    if (properties == null || properties.getFile.isEmpty) {
      new Digiroad2PropertiesFromEnv
    } else {
      logger.info("Reading properties from file 'env.properties'.")
      new Digiroad2PropertiesFromFile
    }
  }

  lazy val speedLimitProvider: String = properties.speedLimitProvider
  lazy val userProvider: String = properties.userProvider
  lazy val municipalityProvider: String = properties.municipalityProvider
  lazy val eventBus: String = properties.eventBus
  lazy val useVVHGeometry: String = properties.useVVHGeometry
  lazy val vvhServiceHost: String = properties.vvhServiceHost
  lazy val vvhRestApiEndPoint: String = properties.vvhRestApiEndPoint
  lazy val vvhRoadlinkFrozen: Boolean = properties.vvhRoadlinkFrozen
  lazy val viiteRestApiEndPoint: String = properties.viiteRestApiEndPoint
  lazy val tierekisteriRestApiEndPoint: String = properties.tierekisteriRestApiEndPoint
  lazy val vkmUrl: String = properties.vkmUrl
  lazy val valluServerSendingEnabled: Boolean = properties.valluServerSengindEnabled
  lazy val valluServerAddress: String = properties.valluServerAddress
  lazy val cacheDirecroty: String = properties.cacheDirectory
  lazy val feedbackAssetsEndPoint: String = properties.feedbackAssetsEndPoint
  lazy val tierekisteriViiteRestApiEndPoint: String = properties.tierekisteriViiteRestApiEndPoint
  lazy val tierekisteriEnabled: Boolean = properties.tierekisteriEnabled
  lazy val httpProxySet: Boolean = properties.httpProxySet
  lazy val httpProxyHost: String = properties.httpProxyHost
  lazy val httpProxyPort: String = properties.httpProxyPort
  lazy val httpNonProxyHosts: String = properties.httpNonProxyHosts
  lazy val authenticationTestMode: Boolean = properties.authenticationTestMode
  lazy val bonecpJdbcUrl: String = properties.bonecpJdbcUrl
  lazy val bonecpUsername: String = properties.bonecpUsername
  lazy val bonecpPassword: String = properties.bonecpPassword
  lazy val revision: String = properties.revision
  lazy val latestDeploy: String = properties.latestDeploy
  lazy val tierekisteriUsername: String = properties.tierekisteriUsername
  lazy val tierekisteriPassword: String = properties.tierekisteriPassword
  lazy val viiteApiKey: String = properties.viiteApiKey
  lazy val sesName: String = properties.sesName
  lazy val sesPassword: String = properties.sesPassword
  lazy val tierekisteriOldUsername: String = properties.tierekisteriOldUsername
  lazy val tierekisteriOldPassword: String = properties.tierekisteriOldPassword
  lazy val oagUsername: String = properties.oagUsername
  lazy val oagPassword: String = properties.oagPassword
  lazy val emailTo: String = properties.emailTo
  lazy val emailHost: String = properties.emailHost
  lazy val emailPort: String = properties.emailPort
  lazy val env: String = properties.env
  lazy val featureProvider: String = properties.featureProvider
  lazy val googleMapApiClientId: String = properties.googleMapApiClientId
  lazy val googleMapApiCryptoKey: String = properties.googleMapApiCryptoKey
  lazy val rasterServiceUrl: String = properties.rasterServiceUrl
  lazy val batchMode: Boolean = properties.batchMode

  lazy val bonecpProperties: Properties = properties.bonecpProperties
}