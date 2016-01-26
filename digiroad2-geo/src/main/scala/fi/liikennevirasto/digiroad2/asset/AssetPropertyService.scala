package fi.liikennevirasto.digiroad2.asset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.LocalDate

class AssetNotFoundException(externalId: Long) extends RuntimeException
class LRMPositionDeletionFailed(val reason: String) extends RuntimeException

case class BoundingRectangle(leftBottom: Point, rightTop: Point)