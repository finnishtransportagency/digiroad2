package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.dao.{RoadLinkReplacementWorkListItem, RoadLinkReplacementWorkListDAO}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.MatchedRoadLinks

class RoadLinkReplacementWorkListService {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  lazy val missingReplacementsDao: RoadLinkReplacementWorkListDAO = new RoadLinkReplacementWorkListDAO

  def insertMatchedLinksToWorkList(matchedRoadLinksList: Seq[MatchedRoadLinks], newTransaction: Boolean = true): Unit = {
    if(newTransaction) withDynTransaction(missingReplacementsDao.insertMatchedLinksToWorkList(matchedRoadLinksList))
    else missingReplacementsDao.insertMatchedLinksToWorkList(matchedRoadLinksList)
  }

  def getMatchedLinksWorkList(newTransaction: Boolean = true): Seq[RoadLinkReplacementWorkListItem] = {
    if(newTransaction) withDynTransaction {
      missingReplacementsDao.getMatchedRoadLinksWorkList()
    } else missingReplacementsDao.getMatchedRoadLinksWorkList()
  }

  def deleteFromWorkList(idsToDelete: Set[Long], newTransaction: Boolean = true): Unit = {
    if(newTransaction) withDynTransaction(missingReplacementsDao.deleteFromWorkList(idsToDelete))
    else missingReplacementsDao.deleteFromWorkList(idsToDelete)
  }
}
