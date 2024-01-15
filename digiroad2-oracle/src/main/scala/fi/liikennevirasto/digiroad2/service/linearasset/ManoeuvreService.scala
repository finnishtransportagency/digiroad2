package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, Manoeuvres, SideCode}
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.{ManoeuvreDao, ManoeuvreUpdateLinks, PersistedManoeuvreRow}
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriod}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LogUtils, PolygonTools}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignInfo
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import java.security.InvalidParameterException

case class Manoeuvre(id: Long, elements: Seq[ManoeuvreElement], validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], modifiedDateTime: Option[DateTime],
                     modifiedBy: Option[String], additionalInfo: String, createdDateTime: DateTime, createdBy: String, isSuggested: Boolean)
case class ManoeuvreElement(manoeuvreId: Long, sourceLinkId: String, destLinkId: String, elementType: Int)
case class NewManoeuvre(validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], additionalInfo: Option[String], linkIds: Seq[String], trafficSignId: Option[Long], isSuggested: Boolean)
case class ManoeuvreUpdates(validityPeriods: Option[Set[ValidityPeriod]], exceptions: Option[Seq[Int]], additionalInfo: Option[String],  isSuggested: Option[Boolean])
case class MissingElement(message:String) extends NoSuchElementException(message)

sealed trait ManoeuvreTurnRestrictionType {
  def value: Int
}
object ManoeuvreTurnRestrictionType {
  val values = Set(UTurn, LeftTurn, RightTurn, Unknown)

  def apply(intValue: Int): ManoeuvreTurnRestrictionType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object UTurn extends ManoeuvreTurnRestrictionType { def value = 1 }
  case object LeftTurn extends ManoeuvreTurnRestrictionType { def value = 2 }
  case object RightTurn extends ManoeuvreTurnRestrictionType { def value = 3 }
  case object Unknown extends ManoeuvreTurnRestrictionType { def value = 99 }
}

object ElementTypes {
  val FirstElement = 1
  val IntermediateElement = 2
  val LastElement = 3
}
class ManoeuvreCreationException(val response: Set[String]) extends RuntimeException {}



case class ChangedManoeuvre (manoeuvreId:Long,linkIds:Set[String])
case class SamuuutusWorkListItem(assetId:Long,links:String)
class ManoeuvreService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  val logger = LoggerFactory.getLogger(getClass)
  def roadLinkService: RoadLinkService = roadLinkServiceImpl
  def municipalityDao: MunicipalityDao = new MunicipalityDao
  def eventBus: DigiroadEventBus = eventBusImpl
  def polygonTools: PolygonTools = new PolygonTools()
  def assetDao: PostGISAssetDao = new PostGISAssetDao

  def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  
  def dao: ManoeuvreDao = new ManoeuvreDao()
  def inaccurateDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksByMunicipalityUsingCache(municipalityNumber)
    getBySourceRoadLinks(roadLinks)
  }

  def getByMunicipalityAndRoadLinks(municipalityNumber: Int): Seq[(Manoeuvre, Seq[RoadLink])] = {
    val roadLinks = roadLinkService.getRoadLinksByMunicipalityUsingCache(municipalityNumber)
    val manoeuvres = getBySourceRoadLinks(roadLinks)
    manoeuvres.map{ manoeuvre => (manoeuvre, roadLinks.filter(road => road.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).get ||
      road.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).get))
    }
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    val roadLinks = LogUtils.time(logger, "TEST LOG manoeuvres get roadlinks by boundingBox") {
      roadLinkService.getRoadLinksByBoundsAndMunicipalities(bounds,asyncMode = false)
    }
    LogUtils.time(logger, "TEST LOG manoeuvres get assets by roadlinks, roadlink count: " + roadLinks.size) {
      getByRoadLinks(roadLinks)
    }
  }

  def updateManoeuvre(userName: String, oldManoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates, modifiedDate: Option[DateTime]): Long = {
     withDynTransaction {
      val manoeuvreRowOld = dao.fetchManoeuvreById(oldManoeuvreId).head
      val manoeuvreId = dao.createManoeuvreForUpdate(userName, manoeuvreRowOld, manoeuvreUpdates.additionalInfo, modifiedDate: Option[DateTime])
      dao.expireManoeuvre(oldManoeuvreId)
      manoeuvreUpdates.exceptions.foreach(dao.setManoeuvreExceptions(manoeuvreId))
      manoeuvreUpdates.validityPeriods.foreach(dao.setManoeuvreValidityPeriods(manoeuvreId))

      eventBus.publish("manoeuvre:Validator",AssetValidatorInfo(Set(oldManoeuvreId) ++ Set(manoeuvreId)))
      manoeuvreId
    }
  }

  /**
    * Builds a valid chain from given pieces if possible:
    * Starting with a start element in chain, add every piece
    * to the chain until in last element. If there are multiple choices,
    * returns first found list of items that forms the chain.
    * 1) Find possible next elements
    * 2) For each of them
    *    a) If it is the last element, add it to the current chain and return it
    *    b) If it is an intermediate element, add it to the list and call this function recursively.
    *       Then check that the resulting chain has the final element as the last item (i.e. it is valid chain)
    *       and return that.
    *    c) If the recursive call fails, try next candidate
    * 3) If none work, return chain as it currently is (thus, we will return with the original starting chain)
    * @param toProcess List of elements that aren't yet accepted to valid chain
    * @param chain     List of elements that are accepted to our valid chain (or valid as far as we know)
    * @return          Valid chain or the starting value for chain (only starting element).
    */
  private def buildChain(toProcess: Seq[ManoeuvreElement], chain: Seq[ManoeuvreElement]): Seq[ManoeuvreElement] = {
    val (candidates, rest) = toProcess.partition(element => element.sourceLinkId == chain.last.destLinkId)
    val nextElements = candidates.toIterator
    while (nextElements.hasNext) {
      val element = nextElements.next()
      if (element.elementType == ElementTypes.LastElement)
        return chain ++ Seq(element)
      val resultChain = buildChain(rest, chain ++ Seq(element))
      if (resultChain.last.elementType == ElementTypes.LastElement)
        return resultChain
    }
    chain
  }

  /**
    * Cleans the chain from extra elements, returning a valid chain or an empty chain if no valid chain is possible
    * @param firstElement Starting element
    * @param lastElement  Target element
    * @param intermediateElements Intermediate elements' list
    * @return Sequence of Start->Intermediate(s)->Target elements.
    */
  def cleanChain(firstElement: Option[ManoeuvreElement], lastElement: Option[ManoeuvreElement], intermediateElements: Seq[ManoeuvreElement]) : Seq[ManoeuvreElement] = {
    if (firstElement.isEmpty || lastElement.isEmpty) {
      Seq()
    }
    else if (intermediateElements.isEmpty) {
      Seq(firstElement.get, lastElement.get)
    } else {
      val chain = buildChain(intermediateElements ++ Seq(lastElement.get), Seq(firstElement.get))
      if (chain.nonEmpty && chain.last.elementType == ElementTypes.LastElement)
        chain
      else
        Seq()
    }
  }

  /**
    * Validate the manoeuvre elements chain, after cleaning the extra elements
    * @param newManoeuvre Manoeuvre to be  validated
    * @param roadLinks Manoeuvre roadlinks
    * @return true if it's valid.
    */
  def isValid(newManoeuvre: NewManoeuvre, roadLinks: Seq[RoadLink] = Seq()) : Boolean = {

    val linkPairs = newManoeuvre.linkIds.zip(newManoeuvre.linkIds.tail)

    val startingElement = linkPairs.head
    val firstElement = ManoeuvreElement(0, startingElement._1, startingElement._2, ElementTypes.FirstElement)

    val destLinkId = newManoeuvre.linkIds.last
    val lastElement = ManoeuvreElement(0, destLinkId, "", ElementTypes.LastElement)

    val intermediateLinkIds = linkPairs.tail
    val intermediateElements = intermediateLinkIds.map( linkPair =>
      ManoeuvreElement(0, linkPair._1, linkPair._2, ElementTypes.IntermediateElement)
    )

    if (isValidLinkChain(linkPairs) == false) return false

    val cleanedManoeuvreElements = cleanChain(Some(firstElement), Some(lastElement), intermediateElements)

    val manoeuvre = Manoeuvre(0, cleanedManoeuvreElements, newManoeuvre.validityPeriods, newManoeuvre.exceptions, None, null, newManoeuvre.additionalInfo.orNull, null, null, false)

    isValidManoeuvre(roadLinks)(manoeuvre)
  }

  /**
    * Check if the chain makes sense and is truly a chain. Loops through the linkPairs and checks
    * that each pair's starting roadlink is not equal to next pair's
    * ending roadlink, also checks that pair's starting and ending roadlinks are not the same roadlink.
    * @param linkPairs sequence containing the start and end roadlinks for each pair
    * @return false if chain breaks the rules, otherwise true
    */
  private def isValidLinkChain(linkPairs: Seq[(String, String)]) : Boolean = {
    for( i <- 0 until linkPairs.length - 1){
    if( linkPairs(i)._1 == linkPairs(i + 1)._2 || linkPairs(i)._1 == linkPairs(i)._2 || linkPairs(i+1)._1 == linkPairs(i+1)._2) return false
  }
  true
  }

  private def getBySourceRoadLinks(roadLinks: Seq[RoadLink]): Seq[Manoeuvre] = {
    getByRoadLinks(roadLinks, dao.getByElementTypeRoadLinks(ElementTypes.FirstElement))
  }

  private def getByRoadLinks(roadLinks: Seq[RoadLink]): Seq[Manoeuvre] = {
    getByRoadLinks(roadLinks, dao.getByRoadLinks)
  }

  def fetchExistingAssetsByLinksIdsString(linksIds: Set[String], newTransaction: Boolean = true): Seq[PersistedManoeuvreRow] = {
    val existingAssets = if (newTransaction) 
      withDynTransaction {
        dao.fetchManoeuvresByLinkIdsNoGrouping(linksIds.toSeq)
      } 
    else dao.fetchManoeuvresByLinkIdsNoGrouping(linksIds.toSeq)
    existingAssets
  }
  /**
    * No manouvre validation, used for for test and samuutus updater
    * @param linksIds
    * @param newTransaction
    * @return
    */
  def getByRoadLinkIdsNoValidation(linksIds: Set[String], newTransaction: Boolean = true):  Seq[Manoeuvre]  = {
    def getManoeuvres: Seq[Option[Manoeuvre]] = {
     dao.getByRoadLinks(linksIds.toSeq).map { manoeuvre =>
       try {
         val firstElement = manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement)
           .getOrElse(throw MissingElement(s"Manoeuvre is invalid ${manoeuvre.id}, no first element"))
         val lastElement = manoeuvre.elements.find(_.elementType == ElementTypes.LastElement)
           .getOrElse(throw MissingElement(s"Manoeuvre is invalid ${manoeuvre.id}, no last element"))
         val intermediateElements = manoeuvre.elements.filter(_.elementType == ElementTypes.IntermediateElement)
         Some(manoeuvre.copy(elements = cleanChain(Some(firstElement), Some(lastElement), intermediateElements)))
       } catch {
         case e: MissingElement =>  logger.error(e.getMessage); None
         case e: Throwable => throw e
       }
      }
    }
    if (newTransaction) withDynTransaction {getManoeuvres.filter(_.isDefined).map(_.get)} 
    else getManoeuvres.filter(_.isDefined).map(_.get)
  }
  
  def updateManoeuvreLinkVersions(updates:Seq[ManoeuvreUpdateLinks], newTransaction: Boolean = true): Unit = {
    if (newTransaction) withDynTransaction {dao.updateManoeuvreLinkIds(updates)}
    else dao.updateManoeuvreLinkIds(updates)
  }
  
  private def getByRoadLinks(roadLinks: Seq[RoadLink], getDaoManoeuvres: Seq[String] => Seq[Manoeuvre]): Seq[Manoeuvre] = {
    val manoeuvres =
      withDynTransaction {
        getDaoManoeuvres(roadLinks.map(_.linkId)).map{ manoeuvre =>
          val firstElement = manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement)
          val lastElement = manoeuvre.elements.find(_.elementType == ElementTypes.LastElement)
          val intermediateElements = manoeuvre.elements.filter(_.elementType == ElementTypes.IntermediateElement)

          manoeuvre.copy(elements = cleanChain(firstElement, lastElement, intermediateElements))

        }
      }
    manoeuvres.filter(isValidManoeuvre(roadLinks))
  }

  private def sourceLinkId(manoeuvre: Manoeuvre) : Option[String] = {
    manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId)
  }

  private def destinationLinkId(manoeuvre: Manoeuvre) : Option[String]  = {
    manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId)
  }

  private def intermediateLinkIds(manoeuvre: Manoeuvre) : Option[String] = {
    manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId)
  }

  private def allLinkIds(manoeuvre: Manoeuvre): Seq[String] = {
    manoeuvre.elements.map(_.sourceLinkId)
  }

  private def isValidManoeuvre(roadLinks: Seq[RoadLink] = Seq())(manoeuvre: Manoeuvre): Boolean = {
    def checkAdjacency(manoeuvreElement: ManoeuvreElement, allRoadLinks: Seq[RoadLink]): Boolean = {

      val destRoadLinkOption = allRoadLinks.find(_.linkId == manoeuvreElement.destLinkId)
      val sourceRoadLinkOption = allRoadLinks.find(_.linkId == manoeuvreElement.sourceLinkId)

      (sourceRoadLinkOption, destRoadLinkOption) match {
        case (Some(sourceRoadLink), Some(destRoadLink)) =>
          GeometryUtils.areAdjacent(sourceRoadLink.geometry, destRoadLink.geometry) &&
            sourceRoadLink.isCarTrafficRoad &&
            destRoadLink.isCarTrafficRoad
        case _ => false
      }
    }

    //Get all road links from vvh that are not on the RoadLinks sequence passed as parameter
    val linkIds = allLinkIds(manoeuvre)
    val additionalRoadLinks = linkIds.forall(id => roadLinks.exists(_.linkId == id)) match {
      case false => roadLinkService.getRoadLinksByLinkIds(linkIds.toSet -- roadLinks.map(_.linkId))
      case true => Seq()
    }

    val allRoadLinks = roadLinks ++ additionalRoadLinks

    if(manoeuvre.elements.isEmpty ||
      manoeuvre.elements.head.elementType != ElementTypes.FirstElement ||
      manoeuvre.elements.last.elementType != ElementTypes.LastElement)
      return false

    manoeuvre.elements.forall{ manoeuvreElement =>
      manoeuvreElement.elementType match {
        case ElementTypes.LastElement =>
          true
        case _ =>
          //If it's IntermediateElement or FirstElement
          checkAdjacency(manoeuvreElement, allRoadLinks)
      }
    }
  }

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre) : Long = {
    val manoeuvreId = withDynTransaction {
      dao.createManoeuvre(userName, manoeuvre)
    }

    eventBus.publish("manoeuvre:Validator",AssetValidatorInfo(Set(manoeuvreId)))
    manoeuvreId

  }

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre, roadlinks: Seq[RoadLink],newTransaction:Boolean=true) : Long = {
    if (newTransaction) withDynTransaction {createWithoutTransaction(userName, manoeuvre, roadlinks)} 
    else createWithoutTransaction(userName, manoeuvre, roadlinks)
  }

  def createWithoutTransaction(userName: String, manoeuvre: NewManoeuvre, roadlinks: Seq[RoadLink]) : Long = {
    if(!isValid(manoeuvre, roadlinks))
      throw new InvalidParameterException("Invalid 'manoeuvre'")

    dao.createManoeuvre(userName, manoeuvre)
  }

  def deleteManoeuvre(s: String, id: Long): Long = {
    val deletedId = withDynTransaction {
      dao.deleteManoeuvre(s, id)
    }

    eventBus.publish("manoeuvre:Validator",AssetValidatorInfo(Set(deletedId)))
    deletedId
  }

  def deleteManoeuvreFromSign(id: Long): Long = {
    logger.info("expiring manoeuvre")
    withDynTransaction {
      dao.deleteManoeuvreByTrafficSign(id)
    }
  }

  def deleteManoeuvreFromSign(filter: String => String, username: Option[String] = None, withTransaction: Boolean = true): Unit = {
    logger.info("expiring manoeuvre")
    if (withTransaction)
      withDynTransaction {
        dao.deleteManoeuvreByTrafficSign(filter, username)
      }
    else
      dao.deleteManoeuvreByTrafficSign(filter, username)
  }

   def withMunicipalities(municipalities: Set[Int])(query: String): String = {
    query + s"and a.municipality_code in (${municipalities.mkString(",")})"
  }

   def withId(id: Long)(query: String): String = {
    query + s" and a.id = $id"
  }
   def withIds(ids: Set[Long])(query: String): String = {
    query + s"and a.id in (${ids.mkString(",")})"
  }

  def getSourceRoadLinkIdById(id: Long) : String = {
    withDynTransaction {
      dao.getSourceRoadLinkIdById(id)
    }
  }

  def find(id: Long) : Option[Manoeuvre] = {
    withDynTransaction {
      dao.find(id)
    }
  }

  def getInaccurateRecords(typeId:Int,municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = {
    withDynTransaction {
      inaccurateDAO.getInaccurateAsset(Manoeuvres.typeId, municipalities, adminClass)
        .groupBy(_.municipality)
        .mapValues {
          _.groupBy(_.administrativeClass)
            .mapValues(_.map{values => Map("assetId" -> values.assetId, "linkId" -> values.linkId)})
        }
    }
  }
  
  private def createManoeuvreFromTrafficSign(trafficSignInfo: TrafficSignInfo ): Seq[Long] = {
    logger.info("creating manoeuvre from traffic sign")
    val tsLinkId = trafficSignInfo.linkId
    val tsDirection = trafficSignInfo.validityDirection

    if (tsLinkId != trafficSignInfo.roadLink.linkId)
      throw new ManoeuvreCreationException(Set("Wrong roadlink"))

    if (SideCode(tsDirection) == SideCode.BothDirections)
      throw new ManoeuvreCreationException(Set("Isn't possible to create a manoeuvre based on a traffic sign with BothDirections"))

    val connectionPoint = roadLinkService.getRoadLinkEndDirectionPoints(trafficSignInfo.roadLink, Some(tsDirection)).headOption.getOrElse(throw new ManoeuvreCreationException(Set("Connection Point not valid")))

    try {
        val (intermediates, adjacents, adjacentConnectPoint) = recursiveGetAdjacent(tsLinkId, connectionPoint)
        val manoeuvreInit = trafficSignInfo.roadLink +: intermediates

        val tsType = TrafficSignType.applyOTHValue(trafficSignInfo.signType)

        val roadLinks = tsType match {
          case NoLeftTurn => manoeuvreInit :+ roadLinkService.pickLeftMost(manoeuvreInit.last, adjacents)

          case NoRightTurn => manoeuvreInit :+ roadLinkService.pickRightMost(manoeuvreInit.last, adjacents)

          case NoUTurn =>
            val firstLeftMost = roadLinkService.pickLeftMost(manoeuvreInit.last, adjacents)
            val (int, newAdjacents, _)= recursiveGetAdjacent(firstLeftMost.linkId, getOpositePoint(firstLeftMost.geometry, adjacentConnectPoint))
            (manoeuvreInit :+ firstLeftMost) ++ (int :+ roadLinkService.pickLeftMost(firstLeftMost, newAdjacents))

          case _ => Seq.empty[RoadLink]
        }

      if(!validateManoeuvre(trafficSignInfo.roadLink.linkId, roadLinks.last.linkId, ElementTypes.FirstElement))
        throw new ManoeuvreCreationException(Set("Manoeuvre creation not valid"))
      else
        Seq(createWithoutTransaction("traffic_sign_generated", NewManoeuvre(Set(), Seq.empty[Int], None, roadLinks.map(_.linkId), Some(trafficSignInfo.id), false), roadLinks))

    } catch {
      case mce: ManoeuvreCreationException =>
         throw mce
      case eipe: InvalidParameterException => 
         throw eipe
    }
  }
  
  def createBasedOnTrafficSign(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true): Seq[Long] = {
    if(newTransaction) {
      withDynTransaction {
        createManoeuvreFromTrafficSign(trafficSignInfo)
      }
    }
    else
      createManoeuvreFromTrafficSign(trafficSignInfo)
  }

  private def getOpositePoint(geometry: Seq[Point], point: Point) = {
    val (headPoint, lastPoint) = GeometryUtils.geometryEndpoints(geometry)
    if(GeometryUtils.areAdjacent(headPoint, point))
      lastPoint
    else
      headPoint
  }

  private def recursiveGetAdjacent(linkId: String, point: Point, intermediants: Seq[RoadLink] = Seq(), numberOfConnections: Int = 0): (Seq[RoadLink], Seq[RoadLink], Point) = {

    val adjacents = roadLinkService.getAdjacent(linkId, Seq(point), newTransaction = false)
    if (adjacents.isEmpty)
      throw new ManoeuvreCreationException(Set("No adjecents found for that link id, the manoeuvre will not be created"))

    if(adjacents.size == 1 && numberOfConnections == 3)
      throw new ManoeuvreCreationException(Set("No turn found, manoeuvre not created"))

    if (adjacents.size == 1  && roadLinkService.getAdjacent(adjacents.head.linkId, Seq(getOpositePoint(adjacents.head.geometry, point)), newTransaction = false).nonEmpty) {
      recursiveGetAdjacent(adjacents.head.linkId, getOpositePoint(adjacents.head.geometry, point), intermediants ++ adjacents, numberOfConnections + 1)
    } else {
      (intermediants, adjacents, point)
    }
  }

  private def countExistings(sourceId: String, destId: String, elementType: Int): Long = {
    dao.countExistings(sourceId, destId, elementType)
  }

  private def validateManoeuvre(sourceId: String, destLinkId: String, elementType: Int): Boolean  = {
    countExistings(sourceId, destLinkId, elementType) == 0
  }

  def insertSamuutusChange(rows: Seq[ChangedManoeuvre],newTransaction: Boolean = true): Seq[ChangedManoeuvre]= {
    if (newTransaction) { withDynTransaction {  dao.insertSamuutusChange(rows)}
    } else dao.insertSamuutusChange(rows)
    rows
  }
  
  
  def getManoeuvreSamuutusWorkList(newTransaction: Boolean = true): Seq[SamuuutusWorkListItem] = {
    if (newTransaction) { withDynTransaction {dao.getSamuutusChange()}
    }else dao.getSamuutusChange()
  }
  
}
