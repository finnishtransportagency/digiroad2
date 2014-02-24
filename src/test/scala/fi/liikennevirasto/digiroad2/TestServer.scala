package fi.liikennevirasto.digiroad2

object TestServer extends App with DigiroadServer {
  override def commenceMtkFileImport(): Unit = {}
  override val contextPath: String = "/"

  startServer()
}
