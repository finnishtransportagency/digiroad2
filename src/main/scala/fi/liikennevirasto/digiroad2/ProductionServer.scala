package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.mtk.MtkFileSlurper

object ProductionServer extends App with DigiroadServer {
  override def commenceMtkFileImport() = MtkFileSlurper.startWatching()
  override val contextPath: String = "/digiroad"

  startServer()
}
