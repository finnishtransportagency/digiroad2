package fi.liikennevirasto.digiroad2

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import fi.liikennevirasto.digiroad2.mtk.MtkFileSlurper

object DigiroadServer extends App {
  val server = new Server(8080)
  val context = new WebAppContext()
  context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
  context.setResourceBase("src/main/webapp")
  context.setContextPath("/digiroad")
  context.setParentLoaderPriority(true)
  context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
  server.setHandler(context)
  MtkFileSlurper.startWatching()
  server.start()
  server.join()
}
