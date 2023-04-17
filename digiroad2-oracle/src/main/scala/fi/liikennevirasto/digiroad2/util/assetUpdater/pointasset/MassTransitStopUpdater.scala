package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.{PropertyValue, Unknown}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.MassTransitStopDao
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{BusStopType, MassTransitStopOperations, MassTransitStopService}
import fi.liikennevirasto.digiroad2.{FloatingReason, PersistedPointAsset}
import org.slf4j.LoggerFactory

class MassTransitStopUpdater(service: MassTransitStopService) extends DirectionalPointAssetUpdater(service: MassTransitStopService) {

  val massTransitStopDao = new MassTransitStopDao
  val logger = LoggerFactory.getLogger(getClass)

  override def shouldFloat(asset: PersistedPointAsset, replaceInfo: ReplaceInfo, newLink: Option[RoadLinkInfo]): (Boolean, Option[FloatingReason]) = {
    val assetAdministrativeClass = MassTransitStopOperations.getAdministrationClass(asset.propertyData).getOrElse(Unknown)
    newLink match {
      case Some(newLink) if MassTransitStopOperations.administrativeClassMismatch(assetAdministrativeClass, Some(newLink.adminClass)) =>
        (true, Some(FloatingReason.RoadOwnerChanged))
      case Some(newLink) if isTerminal(asset) && massTransitStopDao.countTerminalChildBusStops(asset.id) == 0 =>
        (true, Some(FloatingReason.TerminalChildless))
      case _ =>
        super.shouldFloat(asset, replaceInfo, newLink)
    }
  }

  private def isTerminal(asset: PersistedPointAsset): Boolean = {
    asset.propertyData.exists(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId &&
      p.values.exists(v => v.asInstanceOf[PropertyValue].propertyValue == BusStopType.Terminal.value.toString))
  }

}
