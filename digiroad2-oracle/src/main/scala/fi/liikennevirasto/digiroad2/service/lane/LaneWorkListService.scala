package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.lane.{LaneWorkListDAO, LaneWorkListItem}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.LinkProperties
import fi.liikennevirasto.digiroad2.util.MainLanePopulationProcess.twoWayLanes
import org.joda.time.DateTime

class LaneWorkListService {
   def workListDao: LaneWorkListDAO = new LaneWorkListDAO

  def getLaneWorkList: Seq[LaneWorkListItem] = {
    PostGISDatabase.withDynTransaction {
      workListDao.getAllItems
    }
  }

  def insertToLaneWorkList(propertyName: String, optionalExistingValue: Option[Int], linkProperty: LinkProperties,
                           vvhRoadLink: VVHRoadlink, username: Option[String], newTransaction: Boolean = true): Unit = {
    val itemToInsert = propertyName match {
      case "traffic_direction" =>
        val newValue = linkProperty.trafficDirection.value
        val oldValue = optionalExistingValue.getOrElse(vvhRoadLink.trafficDirection.value)
        val timeStamp = DateTime.now()
        val createdBy = username.getOrElse("")
        val itemToInsert = LaneWorkListItem(0, vvhRoadLink.linkId, propertyName, oldValue, newValue, timeStamp, createdBy)
        if(newValue != oldValue) Some(itemToInsert)
        else None
      case "link_type" =>
        val newValue = linkProperty.linkType.value
        val oldValue = optionalExistingValue.getOrElse(99)
        val timeStamp = DateTime.now()
        val createdBy = username.getOrElse("")
        val itemToInsert = LaneWorkListItem(0, vvhRoadLink.linkId, propertyName, oldValue, newValue, timeStamp, createdBy)

        val twoWayLaneLinkTypeChange = twoWayLanes.map(_.value).contains(newValue) || twoWayLanes.map(_.value).contains(oldValue)
        if(twoWayLaneLinkTypeChange && (newValue != oldValue)) Some(itemToInsert)
        else None
      case _ => None
    }

    if (itemToInsert.isDefined) {
      if (newTransaction) {
        PostGISDatabase.withDynTransaction {
          workListDao.insertItem(itemToInsert.get)
        }
      }
      else workListDao.insertItem(itemToInsert.get)
    }
  }

  def deleteFromLaneWorkList(itemsToDelete: Set[Long], newTransaction: Boolean = true): Set[Long] = {
    if (newTransaction) {
      PostGISDatabase.withDynTransaction {
        workListDao.deleteItemsById(itemsToDelete)
      }
    }
    else workListDao.deleteItemsById(itemsToDelete)
    itemsToDelete
  }
}
