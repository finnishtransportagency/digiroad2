package fi.liikennevirasto.digiroad2

object ProductionServer extends App with DigiroadServer {
  override val contextPath: String = "/digiroad"

  startServer()
}
