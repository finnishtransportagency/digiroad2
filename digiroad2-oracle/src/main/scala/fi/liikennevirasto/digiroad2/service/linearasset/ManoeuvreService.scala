package fi.liikennevirasto.digiroad2.service.linearasset

import java.security.InvalidParameterException

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, PropertyValue, SideCode, TrafficSignType}
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriod}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class Manoeuvre(id: Long, elements: Seq[ManoeuvreElement], validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], modifiedDateTime: Option[DateTime], modifiedBy: Option[String], additionalInfo: String, createdDateTime: DateTime, createdBy: String)
case class ManoeuvreElement(manoeuvreId: Long, sourceLinkId: Long, destLinkId: Long, elementType: Int)
case class NewManoeuvre(validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], additionalInfo: Option[String], linkIds: Seq[Long], trafficSignId: Option[Long])
case class ManoeuvreUpdates(validityPeriods: Option[Set[ValidityPeriod]], exceptions: Option[Seq[Int]], additionalInfo: Option[String])
case class ManoeuvreProvider(trafficSign: PersistedTrafficSign, sourceRoadLink: RoadLink)
class ManoeuvreCreationException(val response: Set[String]) extends RuntimeException {}

object ElementTypes {
  val FirstElement = 1
  val IntermediateElement = 2
  val LastElement = 3
}

class ManoeuvreService(roadLinkService: RoadLinkService) {
  val logger = LoggerFactory.getLogger(getClass)
  def dao: ManoeuvreDao = new ManoeuvreDao(roadLinkService.vvhClient)
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityNumber)
    getBySourceRoadLinks(roadLinks)
  }

  def getByMunicipalityAndRoadLinks(municipalityNumber: Int): Seq[(Manoeuvre, Seq[RoadLink])] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityNumber)
    val manoeuvres = getBySourceRoadLinks(roadLinks)
    manoeuvres.map{ manoeuvre => (manoeuvre, roadLinks.filter(road => road.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).get ||
      road.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).get))
    }
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds)
    getByRoadLinks(roadLinks)
  }

  def updateManoeuvre(userName: String, oldManoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates, modifiedDate: Option[DateTime]): Long = {
     withDynTransaction {
      val manoeuvreRowOld = dao.fetchManoeuvreById(oldManoeuvreId).head
      val manoeuvreId = dao.createManoeuvreForUpdate(userName, manoeuvreRowOld, manoeuvreUpdates.additionalInfo, modifiedDate: Option[DateTime])
      dao.expireManoeuvre(oldManoeuvreId)
      manoeuvreUpdates.exceptions.foreach(dao.setManoeuvreExceptions(manoeuvreId))
      manoeuvreUpdates.validityPeriods.foreach(dao.setManoeuvreValidityPeriods(manoeuvreId))
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
  def cleanChain(firstElement: ManoeuvreElement, lastElement: ManoeuvreElement, intermediateElements: Seq[ManoeuvreElement]) : Seq[ManoeuvreElement] = {
    if (intermediateElements.isEmpty)
      Seq(firstElement, lastElement)
    else {
      val chain = buildChain(intermediateElements ++ Seq(lastElement), Seq(firstElement))
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
    val lastElement = ManoeuvreElement(0, destLinkId, 0, ElementTypes.LastElement)

    val intermediateLinkIds = linkPairs.tail
    val intermediateElements = intermediateLinkIds.map( linkPair =>
      ManoeuvreElement(0, linkPair._1, linkPair._2, ElementTypes.IntermediateElement)
    )

    val cleanedManoeuvreElements = cleanChain(firstElement, lastElement, intermediateElements)

    val manoeuvre = Manoeuvre(0, cleanedManoeuvreElements, newManoeuvre.validityPeriods, newManoeuvre.exceptions, None, null, newManoeuvre.additionalInfo.orNull, null, null)

    isValidManoeuvre(roadLinks)(manoeuvre)
  }

  private def getBySourceRoadLinks(roadLinks: Seq[RoadLink]): Seq[Manoeuvre] = {
    getByRoadLinks(roadLinks, dao.getByElementTypeRoadLinks(ElementTypes.FirstElement))
  }

  private def getByRoadLinks(roadLinks: Seq[RoadLink]): Seq[Manoeuvre] = {
    getByRoadLinks(roadLinks, dao.getByRoadLinks)
  }

  private def getByRoadLinks(roadLinks: Seq[RoadLink], getDaoManoeuvres: Seq[Long] => Seq[Manoeuvre]): Seq[Manoeuvre] = {
    val manoeuvres =
      withDynTransaction {
        getDaoManoeuvres(roadLinks.map(_.linkId)).map{ manoeuvre =>
          val firstElement = manoeuvre.elements.filter(_.elementType == ElementTypes.FirstElement).head
          val lastElement = manoeuvre.elements.filter(_.elementType == ElementTypes.LastElement).head
          val intermediateElements = manoeuvre.elements.filter(_.elementType == ElementTypes.IntermediateElement)

          manoeuvre.copy(elements = cleanChain(firstElement, lastElement, intermediateElements))

        }
      }
    manoeuvres.filter(isValidManoeuvre(roadLinks))
  }

  private def sourceLinkId(manoeuvre: Manoeuvre) : Option[Long] = {
    manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId)
  }

  private def destinationLinkId(manoeuvre: Manoeuvre) : Option[Long]  = {
    manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId)
  }

  private def intermediateLinkIds(manoeuvre: Manoeuvre) : Option[Long] = {
    manoeuvre.elements.find(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId)
  }

  private def allLinkIds(manoeuvre: Manoeuvre): Seq[Long] = {
    manoeuvre.elements.map(_.sourceLinkId)
  }

  private def isValidManoeuvre(roadLinks: Seq[RoadLink] = Seq())(manoeuvre: Manoeuvre): Boolean = {
    def checkAdjacency(manoeuvreElement: ManoeuvreElement, allRoadLinks: Seq[RoadLink]): Boolean = {

      val destRoadLinkOption = allRoadLinks.find(_.linkId == manoeuvreElement.destLinkId)
      val sourceRoadLinkOption = allRoadLinks.find(_.linkId == manoeuvreElement.sourceLinkId)

      (sourceRoadLinkOption, destRoadLinkOption) match {
        case (Some(sourceRoadLink), Some(destRoadLink)) => {
          GeometryUtils.areAdjacent(sourceRoadLink.geometry, destRoadLink.geometry) &&
            sourceRoadLink.isCarTrafficRoad &&
            destRoadLink.isCarTrafficRoad
        }
        case _ => false
      }
    }

    //Get all road links from vvh that are not on the RoadLinks sequence passed as parameter
    val linkIds = allLinkIds(manoeuvre)
    val additionalRoadLinks = linkIds.forall(id => roadLinks.exists(_.linkId == id)) match {
      case false => roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIds.toSet -- roadLinks.map(_.linkId))
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

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre, roadlinks: Seq[RoadLink]) : Long = {
    withDynTransaction {
      createWithoutTransaction(userName, manoeuvre, roadlinks)
    }
  }

  def createWithoutTransaction(userName: String, manoeuvre: NewManoeuvre, roadlinks: Seq[RoadLink]) : Long = {
    if(!isValid(manoeuvre, roadlinks))
      throw new InvalidParameterException("Invalid 'manoeuvre")

    dao.createManoeuvre(userName, manoeuvre)
  }

  def deleteManoeuvre(s: String, id: Long): Long = {
    withDynTransaction {
      dao.deleteManoeuvre(s, id)
    }
  }

  def deleteManoeuvreFromSign(id: Long): Long = {
    logger.info("expiring manoeuvre")
    withDynTransaction {
      dao.deleteManoeuvreByTrafficSign(id)
    }
  }

  def getSourceRoadLinkIdById(id: Long) : Long = {
    withDynTransaction {
      dao.getSourceRoadLinkIdById(id)
    }
  }

  def find(id: Long) : Option[Manoeuvre] = {
    withDynTransaction {
      dao.find(id)
    }
  }

  private def createManoeuvreFromTrafficSign(manouvreProvider: ManoeuvreProvider): Option[Long] = {
    logger.info("creating manoeuvre from traffic sign")
    val tsLinkId = manouvreProvider.trafficSign.linkId
    val tsDirection = manouvreProvider.trafficSign.validityDirection

    if (tsLinkId != manouvreProvider.sourceRoadLink.linkId)
      throw new ManoeuvreCreationException(Set("Wrong roadlink"))

    if (SideCode(tsDirection) == SideCode.BothDirections)
      throw new ManoeuvreCreationException(Set("Isn't possible to create a manoeuvre based on a traffic sign with BothDirections"))

    val connectionPoint = roadLinkService.getRoadLinkEndDirectionPoints(manouvreProvider.sourceRoadLink, Some(tsDirection)).headOption.getOrElse(throw new ManoeuvreCreationException(Set("Connection Point not valid")))

    val (intermediates, adjacents, adjacentConnectPoint) = recursiveGetAdjacent(tsLinkId, connectionPoint)
    val manoeuvreInit = manouvreProvider.sourceRoadLink +: intermediates

    val roadLinks = getTrafficSignsProperties(manouvreProvider.trafficSign, "trafficSigns_type").map { prop =>
      val tsType = TrafficSignType(prop.propertyValue.toInt)

      tsType match {

        case TrafficSignType.NoLeftTurn => manoeuvreInit :+ roadLinkService.pickLeftMost(manoeuvreInit.last, adjacents)

        case TrafficSignType.NoRightTurn => manoeuvreInit :+ roadLinkService.pickRightMost(manoeuvreInit.last, adjacents)

        case TrafficSignType.NoUTurn => {
          val firstLeftMost = roadLinkService.pickLeftMost(manoeuvreInit.last, adjacents)
          val (int, newAdjacents, _)= recursiveGetAdjacent(firstLeftMost.linkId, getOpositePoint(firstLeftMost.geometry, adjacentConnectPoint))
          (manoeuvreInit :+ firstLeftMost) ++ (int :+ roadLinkService.pickLeftMost(firstLeftMost, newAdjacents))
        }
        case _ => Seq.empty[RoadLink]
      }
    }.getOrElse(Seq.empty[RoadLink])

    if(!validateManoeuvre(manouvreProvider.sourceRoadLink.linkId, roadLinks.last.linkId, ElementTypes.FirstElement))
      throw new ManoeuvreCreationException(Set("Manoeuvre creation not valid"))
    else
      Some(createWithoutTransaction("traffic_sign_generated", NewManoeuvre(Set(), Seq.empty[Int], None, roadLinks.map(_.linkId), Some(manouvreProvider.trafficSign.id)), roadLinks))

  }

  def createManoeuvreBasedOnTrafficSign(manouvreProvider: ManoeuvreProvider, newTransaction: Boolean = true): Option[Long] = {
    if(newTransaction) {
      withDynTransaction {
        createManoeuvreFromTrafficSign(manouvreProvider)
      }
    }
    else
      createManoeuvreFromTrafficSign(manouvreProvider)
  }

  private def getTrafficSignsProperties(trafficSign: PersistedTrafficSign, property: String): Option[PropertyValue] = {
    trafficSign.propertyData.find(p => p.publicId == property).get.values.headOption
  }

  private def getOpositePoint(geometry: Seq[Point], point: Point) = {
    val (headPoint, lastPoint) = GeometryUtils.geometryEndpoints(geometry)
    if(GeometryUtils.areAdjacent(headPoint, point))
      lastPoint
    else
      headPoint
  }

  private def recursiveGetAdjacent(linkId: Long, point: Point, intermediants: Seq[RoadLink] = Seq(), numberOfConnections: Int = 0): (Seq[RoadLink], Seq[RoadLink], Point) = {

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

  private def countExistings(sourceId: Long, destId: Long, elementType: Int): Long = {
    dao.countExistings(sourceId, destId, elementType)
  }

  private def validateManoeuvre(sourceId: Long, destLinkId: Long, elementType: Int): Boolean  = {
    countExistings(sourceId, destLinkId, elementType) == 0
  }
}
