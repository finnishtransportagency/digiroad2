package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriod, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.manoeuvre.oracle.{ManoeuvreDao, PersistedManoeuvreRow}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession

case class Manoeuvre(id: Long, elements: Seq[ManoeuvreElement], validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], modifiedDateTime: Option[DateTime], modifiedBy: Option[String], additionalInfo: String, createdDateTime: DateTime, createdBy: String)
case class ManoeuvreElement(manoeuvreId: Long, sourceLinkId: Long, destLinkId: Long, elementType: Int)
case class NewManoeuvre(validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], additionalInfo: Option[String], linkIds: Seq[Long])
case class ManoeuvreUpdates(validityPeriods: Option[Set[ValidityPeriod]], exceptions: Option[Seq[Int]], additionalInfo: Option[String])

object ElementTypes {
  val FirstElement = 1
  val IntermediateElement = 2
  val LastElement = 3
}

class ManoeuvreService(roadLinkService: RoadLinkService) {

  def dao: ManoeuvreDao = new ManoeuvreDao(roadLinkService.vvhClient)
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityNumber)
    getBySourceRoadLinks(roadLinks)
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    getByRoadLinks(roadLinks)
  }

  def updateManoeuvre(userName: String, manoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates) = {
    withDynTransaction {
      dao.updateManoeuvre(userName, manoeuvreId, manoeuvreUpdates)
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
  def cleanChain(firstElement: ManoeuvreElement, lastElement: ManoeuvreElement, intermediateElements: Seq[ManoeuvreElement]) = {
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

    val manoeuvre = Manoeuvre(0, cleanedManoeuvreElements, newManoeuvre.validityPeriods, newManoeuvre.exceptions, None, null, newManoeuvre.additionalInfo.getOrElse(null), null, null)

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

  private def sourceLinkId(manoeuvre: Manoeuvre) = {
    manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId)
  }

  private def destinationLinkId(manoeuvre: Manoeuvre) = {
    manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId)
  }

  private def intermediateLinkIds(manoeuvre: Manoeuvre) = {
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

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre) = {
    withDynTransaction {
      dao.createManoeuvre(userName, manoeuvre)
    }
  }

  def deleteManoeuvre(s: String, id: Long) = {
    withDynTransaction {
      dao.deleteManoeuvre(s, id)
    }
  }

  def getSourceRoadLinkIdById(id: Long) = {
    withDynTransaction {
      dao.getSourceRoadLinkIdById(id)
    }
  }

  def find(id: Long) = {
    withDynTransaction {
      dao.find(id)
    }
  }

}
