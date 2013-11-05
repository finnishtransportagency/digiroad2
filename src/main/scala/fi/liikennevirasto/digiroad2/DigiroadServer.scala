package fi.liikennevirasto.digiroad2

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object DigiroadServer extends App {
  val server = new Server(8080)
  val context = new WebAppContext()
  context.setDescriptor("src/main/webapp/WEB-INF/web.xml")
  context.setResourceBase("src/main/webapp")
  context.setContextPath("/")
  context.setParentLoaderPriority(true)
  context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
  server.setHandler(context)
  server.start()
  server.join()
}
