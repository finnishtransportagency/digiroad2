package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{MultiTypePropertyValue, MultiTypeProperty}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{MultiValueLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.joda.time.DateTime

class MultiValueLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao
  def multiValueLinearAssetDao: MultiValueLinearAssetDao = new MultiValueLinearAssetDao
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  val roadName_FI = "osoite_suomeksi"
  val roadName_SE = "osoite_ruotsiksi"


  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      multiValueLinearAssetDao.fetchMultiValueLinearAssetsByIds(typeId, ids)
    }
  }

  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      multiValueLinearAssetDao.fetchMultiValueLinearAssetsByLinkIds(typeId, linkIds)
    }
  }
  override protected def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
        multiValueLinearAssetDao.fetchMultiValueLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds)
      }.filterNot(_.expired)
    existingAssets
  }

  protected def updateValueByExpiration(assetId: Long, typeId: Int, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case MultiValue(multiTypeProps) =>
          multiValueLinearAssetDao.fetchMultiValueLinearAssetsByIds(typeId, Set(assetId)).head
        case _ => return None
      }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
    //Create New Asset
    val newAssetIDcreate = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, getVerifiedBy(username, oldAsset.typeId))

    Some(newAssetIDcreate)
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, measures: Option[Measures] = None, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      value match {
        case MultiValue(multiTypeProps) =>
          updateValueByExpiration(id, typeId, MultiValue(multiTypeProps), LinearAssetTypes.numericValuePropertyId, username, measures, vvhTimeStamp, sideCode)
        case _ =>
          Some(id)
      }
    }
  }

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None): Long = {

    val id = multiValueLinearAssetDao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy)

    value match {
      case MultiValue(multiTypeProps) =>
        val properties = setPropertiesDefaultValues(multiTypeProps, roadLink)
        val defaultValues = multiValueLinearAssetDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
        multiValueLinearAssetDao.updateAssetProperties(id, properties ++ defaultValues.toSet)
      case _ => None
    }
    id
  }

  def setPropertiesDefaultValues(properties: Seq[MultiTypeProperty], roadLink: Option[RoadLinkLike]): Seq[MultiTypeProperty] = {
    val defaultPropertiesPublicId = Seq(roadName_FI, roadName_SE)
    val defaultProperties = defaultPropertiesPublicId.flatMap {
      key =>
        if (!properties.exists(_.publicId == key))
          Some(MultiTypeProperty(publicId = key, propertyType = "", values = Seq.empty[MultiTypePropertyValue]))
        else
          None
    } ++ properties


    defaultProperties.map { parameter =>
      if (parameter.values.isEmpty || parameter.values.exists(_.propertyValue == "")) {
        parameter.publicId match {
//          UNTIL DON'T HAVE A ASSET USING THE NEW SYSTEM OF PROPERTIES LETS KEEP THE EXAMPLES
//          case roadName_FI => parameter.copy(values = Seq(MultiTypePropertyValue(roadLink.attributes.getOrElse("ROADNAME_FI", "").toString)))
//          case roadName_SE => parameter.copy(values = Seq(MultiTypePropertyValue(roadLink.attributes.getOrElse("ROADNAME_SE", "").toString)))
          case _ => parameter
        }
      } else
        parameter
    }
  }

}