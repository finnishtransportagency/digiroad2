package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.client.ReplaceInfoWithGeometry
import fi.liikennevirasto.digiroad2.dao.RoadLinkMissingReplacementDAO
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

class RoadLinkMissingReplacementService {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  lazy val missingReplacementsDao: RoadLinkMissingReplacementDAO = new RoadLinkMissingReplacementDAO

  def insertReplaceInfos(replaceInfos: Seq[ReplaceInfoWithGeometry], newTransaction: Boolean = true): Unit = {
    if(newTransaction) withDynTransaction(missingReplacementsDao.insertReplaceInfos(replaceInfos))
    else missingReplacementsDao.insertReplaceInfos(replaceInfos)
  }
}
