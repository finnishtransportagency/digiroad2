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

  def insertToLaneWorkList(itemToInsert: LaneWorkListItem, newTransaction: Boolean = true): Unit = {
    if (newTransaction) {
      PostGISDatabase.withDynTransaction {
        workListDao.insertItem(itemToInsert)
      }
    }
    else workListDao.insertItem(itemToInsert)
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
