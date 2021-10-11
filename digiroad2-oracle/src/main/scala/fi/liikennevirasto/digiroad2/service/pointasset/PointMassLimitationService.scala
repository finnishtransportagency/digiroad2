package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.{GeometryUtils, PersistedPointAsset, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISPointMassLimitationDao
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime

case class MassLimitationPointAsset(lon: Double, lat: Double, assets : Seq[PersistedPointAsset])

case class WeightGroupLimitation(id: Long,
                                typeId: Int,
                                linkId: Long,
                                lon: Double, lat: Double,
                                mValue: Double, floating: Boolean,
                                vvhTimeStamp: Long,
                                municipalityCode: Int,
                                createdBy: Option[String] = None,
                                createdAt: Option[DateTime] = None,
                                modifiedBy: Option[String] = None,
                                modifiedAt: Option[DateTime] = None,
                                linkSource: LinkGeomSource,
                                limit: Double,
                                propertyData: Seq[Property] = Seq()) extends PersistedPointAsset


class PointMassLimitationService(roadLinkService: RoadLinkService, dao: PostGISPointMassLimitationDao) {

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  val pointMassLimitationTypes = Seq(TrTrailerTruckWeightLimit.typeId,
    TrAxleWeightLimit.typeId, TrWeightLimit.typeId, TrBogieWeightLimit.typeId)


  def getByBoundingBox(user: User, bounds: BoundingRectangle) :Seq[MassLimitationPointAsset] = {
    getByRoadLinks(pointMassLimitationTypes, bounds)
  }

  def getByRoadLinks(typeIds: Seq[Int], bounds: BoundingRectangle): Seq[MassLimitationPointAsset] = {
    withDynTransaction {
      val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where $boundingBoxFilter"
      val assets = dao.fetchByBoundingBox(typeIds, withFilter(filter))

      assets.foldLeft(Seq.empty[MassLimitationPointAsset]) {
        (prev, asset) =>
          prev.exists( massLimitationSeq => massLimitationSeq.assets.exists(persisted => persisted.id == asset.id)) match {
            case true => prev
            case false =>
              val assetsOnRange = assets.filter( weighGroup => GeometryUtils.geometryLength(Seq(Point(asset.lon, asset.lat), Point(weighGroup.lon, weighGroup.lat))) < GeometryUtils.getDefaultEpsilon )
              prev ++ Seq(MassLimitationPointAsset(asset.lon, asset.lat, assetsOnRange))
          }
      }
    }
  }

  def getByIds(ids: Seq[Long]):Seq[MassLimitationPointAsset] = {
    withDynTransaction {
      val filter = s"where a.id in (${ids.mkString(",")})"
      val assets = dao.fetchByBoundingBox(pointMassLimitationTypes, withFilter(filter))

      assets.foldLeft(Seq.empty[MassLimitationPointAsset]) {
        (prev, asset) =>
          prev.exists( massLimitationSeq => massLimitationSeq.assets.exists(persisted => persisted.id == asset.id)) match {
            case true => prev
            case false =>
              val assetsOnRange = assets.filter( weighGroup => GeometryUtils.geometryLength(Seq(Point(asset.lon, asset.lat), Point(weighGroup.lon, weighGroup.lat))) < GeometryUtils.getDefaultEpsilon )
              prev ++ Seq(MassLimitationPointAsset(asset.lon, asset.lat, assetsOnRange))
          }
      }
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

}
