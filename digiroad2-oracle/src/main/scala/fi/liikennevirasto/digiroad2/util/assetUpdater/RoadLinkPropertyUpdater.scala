package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.FeatureClass.WinterRoads
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Remove, Replace, Split}
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClass, TrafficDirection => TrafficDirectionString, _}
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadLinkOverrideDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{IncompleteLink, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Creation, Deletion, Divided, Replaced}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, KgvUtil, LinearAssetUtils}
import fi.liikennevirasto.digiroad2.DummyEventBus
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.util.Try

class RoadLinkPropertyUpdater {

  lazy val roadLinkService: RoadLinkService = new RoadLinkService(new RoadLinkClient(), new DummyEventBus)
  lazy val roadLinkChangeClient: RoadLinkChangeClient = new RoadLinkChangeClient
  val logger: Logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def roadLinkChangeToChangeType(roadLinkChangeType: RoadLinkChangeType): ChangeType = {
    roadLinkChangeType match {
      case Add => Creation
      case Remove => Deletion
      case Split => Divided
      case Replace => Replaced
    }
  }

  def transferFunctionalClass(changeType: RoadLinkChangeType, oldLink: RoadLinkInfo, newLink: RoadLinkInfo, timeStamp: String): Option[FunctionalClassChange] = {
    FunctionalClassDao.getExistingValue(oldLink.linkId) match {
      case Some(functionalClass) =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), functionalClass, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), Some(functionalClass), Some(functionalClass), "oldLink",Some(oldLink.linkId)))
      case _ => None
    }
  }

  def generateFunctionalClass(changeType: RoadLinkChangeType, newLink: RoadLinkInfo): Option[FunctionalClassChange] = {
    val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
    val timeStamp = DateTime.now().toString()
    featureClass match {
      case FeatureClass.TractorRoad =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), PrimitiveRoad.value, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(PrimitiveRoad.value), "mtkClass",None))
      case FeatureClass.HardShoulder =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), FunctionalClass9.value, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(FunctionalClass9.value), "mtkClass",None))
      case FeatureClass.DrivePath | FeatureClass.CarRoad_IIIb =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), AnotherPrivateRoad.value, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(AnotherPrivateRoad.value), "mtkClass",None))
      case FeatureClass.CycleOrPedestrianPath =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), WalkingAndCyclingPath.value, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(WalkingAndCyclingPath.value), "mtkClass",None))
      case FeatureClass.SpecialTransportWithoutGate =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), UnknownFunctionalClass.value, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(UnknownFunctionalClass.value), "mtkClass",None))
      case FeatureClass.SpecialTransportWithGate =>
        FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), UnknownFunctionalClass.value, timeStamp)
        Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(UnknownFunctionalClass.value), "mtkClass",None))
      case FeatureClass.CarRoad_IIIa => newLink.adminClass match {
        case State =>
          FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), FunctionalClass4.value, timeStamp)
          Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(FunctionalClass4.value), "mtkClass",None))
        case Municipality | Private =>
          FunctionalClassDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), FunctionalClass5.value, timeStamp)
          Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(FunctionalClass5.value), "mtkClass",None))
        case _ => None
      }
      case _ => None
    }
  }

  def transferLinkType(changeType: RoadLinkChangeType, oldLink: RoadLinkInfo, newLink: RoadLinkInfo, timeStamp: String): Option[LinkTypeChange] = {
    LinkTypeDao.getExistingValue(oldLink.linkId) match {
      case Some(linkType) =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), linkType, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), Some(linkType), Some(linkType), "oldLink",Some(oldLink.linkId)))
      case _ =>
        None
    }
  }

  def generateLinkType(changeType: RoadLinkChangeType, newLink: RoadLinkInfo): Option[LinkTypeChange] = {
    val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
    val timeStamp = DateTime.now().toString()
    featureClass match {
      case FeatureClass.TractorRoad =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), TractorRoad.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(TractorRoad.value), "mtkClass",None))
      case FeatureClass.HardShoulder =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), HardShoulder.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(HardShoulder.value), "mtkClass",None))
      case FeatureClass.DrivePath | FeatureClass.CarRoad_IIIb =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), SingleCarriageway.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(SingleCarriageway.value), "mtkClass",None))
      case FeatureClass.CycleOrPedestrianPath =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), CycleOrPedestrianPath.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(CycleOrPedestrianPath.value), "mtkClass",None))
      case FeatureClass.SpecialTransportWithoutGate =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), SpecialTransportWithoutGate.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(SpecialTransportWithoutGate.value), "mtkClass",None))
      case FeatureClass.SpecialTransportWithGate =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), SpecialTransportWithGate.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(SpecialTransportWithGate.value), "mtkClass",None))
      case FeatureClass.CarRoad_IIIa =>
        LinkTypeDao.insertValues(newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), SingleCarriageway.value, timeStamp)
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(SingleCarriageway.value), "mtkClass",None))
      case _ => None
    }
  }

  def incompleteLinkIsInUse(incompleteLink: IncompleteLink, roadLinkData: Seq[RoadLink]) = {
    val correspondingRoadLink = roadLinkData.find(_.linkId == incompleteLink.linkId)
    correspondingRoadLink match {
      case Some(roadLink) => roadLink.constructionType == InUse
      case _ => false
    }
  }

  def transferOrGenerateFunctionalClass(changeType: RoadLinkChangeType, optionalOldLink: Option[RoadLinkInfo], newLink: RoadLinkInfo): Option[FunctionalClassChange] = {
    val timeStamp = DateTime.now().toString()
    optionalOldLink match {
      case Some(oldLink) =>
        transferFunctionalClass(changeType, oldLink, newLink, timeStamp) match {
          case Some(functionalClassChange) => Some(functionalClassChange)
          case _ =>
            generateFunctionalClass(changeType, newLink) match {
              case Some(generatedFunctionalClassChange) => Some(generatedFunctionalClassChange)
              case _ => None
            }
        }
      case None =>
        generateFunctionalClass(changeType, newLink) match {
          case Some(generatedFunctionalClass) => Some(generatedFunctionalClass)
          case _ => None
        }
    }
  }

  def transferOrGenerateLinkType(changeType: RoadLinkChangeType, optionalOldLink: Option[RoadLinkInfo], newLink: RoadLinkInfo): Option[LinkTypeChange] = {
    val timeStamp = DateTime.now().toString()
    val alreadyUpdatedLinkType = LinkTypeDao.getExistingValue(newLink.linkId)
    (alreadyUpdatedLinkType, optionalOldLink) match {
      case (Some(_), _) =>
        None
      case (None, Some(oldLink)) =>
        transferLinkType(changeType, oldLink, newLink, timeStamp) match {
          case Some(linkTypeChange) => Some(linkTypeChange)
          case _ =>
            generateLinkType(changeType, newLink) match {
              case Some(linkTypeChange) => Some(linkTypeChange)
              case _ => None
            }
        }
      case (None, None) =>
        generateLinkType(changeType, newLink) match {
          case Some(generatedLinkType) => Some(generatedLinkType)
          case _ => None
        }
    }
  }

  /***
   * Filters out links that need no property update processing due to already having Functional Class or Link type,
   * or having ignorable Feature Class
   * @param newLink
   * @return
   */
  def isProcessableLink(newLink: RoadLinkInfo): Boolean = {
    val alreadyUpdatedFunctionalClass = FunctionalClassDao.getExistingValue(newLink.linkId)
    val alreadyUpdatedLinkType = LinkTypeDao.getExistingValue(newLink.linkId)
    if (alreadyUpdatedFunctionalClass.nonEmpty) {
      logger.info(s"Functional Class already exists for new link ${newLink.linkId}")
    }
    if (alreadyUpdatedLinkType.nonEmpty) {
      logger.info(s"Link Type already exists for new link ${newLink.linkId}")
    }
    val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
    val hasIgnoredFeatureClass = FeatureClass.featureClassesToIgnore.contains(featureClass)

    alreadyUpdatedFunctionalClass.isEmpty && alreadyUpdatedLinkType.isEmpty && !hasIgnoredFeatureClass
  }

  def transferOrGenerateFunctionalClassesAndLinkTypes(changes: Seq[RoadLinkChange]): Seq[ReportedChange] = {
    val incompleteLinks = new ListBuffer[IncompleteLink]()
    val createdProperties = new ListBuffer[Option[ReportedChange]]()
    val iteratedNewLinks = new ListBuffer[RoadLinkInfo]
    val timeStamp = DateTime.now().toString()
    changes.foreach { change =>
      change.changeType match {
        case Replace =>
          val newLink = change.newLinks.head
          val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
          if ((!iteratedNewLinks.exists(_.linkId == newLink.linkId)) && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
            val relatedMerges = changes.filter(change => change.changeType == Replace && change.newLinks.head == newLink)
            val (created, failed) = transferFunctionalClassesAndLinkTypesForSingleReplace(relatedMerges, newLink, timeStamp)
            createdProperties ++= created
            incompleteLinks ++= failed
            iteratedNewLinks += newLink
          }
        case _ =>
          change.newLinks.filter(isProcessableLink).foreach { newLink =>
            if (!iteratedNewLinks.exists(_.linkId == newLink.linkId)) {
              val functionalClassChange = transferOrGenerateFunctionalClass(change.changeType, change.oldLink, newLink)
              val linkTypeChange = transferOrGenerateLinkType(change.changeType, change.oldLink, newLink)
              if (functionalClassChange.isEmpty || linkTypeChange.isEmpty) {
                incompleteLinks += IncompleteLink(newLink.linkId, newLink.municipality.getOrElse(throw new NoSuchElementException(s"${newLink.linkId} does not have municipality code")), newLink.adminClass)
              }
              createdProperties += functionalClassChange
              createdProperties += linkTypeChange
              iteratedNewLinks += newLink
            }
          }
      }
    }
    val roadLinkData = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(incompleteLinks.map(_.linkId).toSet, false)
    val incompleteLinksInUse = incompleteLinks.filter(il => incompleteLinkIsInUse(il, roadLinkData))
    roadLinkService.updateIncompleteLinks(incompleteLinksInUse)
    createdProperties.flatten
  }

  /**
   * Transfers properties from old links to the new link. Each property is transfered exactly once, and if any property is missing from any old link, new link is deemed as incomplete.
   * @param relatedChanges all changes related to the merge
   * @param newLink
   * @param timeStamp
   * @return All created properties and possible incomplete link
   */
  def transferFunctionalClassesAndLinkTypesForSingleReplace(relatedMerges: Seq[RoadLinkChange], newLink: RoadLinkInfo, timeStamp: String): (ListBuffer[Option[ReportedChange]], ListBuffer[IncompleteLink]) = {
    val incompleteLink = new ListBuffer[IncompleteLink]()
    val createdProperties = new ListBuffer[Option[ReportedChange]]()
    val transferedFunctionalClasses = new ListBuffer[FunctionalClassChange]
    val transferedLinkTypes = new ListBuffer[LinkTypeChange]
    relatedMerges.foreach { merge =>
      if (incompleteLink.isEmpty) {
        val functionalClassChange = (transferedFunctionalClasses.isEmpty) match {
          case true => transferFunctionalClass(merge.changeType, merge.oldLink.get, newLink, timeStamp)
          case false => None
        }
        if (functionalClassChange.nonEmpty) {
          transferedFunctionalClasses += functionalClassChange.get
          createdProperties += functionalClassChange
        }
        val linkTypeChange = (transferedLinkTypes.isEmpty) match {
          case true => transferLinkType(merge.changeType, merge.oldLink.get, newLink, timeStamp)
          case false => None
        }
        if (linkTypeChange.nonEmpty) {
          transferedLinkTypes += linkTypeChange.get
          createdProperties += linkTypeChange
        }
        if (transferedFunctionalClasses.isEmpty || transferedLinkTypes.isEmpty) {
          incompleteLink += IncompleteLink(newLink.linkId, newLink.municipality.getOrElse(throw new NoSuchElementException(s"${newLink.linkId} does not have municipality code")), newLink.adminClass)
        }
      }
    }
    (createdProperties, incompleteLink)
  }

  def transferOverriddenPropertiesAndPrivateRoadInfo(changes: Seq[RoadLinkChange]): Seq[ReportedChange] = {
    def applyDigitizationChange(digitizationChange: Boolean, trafficDirectionValue: Int) = {
      digitizationChange match {
        case true =>
          TrafficDirection(trafficDirectionValue) match {
            case TowardsDigitizing => AgainstDigitizing.value
            case AgainstDigitizing => TowardsDigitizing.value
            case _ => trafficDirectionValue
          }
        case false =>
          trafficDirectionValue
      }
    }

    val transferredProperties = ListBuffer[ReportedChange]()
    changes.foreach { change =>
      val oldLink = change.oldLink.get
      val versionChange = oldLink.linkId.substring(0, 36) == change.newLinks.head.linkId.substring(0, 36)
      if (versionChange) {
        val optionalOverriddenTrafficDirection = TrafficDirectionDao.getExistingValue(oldLink.linkId)
        optionalOverriddenTrafficDirection match {
          case Some(overriddenTrafficDirection) =>
            change.newLinks.foreach { newLink =>
              val alreadyUpdatedValue = TrafficDirectionDao.getExistingValue(newLink.linkId)
              val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
              val digitizationChange = change.replaceInfo.find(_.newLinkId.get == newLink.linkId).get.digitizationChange
              val trafficDirectionWithDigitizationCheck = applyDigitizationChange(digitizationChange, overriddenTrafficDirection)
              if (trafficDirectionWithDigitizationCheck != newLink.trafficDirection.value && alreadyUpdatedValue.isEmpty && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
                RoadLinkOverrideDAO.insert(TrafficDirectionString, newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), trafficDirectionWithDigitizationCheck)
                transferredProperties += TrafficDirectionChange(newLink.linkId, roadLinkChangeToChangeType(change.changeType), overriddenTrafficDirection, Some(trafficDirectionWithDigitizationCheck),Some(oldLink.linkId))
              }
            }
          case _ => //do nothing
        }

        val optionalOverriddenAdminClass = AdministrativeClassDao.getExistingValue(oldLink.linkId)
        optionalOverriddenAdminClass match {
          case Some(overriddenAdminClass) =>
            change.newLinks.foreach { newLink =>
              val alreadyUpdatedValue = AdministrativeClassDao.getExistingValue(newLink.linkId)
              val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
              if (overriddenAdminClass != newLink.adminClass.value && alreadyUpdatedValue.isEmpty && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
                RoadLinkOverrideDAO.insert(AdministrativeClass, newLink.linkId, Some(AutoGeneratedUsername.automaticGeneration), overriddenAdminClass)
                transferredProperties += AdministrativeClassChange(newLink.linkId, roadLinkChangeToChangeType(change.changeType), overriddenAdminClass, Some(overriddenAdminClass),Some(oldLink.linkId))
              }
            }
          case _ => //do nothing
        }
      }

      val roadLinkAttributes = LinkAttributesDao.getExistingValues(oldLink.linkId)
      if (roadLinkAttributes.nonEmpty) {
        change.newLinks.foreach { newLink =>
          val alreadyUpdatedValues = LinkAttributesDao.getExistingValues(newLink.linkId)
          val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
          if (alreadyUpdatedValues.isEmpty && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
            roadLinkAttributes.foreach { attribute =>
              LinkAttributesDao.insertAttributeValueByChanges(newLink.linkId, AutoGeneratedUsername.automaticGeneration, attribute._1, attribute._2, LinearAssetUtils.createTimeStamp())
            }
            transferredProperties += RoadLinkAttributeChange(newLink.linkId, roadLinkChangeToChangeType(change.changeType), roadLinkAttributes, roadLinkAttributes,Some(oldLink.linkId))
          }
        }
      }
    }
    transferredProperties
  }

  def updateProperties(): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession( Queries.getLatestSuccessfulSamuutus(RoadLinkProperties.typeId) )
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)

    changeSets.foreach(changeSet => {withDynTransaction {
      logger.info(s"Started processing change set ${changeSet.key}")
      val changeReport = runProcess(changeSet.changes)
      val (reportBody, contentRowCount) = ChangeReporter.generateCSV(changeReport)
      ChangeReporter.saveReportToS3("roadLinkProperties", changeSet.targetDate, reportBody, contentRowCount)
      Queries.updateLatestSuccessfulSamuutus(RoadLinkProperties.typeId, changeSet.targetDate)
    }
    
    })
  }
  def runProcess(changes:  Seq[RoadLinkChange]) = {
    val (addChanges, remaining) = changes.partition(_.changeType == Add)
    val (removeChanges, otherChanges) = remaining.partition(_.changeType == Remove)

    val removeReports = createReportsForPropertiesToBeDeleted(removeChanges).distinct
    val transferredProperties = transferOverriddenPropertiesAndPrivateRoadInfo(otherChanges).distinct
    val createdProperties = transferOrGenerateFunctionalClassesAndLinkTypes(addChanges ++ otherChanges).distinct

    val oldLinkList = ListBuffer[RoadLinkInfo]()
    val newLinkList = ListBuffer[RoadLinkInfo]()
    (addChanges ++ otherChanges).foreach(a => {newLinkList ++= a.newLinks; oldLinkList ++= a.oldLink.toSeq})

    val list = transferredProperties ++ createdProperties ++ removeReports
    val constructionTypeChanges = list.map {
      case AdministrativeClassChange(newLinkId, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkList,newLinkList, newLinkId, oldLinkId)
      case TrafficDirectionChange(newLinkId, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkList,newLinkList, newLinkId, oldLinkId)
      case RoadLinkAttributeChange(newLinkId, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkList,newLinkList, newLinkId, oldLinkId)
      case FunctionalClassChange(newLinkId, _, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkList,newLinkList, newLinkId, oldLinkId)
      case LinkTypeChange(newLinkId, _, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkList,newLinkList, newLinkId, oldLinkId)
      case _ => None
    }.filter(_.isDefined).map(_.get).distinct
   ChangeReport(RoadLinkProperties.typeId, transferredProperties ++ createdProperties ++ removeReports ++ constructionTypeChanges)
  }
  private def addConstructionTypeChange(oldLinkList: ListBuffer[RoadLinkInfo], newLinkList:ListBuffer[RoadLinkInfo], newLinkId: String, oldLinkId: Option[String]): Option[ConstructionTypeChange] = {
    val oldLink = oldLinkList.find(_.linkId == oldLinkId.getOrElse(""))
    val newLink = newLinkList.find(_.linkId == newLinkId)
    val lifeCycleStatusOld = if (oldLink.nonEmpty) Some(oldLink.get.lifeCycleStatus) else None
    val lifeCycleStatusNew = if (newLink.nonEmpty) Some(newLink.get.lifeCycleStatus) else None
    Some(ConstructionTypeChange(newLinkId, ChangeTypeReport.Dummy, lifeCycleStatusOld, lifeCycleStatusNew))
  }
  /**
    *  Create ReportedChange objects for removed road link properties.
    *  Expired road links and their properties are only deleted later after all samuutus-batches have completed
    *  and the expired links have no assets left on them
    *
    * @param removeChanges RoadLinkChanges with changeType Remove
    * @return created ReportedChange objects for report
    */
  def createReportsForPropertiesToBeDeleted(removeChanges: Seq[RoadLinkChange]): Seq[ReportedChange] = {
    val groupedChanges = removeChanges.groupBy(_.oldLink.get.linkId)
    val oldLinkIds = removeChanges.map(_.oldLink.get.linkId)
    val deletedTrafficDirections = TrafficDirectionDao.getExistingValues(oldLinkIds).map(deletion =>
      TrafficDirectionChange(deletion.linkId, roadLinkChangeToChangeType(groupedChanges(deletion.linkId).head.changeType), deletion.value.get, None,linkIdOld=Some(deletion.linkId)))
    val deletedAdministrativeClasses = AdministrativeClassDao.getExistingValues(oldLinkIds).map(deletion =>
      AdministrativeClassChange(deletion.linkId, roadLinkChangeToChangeType(groupedChanges(deletion.linkId).head.changeType), deletion.value.get, None,linkIdOld=Some(deletion.linkId)))
    val deletedFunctionalClasses = FunctionalClassDao.getExistingValues(oldLinkIds).map(deletion =>
      FunctionalClassChange(deletion.linkId, roadLinkChangeToChangeType(groupedChanges(deletion.linkId).head.changeType), deletion.value, None,linkIdOld=Some(deletion.linkId)))
    val deletedLinkTypes = LinkTypeDao.getExistingValues(oldLinkIds).map(deletion =>
      LinkTypeChange(deletion.linkId, roadLinkChangeToChangeType(groupedChanges(deletion.linkId).head.changeType), deletion.value, None,linkIdOld= Some(deletion.linkId)))
    val deletedAttributes = oldLinkIds.map(linkId => {
      val oldAttributes = LinkAttributesDao.getExistingValues(linkId)
      RoadLinkAttributeChange(linkId, roadLinkChangeToChangeType(groupedChanges(linkId).head.changeType), oldAttributes, Map(),linkIdOld=Some(linkId))
    })
    val deletedProperties: Seq[ReportedChange] = deletedTrafficDirections ++ deletedAdministrativeClasses ++ deletedFunctionalClasses ++ deletedLinkTypes ++ deletedAttributes

    deletedProperties
  }

}
