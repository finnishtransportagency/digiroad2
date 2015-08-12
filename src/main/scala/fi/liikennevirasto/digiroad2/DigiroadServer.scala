package fi.liikennevirasto.digiroad2

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.eclipse.jetty.http.{MimeTypes, HttpURI}
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.client.api.Request

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
  override def rewriteURI(req: HttpServletRequest): java.net.URI = {
    val uri = req.getRequestURI
    java.net.URI.create("http://karttamoottori.maanmittauslaitos.fi"
      + uri.replaceFirst("/digiroad", ""))
  }

  override def sendProxyRequest(clientRequest: HttpServletRequest, proxyResponse: HttpServletResponse, proxyRequest: Request): Unit = {
    proxyRequest.header("Referer", "http://www.paikkatietoikkuna.fi/web/fi/kartta")
    proxyRequest.header("Host", null)
    super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest)
  }
}
