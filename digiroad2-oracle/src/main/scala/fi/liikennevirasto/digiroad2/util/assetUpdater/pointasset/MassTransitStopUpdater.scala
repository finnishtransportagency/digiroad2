package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.{PropertyValue, Unknown}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.MassTransitStopDao
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{BusStopType, MassTransitStopOperations, MassTransitStopService}
import fi.liikennevirasto.digiroad2.{FloatingReason, PersistedPointAsset}

class MassTransitStopUpdater(service: MassTransitStopService) extends DirectionalPointAssetUpdater(service: MassTransitStopService) {

  val massTransitStopDao = new MassTransitStopDao

  override def shouldFloat(asset: PersistedPointAsset, replaceInfo: ReplaceInfo, newLinkInfo: Option[RoadLinkInfo],
                           newLink: Option[RoadLink]): (Boolean, Option[FloatingReason]) = {
    val assetAdministrativeClass = MassTransitStopOperations.getAdministrationClass(asset.propertyData).getOrElse(Unknown)
    newLink match {
      case Some(newLink) if MassTransitStopOperations.administrativeClassMismatch(assetAdministrativeClass, Some(newLink.administrativeClass)) =>
        (true, Some(FloatingReason.RoadOwnerChanged))
      case Some(_) if isTerminal(asset) && massTransitStopDao.countTerminalChildBusStops(asset.id) == 0 =>
        (true, Some(FloatingReason.TerminalChildless))
      case _ =>
        super.shouldFloat(asset, replaceInfo, newLinkInfo, newLink)
    }
  }

  private def isTerminal(asset: PersistedPointAsset): Boolean = {
    asset.propertyData.exists(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId &&
      p.values.exists(v => v.asInstanceOf[PropertyValue].propertyValue == BusStopType.Terminal.value.toString))
  }

}
