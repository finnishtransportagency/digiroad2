package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.viite.RoadAddressService
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}

object RoadAddressChangeInfoMapper{

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

  def resolveChangesToMap(roadAddresses: Map[Long, Seq[RoadAddress]], changedRoadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Map[Long, Seq[RoadAddress]] = {
    val changesWithRoadAddresses = matchChangesWithRoadAddresses(roadAddresses.flatMap(_._2).asInstanceOf[Seq[RoadAddress]], changes)
    changesWithRoadAddresses.foreach(crl =>{
      val roadAddress = Seq(crl._1)
      roadAddress.map(ra => {
        crl._2.foreach(change => change.changeType match {
          case 1 | 2 => applyCombined(ra, change)
          case _ => Map()
        })
        //TODO - return RoadAddress with all changes appllied
        ra
      })

    }
    )

    Map()
  }

  def applyCombined(address : RoadAddress, change : ChangeInfo) : RoadAddress = {
    RoadAddressDAO.expireById(Set(address.linkId))
    //RoadAddressDAO.create(Seq(address), Some(username))
    //recalculateRoadAddresses(address.roadNumber.toInt, address.roadPartNumber.toInt)

    address
  }
}
