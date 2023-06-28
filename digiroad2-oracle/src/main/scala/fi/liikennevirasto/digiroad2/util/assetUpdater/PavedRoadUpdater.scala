package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, DynamicPropertyValue, LinkGeomSource, MmlNls, SideCode}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, PersistedLinearAsset, SurfaceType}
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import org.joda.time.DateTime

class PavedRoadUpdater(service: PavedRoadService) extends DynamicLinearAssetUpdater(service) {
  
  override def operationForNewLink(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    val newLink = change.newLinks.head
    
    if (newLink.surfaceType == SurfaceType.Paved) {
      val defaultMultiTypePropSeq = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("99")))))
      val defaultPropertyData = DynamicValue(defaultMultiTypePropSeq)
      
      val newAsset = PersistedLinearAsset(0, newLink.linkId,
        sideCode = SideCode.BothDirections.value,
        value = Some(defaultPropertyData),
        startMeasure = 0, endMeasure = GeometryUtils.geometryLength(newLink.geometry), createdBy = None,
        createdDateTime = Some(DateTime.now()),
        modifiedBy = None, modifiedDateTime = None, expired = false, 
        typeId = LinearAssetTypes.PavedRoadAssetTypeId,
        timeStamp = LinearAssetUtils.createTimeStamp(), geomModifiedDate = Some(DateTime.now()),
        linkSource = LinkGeomSource.NormalLinkInterface,
        verifiedBy = None, verifiedDate = None,
        informationSource = Some(MmlNls))
      Seq((newAsset, changeSets))
    } else {
      Seq.empty[(PersistedLinearAsset, ChangeSet)]
    }
    
  }


  override def additionalUpdateOrChange(change: RoadLinkChange, assetsAll: Seq[PersistedLinearAsset], changeSets: ChangeSet): Seq[(PersistedLinearAsset, ChangeSet)] = {
    change.changeType match {
      //remove pavement
      case RoadLinkChangeType.Replace | RoadLinkChangeType.Split =>
        val expiredPavement = assetsAll.filter(a => change.newLinks.map(_.linkId).contains(a.linkId)).map(asset => {
          val replace = change.newLinks.find(_.linkId == asset.linkId).get
          if (replace.surfaceType == SurfaceType.None) {
            if (asset.id != 0){
              (asset, changeSets.copy(expiredAssetIds = changeSets.expiredAssetIds ++ Set(asset.id)))
            } else {
              (asset.copy(id = removePart), changeSets)
            }
          } else {
            (asset, changeSets)
          }
        })
        expiredPavement
      case _ => Seq.empty[(PersistedLinearAsset, ChangeSet)]
    }
  }

  override def filterChanges(changes: Seq[RoadLinkChange]): Seq[RoadLinkChange] = {
    val (remove, other) = changes.partition(_.changeType == RoadLinkChangeType.Remove)
    val linksOther = other.flatMap(_.newLinks.map(_.linkId)).toSet
    val filterChanges = if (linksOther.nonEmpty) {
      val links = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksOther,false)
      val filteredLinks = links.filter(_.functionalClass > 4).map(_.linkId)
      other.filter(p => filteredLinks.contains(p.newLinks.head.linkId))
    } else Seq()
    filterChanges ++ remove
  }

}
