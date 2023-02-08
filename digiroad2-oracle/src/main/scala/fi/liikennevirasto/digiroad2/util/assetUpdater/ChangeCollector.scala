package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.slf4j.LoggerFactory

sealed case class MValue(linkId: String, startMValue: Double, endMValue: Double, length: Double)

sealed case class Asset(assetId: Long, value: Any, municipalityCode: Option[Int], sideCode: Int, geometry: Option[Seq[Point]],
                        linkId: String, mValue: MValue, isPointAsset: Boolean = false) {

  def directLink: String = Digiroad2Properties.feedbackAssetsEndPoint
  val logger = LoggerFactory.getLogger(getClass)
  def geometryToString: String = {
    if (geometry.nonEmpty) {
      if (!isPointAsset) {
        GeometryUtils.toWktLineString(GeometryUtils.toDefaultPrecision(geometry.get)).string
      } else {
        val point = geometry.get.last
        GeometryUtils.toWktPoint(point.x, point.y).string
      }

    } else {
      logger.warn("Asset does not have geometry")
      ""
    }
  }

  def getUrl: String = {
    s"""$directLink#linkProperty/$linkId"""
  }

}

sealed trait ChangeType {
  def value: Int
}

object ChangeType {
  
  case object Creation extends ChangeType {
    def value: Int = 1
  }

  case object Deletion extends ChangeType {
    def value: Int = 2
  }

  case object Divided extends ChangeType {
    def value: Int = 3
  }

  case object Replaced extends ChangeType {
    def value: Int = 4
  }
  case object PropertyChange extends ChangeType {
    def value: Int = 5
  }
  
  /**
    * For point asset
    * */
  case object Move extends ChangeType {
    def value: Int = 7
  }

  /**
    * For point asset
    * */
  case object Floating extends ChangeType {
    def value: Int = 8
  }
}

/**
  *
  * @param linkId     link where changes is happening TODO remove if not needed
  * @param assetId    asset which is under samuutus TODO remove if not needed
  * @param changeType characteristic of change
  * @param before     situation before samuutus
  * @param after      after samuutus
  * */
case class ChangedAsset(linkId: String, assetId: Long, changeType: ChangeType, before: Asset, after: Seq[Asset])

/**
  *
  * @param assetType
  * @param changes
  */
case class ChangeReport(assetType: Int, changes: Seq[ChangedAsset])

