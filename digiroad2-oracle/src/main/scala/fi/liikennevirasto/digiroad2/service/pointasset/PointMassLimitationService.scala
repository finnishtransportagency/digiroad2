package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PersistedPointAsset
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.OraclePointMassLimitationDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, Value}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime

case class MassLimitationPointAsset(linkId: Long, value: Option[Value])

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
                                limit: Double) extends PersistedPointAsset


class PointMassLimitationService(roadLinkService: RoadLinkService, dao: OraclePointMassLimitationDao) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val pointMassLimitationTypes = Seq(TrTrailerTruckWeightLimit.typeId,
    TrAxleWeightLimit.typeId, TrWeightLimit.typeId, TrBogieWeightLimit.typeId)


  def getByBoundingBox(user: User, bounds: BoundingRectangle) :Seq[PersistedPointAsset] = {
    getByRoadLinks(pointMassLimitationTypes, bounds)
  }

  def getByRoadLinks(typeIds: Seq[Int], bounds: BoundingRectangle): Seq[PersistedPointAsset] = {
    withDynTransaction {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where $boundingBoxFilter"
      dao.fetchByBoundingBox(typeIds, withFilter(filter))
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

}
