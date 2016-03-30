package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.LinearAsset

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
}
