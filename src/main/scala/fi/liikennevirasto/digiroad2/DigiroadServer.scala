package fi.liikennevirasto.digiroad2

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import fi.liikennevirasto.digiroad2.mtk.MtkFileSlurper
import org.eclipse.jetty.http.HttpURI
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.servlets.ProxyServlet
import org.eclipse.jetty.client.HttpExchange

object DigiroadServer extends App {
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


  val server = new Server(8080)
  val context = new WebAppContext()
  context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
  context.setResourceBase("src/main/webapp")
  context.setContextPath("/digiroad")
  context.setParentLoaderPriority(true)
  context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
  context.addServlet(classOf[NLSProxyServlet], "/maasto/*")
  server.setHandler(context)

  MtkFileSlurper.startWatching()
  server.start()
  server.join()
}
