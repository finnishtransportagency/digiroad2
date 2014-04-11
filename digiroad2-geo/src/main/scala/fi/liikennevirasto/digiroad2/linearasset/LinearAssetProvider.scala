package fi.liikennevirasto.digiroad2.linearasset

case class Point(x: Double, y: Double)
case class LinearAsset(id: Long, points: Seq[Point])

trait LinearAssetProvider {
  def get(id: Long): LinearAsset
  def getAll(): Seq[LinearAsset]
}