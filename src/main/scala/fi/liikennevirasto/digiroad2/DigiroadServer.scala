package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, OAGAuthPropertyReader}

import java.lang.management.ManagementFactory
import java.util.Properties
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.client.{HttpClient, HttpProxy}
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.jmx.MBeanContainer
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.LoggerFactory

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
    context.addServlet(classOf[OAGProxyServlet], "/maasto/*")
    context.addServlet(classOf[VKMProxyServlet], "/viitekehysmuunnin/*")
    context.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    context.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    context.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    context.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    context
  }

  def startServer() {
    val server = new Server(8080)
    val mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer)
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

class OAGProxyServlet extends ProxyServlet {

  def regex = "(/(digiroad(-dev)?))?/(maasto)/(wmts)".r
  private val oagAuth = new OAGAuthPropertyReader
  private val logger = LoggerFactory.getLogger(getClass)

  override def newHttpClient(): HttpClient = {
    new HttpClient(new SslContextFactory)
  }

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val url = Digiroad2Properties.rasterServiceUrl +  regex.replaceFirstIn(req.getRequestURI, "/wmts/maasto")
    logger.info(url)
    java.net.URI.create(url)
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    proxyRequest.header("Authorization","Basic " + oagAuth.getAuthInBase64)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }
}

class VKMProxyServlet extends ProxyServlet {
  def regex = "/(digiroad(-dev)?)".r
  private val oagAuth = new OAGAuthPropertyReader
  private val logger = LoggerFactory.getLogger(getClass)

  override def newHttpClient(): HttpClient = {
    new HttpClient(new SslContextFactory)
  }

  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val vkmUrl: String = Digiroad2Properties.vkmUrl
    logger.info(vkmUrl + regex.replaceFirstIn(req.getRequestURI, ""))
    java.net.URI.create(vkmUrl + regex.replaceFirstIn(req.getRequestURI, ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    val parameters = clientRequest.getParameterMap
    parameters.foreach { case(key, value) =>
      proxyRequest.param(key, value.mkString(""))
    }

    proxyRequest.header("Authorization","Basic " + oagAuth.getAuthInBase64)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }
}
