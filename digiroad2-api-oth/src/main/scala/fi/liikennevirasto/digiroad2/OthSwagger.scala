package fi.liikennevirasto.digiroad2

import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ApiInfo, NativeSwaggerBase, Swagger}

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object OthApiInfo extends ApiInfo(title = "OTH API DOCUMENTATION",
  description = "Documentations for all OTH APIs",
  termsOfServiceUrl = "",
  contact = "",
  license = "",
  licenseUrl =""
)

class OthSwagger extends Swagger(Swagger.SpecVersion, "1.0.0", OthApiInfo)