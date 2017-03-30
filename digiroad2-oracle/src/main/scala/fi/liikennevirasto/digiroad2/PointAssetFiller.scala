package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.util.MunicipalityCodeImporter
import org.joda.time.DateTime

object PointAssetFiller {
  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: Long, mValue: Double, floating: Boolean, vvhTimeStamp: Long) extends PersistedPointAsset


  case class AssetAdjustment(assetId: Long, lon: Double, lat: Double, linkId: Long, mValue: Double, floating: Boolean)
  private val MaxDistanceDiffAllowed = 3.0

   def correctRoadLinkAndGeometry(asset: PersistedPointAsset , roadLinks: Seq[RoadLink], changeInfo: ChangeInfo): Option[AssetAdjustment]={
    val newRoadLink = roadLinks.filter(_.linkId == changeInfo.newId.getOrElse(0L)).head
    val points = GeometryUtils.geometryEndpoints(newRoadLink.geometry)
    val assetPoint = Point(asset.lon, asset.lat)
    val pointToIni = Seq(assetPoint, points._1)
    val pointToEnd = Seq(assetPoint, points._2)
    val distBetweenPointEnd = GeometryUtils.geometryLength(pointToEnd)

    val newAssetPointOption = GeometryUtils.geometryLength(pointToIni) match {

      case iniDist if iniDist > MaxDistanceDiffAllowed && distBetweenPointEnd <= MaxDistanceDiffAllowed => Some(points._2)
      case iniDist if iniDist <= MaxDistanceDiffAllowed && iniDist <= distBetweenPointEnd => Some(points._1)
      case iniDist if iniDist <= MaxDistanceDiffAllowed && iniDist > distBetweenPointEnd => Some(points._2)
      case iniDist if iniDist <= MaxDistanceDiffAllowed => Some(points._1)
      case _ =>  None
    }

    newAssetPointOption match {
      case Some(newAssetPoint) =>
        val mValue = GeometryUtils.calculateLinearReferenceFromPoint(newAssetPoint, newRoadLink.geometry)
        correctGeometry(asset, newRoadLink.linkId, newRoadLink.geometry, mValue)
      case _ =>
        None
    }

  }
 //TODO: use asset vvhTimeStamp
  def correctOnlyGeometry(asset: PersistedPointAsset, roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]): Option[AssetAdjustment] = {
    val pointAssetLastChanges = changeInfos.filter( changeInfo => changeInfo.oldId.getOrElse(0L) == asset.linkId && changeInfo.vvhTimeStamp >= changeInfos.map(_.vvhTimeStamp).max)

    pointAssetLastChanges.flatMap {
      changeInfo =>
        ChangeType.apply(changeInfo.changeType) match {
          case ChangeType.CombinedModifiedPart | ChangeType.CombinedRemovedPart if changeInfo.newId != changeInfo.oldId => //Geometry Combined
            correctValuesAndGeometry(asset, roadLinks, changeInfo)

          case ChangeType.LenghtenedCommonPart | ChangeType.LengthenedNewPart if asset.mValue < changeInfo.newStartMeasure.getOrElse(0.0) => //Geometry Lengthened
            correctValuesAndGeometry(asset, roadLinks, changeInfo)

          case ChangeType.DividedModifiedPart | ChangeType.DividedNewPart if (asset.mValue >= changeInfo.oldStartMeasure.getOrElse(0.0)) && (asset.mValue <= changeInfo.oldEndMeasure.getOrElse(0.0)) => //Geometry Divided
            correctValuesAndGeometry(asset, roadLinks, changeInfo)

          case ChangeType.ShortenedCommonPart | ChangeType.ShortenedRemovedPart => //Geometry Shortened
            correctRoadLinkAndGeometry(asset, roadLinks, changeInfo)

          case _ => None
        }
    }.headOption
  }


  private def correctValuesAndGeometry(asset: PersistedPointAsset, roadLinks: Seq[RoadLink], filteredChangeInfos: ChangeInfo) = {
    val newMValue = (asset.mValue - filteredChangeInfos.oldStartMeasure.getOrElse(0.0)) + filteredChangeInfos.newStartMeasure.getOrElse(0.0)
    val newRoadLink = roadLinks.find(_.linkId == filteredChangeInfos.newId.getOrElse(0L)).get
    correctGeometry(asset, newRoadLink.linkId, newRoadLink.geometry, newMValue)
  }

  def correctedPersistedAsset(asset: PersistedPointAsset, roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]): Option[AssetAdjustment] = {
    correctOnlyGeometry(asset, roadLinks, changeInfos) match {
      case Some(asset_) => Some(asset_)
      case None => None
    }
  }

  private def correctGeometry(asset: PersistedPointAsset, newRoadLinkId: Long, newRoadLinkGeometry: Seq[Point], newMValue: Double): Option[AssetAdjustment] = {
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(newRoadLinkGeometry, newMValue)
    newAssetPoint match {
      case Some(point) => Some(AssetAdjustment(asset.id, point.x, point.y, newRoadLinkId, newMValue, floating = false))
      case _ => None
    }
  }
}