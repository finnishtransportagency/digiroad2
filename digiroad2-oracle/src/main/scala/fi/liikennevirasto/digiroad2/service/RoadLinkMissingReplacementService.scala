package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.dao.{MatchedRoadLinkWorkListItem, RoadLinkMissingReplacementDAO}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.MatchedRoadLinks

class RoadLinkMissingReplacementService {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  lazy val missingReplacementsDao: RoadLinkMissingReplacementDAO = new RoadLinkMissingReplacementDAO

  def insertMatchedLinksToWorkList(matchedRoadLinksList: Seq[MatchedRoadLinks], newTransaction: Boolean = true): Unit = {
    if(newTransaction) withDynTransaction(missingReplacementsDao.insertMatchedLinksToWorkList(matchedRoadLinksList))
    else missingReplacementsDao.insertMatchedLinksToWorkList(matchedRoadLinksList)
  }

  def getMatchedLinksWorkList(newTransaction: Boolean = true): Seq[MatchedRoadLinkWorkListItem] = {
    if(newTransaction) withDynTransaction {
      missingReplacementsDao.getMatchedRoadLinksWorkList()
    } else missingReplacementsDao.getMatchedRoadLinksWorkList()
  }

  def deleteFromWorkList(idsToDelete: Seq[Long], newTransaction: Boolean = true): Unit = {
    if(newTransaction) withDynTransaction(missingReplacementsDao.deleteFromWorkList(idsToDelete))
    else missingReplacementsDao.deleteFromWorkList(idsToDelete)
  }
}
