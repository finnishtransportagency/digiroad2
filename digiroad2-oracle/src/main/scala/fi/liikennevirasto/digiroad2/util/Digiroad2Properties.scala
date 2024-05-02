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
  val kgvEndpoint:String
  val kgvApiKey:String
  val vvhRestUsername: String
  val vvhRestPassword: String
  val viiteRestApiEndPoint: String
  val vkmUrl: String
  val vkmApiKey: String
  val valluApiKey: String
  val valluServerSengindEnabled: Boolean
  val valluServerAddress: String
  val cacheHostname: String
  val cacheHostPort: String
  val caching: Boolean
  val cacheTTL: String
  val feedbackAssetsEndPoint: String
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
  val viiteApiKey: String
  val sesUsername: String
  val sesPassword: String
  val emailTo: String
  val emailHost: String
  val emailPort: String
  val emailFrom: String
  val env: String
  val featureProvider: String
  val googleMapApiClientId: String
  val googleMapApiCryptoKey: String
  val rasterServiceUrl: String
  val rasterServiceApiKey: String
  val apiS3BucketName: String
  val apiS3ObjectTTLSeconds: String
  val awsConnectionEnabled: Boolean
  val roadLinkChangeS3BucketName: String
  val samuutusReportsBucketName: String
  val failSamuutusOnFailedValidation: Boolean
  val validationReportsBucketName: String
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
  val kgvEndpoint: String = scala.util.Properties.envOrElse("kgv.endpoint", null)
  val kgvApiKey: String = scala.util.Properties.envOrElse("kgv.apikey", null)
  val viiteApiKey: String = scala.util.Properties.envOrElse("viite.apikey", null)
  val sesUsername: String = scala.util.Properties.envOrElse("ses.username", null)
  val sesPassword: String = scala.util.Properties.envOrElse("ses.password", null)
  val vkmUrl: String = scala.util.Properties.envOrElse("vkmUrl", null)
  val vkmApiKey: String = scala.util.Properties.envOrElse("vkm.apikey", null)
  val valluApiKey: String = scala.util.Properties.envOrElse("vallu.apikey", null)
  val valluServerSengindEnabled: Boolean = scala.util.Properties.envOrElse("vallu.server.sending_enabled", "true").toBoolean
  val valluServerAddress: String = scala.util.Properties.envOrElse("vallu.server.address", null)
  val feedbackAssetsEndPoint: String = scala.util.Properties.envOrElse("feedbackAssetsEndPoint", null)
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
  val emailFrom = scala.util.Properties.envOrElse("emailFrom", null)
  val env: String = scala.util.Properties.envOrElse("env", "Unknown")
  val featureProvider: String = scala.util.Properties.envOrElse("featureProvider", null)
  val rasterServiceUrl: String = scala.util.Properties.envOrElse("rasterServiceUrl", null)
  val rasterServiceApiKey: String = scala.util.Properties.envOrElse("rasterService.apikey", null)
  val apiS3BucketName: String = scala.util.Properties.envOrElse("apiS3BucketName", null)
  val apiS3ObjectTTLSeconds: String = scala.util.Properties.envOrElse("apiS3ObjectTTLSeconds", null)
  val awsConnectionEnabled: Boolean = scala.util.Properties.envOrElse("awsConnectionEnabled", "true").toBoolean
  val batchMode: Boolean = scala.util.Properties.envOrElse("batchMode", "false").toBoolean
  val roadLinkChangeS3BucketName: String = scala.util.Properties.envOrElse("roadLinkChangeS3BucketName", null)
  val samuutusReportsBucketName: String = scala.util.Properties.envOrElse("samuutusReportsBucketName", null)
  val failSamuutusOnFailedValidation: Boolean = scala.util.Properties.envOrElse("failSamuutusOnFailedValidation", "false").toBoolean
  val validationReportsBucketName: String = scala.util.Properties.envOrElse("validationReportsBucketName", null)
  
  val cacheHostname: String = scala.util.Properties.envOrElse("cacheHostname", null)
  val cacheHostPort: String = scala.util.Properties.envOrElse("cacheHostPort", null)
  val caching: Boolean = scala.util.Properties.envOrElse("caching", "false").toBoolean
  val cacheTTL: String = scala.util.Properties.envOrElse("cacheTTL", null)
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

  val vvhRestUsername: String = selectEnvType(scala.util.Properties.envOrElse("vvhRest_username", null), scala.util.Properties.envOrElse("vvhRest.username", null))
  val vvhRestPassword: String = selectEnvType(scala.util.Properties.envOrElse("vvhRest_password", null), scala.util.Properties.envOrElse("vvhRest.password", null))
  val googleMapApiClientId: String = selectEnvType(scala.util.Properties.envOrElse("googlemapapi_client_id", null), scala.util.Properties.envOrElse("googlemapapi.client_id", null))
  val googleMapApiCryptoKey: String = selectEnvType(scala.util.Properties.envOrElse("googlemapapi_crypto_key", null), scala.util.Properties.envOrElse("googlemapapi.crypto_key", null))
  val bonecpJdbcUrl: String = selectEnvType(scala.util.Properties.envOrElse("bonecp_jdbcUrl", null), scala.util.Properties.envOrElse("bonecp.jdbcUrl", null))
  val bonecpUsername: String = selectEnvType(scala.util.Properties.envOrElse("bonecp_username", null), scala.util.Properties.envOrElse("bonecp.username", null))
  val bonecpPassword: String = selectEnvType(scala.util.Properties.envOrElse("bonecp_password", null), scala.util.Properties.envOrElse("bonecp.password", null))
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
  override val vvhRestUsername: String = envOrProperties("vvhRest.username")
  override val vvhRestPassword: String = envOrProperties("vvhRest.password")
  override val vvhRoadlinkFrozen: Boolean = envProps.getProperty("vvhRoadlink.frozen", "false").toBoolean
  override val kgvEndpoint: String = envProps.getProperty("kgv.endpoint", null)
  override val kgvApiKey: String = envOrProperties("kgv.apikey")
  override val viiteRestApiEndPoint: String =  envOrProperties("viiteRestApiEndPoint")
  override val vkmUrl: String = envProps.getProperty("vkmUrl")
  override val vkmApiKey: String = envOrProperties("vkm.apikey")
  override val valluApiKey: String = envOrProperties("vallu.apikey")
  override val valluServerSengindEnabled: Boolean = envProps.getProperty("vallu.server.sending_enabled", "true").toBoolean
  override val valluServerAddress: String = envProps.getProperty("vallu.server.address")
  override val cacheHostname: String = envProps.getProperty("cacheHostname", null)
  override val cacheHostPort: String = envProps.getProperty("cacheHostPort", null)
  override val caching: Boolean = envProps.getProperty("caching", "false").toBoolean
  override val cacheTTL: String = envProps.getProperty("cacheTTL", null)
  override val feedbackAssetsEndPoint: String = envProps.getProperty("feedbackAssetsEndPoint")
  override val httpProxySet: Boolean = envProps.getProperty("http.proxySet", "false").toBoolean
  override val httpProxyHost: String = envProps.getProperty("http.proxyHost")
  override val httpProxyPort: String = envProps.getProperty("http.proxyPort")
  override val httpNonProxyHosts: String = envProps.getProperty("http.nonProxyHosts", "")
  override val authenticationTestMode: Boolean = envProps.getProperty("authenticationTestMode", "true").toBoolean
  override val bonecpJdbcUrl: String = envOrProperties("bonecp.jdbcUrl")
  override val bonecpUsername: String = envOrProperties("bonecp.username")
  override val bonecpPassword: String = envOrProperties("bonecp.password")
  override val revision: String = envProps.getProperty("revision")
  override val latestDeploy: String = envProps.getProperty("latestDeploy")
  override val viiteApiKey: String = envOrProperties("viite.apikey")
  override val sesUsername: String = envOrProperties("ses.username")
  override val sesPassword: String = envOrProperties("ses.password")
  override val emailTo: String = envProps.getProperty("email.to")
  override val emailHost: String = envProps.getProperty("email.host")
  override val emailPort: String = envProps.getProperty("email.port")
  override val emailFrom: String = envProps.getProperty("email.from")
  override val env: String = envProps.getProperty("env")
  override val featureProvider: String = envProps.getProperty("featureProvider")
  override val googleMapApiClientId: String = envOrProperties("googlemapapi.client_id")
  override val googleMapApiCryptoKey: String = envOrProperties("googlemapapi.crypto_key")
  override val rasterServiceUrl: String = envProps.getProperty("rasterServiceUrl")
  override val rasterServiceApiKey: String = envOrProperties("rasterService.apikey")
  override val apiS3BucketName: String = envOrProperties("apiS3BucketName")
  override val apiS3ObjectTTLSeconds: String = envOrProperties("apiS3ObjectTTLSeconds")
  override val awsConnectionEnabled: Boolean = envProps.getProperty("awsConnectionEnabled", "true").toBoolean
  override val roadLinkChangeS3BucketName: String = envOrProperties("roadLinkChangeS3BucketName")
  override val samuutusReportsBucketName: String = envOrProperties("samuutusReportsBucketName")
  override val failSamuutusOnFailedValidation: Boolean = envProps.getProperty("failSamuutusOnFailedValidation", "false").toBoolean
  override val validationReportsBucketName: String = envOrProperties("validationReportsBucketName")
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
  
  def envOrProperties(parameter:String) ={
    scala.util.Properties.envOrElse(parameter, envProps.getProperty(parameter))
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
  lazy val vvhRestUsername: String = properties.vvhRestUsername
  lazy val vvhRestPassword: String = properties.vvhRestPassword
  lazy val kgvEndpoint: String = properties.kgvEndpoint
  lazy val kgvApiKey: String = properties.kgvApiKey
  lazy val viiteRestApiEndPoint: String = properties.viiteRestApiEndPoint
  lazy val vkmUrl: String = properties.vkmUrl
  lazy val vkmApiKey: String = properties.vkmApiKey
  lazy val valluApikey: String = properties.valluApiKey
  lazy val valluServerSendingEnabled: Boolean = properties.valluServerSengindEnabled
  lazy val valluServerAddress: String = properties.valluServerAddress
  lazy val cacheHostname: String = properties.cacheHostname
  lazy val cacheHostPort: String = properties.cacheHostPort
  lazy val caching: Boolean = properties.caching
  lazy val cacheTTL: String = properties.cacheTTL
  lazy val feedbackAssetsEndPoint: String = properties.feedbackAssetsEndPoint
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
  lazy val viiteApiKey: String = properties.viiteApiKey
  lazy val sesUsername: String = properties.sesUsername
  lazy val sesPassword: String = properties.sesPassword
  lazy val emailTo: String = properties.emailTo
  lazy val emailHost: String = properties.emailHost
  lazy val emailPort: String = properties.emailPort
  lazy val emailFrom: String = properties.emailFrom
  lazy val env: String = properties.env
  lazy val featureProvider: String = properties.featureProvider
  lazy val googleMapApiClientId: String = properties.googleMapApiClientId
  lazy val googleMapApiCryptoKey: String = properties.googleMapApiCryptoKey
  lazy val rasterServiceUrl: String = properties.rasterServiceUrl
  lazy val rasterServiceApiKey:String = properties.rasterServiceApiKey
  lazy val apiS3BucketName: String = properties.apiS3BucketName
  lazy val apiS3ObjectTTLSeconds: String = properties.apiS3ObjectTTLSeconds
  lazy val awsConnectionEnabled: Boolean = properties.awsConnectionEnabled
  lazy val roadLinkChangeS3BucketName: String = properties.roadLinkChangeS3BucketName
  lazy val samuutusReportsBucketName: String = properties.samuutusReportsBucketName
  /**
    * Defaul false
    */
  lazy val failSamuutusOnFailedValidation: Boolean = properties.failSamuutusOnFailedValidation
  lazy val validationReportsBucketName: String = properties.validationReportsBucketName
  lazy val batchMode: Boolean = properties.batchMode

  lazy val bonecpProperties: Properties = properties.bonecpProperties
}