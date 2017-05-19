package fi.liikennevirasto.digiroad2

/**
  * Created by pedrosag on 18-05-2017.
  */
import org.eclipse.jetty.webapp.WebAppContext


object ViiteTestServer extends App with DigiroadServer {
  override val contextPath: String = "/"
  override val viiteContextPath: String = "/viite"

    override def setupWebContext(): WebAppContext ={
      val context = super.setupWebContext()
      context.addServlet(classOf[TierekisteriTestApi], "/api/tierekisteri/*")
      context.addServlet(classOf[ViiteTierekisteriTestApi], "/trrest/*")
      context
    }

  startServer()

}
