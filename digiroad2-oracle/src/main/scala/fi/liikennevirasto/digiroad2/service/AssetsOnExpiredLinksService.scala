package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.dao.AssetsOnExpiredLinksDAO
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.joda.time.DateTime


case class AssetOnExpiredLink(id: Long, assetTypeId: Int, linkId: String, sideCode: Int, startMeasure: Double,
                              endMeasure: Double, geometry: Seq[Point], roadLinkExpiredDate: Option[DateTime])

class AssetsOnExpiredLinksService {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  protected def dao: AssetsOnExpiredLinksDAO = new AssetsOnExpiredLinksDAO

  def insertAssets(assets: Seq[AssetOnExpiredLink], newTransaction: Boolean = false): Unit = {
    if(newTransaction) withDynTransaction{
      assets.foreach(asset => dao.insertToWorkList(asset))
    }
    else assets.foreach(asset => dao.insertToWorkList(asset))
  }
}
