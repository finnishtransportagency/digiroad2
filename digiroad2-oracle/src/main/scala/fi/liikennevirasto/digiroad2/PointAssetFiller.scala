package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.util.MunicipalityCodeImporter
import org.joda.time.DateTime

object PointAssetFiller {
  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: Long, mValue: Double, floating: Boolean) extends PersistedPointAsset


  case class AssetAdjustment(assetId: Long, lon: Double, lat: Double, linkId: Long, mValue: Double, floating: Boolean)
  private val MaxDistanceDiffAllowed = 3.0

  def correctRoadLinkAndGeometry(asset: PersistedPointAsset , roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]) : Option [AssetAdjustment] = {
    val pointAssetLastChange = changeInfos.filter(_.oldId.getOrElse(0L) == asset.linkId).filter(ci => ci.oldId == ci.newId).groupBy(_.oldId.get)

    if (!pointAssetLastChange.isEmpty) {
      pointAssetLastChange.map {
        case (linkId, filteredChangeInfos) =>
          val newRoadLink = roadLinks.filter(_.linkId == filteredChangeInfos.head.newId.getOrElse(0L)).head


          ChangeType.apply(filteredChangeInfos.head.changeType) match {
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
              correctGeometry(asset, newRoadLink.linkId, newRoadLink.geometry, mValue)
            }
            case _ => None
          }
        case _ => None
      }.head
    } else {
      None
    }
  }

  def correctOnlyGeometry(asset: PersistedPointAsset, roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]): Option[AssetAdjustment] = {
    val pointAssetLastChange = changeInfos.filter(_.oldId.getOrElse(0L) == asset.linkId).groupBy(_.oldId.get)

    if (!pointAssetLastChange.isEmpty) {
      pointAssetLastChange.map {
        case (linkId, filteredChangeInfos) =>
          ChangeType.apply(filteredChangeInfos.head.changeType) match {

            case ChangeType.CombinedModifiedPart | ChangeType.CombinedRemovedPart
              if filteredChangeInfos.head.newId != filteredChangeInfos.head.oldId => //Geometry Combined
              val newMValue = (asset.mValue - filteredChangeInfos.head.oldStartMeasure.getOrElse(0.0)) + filteredChangeInfos.head.newStartMeasure.getOrElse(0.0)
              val newRoadLink = roadLinks.find(_.linkId == filteredChangeInfos.head.newId.getOrElse(0L)).get
              correctGeometry(asset, newRoadLink.linkId, newRoadLink.geometry, newMValue)

            case ChangeType.LenghtenedCommonPart | ChangeType.LengthenedNewPart => //Geometry Lengthened
              filteredChangeInfos.map {
                case (filteredChangeInfo) =>
                  if (asset.mValue < filteredChangeInfo.newStartMeasure.getOrElse(0.0)) {
                    val newMValue = (asset.mValue - filteredChangeInfo.oldStartMeasure.getOrElse(0.0)) + filteredChangeInfo.newStartMeasure.getOrElse(0.0)
                    val newRoadLink = roadLinks.find(_.linkId == filteredChangeInfo.newId.getOrElse(0L)).get
                    correctGeometry(asset, newRoadLink.linkId, newRoadLink.geometry, newMValue)
                  } else {
                    None
                  }
              }.head

            case ChangeType.DividedModifiedPart | ChangeType.DividedNewPart => //Geometry Divided
              val newRoadLinkChangeInfo = filteredChangeInfos.filter(fci => fci.oldId != fci.newId)
                .filter(fci2 => (asset.mValue >= fci2.oldStartMeasure.getOrElse(0.0)) && (asset.mValue <= fci2.oldEndMeasure.getOrElse(0.0)))

              if (!newRoadLinkChangeInfo.isEmpty) {
                val newMValue = (asset.mValue - newRoadLinkChangeInfo.head.oldStartMeasure.getOrElse(0.0)) + newRoadLinkChangeInfo.head.newStartMeasure.getOrElse(0.0)
                val newRoadLink = roadLinks.find(_.linkId == newRoadLinkChangeInfo.head.newId.getOrElse(0L)).get
                correctGeometry(asset, newRoadLink.linkId, newRoadLink.geometry, newMValue)
              } else {
                None
              }

            case ChangeType.ShortenedCommonPart | ChangeType.ShortenedRemovedPart => //Geometry Shortened
              correctRoadLinkAndGeometry(asset, roadLinks, changeInfos)

            case _ => None
          }
        case _ => None
      }.head
    } else {
      None
    }
  }

  def correctedPersistedAsset(asset: PersistedPointAsset, roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo]): Option[AssetAdjustment] = {
    correctRoadLinkAndGeometry(asset, roadLinks, changeInfo) match {
      case Some(asset) => Some(asset)
      case None =>
        correctOnlyGeometry(asset, roadLinks, changeInfo) match {
          case Some(asset_) => Some(asset_)
          case None => None
      }
    }
  }

  private def correctGeometry(asset: PersistedPointAsset, newRoadLinkId: Long, newRoadLinkGeometry: Seq[Point], newMValue: Double): Option[AssetAdjustment] = {
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(newRoadLinkGeometry, newMValue)
    newAssetPoint match {
      case Some(point) => Some(AssetAdjustment(asset.id, point.x, point.y, newRoadLinkId, newMValue, false))
      case _ => None
    }
  }
}