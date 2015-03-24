package fi.liikennevirasto.digiroad2

import org.eclipse.jetty.servlets.ProxyServlet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.http.{MimeTypes, HttpURI}
import org.eclipse.jetty.client.HttpExchange
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

trait DigiroadServer {
  val contextPath : String

  def startServer() {
    val server = new Server(8080)
    val context = new WebAppContext()
    context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
    context.setResourceBase("src/main/webapp")
    context.setContextPath(contextPath)
    context.setParentLoaderPriority(true)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.addServlet(classOf[NLSProxyServlet], "/maasto/*")
    context.getMimeTypes.addMimeMapping("ttf", "application/x-font-ttf")
    context.getMimeTypes.addMimeMapping("woff", "application/x-font-woff")
    context.getMimeTypes.addMimeMapping("eot", "application/vnd.ms-fontobject")
    context.getMimeTypes.addMimeMapping("js", "application/javascript; charset=UTF-8")
    server.setHandler(context)

    server.start()
    server.join()
  }
}

class NLSProxyServlet extends ProxyServlet {
  override protected def proxyHttpURI(req: HttpServletRequest, uri: String): HttpURI = {
    new HttpURI("http://karttamoottori.maanmittauslaitos.fi"
      + uri.replaceFirst("/digiroad", ""))
  }

  override def customizeExchange(exchange: HttpExchange, req: HttpServletRequest): Unit = {
    exchange.setRequestHeader("Referer", "http://www.paikkatietoikkuna.fi/web/fi/kartta")
    exchange.setRequestHeader("Host", null)
    super.customizeExchange(exchange, req)
  }
}
