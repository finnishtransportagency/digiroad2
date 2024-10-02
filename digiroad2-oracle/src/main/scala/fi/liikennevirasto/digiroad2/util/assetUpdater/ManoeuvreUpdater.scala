package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreUpdateLinks
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Manoeuvre, ManoeuvreService}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.Seq

class ManoeuvreUpdater() {
  def eventBus: DigiroadEventBus = new DummyEventBus
  def roadLinkClient: RoadLinkClient = new RoadLinkClient()
  def roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus)
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def service = new ManoeuvreService(new RoadLinkService(roadLinkClient, eventBus),eventBus)
  
  private val roadLinkChangeClient = new RoadLinkChangeClient
  
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  
  def updateLinearAssets(typeId: Int = Manoeuvres.typeId): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(typeId))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)
    logger.info(s"Processing ${changeSets.size}} road link changes set")
    changeSets.foreach(changeSet => {
      logger.info(s"Started processing change set ${changeSet.key}")
      withDynTransaction {

        LogUtils.time(logger, s"Updating manoeuvres finished: ") {
          updateByRoadLinks(typeId, changeSet.changes)
        }
        Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
      }
    })
  }
  def recognizeVersionUpgrade(change:RoadLinkChange ): Boolean = {
    change.changeType == RoadLinkChangeType.Replace && change.replaceInfo.size == 1 && kmtkIdAreSame(change.replaceInfo.head)
  }
  def splitLinkId(linkId: String): (String, Int) = {
    val split = linkId.split(":")
    (split(0), split(1).toInt)
  }
  
  def kmtkIdAreSame(change: ReplaceInfo): Boolean = {
    val oldId = splitLinkId(change.oldLinkId.get)._1
    val newId = splitLinkId(change.newLinkId.get)._1
    oldId == newId 
  }

  def separateVersionUpgradeAndOther(changesAll: Seq[RoadLinkChange]): (Seq[RoadLinkChange], Seq[RoadLinkChange]) = {
    val (replace, other) = changesAll.partition(_.changeType == RoadLinkChangeType.Replace)
    val (versionUpgrade, other2) = replace.partition(recognizeVersionUpgrade)
    (versionUpgrade, other ++ other2)
  }
  
  case class VersionUpgrade(oldId:String, newId:String)
  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]):Seq[Manoeuvre] = {
    
    val (versionUpgrade, other) = separateVersionUpgradeAndOther(changesAll)
    val versionUpgradeIds = versionUpgrade.groupBy(_.oldLink.get.linkId).keySet
    val pairs = versionUpgrade.map(a=> (a.oldLink.get.linkId, a.newLinks.map(_.linkId).distinct)).filter(a=>{a._2.size==1}).map(a=>{VersionUpgrade(a._1,a._2.head)})
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(versionUpgradeIds, newTransaction = false)
    logger.info(s"Processing assets: ${typeId}, assets count: ${existingAssets.size}, number of version upgrade in the sets: ${versionUpgrade.size}")
    
    val forLogging = existingAssets.filter(a=> versionUpgradeIds.contains(a.linkId) || versionUpgradeIds.contains(a.destLinkId))
    LogUtils.time(logger, s"Updating manoeuvres into new version of link took: ") {
      service.updateManoeuvreLinkVersions(pairs.map(a=>ManoeuvreUpdateLinks(a.oldId,a.newId)),newTransaction = false)
    }
    
   val manoeuvresOnChangedLinks =  service.getByRoadLinkIdsNoValidation(other.map(_.oldLink.map(_.linkId)).filter(_.isDefined).map(_.get).toSet, newTransaction = false)

    logger.info(s"Number of manoeuvre ${forLogging.size} which has been updated automatically to new version.")
    logger.info(s"Assets: ${forLogging.map(_.id).mkString(",")}")
    logger.info(s"Number of manoeuvre ${manoeuvresOnChangedLinks.size} which need manual adjustments.")
    LogUtils.time(logger, s"Inserting into worklist took: ") {
      service.insertSamuutusChange(manoeuvresOnChangedLinks,newTransaction = false)
    }
   
  }
}
