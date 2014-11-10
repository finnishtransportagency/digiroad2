package fi.liikennevirasto.digiroad2

object TestServer extends App with DigiroadServer {
  override val contextPath: String = "/"

  startServer()
}
