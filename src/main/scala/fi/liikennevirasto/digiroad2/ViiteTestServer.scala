package fi.liikennevirasto.digiroad2

import org.eclipse.jetty.webapp.WebAppContext

//TODO this can be deleted after merge with master
object ViiteTestServer extends App with DigiroadServer {
  override val contextPath: String = "/"
  override val viiteContextPath: String = "/viite"

    override def setupWebContext(): WebAppContext ={
      val context = super.setupWebContext()
      context.addServlet(classOf[TierekisteriTestApi], "/api/tierekisteri/*")
      context
    }

  startServer()

}
