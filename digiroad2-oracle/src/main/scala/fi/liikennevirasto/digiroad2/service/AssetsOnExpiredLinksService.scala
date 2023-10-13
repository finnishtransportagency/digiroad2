package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.dao.AssetsOnExpiredLinksDAO
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.joda.time.DateTime


case class AssetOnExpiredLink(id: Long, assetTypeId: Int, linkId: String, sideCode: Int, startMeasure: Double,
                              endMeasure: Double, geometry: Seq[Point], roadLinkExpiredDate: DateTime)

class AssetsOnExpiredLinksService {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  protected def dao: AssetsOnExpiredLinksDAO = new AssetsOnExpiredLinksDAO

  def getAllWorkListAssets(newTransaction: Boolean = false): Seq[AssetOnExpiredLink] = {
    if(newTransaction) withDynTransaction{
      dao.fetchWorkListAssets()
    }
    else dao.fetchWorkListAssets()
  }

  def insertAssets(assets: Seq[AssetOnExpiredLink], newTransaction: Boolean = false): Unit = {
    if(newTransaction) withDynTransaction{
      assets.foreach(asset => dao.insertToWorkList(asset))
    }
    else assets.foreach(asset => dao.insertToWorkList(asset))
  }

  def deleteFromWorkList(assetIds: Set[Long], newTransaction: Boolean = false): Unit  = {
    if(newTransaction) withDynTransaction{
      dao.deleteFromWorkList(assetIds)
    }
    else dao.deleteFromWorkList(assetIds)
  }
}
