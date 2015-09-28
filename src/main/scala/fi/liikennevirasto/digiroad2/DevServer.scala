package fi.liikennevirasto.digiroad2

object DevServer extends App with DigiroadServer {
  override val contextPath: String = "/"
  startServer()
}
