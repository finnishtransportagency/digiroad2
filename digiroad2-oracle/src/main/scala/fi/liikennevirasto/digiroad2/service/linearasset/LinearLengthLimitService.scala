package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, AxleWeightLimit, LengthLimit}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools

class LinearLengthLimitService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  def inaccurateDAO: InaccurateAssetDAO = new InaccurateAssetDAO

  override def update(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    val outputIds = withDynTransaction {
      updateWithoutTransaction(ids, value, username)
    }

    eventBus.publish("lengthLimit:Validator",AssetValidatorInfo(ids.toSet, outputIds.toSet))
    outputIds
  }

  override def getInaccurateRecords(municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = {
    def toInaccurateLinearAsset(x: (Long, String, Int)) = InaccurateLinearAsset(x._1, x._2, AdministrativeClass(x._3).toString)

    withDynTransaction {
      inaccurateDAO.getInaccurateAssetByTypeId(LengthLimit.typeId, municipalities, adminClass)
        .map(toInaccurateLinearAsset)
        .groupBy(_.municipality)
        .mapValues {
          _.groupBy(_.administrativeClass)
            .mapValues(_.map(_.linkId))
        }
    }
  }
}
