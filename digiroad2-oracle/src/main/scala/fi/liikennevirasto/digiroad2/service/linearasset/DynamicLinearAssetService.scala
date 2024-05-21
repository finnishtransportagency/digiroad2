package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LogUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime

case class  AssetMissingProperties(msg:String) extends Exception(msg)

class DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  def inaccurateDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) : Map[String, Map[String ,List[Long]]] = throw new UnsupportedOperationException("Not supported method")

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction)
      withDynTransaction {
        enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(ids))
      }
    else
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(ids))
  }


  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction)
      withDynTransaction {
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds))
    } else
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds))
  }

  override def fetchExistingAssetsByLinksIdsString(typeId: Int, linksIds: Set[String], removedLinkIds: Set[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val existingAssets = if (newTransaction) {
      withDynTransaction {
        enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, (linksIds ++ removedLinkIds).toSeq))
      }.filterNot(_.expired)
    } else {
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, (linksIds ++ removedLinkIds).toSeq).filterNot(_.expired))
    }
    existingAssets
  }

  override def fetchExistingAssetsByLinksIds(typeId: Int, roadLinks: Seq[RoadLink], removedLinkIds: Seq[String], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val existingAssets = if (newTransaction) {
      withDynTransaction {
        enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds))
      }.filterNot(_.expired)
    } else {
      enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds ++ removedLinkIds)).filterNot(_.expired)
    }
    existingAssets
  }

  protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], timeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case DynamicValue(multiTypeProps) =>
          enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(assetId))).head
        case _ => return None
      }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(oldAsset.linkId, newTransaction = false)
    //Create New Asset
    val newAssetIdCreated = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, timeStamp.getOrElse(createTimeStamp()), roadLink, fromUpdate = true, oldAsset.createdBy, oldAsset.createdDateTime, verifiedBy = getVerifiedBy(username, oldAsset.typeId))

    Some(newAssetIdCreated)
  }

  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
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
      
      var roadLink:RoadLinkFetched=null
      try{
        roadLink= roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(oldLinearAsset.linkId).get
      }
      catch {
        case e:NoSuchElementException=>
          logger.warn("RoadLink no longer available: "+oldLinearAsset.linkId+" For asset: "+oldLinearAsset.id+ ",Expired: "+ oldLinearAsset.expired)
        case e: Throwable =>
          logger.warn("Some other error occurred, For asset: "+oldLinearAsset.id+ ",Expired: "+ oldLinearAsset.expired+" \n  ",e)
      }
      
      value match {
        case DynamicValue(multiTypeProps) =>
          if ((validateMinDistance(newMeasures.startMeasure, oldLinearAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldLinearAsset.endMeasure)) || newSideCode != oldLinearAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, DynamicValue(multiTypeProps), newSideCode, newMeasures, username, createTimeStamp(), Some(roadLink)))
          }
          else
            Some(updateValues(id, typeId, DynamicValue(multiTypeProps), username, Some(roadLink)))
        case _ =>
          Some(id)
      }
    }
  }

  private def updateValues(id: Long, typeId: Int, value: Value, username: String, roadLink: Option[RoadLinkLike]): Long ={
    value match {
      case DynamicValue(multiTypeProps) =>
        val properties = validateAndSetDefaultProperties(typeId, value, roadLink)
        properties match {
          case Some(x) => 
            dynamicLinearAssetDao.updateAssetProperties(id, x, typeId)
            dynamicLinearAssetDao.updateAssetLastModified(id, username)
          case None => logger.warn(s"Asset ${id} in link ${roadLink.get.linkId} is missing properties, type of ${typeId}")
        }
      case _ => None
    }
    id
  }

  override def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long = createTimeStamp(), roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                        createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()),
                                        modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {
    
   val properties = validateAndSetDefaultProperties(typeId, value, roadLink)
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      timeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, modifiedByFromUpdate, modifiedDateTimeFromUpdate, verifiedBy, informationSource = informationSource)
    properties match {
      case Some(x) => dynamicLinearAssetDao.updateAssetProperties(id, x, typeId)
      case None => logger.warn(s"Asset ${id} in link ${linkId} is missing properties, type of ${typeId}")
    }
    id
  }

  protected def validateAndSetDefaultProperties(typeId: Int, value: Value, roadLink: Option[RoadLinkLike]): Option[Seq[DynamicProperty]] = {
    value match {
      case DynamicValue(multiTypeProps) =>
        val properties = setPropertiesDefaultValues(multiTypeProps.properties, roadLink)
        val defaultValues = dynamicLinearAssetDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
        val props = properties ++ defaultValues.toSet
        validateRequiredProperties(typeId, props)
        Some(props)
      case _ => None
    }
  }
  override def createMultipleLinearAssets(list: Seq[NewLinearAssetMassOperation]): Unit = {
    val assetsSaved = dao.createMultipleLinearAssets(list)
    LogUtils.time(logger,"Saving assets properties"){
      assetsSaved.foreach(a => {
        val value = a.asset.value
        val properties = validateAndSetDefaultProperties(a.asset.typeId, value, a.asset.roadLink)
        properties match {
          case Some(x) => dynamicLinearAssetDao.updateAssetProperties(a.id, x, a.asset.typeId)
          case None => logger.warn(s"Asset ${a.id} in link ${a.asset.roadLink} is missing properties, type of ${a.asset.typeId}")
        }
      })
    }
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

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
  val ids = withDynTransaction {
      val linearAsset = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(linearAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink),fromUpdate = true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }

  override def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, adjust:Boolean = true): Seq[Long] = {
    val ids =  withDynTransaction {
      val existing = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id)

      val(newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)),
          valueAgainstDigitization.map( createWithoutTransaction(existing.typeId, existing.linkId, _,  SideCode.AgainstDigitizing.value,  Measures(existing.startMeasure, existing.endMeasure), username, existing.timeStamp, Some(roadLink), fromUpdate= true, createdByFromUpdate = existing.createdBy, createdDateTimeFromUpdate = existing.createdDateTime)))
      
      Seq(newId1, newId2).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }

  override def adjustAssets(ids: Seq[Long]): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(ids.toSet)
      adjustLinearAssetsAction(linearAsset.map(_.linkId).toSet, linearAsset.head.typeId, newTransaction = false)
    }
    ids
  }
  def validateRequiredProperties(typeId: Int, properties: Seq[DynamicProperty]): Unit = {
    val mandatoryProperties: Map[String, String] = dynamicLinearAssetDao.getAssetRequiredProperties(typeId)
    val nonEmptyMandatoryProperties: Seq[DynamicProperty] = properties.filter { property =>
      mandatoryProperties.contains(property.publicId) && property.values.nonEmpty
    }
    val missingProperties = mandatoryProperties.keySet -- nonEmptyMandatoryProperties.map(_.publicId).toSet

    if (missingProperties.nonEmpty)
      throw new MissingMandatoryPropertyException(missingProperties)
  }

  def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]) : Seq[PersistedLinearAsset] = persistedLinearAsset

  def enrichWithProperties(properties: Map[Long, Seq[DynamicProperty]], persistedLinearAsset: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    persistedLinearAsset.groupBy(_.id).flatMap {
      case (id, assets) =>
        properties.get(id) match {
          case Some(props) => assets.map(a => a.copy(value = a.value match {
            case Some(value) =>
              val multiValue = value.asInstanceOf[DynamicValue]
              //If exist at least one property to enrich with value all null properties value could be filter out
              Some(multiValue.copy(value = DynamicAssetValue(multiValue.value.properties.filter(_.values.nonEmpty) ++ props)))
            case _ =>
              Some(DynamicValue(DynamicAssetValue(props)))
          }))
          case _ => assets
        }
    }.toSeq
  }

  /**
    * This method returns linear assets that have been changed in OTH between given date values. It is used by TN-ITS ChangeApi.
    *
    * @param typeId
    * @param since
    * @param until
    * @param withAutoAdjust
    * @return Changed linear assets
    */
  override def getChanged(typeId: Int, since: DateTime, until: DateTime, withAutoAdjust: Boolean = false, token: Option[String]): Seq[ChangedLinearAsset] = {
    val persistedLinearAssets = withDynTransaction {
      dynamicLinearAssetDao.getDynamicLinearAssetsChangedSince(typeId, since, until, withAutoAdjust, token)
    }
    val roadLinks = roadLinkService.getRoadLinksByLinkIds(persistedLinearAssets.map(_.linkId).toSet)
    processLinearAssetChanges(persistedLinearAssets, roadLinks)
  }

  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = {
    withDynTransaction {
      inaccurateDAO.getInaccurateAsset(typeId, municipalities, adminClass)
        .groupBy(_.municipality)
        .mapValues {
          _.groupBy(_.administrativeClass)
            .mapValues(_.map{values => Map("assetId" -> values.assetId, "linkId" -> values.linkId)})
        }
    }
  }
}