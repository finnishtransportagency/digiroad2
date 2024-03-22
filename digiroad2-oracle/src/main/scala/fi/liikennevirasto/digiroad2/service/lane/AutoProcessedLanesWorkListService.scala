package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.dao.lane.{AutoProcessedLanesWorkListItem, AutoProcessedLanesWorkListDAO}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

class AutoProcessedLanesWorkListService {
  def dao: AutoProcessedLanesWorkListDAO = new AutoProcessedLanesWorkListDAO

  def getAutoProcessedLanesWorkList(newTransaction:Boolean = true): Seq[AutoProcessedLanesWorkListItem] = {
    if (newTransaction) {
      PostGISDatabase.withDynTransaction {
        dao.getAllItems
      }
    } else {
      dao.getAllItems
    }
  }

  def insertToAutoProcessedLanesWorkList(itemToInsert: AutoProcessedLanesWorkListItem, newTransaction: Boolean = true): Unit = {
    if (newTransaction) {
      PostGISDatabase.withDynTransaction {
        dao.insertItem(itemToInsert)
      }
    }
    else dao.insertItem(itemToInsert)
  }

  def deleteFromAutoProcessedLanesWorkList(itemsToDelete: Set[Long], newTransaction: Boolean = true): Set[Long] = {
    if (newTransaction) {
      PostGISDatabase.withDynTransaction {
        dao.deleteItemsById(itemsToDelete)
      }
    }
    else dao.deleteItemsById(itemsToDelete)
    itemsToDelete
  }
}
