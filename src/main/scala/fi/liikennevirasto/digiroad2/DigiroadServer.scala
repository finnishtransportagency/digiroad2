package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.jmx.MBeanContainer
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server._
import org.eclipse.jetty.servlet.{DefaultServlet, ServletHolder}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConversions._


trait DigiroadServer {
  val contextPath : String

  protected def setupWebContext(): WebAppContext ={
    val context = new WebAppContext()
    context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
    context.setResourceBase("src/main/webapp")
    context.setContextPath(contextPath)
    context.setParentLoaderPriority(true)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    // Disable browser cache
    val defaultServlet = new DefaultServlet
    val holder = new ServletHolder(defaultServlet)
    holder.setInitParameter("cacheControl", "no-store, no-cache")
    context.addServlet(holder, "/index.html")
    
    context.addServlet(classOf[MMLProxyServlet], "/maasto/*")
    context.addServlet(classOf[VKMProxyServlet], "/viitekehysmuunnin/*")
    context.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    context.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    context.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    context.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    context
  }

  def startServer() {
    val server = new Server
    val mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer)
    val httpConfiguration = new HttpConfiguration()
    // 32 kb
    httpConfiguration.setRequestHeaderSize(32*1024)
    httpConfiguration.setResponseHeaderSize(32*1024)
    httpConfiguration.setHeaderCacheSize(32*1024)
    val connector = new ServerConnector(server,new HttpConnectionFactory(httpConfiguration))
    connector.setPort(8080)
    server.setConnectors(Array[Connector](connector))
    server.addEventListener(mbContainer)
    server.addBean(mbContainer)
    val handler = new ContextHandlerCollection()
    val handlers = Array(setupWebContext())
    handler.setHandlers(handlers.map(_.asInstanceOf[Handler]))
    server.setHandler(handler)
    server.start()
    server.join()
  }
}

class MMLProxyServlet extends ProxyServlet {

  def regex = "(/(digiroad(-dev)?))?/(maasto)/(wmts)".r
  private val logger = LoggerFactory.getLogger(getClass)

  override def newHttpClient(): HttpClient = {
    new HttpClient(new SslContextFactory)
  }

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val url = Digiroad2Properties.rasterServiceUrl +  regex.replaceFirstIn(req.getRequestURI, "/wmts/maasto")
    logger.debug(url)
    java.net.URI.create(url)
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    logger.debug("Header start")
    logger.debug(proxyRequest.getHeaders.toString)
    logger.debug("Header end")

    proxyRequest.getHeaders.remove("X-Iam-Data")
    proxyRequest.getHeaders.remove("X-Iam-Accesstoken")
    proxyRequest.getHeaders.remove("X-Amzn-Trace-Id")
    proxyRequest.getHeaders.remove("X-Iam-Identity")
    
    proxyRequest.header("X-API-Key", Digiroad2Properties.rasterServiceApiKey)
    logger.debug("Header clean start")
    logger.debug(proxyRequest.getHeaders.toString)
    logger.debug("Header clean end")
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }
}

class VKMProxyServlet extends ProxyServlet {
  def regex = "/(digiroad(-dev)?)".r
  private val logger = LoggerFactory.getLogger(getClass)

  override def newHttpClient(): HttpClient = {
    new HttpClient(new SslContextFactory)
  }

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val vkmUrl: String = Digiroad2Properties.vkmUrl
    logger.debug(vkmUrl + regex.replaceFirstIn(req.getRequestURI, ""))
    java.net.URI.create(vkmUrl + regex.replaceFirstIn(req.getRequestURI, ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    val parameters = clientRequest.getParameterMap
    parameters.foreach { case(key, value) =>
      proxyRequest.param(key, value.mkString(""))
    }
    
    proxyRequest.getHeaders.remove("X-Iam-Data")
    proxyRequest.getHeaders.remove("X-Iam-Accesstoken")
    proxyRequest.getHeaders.remove("X-Amzn-Trace-Id")
    proxyRequest.getHeaders.remove("X-Iam-Identity")
    
    proxyRequest.header("X-API-Key", Digiroad2Properties.vkmApiKey)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }
}
