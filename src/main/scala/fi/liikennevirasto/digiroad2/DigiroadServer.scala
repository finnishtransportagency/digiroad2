package fi.liikennevirasto.digiroad2

import java.util.Properties
import java.util.logging.Logger
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.eclipse.jetty.client.{HttpClient, HttpProxy, ProxyConfiguration}

import scala.collection.JavaConversions._
import org.eclipse.jetty.http.{HttpURI, MimeTypes}
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.server.handler.{ContextHandler, ContextHandlerCollection}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.slf4j.LoggerFactory


trait DigiroadServer {
  val contextPath : String
  val viiteContextPath: String

  protected def setupWebContext(): WebAppContext ={
    val context = new WebAppContext()
    context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
    context.setResourceBase("src/main/webapp")
    context.setContextPath(contextPath)
    context.setParentLoaderPriority(true)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.addServlet(classOf[NLSProxyServlet], "/maasto/*")
    context.addServlet(classOf[VioniceProxyServlet], "/vionice/*")
    context.addServlet(classOf[VKMProxyServlet], "/vkm/*")
    context.addServlet(classOf[VKMUIProxyServlet], "/viitekehysmuunnin/*")
    context.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    context.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    context.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    context.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    context
  }

  def startServer() {
    val server = new Server(8080)
    val context = setupWebContext()
    val handler = new ContextHandlerCollection()
    val handlers = Array(context, createViiteContext())
    handler.setHandlers(handlers.map(_.asInstanceOf[Handler]))
    server.setHandler(handler)
    server.start()
    server.join()
  }

  def createViiteContext() = {
    val appContext = new WebAppContext()
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    appContext.setDescriptor("src/main/webapp/WEB-INF/viite_web.xml")
    appContext.setResourceBase("src/main/webapp/viite")
    appContext.setContextPath(viiteContextPath)
    appContext.setParentLoaderPriority(true)
    appContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    appContext.addServlet(classOf[NLSProxyServlet], "/maasto/*")
    appContext.addServlet(classOf[ArcGisProxyServlet], "/arcgis/*")
    appContext.addServlet(classOf[VKMProxyServlet], "/vkm/*")
    appContext.addServlet(classOf[VKMUIProxyServlet], "/viitekehysmuunnin/*")
    appContext.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    appContext.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    appContext.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    appContext.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    appContext
  }
}

class NLSProxyServlet extends ProxyServlet {

  def regex = "/(digiroad|viite)".r

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val uri = req.getRequestURI
    java.net.URI.create("http://karttamoottori.maanmittauslaitos.fi"
      + regex.replaceFirstIn(uri, ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    proxyRequest.header("Referer", "http://www.paikkatietoikkuna.fi/web/fi/kartta")
    proxyRequest.header("Host", null)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }

  override def getHttpClient: HttpClient = {
    val client = super.getHttpClient
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    if (properties.getProperty("http.proxySet", "false").toBoolean) {
      val proxy = new HttpProxy(properties.getProperty("http.proxyHost", "localhost"), properties.getProperty("http.proxyPort", "80").toInt)
      proxy.getExcludedAddresses.addAll(properties.getProperty("http.nonProxyHosts", "").split("|").toList)
      client.getProxyConfiguration.getProxies.add(proxy)
      client.setIdleTimeout(60000)
    }
    client
  }
}

class VioniceProxyServlet extends ProxyServlet {
  val raLogger = LoggerFactory.getLogger(getClass)
  def regex = "/(digiroad)/(vionice)".r

  def appendQueryString(uri: java.net.URI, appendQuery: String): java.net.URI = {
    val newQuery = if (uri.getQuery == null) appendQuery else s"""${uri.getQuery}&${appendQuery}"""
    new java.net.URI(uri.getScheme, uri.getAuthority,
      uri.getPath, newQuery, uri.getFragment)
  }

  override def newHttpClient() : HttpClient = {
    val factory = new SslContextFactory()
    factory.setTrustAll(true)
    new HttpClient(factory)
  }

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    raLogger.info("Vionice request enter")
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/keys.properties"))
    val apiKey = properties.getProperty("vioniceApiKey", "")
    raLogger.info("Vionice key property " + apiKey)
    val queryString = if(req.getQueryString == null) "" else "?" + req.getQueryString
    val uri = java.net.URI.create("https://map.vionice.io" + req.getPathInfo + queryString)
    raLogger.info("Vionice request " + appendQueryString(uri, s"""apiKey=$apiKey"""))
    appendQueryString(uri, s"""apiKey=$apiKey""")
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    proxyRequest.header("Referer", null)
    proxyRequest.header("Host", "map.vionice.io:443")
    proxyRequest.header("Cookie", null)
    proxyRequest.header("OAM_REMOTE_USER", null)
    proxyRequest.header("OAM_IDENTITY_DOMAIN", null)
    proxyRequest.header("OAM_LAST_REAUTHENTICATION_TIME", null)
    proxyRequest.header("OAM_GROUPS", null)
    proxyRequest.header("X-Forwarded-Host", null)
    proxyRequest.header("X-Forwarded-Server", null)
    proxyRequest.header("Via", null)
    proxyRequest.header("X-Forwarded-For", null)
    proxyRequest.header("X-Forwarded-Proto", null)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }

  override def getHttpClient: HttpClient = {
    val client = super.getHttpClient
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    if (properties.getProperty("http.proxySet", "false").toBoolean) {
      val proxy = new HttpProxy(properties.getProperty("http.proxyHost", "localhost"), properties.getProperty("http.proxyPort", "80").toInt)
      proxy.getExcludedAddresses.addAll(properties.getProperty("http.nonProxyHosts", "").split("|").toList)
      client.getProxyConfiguration.getProxies.add(proxy)
      client.setIdleTimeout(60000)
    }
    client
  }
}

class ArcGisProxyServlet extends ProxyServlet {
  val logger = LoggerFactory.getLogger(getClass)
  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val uri = req.getRequestURI
    java.net.URI.create("http://aineistot.esri.fi"
      + uri.replaceFirst("/viite", ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    proxyRequest.header("Referer", null)
    proxyRequest.header("Host", null)
    proxyRequest.header("Cookie", null)
    proxyRequest.header("OAM_REMOTE_USER", null)
    proxyRequest.header("OAM_IDENTITY_DOMAIN", null)
    proxyRequest.header("OAM_LAST_REAUTHENTICATION_TIME", null)
    proxyRequest.header("OAM_GROUPS", null)
    proxyRequest.header("X-Forwarded-Host", null)
    proxyRequest.header("X-Forwarded-Server", null)
    proxyRequest.header("Via", null)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }

  override def getHttpClient: HttpClient = {
    val client = super.getHttpClient
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    if (properties.getProperty("http.proxySet", "false").toBoolean) {
      val proxy = new HttpProxy("127.0.0.1", 3128)
      proxy.getExcludedAddresses.addAll(properties.getProperty("http.nonProxyHosts", "").split("|").toList)
      client.getProxyConfiguration.getProxies.add(proxy)
      client.setIdleTimeout(60000)
    }
    client
  }
}
class VKMProxyServlet extends ProxyServlet {
  def regex = "/(digiroad|viite)".r

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    val vkmUrl: String = properties.getProperty("digiroad2.VKMUrl")
    java.net.URI.create(vkmUrl + regex.replaceFirstIn(req.getRequestURI, ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    val parameters = clientRequest.getParameterMap
    parameters.foreach { case(key, value) =>
      proxyRequest.param(key, value.mkString(""))
    }
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }
}

class VKMUIProxyServlet extends ProxyServlet {
  def regex = "/(digiroad|viite)/viitekehysmuunnin/".r

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    java.net.URI.create("http://localhost:3000" + regex.replaceFirstIn(req.getRequestURI, ""))
  }
}
