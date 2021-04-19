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
  val vvhProdRestApiEndPoint: String
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
  val httpNonProxyHosts: String
  val importOnlyCurrent: Boolean
  val authenticationTestMode: Boolean
  val authenticationTestUser: String
  val bonecpJdbcUrl: String
  val bonecpUsername: String
  val bonecpPassword: String
  val authenticationBasicUsername: String
  val authenticationBasicPassword: String
  val authenticationServiceRoadBasicUsername: String
  val authenticationServiceRoadBasicPassword: String
  val authenticationMunicipalityBasicUsername: String
  val authenticationMunicipalityBasicPassword: String
  val revision: String
  val latestDeploy: String
  val tierekisteriUsername: String
  val tierekisteriPassword: String
  val viiteUsername: String
  val viitePassword: String
  val tierekisteriOldUsername: String
  val tierekisteriOldPassword: String
  val emailTo: String
  val emailHost: String
  val emailPort: String
  val env: String


  val bonecpProperties: Properties

  def getAuthenticationBasicUsername(baseAuth: String = ""): String
  def getAuthenticationBasicPassword(baseAuth: String = ""): String
}

class Digiroad2PropertiesFromEnv extends Digiroad2Properties {
  val speedLimitProvider: String = scala.util.Properties.envOrElse("speedLimitProvider", null)
  val userProvider: String = scala.util.Properties.envOrElse("userProvider", null)
  val municipalityProvider: String = scala.util.Properties.envOrElse("municipalityProvider", null)
  val eventBus: String = scala.util.Properties.envOrElse("eventBus", null)
  val useVVHGeometry: String = scala.util.Properties.envOrElse("useVVHGeometry", null)
  val vvhServiceHost: String = scala.util.Properties.envOrElse("vvhServiceHost", null)
  val vvhRestApiEndPoint: String = scala.util.Properties.envOrElse("vvhRestApiEndPoint", null)
  val vvhProdRestApiEndPoint: String = scala.util.Properties.envOrElse("VVHProdRestApiEndPoint", null)
  val vvhRoadlinkFrozen: Boolean = scala.util.Properties.envOrElse("vvhRoadlink.frozen", "false").toBoolean
  val viiteRestApiEndPoint: String = scala.util.Properties.envOrElse("viiteRestApiEndPoint", null)
  val tierekisteriRestApiEndPoint: String = scala.util.Properties.envOrElse("tierekisteriRestApiEndPoint", null)
  val vkmUrl: String = scala.util.Properties.envOrElse("vkmUrl", null)
  val valluServerSengindEnabled: Boolean = scala.util.Properties.envOrElse("vallu.server.sending_enabled", "true").toBoolean
  val valluServerAddress: String = scala.util.Properties.envOrElse("vallu.server.address", "http://localhost:9002")
  val cacheDirectory: String = scala.util.Properties.envOrElse("cache.directory", "/tmp/digiroad.cache")
  val feedbackAssetsEndPoint: String = scala.util.Properties.envOrElse("feedbackAssetsEndPoint", "http://localhost:9001/index.html")
  val tierekisteriViiteRestApiEndPoint: String = scala.util.Properties.envOrElse("tierekisteriViiteRestApiEndPoint", "http://localhost:8080/api/tierekisteri/")
  val tierekisteriEnabled: Boolean = scala.util.Properties.envOrElse("tierekisteri.enabled", "true").toBoolean
  val httpProxySet: Boolean = scala.util.Properties.envOrElse("http.proxySet", "false").toBoolean
  val httpProxyHost: String = scala.util.Properties.envOrElse("http.proxyHost", null)
  val httpNonProxyHosts: String = scala.util.Properties.envOrElse("http.nonProxyHosts", "")
  val importOnlyCurrent: Boolean = scala.util.Properties.envOrElse("importOnlyCurrent", "false").toBoolean
  val authenticationTestMode: Boolean = scala.util.Properties.envOrElse("authenticationTestMode", "false").toBoolean
  val authenticationTestUser: String = scala.util.Properties.envOrElse("authenticationTestUser", null)
  val bonecpJdbcUrl: String = scala.util.Properties.envOrElse("bonecp.jdbcUrl", null)
  val bonecpUsername: String = scala.util.Properties.envOrElse("bonecp.username", null)
  val bonecpPassword: String = scala.util.Properties.envOrElse("bonecp.password", null)
  val authenticationBasicUsername: String = scala.util.Properties.envOrElse("authentication.basic.username", null)
  val authenticationBasicPassword: String = scala.util.Properties.envOrElse("authentication.basic.password", null)
  val authenticationServiceRoadBasicUsername: String = scala.util.Properties.envOrElse("authentication.serviceRoad.basic.username", null)
  val authenticationServiceRoadBasicPassword: String = scala.util.Properties.envOrElse("authentication.serviceRoad.basic.password", null)
  val authenticationMunicipalityBasicUsername: String = scala.util.Properties.envOrElse("authentication.municipality.basic.username", null)
  val authenticationMunicipalityBasicPassword: String = scala.util.Properties.envOrElse("authentication.municipality.basic.password", null)
  val revision: String = scala.util.Properties.envOrElse("revision", null)
  val latestDeploy: String = scala.util.Properties.envOrElse("latestDeploy", null)
  val tierekisteriUsername: String = scala.util.Properties.envOrElse("tierekisteriUsername", null)
  val tierekisteriPassword: String = scala.util.Properties.envOrElse("tierekisteriPassword", null)
  val viiteUsername: String = scala.util.Properties.envOrElse("viiteUsername", null)
  val viitePassword: String = scala.util.Properties.envOrElse("viitePassword", null)
  val tierekisteriOldUsername: String = scala.util.Properties.envOrElse("tierekisteriOldUsername", null)
  val tierekisteriOldPassword: String = scala.util.Properties.envOrElse("tierekisteriOldPassword", null)
  val emailTo: String = scala.util.Properties.envOrElse("emailTo", null)
  val emailHost = scala.util.Properties.envOrElse("emailHost", null)
  val emailPort = scala.util.Properties.envOrElse("emailPort", null)
  val env: String = scala.util.Properties.envOrElse("env", "Unknown")

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

  def getAuthenticationBasicUsername(baseAuth: String = ""): String = {
    scala.util.Properties.envOrElse("authentication." + baseAuth + (if (baseAuth.isEmpty) "" else ".") + "basic.username", null)
  }

  def getAuthenticationBasicPassword(baseAuth: String = ""): String = {
    scala.util.Properties.envOrElse("authentication." + baseAuth + (if (baseAuth.isEmpty) "" else ".") + "basic.password", null)
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
  override val vvhProdRestApiEndPoint: String = envProps.getProperty("VVHProdRestApiEndPoint")
  override val vvhRoadlinkFrozen: Boolean = envProps.getProperty("vvhRoadlink.frozen", "false").toBoolean
  override val viiteRestApiEndPoint: String = envProps.getProperty("viiteRestApiEndPoint", "http://localhost:9080/api/viite/")
  override val tierekisteriRestApiEndPoint: String = envProps.getProperty("tierekisteriRestApiEndPoint", "http://localhost:8080/api/tierekisteri/")
  override val vkmUrl: String = envProps.getProperty("vkmUrl")
  override val valluServerSengindEnabled: Boolean = envProps.getProperty("vallu.server.sending_enabled", "true").toBoolean
  override val valluServerAddress: String = envProps.getProperty("vallu.server.address", "http://localhost:9002")
  override val cacheDirectory: String = envProps.getProperty("cache.directory", "/tmp/digiroad.cache")
  override val feedbackAssetsEndPoint: String = envProps.getProperty("feedbackAssetsEndPoint", "http://localhost:9001/index.html")
  override val tierekisteriViiteRestApiEndPoint: String = envProps.getProperty("tierekisteriViiteRestApiEndPoint", "http://localhost:8080/api/tierekisteri/")
  override val tierekisteriEnabled: Boolean = envProps.getProperty("tierekisteri.enabled", "true").toBoolean
  override val httpProxySet: Boolean = envProps.getProperty("http.proxySet", "false").toBoolean
  override val httpProxyHost: String = envProps.getProperty("http.proxyHost")
  override val httpNonProxyHosts: String = envProps.getProperty("http.nonProxyHosts", "")
  override val importOnlyCurrent: Boolean = envProps.getProperty("importOnlyCurrent", "false").toBoolean
  override val authenticationTestMode: Boolean = envProps.getProperty("authenticationTestMode", "false").toBoolean
  override val authenticationTestUser: String = envProps.getProperty("authenticationTestUser")
  override val bonecpJdbcUrl: String = envProps.getProperty("bonecp.jdbcUrl")
  override val bonecpUsername: String = envProps.getProperty("bonecp.username")
  override val bonecpPassword: String = envProps.getProperty("bonecp.password")
  override val authenticationBasicUsername: String = envProps.getProperty("authentication.basic.username")
  override val authenticationBasicPassword: String = envProps.getProperty("authentication.basic.password")
  override val authenticationServiceRoadBasicUsername: String = envProps.getProperty("authentication.serviceRoad.basic.username")
  override val authenticationServiceRoadBasicPassword: String = envProps.getProperty("authentication.serviceRoad.basic.password")
  override val authenticationMunicipalityBasicUsername: String = envProps.getProperty("authentication.municipality.basic.username")
  override val authenticationMunicipalityBasicPassword: String = envProps.getProperty("authentication.municipality.basic.password")
  override val revision: String = envProps.getProperty("revision")
  override val latestDeploy: String = envProps.getProperty("latestDeploy")
  override val tierekisteriUsername: String = envProps.getProperty("tierekisteri.username")
  override val tierekisteriPassword: String = envProps.getProperty("tierekisteri.password")
  override val viiteUsername: String = envProps.getProperty("viite.username")
  override val viitePassword: String = envProps.getProperty("viite.password")
  override val tierekisteriOldUsername: String = envProps.getProperty("tierekisteri.old.username")
  override val tierekisteriOldPassword: String = envProps.getProperty("tierekisteri.old.password")
  override val emailTo: String = envProps.getProperty("email.to")
  override val emailHost: String = envProps.getProperty("email.host")
  override val emailPort: String = envProps.getProperty("email.port")
  override val env: String = envProps.getProperty("env")

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

  override def getAuthenticationBasicUsername(baseAuth: String = ""): String = {
    envProps.getProperty("authentication." + baseAuth + (if (baseAuth.isEmpty) "" else ".") + "basic.username")
  }

  override def getAuthenticationBasicPassword(baseAuth: String = ""): String = {
    envProps.getProperty("authentication." + baseAuth + (if (baseAuth.isEmpty) "" else ".") + "basic.password")
  }
}

/**
  * Digiroad2Properties will get the properties from the environment variables by default.
  * If env.properties is found in classpath, then the properties are read from that property file.
  */
object Digiroad2Properties {
  private val logger = LoggerFactory.getLogger(getClass)
  lazy val properties: Digiroad2Properties = {
    if (getClass.getResource("/env.properties").getFile.isEmpty) {
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
  lazy val vvhProdRestApiEndPoint: String = properties.vvhProdRestApiEndPoint
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
  lazy val httpNonProxyHosts: String = properties.httpNonProxyHosts
  lazy val importOnlyCurrent: Boolean = properties.importOnlyCurrent
  lazy val authenticationTestMode: Boolean = properties.authenticationTestMode
  lazy val authenticationTestUser: String = properties.authenticationTestUser
  lazy val bonecpJdbcUrl: String = properties.bonecpJdbcUrl
  lazy val bonecpUsername: String = properties.bonecpUsername
  lazy val bonecpPassword: String = properties.bonecpPassword
  lazy val authenticationBasicUsername: String = properties.authenticationBasicUsername
  lazy val authenticationBasicPassword: String = properties.authenticationBasicPassword
  lazy val authenticationServiceRoadBasicUsername: String = properties.authenticationServiceRoadBasicUsername
  lazy val authenticationServiceRoadBasicPassword: String = properties.authenticationServiceRoadBasicPassword
  lazy val authenticationMunicipalityBasicUsername: String = properties.authenticationMunicipalityBasicUsername
  lazy val authenticationMunicipalityBasicPassword: String = properties.authenticationMunicipalityBasicPassword
  lazy val revision: String = properties.revision
  lazy val latestDeploy: String = properties.latestDeploy
  lazy val tierekisteriUsername: String = properties.tierekisteriUsername
  lazy val tierekisteriPassword: String = properties.tierekisteriPassword
  lazy val viiteUsername: String = properties.viiteUsername
  lazy val viitePassword: String = properties.viitePassword
  lazy val tierekisteriOldUsername: String = properties.tierekisteriOldUsername
  lazy val tierekisteriOldPassword: String = properties.tierekisteriOldPassword
  lazy val emailTo: String = properties.emailTo
  lazy val emailHost: String = properties.emailHost
  lazy val emailPort: String = properties.emailPort
  lazy val env: String = properties.env

  lazy val bonecpProperties: Properties = properties.bonecpProperties

  def getAuthenticationBasicUsername(baseAuth: String = ""): String = properties.getAuthenticationBasicUsername(baseAuth)
  def getAuthenticationBasicPassword(baseAuth: String = ""): String = properties.getAuthenticationBasicPassword(baseAuth)
}