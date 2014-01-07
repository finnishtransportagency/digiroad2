package fi.liikennevirasto.digiroad2.user

case class User(id: Long, username: String, email: String, configuration: Map[String, String] = Map())
