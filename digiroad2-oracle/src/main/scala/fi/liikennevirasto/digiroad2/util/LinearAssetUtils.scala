package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, LinearAsset}

/**
  * Created by venholat on 30.3.2016.
  */
object LinearAssetUtils {
  /**
    * Return true if the vvh time stamp is older than change time stamp
    * and asset may need projecting
    * @param asset SpeedLimit under consideration
    * @param change Change information
    * @return true if speed limit may be outdated
    */
  def newChangeInfoDetected(asset : LinearAsset, change: Seq[ChangeInfo]) = {
    change.map(c => (c.oldId.getOrElse(0), c.newId.getOrElse(0), c.vvhTimeStamp.getOrElse(0))).exists {
      case (oldId: Long, newId: Long, vvhTimeStamp: Long) => (oldId == asset.linkId || newId == asset.linkId) &&
        vvhTimeStamp > asset.vvhTimeStamp
      case _ => false
    }
  }

  /**
    * Returns true if there are new change informations for roadlink assets.
    * Comparing if the assets vvh time stamp is older than the change time stamp
    * @param roadLink Roadlink under consideration
    * @param changeInfo Change information
    * @param assets Linear assets
    * @return true if there are new change informations for roadlink assets
    */
  def isNewProjection(roadLink: RoadLink, changeInfo: Seq[ChangeInfo], assets: Seq[LinearAsset]) = {
    changeInfo.exists(_.newId == roadLink.linkId) &&
      assets.exists(asset => (asset.linkId == roadLink.linkId) &&
        (asset.vvhTimeStamp < changeInfo.filter(_.newId == roadLink.linkId).maxBy(_.vvhTimeStamp).vvhTimeStamp.getOrElse(0: Long)))
  }
}
