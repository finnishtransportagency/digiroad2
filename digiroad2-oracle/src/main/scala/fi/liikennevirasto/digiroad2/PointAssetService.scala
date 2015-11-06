package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

trait PointAssetService {
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PointAsset]] = {
    Nil
  }
}
case class PointAsset(mmlId: Int)
