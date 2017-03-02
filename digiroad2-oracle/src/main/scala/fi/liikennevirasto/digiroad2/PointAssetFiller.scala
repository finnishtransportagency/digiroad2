package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.util.MunicipalityCodeImporter
import org.joda.time.DateTime


object PointAssetFiller {
  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: Long, mValue: Double, floating: Boolean) extends PersistedPointAsset


  case class AssetAdjustment(assetId: Long, lon: Double, lat: Double, linkId: Long, mValue: Double, floating: Boolean)
  private val MaxDistanceDiffAllowed = 3.0

  def correctRoadLinkAndGeometry(asset: PersistedPointAsset , newRoadLink: VVHRoadlink) : Option [AssetAdjustment] = {

    val points = GeometryUtils.geometryEndpoints(newRoadLink.geometry)
    val assetPoint = Point(asset.lon, asset.lat)
    val pointToIni = Seq(assetPoint, points._1)
    val pointToEnd = Seq(assetPoint, points._2)
    val distBetweenPointEnd = GeometryUtils.geometryLength(pointToEnd)

    val newAssetPoint = GeometryUtils.geometryLength(pointToIni) match {

      case iniDist if (iniDist > MaxDistanceDiffAllowed && distBetweenPointEnd <= MaxDistanceDiffAllowed) => points._2
      case iniDist if (iniDist <= MaxDistanceDiffAllowed && iniDist <= distBetweenPointEnd) => points._1
      case iniDist if (iniDist <= MaxDistanceDiffAllowed  &&  iniDist > distBetweenPointEnd) => points._2
      case iniDist if (iniDist <= MaxDistanceDiffAllowed) => points._1
      case _ => return None
    }

    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(newAssetPoint, newRoadLink.geometry)

    Some(AssetAdjustment(asset.id, newAssetPoint.x, newAssetPoint.y, newRoadLink.linkId, mValue, false))
  }
}