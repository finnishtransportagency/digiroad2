package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{ChangeInfo, Point}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.viite.RoadAddressService
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}

object RoadAddressChangeInfoMapper extends RoadAddressMapper {

  /**
    * This will annex all the valid ChangeInfo to the corresponding road address object
    * The criteria for the annexation is the following:
    *   .The changeInfo vvhTimestamp must be bigger than the RoadAddress vvhTimestamp
    *   .Either the newId or the oldId from the Changeinfo must be equal to the linkId in the RoadAddress
    *
    * @param roadAddresses - Sequence of RoadAddresses
    * @param changes - Sequence of ChangeInfo
    * @return List of (RoadAddress, List of ChangeInfo)
    */
  def matchChangesWithRoadAddresses(roadAddresses: Seq[RoadAddress], changes: Seq[ChangeInfo]) = {
    roadAddresses.map(ra => {
      (ra, changes.filter(c => {
        (c.newId.getOrElse(-1) == ra.linkId || c.oldId.getOrElse(-1) == ra.linkId) && c.vvhTimeStamp > ra.adjustedTimestamp
      }))
    })
  }

  private def createAddressMap(sources: Seq[ChangeInfo]): Seq[RoadAddressMapping] = {
    val pseudoGeom = Seq(Point(0.0, 0.0), Point(1.0, 0.0))
    sources.map(ci => {
      ci.changeType match {
        case 1 => RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
          ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp))
        case 2 => RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
          ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp))
      }
    })
  }

  private def applyChanges(changes: Seq[Seq[ChangeInfo]], roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changes.isEmpty)
      roadAddresses
    else {
      val mapping = createAddressMap(changes.head)
      val mapped = roadAddresses.mapValues(_.flatMap(ra =>
        if (mapping.exists(_.matches(ra)))
          mapRoadAddresses(mapping)(ra)
        else
          Seq(ra)))
      applyChanges(changes.tail, mapped)
    }
  }

  def resolveChangesToMap(roadAddresses: Map[Long, Seq[RoadAddress]], changedRoadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Map[Long, Seq[RoadAddress]] = {
    val groupedChanges = changes.groupBy(_.vvhTimeStamp).values.toSeq
    applyChanges(groupedChanges.sortBy(_.head.vvhTimeStamp), roadAddresses)
  }

  def applyCombined(address : RoadAddress, change : ChangeInfo) : RoadAddress = {
    RoadAddressDAO.expireById(Set(address.linkId))
    //RoadAddressDAO.create(Seq(address), Some(username))
    //recalculateRoadAddresses(address.roadNumber.toInt, address.roadPartNumber.toInt)

    address
  }
}
