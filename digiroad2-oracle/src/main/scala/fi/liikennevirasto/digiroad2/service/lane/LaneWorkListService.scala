package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.dao.lane.{LaneWorkListDAO, LaneWorkListItem}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.LinkPropertyChange
import fi.liikennevirasto.digiroad2.util.MainLanePopulationProcess.twoWayLanes
import org.joda.time.DateTime

class LaneWorkListService {
   def workListDao: LaneWorkListDAO = new LaneWorkListDAO

  def getLaneWorkList(newTransaction:Boolean = true): Seq[LaneWorkListItem] = {
    if (newTransaction) {
      PostGISDatabase.withDynTransaction {
        workListDao.getAllItems
      }
    } else {
      workListDao.getAllItems
    }
  }

  def insertToLaneWorkList(linkPropertyChange: LinkPropertyChange, newTransaction: Boolean = true): Unit = {
    val itemToInsert = linkPropertyChange.propertyName match {
      case "traffic_direction" =>
        val newValue = linkPropertyChange.linkProperty.trafficDirection.value
        val oldValue = linkPropertyChange.optionalExistingValue.getOrElse(linkPropertyChange.roadLinkFetched.trafficDirection.value)
        val timeStamp = DateTime.now()
        val createdBy = linkPropertyChange.username.getOrElse("")
        val itemToInsert = LaneWorkListItem(0, linkPropertyChange.roadLinkFetched.linkId, linkPropertyChange.propertyName, oldValue, newValue, timeStamp, createdBy)
        if(newValue != oldValue) Some(itemToInsert)
        else None
      case "link_type" =>
        val newValue = linkPropertyChange.linkProperty.linkType.value
        val oldValue = linkPropertyChange.optionalExistingValue.getOrElse(99)
        val timeStamp = DateTime.now()
        val createdBy = linkPropertyChange.username.getOrElse("")
        val itemToInsert = LaneWorkListItem(0, linkPropertyChange.roadLinkFetched.linkId, linkPropertyChange.propertyName, oldValue, newValue, timeStamp, createdBy)

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
