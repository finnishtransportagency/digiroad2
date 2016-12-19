package fi.liikennevirasto.digiroad2.authentication

case class UserNotFoundException(username: String) extends RuntimeException()