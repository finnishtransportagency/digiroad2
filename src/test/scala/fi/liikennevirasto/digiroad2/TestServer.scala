package fi.liikennevirasto.digiroad2

object TestServer extends App with DigiroadServer {
  override val contextPath: String = "/"
  override val viiteContextPath: String = "/viite"

  startServer()
}
