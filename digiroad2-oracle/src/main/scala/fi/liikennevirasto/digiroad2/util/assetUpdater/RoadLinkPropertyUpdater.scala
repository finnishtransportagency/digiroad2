package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Remove, Replace, Split}
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadLinkAttribute, RoadLinkValue}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{IncompleteLink, RoadLinkService, RoadLinkValueCollection}
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Creation, Deletion, Divided, Replaced}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, KgvUtil, LinearAssetUtils, LogUtils}
import fi.liikennevirasto.digiroad2.DummyEventBus
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.collection.mutable

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

  /**
   * Transfers the functional class from an old link to a new link.
   *
   * @param changeType                The type of change being processed.
   * @param oldLink                   The old link from which the functional class is transferred.
   * @param newLink                   The new link to which the functional class is transferred.
   * @param timestamp                 The timestamp of the transfer operation.
   * @param existingFunctionalClasses A map containing existing functional classes for relevant links.
   * @return An Option containing the FunctionalClassChange, or None if the transfer was not possible.
   */
  private def transferFunctionalClass(changeType: RoadLinkChangeType, oldLink: RoadLinkInfo, newLink: RoadLinkInfo, existingFunctionalClasses: Map[String, Option[Int]]): Option[FunctionalClassChange] = {
      existingFunctionalClasses.get(oldLink.linkId).flatten match {
        case Some(functionalClass) =>
          Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), Some(functionalClass), Some(functionalClass), "oldLink", Some(oldLink.linkId)))
        case _ =>
          logger.info(s"TEST LOG No functional class found from ${existingFunctionalClasses.size} existing functional classes")
          None
      }
  }

  private def generateFunctionalClass(changeType: RoadLinkChangeType, newLink: RoadLinkInfo): Option[FunctionalClassChange] = {
    val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
    featureClass match {
      case FeatureClass.TractorRoad => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(PrimitiveRoad.value), "mtkClass",None))
      case FeatureClass.HardShoulder => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(FunctionalClass9.value), "mtkClass",None))
      case FeatureClass.DrivePath | FeatureClass.CarRoad_IIIb => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(AnotherPrivateRoad.value), "mtkClass",None))
      case FeatureClass.CycleOrPedestrianPath => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(WalkingAndCyclingPath.value), "mtkClass",None))
      case FeatureClass.SpecialTransportWithoutGate => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(UnknownFunctionalClass.value), "mtkClass",None))
      case FeatureClass.SpecialTransportWithGate => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(UnknownFunctionalClass.value), "mtkClass",None))
      case FeatureClass.CarRoad_IIIa => newLink.adminClass match {
        case State => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(FunctionalClass4.value), "mtkClass",None))
        case Municipality | Private => Some(FunctionalClassChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(FunctionalClass5.value), "mtkClass",None))
        case _ =>
          logger.info(s"TEST LOG No functional class generated from feature class ${featureClass}")
          None
      }
      case _ =>
        logger.info(s"TEST LOG No functional class generated for new link ${newLink.linkId} from road class ${newLink.roadClass}")
        None
    }
  }

  /**
   * Transfers the link type from an old link to a new link.
   *
   * @param changeType        The type of change being processed.
   * @param oldLink           The old link from which the link type is transferred.
   * @param newLink           The new link to which the link type is transferred.
   * @param timestamp         The timestamp of the transfer operation.
   * @param existingLinkTypes A map containing existing link types for relevant links.
   * @return An Option containing the LinkTypeChange, or None if the transfer was not possible.
   */
  private def transferLinkType(changeType: RoadLinkChangeType, oldLink: RoadLinkInfo, newLink: RoadLinkInfo, existingLinkTypes: Map[String, Option[Int]]): Option[LinkTypeChange] = {
    existingLinkTypes.get(oldLink.linkId).flatten match {
      case Some(linkType) =>
        Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), Some(linkType), Some(linkType), "oldLink", Some(oldLink.linkId)))
      case _ =>
        None
    }
  }

  /**
   * Generates a new link type for a new link based on its feature class.
   *
   * @param changeType The type of change being processed.
   * @param newLink    The new link for which the link type is to be generated.
   * @return An Option containing the LinkTypeChange, or None if no valid feature class is found.
   */
  private def generateLinkType(changeType: RoadLinkChangeType, newLink: RoadLinkInfo): Option[LinkTypeChange] = {
    val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)

    val newLinkType = featureClass match {
      case FeatureClass.TractorRoad => TractorRoad.value
      case FeatureClass.HardShoulder => HardShoulder.value
      case FeatureClass.DrivePath | FeatureClass.CarRoad_IIIb => SingleCarriageway.value
      case FeatureClass.CycleOrPedestrianPath => CycleOrPedestrianPath.value
      case FeatureClass.SpecialTransportWithoutGate => SpecialTransportWithoutGate.value
      case FeatureClass.SpecialTransportWithGate => SpecialTransportWithGate.value
      case FeatureClass.CarRoad_IIIa => SingleCarriageway.value
      case _ =>
        logger.info(s"TEST LOG No link type generated for new link ${newLink.linkId} with road class ${newLink.roadClass}")
        return None
    }

    Some(LinkTypeChange(newLink.linkId, roadLinkChangeToChangeType(changeType), None, Some(newLinkType), "mtkClass", None))
  }

  def incompleteLinkIsInUse(incompleteLink: IncompleteLink, roadLinkData: Seq[RoadLink]) = {
    val correspondingRoadLink = roadLinkData.find(_.linkId == incompleteLink.linkId)
    correspondingRoadLink match {
      case Some(roadLink) => roadLink.constructionType == InUse
      case _ => false
    }
  }

  /**
   * Transfers or generates a functional class for a new link depending on the existence of old and new link functional classes.
   *
   * @param changeType                The type of change being processed.
   * @param optionalOldLink           An optional old link from which the functional class might be transferred.
   * @param newLink                   The new link for which the functional class is to be set.
   * @param existingFunctionalClasses A map containing existing functional classes for relevant links.
   * @return An Option containing the FunctionalClassChange, or None if no change is made.
   */
  private def transferOrGenerateFunctionalClass(changeType: RoadLinkChangeType, optionalOldLink: Option[RoadLinkInfo], newLink: RoadLinkInfo, existingFunctionalClasses: Map[String, Option[Int]]): Option[FunctionalClassChange] = {
    optionalOldLink match {
      case Some(oldLink) =>
        transferFunctionalClass(changeType, oldLink, newLink, existingFunctionalClasses) match {
          case Some(functionalClassChange) => Some(functionalClassChange)
          case _ =>
            generateFunctionalClass(changeType, newLink) match {
              case Some(functionalClassChange) => Some(functionalClassChange)
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

  /**
   * Transfers or generates a link type for a new link depending on the existence of old and new link types.
   *
   * @param changeType        The type of change being processed.
   * @param optionalOldLink   An optional old link from which the link type might be transferred.
   * @param newLink           The new link for which the link type is to be set.
   * @param existingLinkTypes A map containing existing link types for relevant links.
   * @return An Option containing the LinkTypeChange, or None if no change is made.
   */
  private def transferOrGenerateLinkType(changeType: RoadLinkChangeType, optionalOldLink: Option[RoadLinkInfo], newLink: RoadLinkInfo, existingLinkTypes: Map[String, Option[Int]]): Option[LinkTypeChange] = {
    optionalOldLink match {
      case (Some(oldLink)) =>
        transferLinkType(changeType, oldLink, newLink, existingLinkTypes) match {
          case Some(linkTypeChange) => Some(linkTypeChange)
          case _ =>
            generateLinkType(changeType, newLink) match {
              case Some(linkTypeChange) => Some(linkTypeChange)
              case _ => None
            }
        }
      case None =>
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
  def isProcessableLink(newLink: RoadLinkInfo, functionalClassMap: Map[String, Option[Int]], linkTypeMap: Map[String, Option[Int]]): Boolean = {
    val hasFunctionalClass = functionalClassMap.contains(newLink.linkId)
    val hasLinkType = linkTypeMap.contains(newLink.linkId)

    if (hasFunctionalClass) {
      logger.info(s"Functional Class already exists for new link ${newLink.linkId}")
    }
    if (hasLinkType) {
      logger.info(s"Link Type already exists for new link ${newLink.linkId}")
    }

    val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
    val hasIgnoredFeatureClass = FeatureClass.featureClassesToIgnore.contains(featureClass)

    !hasFunctionalClass && !hasLinkType && !hasIgnoredFeatureClass
  }

  /**
   * Transfers or generates functional classes and link types for a given set of road link changes.
   *
   * Processes a sequence of road link changes and updates the functional classes and link types based on the changes.
   * Replace changes are partitioned from other changes and processed separately.
   * During Replace, any new link inherits or generates properties only once and is ignored afterwards.
   * Any incomplete links or reported changes are collected and returned.
   *
   * @param changes A sequence of road link changes to process.
   * @param existingFunctionalClasses A sequence of existing functional class values per road link.
   * @param existingLinkTypes A sequence of existing link type values per road link.
   * @return A tuple containing:
   *         - A sequence of reported changes that were successfully created.
   *         - A sequence of incomplete links that could not be fully processed.
   */
  def transferOrGenerateFunctionalClassesAndLinkTypes(changes: Seq[RoadLinkChange],
                                                      existingFunctionalClasses: Seq[RoadLinkValue],
                                                      existingLinkTypes: Seq[RoadLinkValue]): (Seq[ReportedChange], Seq[IncompleteLink]) = {
    LogUtils.time(logger, s"TEST LOG RoadLinkPropertyUpdater transferOrGenerateFunctionalClassesAndLinkTypes with ${changes.size} changes", startLogging = true) {
      val iteratedNewLinks = mutable.Set[String]()
      val incompleteLinks = ListBuffer[IncompleteLink]()
      val createdProperties = ListBuffer[ReportedChange]()

      val functionalClassMap = existingFunctionalClasses.map(fc => fc.linkId -> fc.value).toMap
      val linkTypeMap = existingLinkTypes.map(lt => lt.linkId -> lt.value).toMap
      val (replaceChanges, otherChanges) = changes.partition(_.changeType == Replace)
      val replaceGrouped = replaceChanges.groupBy(_.newLinks.head)

      replaceGrouped.foreach { case (newLink, relatedMerges) =>
        val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
        LogUtils.time(logger, s"TEST LOG transferOrGenerateFunctionalClassesAndLinkTypes, process single Replace change", startLogging = true) {
          if (!iteratedNewLinks.contains(newLink.linkId) && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
            val (created, failed) = transferFunctionalClassAndLinkTypeForSingleReplace(relatedMerges, newLink, DateTime.now().toString, functionalClassMap, linkTypeMap)
            createdProperties ++= created
            incompleteLinks ++= failed
            iteratedNewLinks += newLink.linkId
          }
        }
      }
      otherChanges.foreach { change =>
        LogUtils.time(logger, s"TEST LOG transferOrGenerateFunctionalClassesAndLinkTypes, process single ${change.changeType} change", startLogging = true) {
          val processableNewLinks = change.newLinks.filter(newLink =>
            isProcessableLink(newLink, functionalClassMap, linkTypeMap) && !iteratedNewLinks.contains(newLink.linkId)
          )
          processableNewLinks.foreach { newLink =>
            val functionalClassChange = LogUtils.time(logger, s"TEST LOG transferOrGenerateFunctionalClass for single ${change.changeType}", startLogging = true) {
              transferOrGenerateFunctionalClass(change.changeType, change.oldLink, newLink, functionalClassMap)
            }
            val linkTypeChange = LogUtils.time(logger, s"TEST LOG transferOrGenerateLinkType for single ${change.changeType}", startLogging = true) {
              transferOrGenerateLinkType(change.changeType, change.oldLink, newLink, linkTypeMap)
            }
            if (functionalClassChange.isEmpty || linkTypeChange.isEmpty) {
              incompleteLinks += IncompleteLink(newLink.linkId, newLink.municipality.getOrElse(throw new NoSuchElementException(s"${newLink.linkId} does not have municipality code")), newLink.adminClass)
            }
            createdProperties ++= functionalClassChange
            createdProperties ++= linkTypeChange
            iteratedNewLinks += newLink.linkId
          }
        }
      }
      logger.info(s"TEST LOG generated ${incompleteLinks.size} incomplete links")
      (createdProperties.distinct, incompleteLinks)
    }
  }

  /**
   * Transfers properties from old links to the new link for a given set of related road link changes.
   * Each property is transferred exactly once, and if any property is missing from any old link, the new link is deemed as incomplete.
   *
   * @param relatedMerges All changes related to the merge.
   * @param newLink The new link to which properties are transferred.
   * @param timestamp The timestamp of the transfer operation.
   * @param existingFunctionalClasses A map containing existing functional classes for relevant links.
   * @param existingLinkTypes A map containing existing link types for relevant links.
   * @return A tuple (List of created properties, List of incomplete links).
   */
  private def transferFunctionalClassAndLinkTypeForSingleReplace(relatedMerges: Seq[RoadLinkChange],
                                                                 newLink: RoadLinkInfo,
                                                                 timestamp: String,
                                                                 existingFunctionalClasses: Map[String, Option[Int]],
                                                                 existingLinkTypes: Map[String, Option[Int]]
                                                                ): (ListBuffer[ReportedChange], ListBuffer[IncompleteLink]) = {
    LogUtils.time(logger, "TEST LOG transferFunctionalClassAndLinkTypeForSingleReplace", startLogging = true) {
      val incompleteLink = ListBuffer[IncompleteLink]()
      val createdProperties = ListBuffer[ReportedChange]()
      var functionalClassTransferred = false
      var linkTypeTransferred = false

      relatedMerges.foreach { merge =>
        if (incompleteLink.isEmpty) {
          if (!functionalClassTransferred) {
            transferFunctionalClass(merge.changeType, merge.oldLink.get, newLink, existingFunctionalClasses).foreach { functionalClassChange =>
              createdProperties += functionalClassChange
              functionalClassTransferred = true
            }
          }

          if (!linkTypeTransferred) {
            transferLinkType(merge.changeType, merge.oldLink.get, newLink, existingLinkTypes).foreach { linkTypeChange =>
              createdProperties += linkTypeChange
              linkTypeTransferred = true
            }
          }

          if (!functionalClassTransferred || !linkTypeTransferred) {
            incompleteLink += IncompleteLink(newLink.linkId, newLink.municipality.getOrElse(throw new NoSuchElementException(s"${newLink.linkId} does not have municipality code")), newLink.adminClass)
          }
        }
      }

      (createdProperties, incompleteLink)
    }
  }

  def transferOverriddenPropertiesAndPrivateRoadInfo(
                                                      changes: Seq[RoadLinkChange],
                                                      existingTrafficDirections: Seq[RoadLinkValue],
                                                      existingAdministrativeClasses: Seq[RoadLinkValue],
                                                      existingLinkAttributes: Seq[RoadLinkAttribute]
                                                    ): Seq[ReportedChange] = {
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

    LogUtils.time(logger, s"TEST LOG RoadLinkPropertyUpdater transferOverriddenPropertiesAndPrivateRoadInfo with ${changes.size} changes") {
      val transferredProperties = ListBuffer[ReportedChange]()
      val processedNewLinksTraffic = mutable.Set[String]()
      val processedNewLinksAdmin = mutable.Set[String]()
      val processedNewLinksAttributes = mutable.Set[String]()

      val trafficDirectionMap = existingTrafficDirections.map(direction => direction.linkId -> direction.value).toMap
      val adminClassMap = existingAdministrativeClasses.map(adminClass => adminClass.linkId -> adminClass.value).toMap
      val linkAttributeMap = existingLinkAttributes.map(linkAttribute => linkAttribute.linkId -> (linkAttribute.attributes)).toMap
      changes.foreach { change =>
        LogUtils.time(logger, s"TEST LOG transferOverriddenPropertiesAndPrivateRoadInfo, processing single ${change.changeType} change", startLogging = true) {
          val oldLink = change.oldLink.get
          val versionChange = oldLink.linkId.substring(0, 36) == change.newLinks.head.linkId.substring(0, 36)
          if (versionChange) {
            val overriddenTrafficDirection = trafficDirectionMap.getOrElse(oldLink.linkId, None)
            overriddenTrafficDirection.foreach { direction =>
              change.newLinks.foreach { newLink =>
                if (!processedNewLinksTraffic.contains(newLink.linkId)) {
                  val alreadyUpdatedValue = trafficDirectionMap.get(newLink.linkId).flatten
                  val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
                  val digitizationChange = change.replaceInfo.find(_.newLinkId.get == newLink.linkId).get.digitizationChange
                  val trafficDirectionWithDigitizationCheck = applyDigitizationChange(digitizationChange, direction)
                  if (trafficDirectionWithDigitizationCheck != newLink.trafficDirection.value && alreadyUpdatedValue.isEmpty && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
                    transferredProperties += TrafficDirectionChange(newLink.linkId, roadLinkChangeToChangeType(change.changeType), direction, Some(trafficDirectionWithDigitizationCheck), Some(oldLink.linkId))
                    processedNewLinksTraffic += newLink.linkId
                  }
                }
              }
            }

            val overriddenAdminClass = adminClassMap.getOrElse(oldLink.linkId, None)
            overriddenAdminClass.foreach { adminClass =>
              change.newLinks.foreach { newLink =>
                if (!processedNewLinksAdmin.contains(newLink.linkId)) {
                  val alreadyUpdatedValue = adminClassMap.get(newLink.linkId).flatten
                  val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
                  if (adminClass != newLink.adminClass.value && alreadyUpdatedValue.isEmpty && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
                    transferredProperties += AdministrativeClassChange(newLink.linkId, roadLinkChangeToChangeType(change.changeType), adminClass, Some(adminClass), Some(oldLink.linkId))
                    processedNewLinksAdmin += newLink.linkId
                  }
                }
              }
            }
          }

          val roadLinkAttributes = linkAttributeMap.getOrElse(oldLink.linkId, Map())
          if (roadLinkAttributes.nonEmpty) {
            change.newLinks.foreach { newLink =>
              if (!processedNewLinksAttributes.contains(newLink.linkId)) {
                val alreadyUpdatedValues = linkAttributeMap.getOrElse(newLink.linkId, Map())
                val featureClass = KgvUtil.extractFeatureClass(newLink.roadClass)
                if (alreadyUpdatedValues.isEmpty && !FeatureClass.featureClassesToIgnore.contains(featureClass)) {
                  transferredProperties += RoadLinkAttributeChange(newLink.linkId, roadLinkChangeToChangeType(change.changeType), roadLinkAttributes, roadLinkAttributes, Some(oldLink.linkId))
                  processedNewLinksAttributes += newLink.linkId
                }
              }
            }
          }
        }
      }
      transferredProperties.distinct
    }
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

  def performIncompleteLinkUpdate(incompleteLinks: Seq[IncompleteLink]) = {
    incompleteLinks.take(25).foreach(link => logger.info(s"TEST LOG sample incomplete link id: ${link.linkId}"))
    val roadLinkData = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(incompleteLinks.map(_.linkId).toSet, false)
    val incompleteLinksInUse = incompleteLinks.filter(il => incompleteLinkIsInUse(il, roadLinkData))
    logger.info(s"TEST LOG Total of ${incompleteLinks.size} incomplete links generated in process, " +
      s"of which ${incompleteLinksInUse.size} are in use. " +
      s"Total number of fetched roadLinks is ${roadLinkData.size}")
    roadLinkService.updateIncompleteLinks(incompleteLinksInUse)
  }

  def runProcess(changes: Seq[RoadLinkChange]): ChangeReport = {
    LogUtils.time(logger, s"TEST LOG RoadLinkPropertyUpdater runProcess with ${changes.size} changes", startLogging = true) {
      val (addChanges, removeChanges, otherChanges) = partitionChanges(changes)
      val allLinkIds = getAllLinkIds(changes).toSet

      val valueCollection = roadLinkService.getRoadLinkValuesMass(allLinkIds)

      val removeReports = createReportsForPropertiesToBeDeleted(removeChanges, valueCollection)
      val transferredProperties = transferOverriddenPropertiesAndPrivateRoadInfo(otherChanges, valueCollection.trafficDirectionValues, valueCollection.adminClassValues, valueCollection.linkAttributeValues)
      val (createdProperties, incompleteLinks) = transferOrGenerateFunctionalClassesAndLinkTypes(addChanges ++ otherChanges, valueCollection.functionalClassValues, valueCollection.linkTypeValues)

      val (oldLinkList, newLinkList) = compileOldAndNewLinkLists(addChanges ++ otherChanges)

      val functionalClassChanges = LogUtils.time(logger, s"TEST LOG accumulateFunctionalClassChanges for ${createdProperties.size} created properties", startLogging = true) {
        accumulateFunctionalClassChanges(createdProperties)
      }
      val linkTypeChanges = accumulateLinkTypeChanges(createdProperties)
      val trafficDirectionChanges = LogUtils.time(logger, s"TEST LOG accumulateTrafficDirectionChanges for ${transferredProperties.size} transferred properties", startLogging = true) {
        accumulateTrafficDirectionChanges(transferredProperties)
      }
      val administrativeClassChanges = accumulateAdministrativeClassChanges(transferredProperties)
      val linkAttributeChanges = LogUtils.time(logger, s"TEST LOG accumulateLinkAttributeChanges for ${transferredProperties.size} transferred properties", startLogging = true) {
        accumulateLinkAttributeChanges(transferredProperties)
      }

      val updatedValueCollection = RoadLinkValueCollection(
        functionalClassValues = functionalClassChanges,
        linkTypeValues = linkTypeChanges,
        trafficDirectionValues = trafficDirectionChanges,
        adminClassValues = administrativeClassChanges,
        linkAttributeValues = linkAttributeChanges
      )

      roadLinkService.insertRoadLinkValuesMass(updatedValueCollection)
      performIncompleteLinkUpdate(incompleteLinks)

      val finalChanges = transferredProperties ++ createdProperties ++ removeReports
      val constructionTypeChanges = finalizeConstructionTypeChanges(finalChanges, oldLinkList, newLinkList)

      ChangeReport(RoadLinkProperties.typeId, finalChanges ++ constructionTypeChanges)
    }
  }

  private def partitionChanges(changes: Seq[RoadLinkChange]): (Seq[RoadLinkChange], Seq[RoadLinkChange], Seq[RoadLinkChange]) = {
    val (addChanges, remaining) = changes.partition(_.changeType == Add)
    val (removeChanges, otherChanges) = remaining.partition(_.changeType == Remove)
    (addChanges, removeChanges, otherChanges)
  }

  private def getAllLinkIds(changes: Seq[RoadLinkChange]): Seq[String] = {
    changes.flatMap(change => change.newLinks.map(_.linkId) ++ change.oldLink.map(_.linkId)).toSet.toSeq
  }

  private def compileOldAndNewLinkLists(changes: Seq[RoadLinkChange]): (mutable.HashMap[String, RoadLinkInfo], mutable.HashMap[String, RoadLinkInfo]) = {
    val oldLinkMap = mutable.HashMap[String, RoadLinkInfo]()
    val newLinkMap = mutable.HashMap[String, RoadLinkInfo]()
    changes.foreach { change =>
      change.newLinks.foreach { newLink => newLinkMap += (newLink.linkId -> newLink) }
      change.oldLink.foreach { oldLink => oldLinkMap += (oldLink.linkId -> oldLink) }
    }
    (oldLinkMap, newLinkMap)
  }


  private def accumulateFunctionalClassChanges(createdProperties: Seq[ReportedChange]): Seq[RoadLinkValue] = {
    createdProperties.collect { case f: FunctionalClassChange => RoadLinkValue(f.linkId, f.newValue) }
  }

  private def accumulateLinkTypeChanges(createdProperties: Seq[ReportedChange]): Seq[RoadLinkValue] = {
    createdProperties.collect { case l: LinkTypeChange => RoadLinkValue(l.linkId, l.newValue) }
  }

  private def accumulateTrafficDirectionChanges(transferredProperties: Seq[ReportedChange]): Seq[RoadLinkValue] = {
    transferredProperties.collect { case t: TrafficDirectionChange => RoadLinkValue(t.linkId, t.newValue) }
  }

  private def accumulateAdministrativeClassChanges(transferredProperties: Seq[ReportedChange]): Seq[RoadLinkValue] = {
    transferredProperties.collect { case a: AdministrativeClassChange => RoadLinkValue(a.linkId, a.newValue) }
  }

  private def accumulateLinkAttributeChanges(transferredProperties: Seq[ReportedChange]): Seq[RoadLinkAttribute] = {
    transferredProperties.collect {
      case r: RoadLinkAttributeChange =>
        RoadLinkAttribute(r.linkId, r.newValues.map { case (key, value) => key -> value })
    }
  }

  private def finalizeConstructionTypeChanges(changes: Seq[ReportedChange], oldLinkMap: mutable.HashMap[String, RoadLinkInfo], newLinkMap: mutable.HashMap[String, RoadLinkInfo]): Seq[ConstructionTypeChange] = {
    LogUtils.time(logger, s"TEST LOG RoadLinkPropertyUpdater finalizeConstructionTypeChanges with " +
      s"${changes.size} changes and " +
      s"${oldLinkMap.size} old links and " +
      s"${newLinkMap.size} new links",
      startLogging = true) {
      changes.collect {
        case AdministrativeClassChange(newLinkId, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkMap, newLinkMap, newLinkId, oldLinkId)
        case TrafficDirectionChange(newLinkId, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkMap, newLinkMap, newLinkId, oldLinkId)
        case RoadLinkAttributeChange(newLinkId, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkMap, newLinkMap, newLinkId, oldLinkId)
        case FunctionalClassChange(newLinkId, _, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkMap, newLinkMap, newLinkId, oldLinkId)
        case LinkTypeChange(newLinkId, _, _, _, _, oldLinkId) => addConstructionTypeChange(oldLinkMap, newLinkMap, newLinkId, oldLinkId)
      }.flatten.distinct
    }
  }

  private def addConstructionTypeChange(oldLinkMap: mutable.HashMap[String, RoadLinkInfo], newLinkMap: mutable.HashMap[String, RoadLinkInfo], newLinkId: String, oldLinkId: Option[String]): Option[ConstructionTypeChange] = {
    LogUtils.time(logger,"TEST LOG addConstructionTypeChange") {
      val oldLink = oldLinkId.flatMap(oldLinkMap.get)
      val newLink = newLinkMap.get(newLinkId)
      val lifeCycleStatusOld = oldLink.map(_.lifeCycleStatus)
      val lifeCycleStatusNew = newLink.map(_.lifeCycleStatus)
      Some(ConstructionTypeChange(newLinkId, ChangeTypeReport.Dummy, lifeCycleStatusOld, lifeCycleStatusNew))
    }
  }

  /**
   *  Create ReportedChange objects for removed road link properties.
   *  Expired road links and their properties are only deleted later after all samuutus-batches have completed
   *  and the expired links have no assets left on them
   *
   * @param removeChanges  A sequence of RoadLinkChange objects representing the changes to be removed.
   * @param roadLinkValues A RoadLinkValueCollection containing the existing values of various road link properties.
   * @return A sequence of ReportedChange objects representing the properties that were deleted.
   */
  def createReportsForPropertiesToBeDeleted(
                                             removeChanges: Seq[RoadLinkChange],
                                             roadLinkValues: RoadLinkValueCollection
                                           ): Seq[ReportedChange] = {
    LogUtils.time(logger, s"TEST LOG RoadLinkPropertyUpdater createReportsForPropertiesToBeDeleted with ${removeChanges.size} changes", startLogging = true) {
      val groupedChanges = removeChanges.groupBy(_.oldLink.get.linkId)
      val oldLinkIds = groupedChanges.keys.toSet

      val deletedTrafficDirections: Seq[ReportedChange] = roadLinkValues.trafficDirectionValues
        .filter { case RoadLinkValue(linkId, value) => oldLinkIds.contains(linkId) && value.isDefined }
        .map { case RoadLinkValue(linkId, Some(value)) =>
          TrafficDirectionChange(linkId, roadLinkChangeToChangeType(groupedChanges(linkId).head.changeType), value, None, linkIdOld = Some(linkId))
        }

      val deletedAdministrativeClasses: Seq[ReportedChange] = roadLinkValues.adminClassValues
        .filter { case RoadLinkValue(linkId, value) => oldLinkIds.contains(linkId) && value.isDefined }
        .map { case RoadLinkValue(linkId, Some(value)) =>
          AdministrativeClassChange(linkId, roadLinkChangeToChangeType(groupedChanges(linkId).head.changeType), value, None, linkIdOld = Some(linkId))
        }

      val deletedFunctionalClasses: Seq[ReportedChange] = roadLinkValues.functionalClassValues
        .filter { case RoadLinkValue(linkId, value) => oldLinkIds.contains(linkId) && value.isDefined }
        .map { case RoadLinkValue(linkId, Some(value)) =>
          FunctionalClassChange(linkId, roadLinkChangeToChangeType(groupedChanges(linkId).head.changeType), Some(value), None, linkIdOld = Some(linkId))
        }

      val deletedLinkTypes: Seq[ReportedChange] = roadLinkValues.linkTypeValues
        .filter { case RoadLinkValue(linkId, value) => oldLinkIds.contains(linkId) && value.isDefined }
        .map { case RoadLinkValue(linkId, Some(value)) =>
          LinkTypeChange(linkId, roadLinkChangeToChangeType(groupedChanges(linkId).head.changeType), Some(value), None, linkIdOld = Some(linkId))
        }

      val deletedAttributes: Seq[ReportedChange] = roadLinkValues.linkAttributeValues
        .filter { case RoadLinkAttribute(linkId, _) => oldLinkIds.contains(linkId) }
        .map { case RoadLinkAttribute(linkId, oldAttributes) =>
          RoadLinkAttributeChange(linkId, roadLinkChangeToChangeType(groupedChanges(linkId).head.changeType), oldAttributes, Map(), linkIdOld = Some(linkId))
        }

      (deletedTrafficDirections ++ deletedAdministrativeClasses ++ deletedFunctionalClasses ++ deletedLinkTypes ++ deletedAttributes).distinct
    }
  }


}
