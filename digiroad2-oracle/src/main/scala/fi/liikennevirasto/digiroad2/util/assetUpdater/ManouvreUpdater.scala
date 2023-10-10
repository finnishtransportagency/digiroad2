package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.{ManoeuvreUpdateLinks, PersistedManoeuvreRow}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ChangedManoeuvre, ManoeuvreService}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.collection.{Seq, mutable}

class ManouvreUpdater() {
  def eventBus: DigiroadEventBus = new DummyEventBus
  def roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  def roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def service = new ManoeuvreService(new RoadLinkService(roadLinkClient, eventBus, new DummySerializer),eventBus)
  
  private val roadLinkChangeClient = new RoadLinkChangeClient
  
  val logger: Logger = LoggerFactory.getLogger(getClass)
  
  def splitLinkId(linkId: String): (String, Int) = {
    val split = linkId.split(":")
    (split(0), split(1).toInt)
  }

  def updateLinearAssets(typeId: Int = Manoeuvres.typeId): Unit = {
    val latestSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(typeId))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(latestSuccess)
    logger.info(s"Processing ${changeSets.size}} road link changes set")
    changeSets.foreach(changeSet => {
      logger.info(s"Started processing change set ${changeSet.key}")
      withDynTransaction {
        updateByRoadLinks(typeId, changeSet.changes)
        Queries.updateLatestSuccessfulSamuutus(typeId, changeSet.targetDate)
      }
    })
  }
  
  def recognizeVersionUpgrade(change: (RoadLinkChangeType, Seq[RoadLinkChange])): Boolean = {
    change._1 == RoadLinkChangeType.Replace && change._2.size == 1 && recognizeVersionUpgrade(change._2.head)
  }
  def recognizeVersionUpgrade(change: RoadLinkChange): Boolean = {
    val oldId = splitLinkId(change.oldLink.get.linkId)._1
    val newId = splitLinkId(change.newLinks.head.linkId)._1
    if (oldId == newId) {
      true
    } else false
  }

  case class VersionUpgrade(oldId:String, newId:String)
  def updateByRoadLinks(typeId: Int, changesAll: Seq[RoadLinkChange]):Seq[ChangedManoeuvre] = {

    def partition = {
      val (versionUpgrade2, other2) = changesAll.groupBy(_.changeType).partition(recognizeVersionUpgrade)
      (versionUpgrade2.values.flatten.toSeq,other2.values.flatten.toSeq)
    }

    val (versionUpgrade, other) = partition
    val versionUpgradeIds = versionUpgrade.groupBy(_.oldLink.get.linkId).keySet
    val pairs = versionUpgrade.map(a=> (a.oldLink.get.linkId, a.newLinks.map(_.linkId).distinct)).filter(a=>{a._2.size==1}).map(a=>{VersionUpgrade(a._1,a._2.head)})
    val existingAssets = service.fetchExistingAssetsByLinksIdsString(versionUpgradeIds, newTransaction = false)
    logger.info(s"Processing assets: ${typeId}, assets count: ${existingAssets.size}, number of version upgrade in the sets: ${versionUpgrade.size}")
    
    val forLogging = existingAssets.filter(a=> versionUpgradeIds.contains(a.linkId) || versionUpgradeIds.contains(a.destLinkId))
    
    pairs.map(a=>ManoeuvreUpdateLinks(a.oldId,a.newId)).foreach(service.updateManouvreLinkVersion(_,newTransaction = false))
    
   val rows =  service.getByRoadLinkId(other.map(_.oldLink.map(_.linkId)).filter(_.isDefined).map(_.get).toSet, false)
     .map(a=> {
      val (elementA,elementB) = (a.elements.map(_.sourceLinkId),a.elements.map(_.destLinkId))
      ChangedManoeuvre(manoeuvreId = a.id,linkIds=(elementA++elementB).toSet)
    })

    logger.info(s"Number of manoeuvre ${forLogging.size} which has been updated automatically updated to new version.")
    logger.info(s"Assets: ${forLogging.map(_.id).mkString(",")}")
    logger.info(s"Number of manoeuvre ${rows.size} which need manual adjustments.")
    
    service.insertSamuutusChange(rows,false)
    
  }


}
