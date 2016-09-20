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


trait DigiroadServer {
  val contextPath : String
  val viiteContextPath: String

  def startServer() {
    val server = new Server(8080)
    val context = new WebAppContext()
    context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
    context.setResourceBase("src/main/webapp")
    context.setContextPath(contextPath)
    context.setParentLoaderPriority(true)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.addServlet(classOf[NLSProxyServlet], "/maasto/*")
    context.addServlet(classOf[VKMProxyServlet], "/vkm/*")
    context.addServlet(classOf[VKMUIProxyServlet], "/viitekehysmuunnin/*")
    context.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    context.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    context.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    context.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    val handler = new ContextHandlerCollection()
    val handlers = Array(context, createViiteContext())
    handler.setHandlers(handlers.map(_.asInstanceOf[Handler]))
    server.setHandler(handler)
    server.start()
    server.join()
  }

  def createViiteContext() = {
    val appContext = new WebAppContext()
    appContext.setDescriptor("src/main/webapp/WEB-INF/viite_web.xml")
    appContext.setResourceBase("src/main/webapp/viite")
    appContext.setContextPath(viiteContextPath)
    appContext.setParentLoaderPriority(true)
    appContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    appContext.addServlet(classOf[NLSProxyServlet], "/maasto/*")
    appContext.addServlet(classOf[ArcGisProxyServlet], "/arcgis/*")
    appContext.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    appContext.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    appContext.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    appContext.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    appContext
  }
}

class NLSProxyServlet extends ProxyServlet {
  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val uri = req.getRequestURI
    java.net.URI.create("http://karttamoottori.maanmittauslaitos.fi"
      + uri.replaceFirst("/digiroad", "").replaceFirst("/viite", ""))
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

class ArcGisProxyServlet extends ProxyServlet {
  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val uri = req.getRequestURI
    java.net.URI.create("http://aineistot.esri.fi"
      + uri.replaceFirst("/digiroad", "").replaceFirst("/viite", ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    proxyRequest.header("Referer", "http://aineistot.esri.fi/arcgis/rest/services/Taustakartat/Harmaasavy/MapServer")
    proxyRequest.header("Host", null)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }

  override def getHttpClient: HttpClient = {
    val client = super.getHttpClient
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    if (properties.getProperty("http.proxySet", "false").toBoolean) {
      val proxy = new HttpProxy("172.17.208.16", 8085)
      proxy.getExcludedAddresses.addAll(properties.getProperty("http.nonProxyHosts", "").split("|").toList)
      client.getProxyConfiguration.getProxies.add(proxy)
      client.setIdleTimeout(60000)
    }
    client
  }
}
class VKMProxyServlet extends ProxyServlet {
  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    val vkmUrl: String = properties.getProperty("digiroad2.VKMUrl")
    java.net.URI.create(vkmUrl + req.getRequestURI.replaceFirst("/digiroad", ""))
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
  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    java.net.URI.create("http://localhost:3000" + req.getRequestURI.replaceFirst("/digiroad/viitekehysmuunnin/", "/"))
  }
}
