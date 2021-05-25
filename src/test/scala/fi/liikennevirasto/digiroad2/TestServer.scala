package fi.liikennevirasto.digiroad2

import org.eclipse.jetty.webapp.WebAppContext

object TestServer extends App with DigiroadServer {
  override val contextPath: String = "/"

  override def setupWebContext(): WebAppContext ={
    val context = super.setupWebContext()
    context.addServlet(classOf[TierekisteriTestApi], "/api/tierekisteri/*")
    context
  }

  startServer()
}

