package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao, Queries}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.joda.time.DateTime

class DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao
  def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  val roadName_FI = "osoite_suomeksi"
  val roadName_SE = "osoite_ruotsiksi"

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(ids))
    }
  }

  /*
 * Creates new linear assets and updates existing. Used by the Digiroad2Context.LinearAssetSaveProjected actor.
 */
  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit ={
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected paved assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)

      if(toUpdate.nonEmpty) {
        val persisted = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(toUpdate.map(_.id).toSet).groupBy(_.id)
        updateProjected(toUpdate, persisted, roadLinks)

        if (newLinearAssets.nonEmpty)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
      }

      toInsert.foreach{ linearAsset =>
        val roadLink = roadLinks.find(_.linkId == linearAsset.linkId)

        val id = dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
          Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp, getLinkSource(roadLink), informationSource = Some(MmlNls.value))
        linearAsset.value match {
          case Some(DynamicValue(multiTypeProps)) =>
            val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, linearAsset.typeId)
            validateRequiredProperties(linearAsset.typeId, props)
            dynamicLinearAssetDao.updateAssetProperties(id, props)
          case _ => None
        }
      }
      if (newLinearAssets.nonEmpty)
        logger.info("Added assets for linkids " + toInsert.map(_.linkId))
    }
  }

  protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]], roadLinks: Seq[RoadLink]) : Unit = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }

    toUpdate.foreach { linearAsset =>
      val roadLink = roadLinks.find(_.linkId == linearAsset.linkId)
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(DynamicValue(multiTypeProps)) =>
            dynamicLinearAssetDao.updateAssetLastModified(id, LinearAssetTypes.VvhGenerated) match {
              case Some(id) =>
                val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, linearAsset.typeId)
                validateRequiredProperties(linearAsset.typeId, props)
                dynamicLinearAssetDao.updateAssetProperties(id, props)
              case _ => None
            }
          case _ => None
        }
      }
    }
  }

  override def publish(eventBus: DigiroadEventBus, changeSet: ChangeSet, projectedAssets: Seq[PersistedLinearAsset]) {
    eventBus.publish("dynamicAsset:update", changeSet)
    eventBus.publish("dynamicAsset:saveProjectedAssets", projectedAssets.filter(_.id == 0L))
  }

  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds))
    }
  }
  override protected def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets =
      withDynTransaction {
        enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds))
      }.filterNot(_.expired)
    existingAssets
  }

  protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case DynamicValue(multiTypeProps) =>
          enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(assetId))).head
        case _ => return None
      }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
    //Create New Asset
    val newAssetIdCreated = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, fromUpdate = true, oldAsset.createdBy, oldAsset.createdDateTime, getVerifiedBy(username, oldAsset.typeId))

    Some(newAssetIdCreated)
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    validateRequiredProperties(assetTypeId.head._2, value.asInstanceOf[DynamicValue].value.properties)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId)}

    ids.flatMap { id =>
      val typeId = assetTypeById(id)

      val oldLinearAsset = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id)).head
      val newMeasures = measures.getOrElse(Measures(oldLinearAsset.startMeasure, oldLinearAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldLinearAsset.sideCode)
      val roadLink = vvhClient.fetchRoadLinkByLinkId(oldLinearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))


      value match {
        case DynamicValue(multiTypeProps) =>
          if ((validateMinDistance(newMeasures.startMeasure, oldLinearAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldLinearAsset.endMeasure)) || newSideCode != oldLinearAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, DynamicValue(multiTypeProps), newSideCode, newMeasures, username, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink)))
          }
          else
            Some(updateValues(id, typeId, DynamicValue(multiTypeProps), username, Some(roadLink)))
        case _ =>
          Some(id)
      }
    }
  }

  protected def setDefaultAndFilterProperties(multiTypeProps: DynamicAssetValue, roadLink: Option[RoadLinkLike], typeId: Int) : Seq[DynamicProperty] = {
    val properties = setPropertiesDefaultValues(multiTypeProps.properties, roadLink)
    val defaultValues = dynamicLinearAssetDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    properties ++ defaultValues.toSet
  }

  private def updateValues(id: Long, typeId: Int, value: Value, username: String, roadLink: Option[RoadLinkLike]): Long ={
    value match {
      case DynamicValue(multiTypeProps) =>
        val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, typeId)
        validateRequiredProperties(typeId, props)
        dynamicLinearAssetDao.updateAssetProperties(id, props)
        dynamicLinearAssetDao.updateAssetLastModified(id, username)
      case _ => None
    }
    id
  }

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {

    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy, informationSource = informationSource)

    value match {
      case DynamicValue(multiTypeProps) =>
        val properties = setPropertiesDefaultValues(multiTypeProps.properties, roadLink)
        val defaultValues = dynamicLinearAssetDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
        val props = properties ++ defaultValues.toSet
        validateRequiredProperties(typeId, props)
        dynamicLinearAssetDao.updateAssetProperties(id, props)
      case _ => None
    }
    id
  }

  def setPropertiesDefaultValues(properties: Seq[DynamicProperty], roadLink: Option[RoadLinkLike]): Seq[DynamicProperty] = {
    //To add Properties with Default Values we need to add the public ID to the Seq below
    val defaultPropertiesPublicId = Seq()
    val defaultProperties = defaultPropertiesPublicId.flatMap {
      key =>
        if (!properties.exists(_.publicId == key))
          Some(DynamicProperty(publicId = key, propertyType = "", values = Seq.empty[DynamicPropertyValue]))
        else
          None
    } ++ properties


    defaultProperties.map { parameter =>
      if (parameter.values.isEmpty || parameter.values.exists(_.value == "")) {
        parameter.publicId match {
//          UNTIL DON'T HAVE A ASSET USING THE NEW SYSTEM OF PROPERTIES LETS KEEP THE EXAMPLES
//          case roadName_FI => parameter.copy(values = Seq(MultiTypePropertyValue(roadLink.attributes.getOrElse("ROADNAME_FI", "").toString)))
//          case roadName_SE => parameter.copy(values = Seq(MultiTypePropertyValue(roadLink.attributes.getOrElse("ROADNAME_SE", "").toString)))
//          case inventoryDateId => parameter.copy(values = Seq(PropertyValue(toIso8601.print(DateTime.now()))))
          case _ => parameter
        }
      } else
        parameter
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linearAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink),fromUpdate = true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
  }

  override def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val existing = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id)

      val(newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)),
          valueAgainstDigitization.map( createWithoutTransaction(existing.typeId, existing.linkId, _,  SideCode.AgainstDigitizing.value,  Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)))

      Seq(newId1, newId2).flatten
    }
  }

  protected def validateRequiredProperties(typeId: Int, properties: Seq[DynamicProperty]): Unit = {
    val mandatoryProperties: Map[String, String] = dynamicLinearAssetDao.getAssetRequiredProperties(typeId)
    val nonEmptyMandatoryProperties: Seq[DynamicProperty] = properties.filter { property =>
      mandatoryProperties.contains(property.publicId) && property.values.nonEmpty
    }
    val missingProperties = mandatoryProperties.keySet -- nonEmptyMandatoryProperties.map(_.publicId).toSet

    if (missingProperties.nonEmpty)
      throw new MissingMandatoryPropertyException(missingProperties)
  }

  def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]) : Seq[PersistedLinearAsset] = {

    val assetIds = persistedLinearAsset.map(_.id)

    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getValidityPeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      persistedLinearAsset.groupBy(_.id).flatMap {
        case (id, assets) =>
          properties.get(id) match {
            case Some(props) => assets.map(a => a.copy(value = a.value match {
              case Some(value) =>
                val multiValue = value.asInstanceOf[DynamicValue]
                //If exist at least one property to enrich with value all null properties value could be filter out
                Some(multiValue.copy(value = DynamicAssetValue(multiValue.value.properties.filter(_.values.nonEmpty ) ++ props)))
              case _ =>
                Some(DynamicValue(DynamicAssetValue(props)))
            }))
            case _ => assets
          }
      }.toSeq
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }

  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
        val oldAsset = getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId)).head

        val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
        expireAsset(oldAsset.typeId, oldAsset.id, LinearAssetTypes.VvhGenerated, true )
        createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAsset.value.get, adjustment.sideCode.value, Measures(oldAsset.startMeasure, oldAsset.endMeasure),
          LinearAssetTypes.VvhGenerated, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink))
    }
}