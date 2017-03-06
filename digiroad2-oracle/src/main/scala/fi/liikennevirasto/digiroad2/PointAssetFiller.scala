package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.util.MunicipalityCodeImporter
import org.joda.time.DateTime


object PointAssetFiller {
  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: Long, mValue: Double, floating: Boolean) extends PersistedPointAsset


  case class AssetAdjustment(assetId: Long, lon: Double, lat: Double, linkId: Long, mValue: Double, floating: Boolean)
  private val MaxDistanceDiffAllowed = 3.0

  def correctRoadLinkAndGeometry(asset: PersistedPointAsset , roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]) : Option [AssetAdjustment] = {
    val pointAssetLastChange = changeInfos.filter(_.oldId.getOrElse(0L) == asset.linkId).head
    val newRoadLink = roadLinks.filter(_.linkId == pointAssetLastChange.newId.getOrElse(0L)).head
    val typed = ChangeType.apply(pointAssetLastChange.changeType)

    ChangeType.apply(pointAssetLastChange.changeType) match {
      case ChangeType.ShortenedCommonPart | ChangeType.ShortenedRemovedPart => {

        val points = GeometryUtils.geometryEndpoints(newRoadLink.geometry)
        val assetPoint = Point(asset.lon, asset.lat)
        val pointToIni = Seq(assetPoint, points._1)
        val pointToEnd = Seq(assetPoint, points._2)
        val distBetweenPointEnd = GeometryUtils.geometryLength(pointToEnd)

        val newAssetPoint = GeometryUtils.geometryLength(pointToIni) match {

          case iniDist if (iniDist > MaxDistanceDiffAllowed && distBetweenPointEnd <= MaxDistanceDiffAllowed) => points._2
          case iniDist if (iniDist <= MaxDistanceDiffAllowed && iniDist <= distBetweenPointEnd) => points._1
          case iniDist if (iniDist <= MaxDistanceDiffAllowed && iniDist > distBetweenPointEnd) => points._2
          case iniDist if (iniDist <= MaxDistanceDiffAllowed) => points._1
          case _ => return None
        }

        val mValue = GeometryUtils.calculateLinearReferenceFromPoint(newAssetPoint, newRoadLink.geometry)

        Some(AssetAdjustment(asset.id, newAssetPoint.x, newAssetPoint.y, newRoadLink.linkId, mValue, false))
      }
        case _ => None
      }
  }

  def correctOnlyGeometry(asset: PersistedPointAsset, roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]): Option[AssetAdjustment] = {
    val pointAssetLastChange = changeInfos.filter(changeInfo => changeInfo.oldId == asset.linkId).head
    val newRoadLink = roadLinks.filter(roadLink => roadLink.linkId == pointAssetLastChange.newId).head
    val typed = ChangeType.apply(pointAssetLastChange.changeType)

    typed match {
      case ChangeType.CombinedModifiedPart | ChangeType.CombinedRemovedPart => correctCombinedGeometry(asset.id, pointAssetLastChange, asset.mValue, newRoadLink) //Geometry Combined
      case ChangeType.LenghtenedCommonPart | ChangeType.LengthenedNewPart => correctLengthenedGeometry(asset.id, pointAssetLastChange, asset.mValue, newRoadLink)//Geometry Lengthened
      case ChangeType.DividedModifiedPart | ChangeType.DividedNewPart => correctDividedGeometry(asset.id, pointAssetLastChange, asset.mValue, newRoadLink) //Geometry Divided
      case ChangeType.ShortenedCommonPart | ChangeType.ShortenedRemovedPart => correctRoadLinkAndGeometry(asset, roadLinks, changeInfos) //Geometry Shortened
      case _ => None
    }
  }

  private def correctCombinedGeometry(assetId: Long, lastAssetChange: ChangeInfo, oldMValue: Double, newRoadLink: RoadLink): Option[AssetAdjustment] = {
    if (lastAssetChange.oldId != lastAssetChange.newId) {
      val newAssetPoint = Point((lastAssetChange.newStartMeasure.get + oldMValue), (lastAssetChange.newStartMeasure.get + oldMValue))
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(newAssetPoint, newRoadLink.geometry)
      Some(AssetAdjustment(assetId, newAssetPoint.x, newAssetPoint.y, newRoadLink.linkId, mValue, false))
    } else {
      None
    }
  }

  private def correctDividedGeometry(assetId: Long, lastAssetChange: ChangeInfo, oldMValue: Double, newRoadLink: RoadLink): Option[AssetAdjustment] = {
    if (lastAssetChange.oldId != lastAssetChange.newId) {
      val newAssetPoint = Point((oldMValue - lastAssetChange.newEndMeasure.get), (oldMValue - lastAssetChange.newEndMeasure.get))
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(newAssetPoint, newRoadLink.geometry)
      Some(AssetAdjustment(assetId, newAssetPoint.x, newAssetPoint.y, newRoadLink.linkId, mValue, false))
    } else {
      None
    }
  }

  //TODO Nedd to be review
  private def correctLengthenedGeometry(assetId: Long, lastAssetChange: ChangeInfo, oldMValue: Double, newRoadLink: RoadLink): Option[AssetAdjustment] = {
    if (lastAssetChange.oldId != lastAssetChange.newId) {
      val newAssetPoint = Point((oldMValue - lastAssetChange.newEndMeasure.get), (oldMValue - lastAssetChange.newEndMeasure.get))
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(newAssetPoint, newRoadLink.geometry)
      Some(AssetAdjustment(assetId, newAssetPoint.x, newAssetPoint.y, newRoadLink.linkId, mValue, false))
    } else {
      None
    }
  }

}