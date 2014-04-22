package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

case class Point(x: Double, y: Double)
case class LinearAsset(id: Long, points: Seq[Point])

trait LinearAssetProvider {
  def get(id: Long): LinearAsset
  def getAll(bounds: Option[BoundingRectangle]): Seq[LinearAsset]
}