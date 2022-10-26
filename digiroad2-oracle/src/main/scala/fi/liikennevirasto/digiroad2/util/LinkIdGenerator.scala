package fi.liikennevirasto.digiroad2.util

import java.util.UUID
import scala.util.Random

object LinkIdGenerator {
  /**
   * Generates random link id that matches the structure of mml link id (uuid:version)
   * Used by unit tests
   */
  def generateRandom(): String = {
    val randomUUID = UUID.randomUUID()
    val randomVersion = Random.nextInt(100)
    s"$randomUUID:$randomVersion"
  }
}


