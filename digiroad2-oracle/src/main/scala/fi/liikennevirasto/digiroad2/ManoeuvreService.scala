package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ValidityPeriod, RoadLink}
import fi.liikennevirasto.digiroad2.manoeuvre.oracle.{PersistedManoeuvreRow, ManoeuvreDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database.dynamicSession

case class Manoeuvre(id: Long, elements: Seq[ManoeuvreElement], validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], modifiedDateTime: String, modifiedBy: String, additionalInfo: String)
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
    getByRoadLinks(roadLinks)
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    getByRoadLinks(roadLinks)
  }

  def updateManoeuvre(userName: String, manoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates) = {
    withDynTransaction {
      dao.updateManoueuvre(userName, manoeuvreId, manoeuvreUpdates)
    }
  }

  private def getByRoadLinks(roadLinks: Seq[RoadLink]): Seq[Manoeuvre] = {
    withDynTransaction {
      dao.getByRoadLinks(roadLinks.map(_.linkId))
        .filter(isValidManoeuvre(roadLinks))
    }
  }

  private def hasOnlyOneSourceAndDestination(manoeuvreRowsForId: (Long, Seq[PersistedManoeuvreRow])): Boolean = {
    val (_, manoeuvreRows) = manoeuvreRowsForId
    manoeuvreRows.size == 2 && manoeuvreRows.exists(_.elementType == ElementTypes.FirstElement) && manoeuvreRows.exists(_.elementType == ElementTypes.LastElement)
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

  private def isValidManoeuvre(roadLinks: Seq[RoadLink])(manoeuvre: Manoeuvre): Boolean = {
    def  checkAdjacency(manoeuvreElement: ManoeuvreElement, allRoadLinks: Seq[RoadLink]): Boolean = {

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
      case false => roadLinkService.getRoadLinksFromVVH(linkIds.toSet -- roadLinks.map(_.linkId))
      case true => Seq()
    }

    val allRoadLinks = roadLinks ++ additionalRoadLinks

    val manoeuvreSortedByElementType = manoeuvre.elements.sortBy(_.elementType)

    if(manoeuvreSortedByElementType.head.elementType != ElementTypes.FirstElement)
      false

    if(manoeuvreSortedByElementType.last.elementType != ElementTypes.LastElement)
      false

    manoeuvreSortedByElementType.forall{ manoeuvreElement =>
      manoeuvreElement.elementType match {
        case ElementTypes.FirstElement =>
          checkAdjacency(manoeuvreElement, allRoadLinks)
        case ElementTypes.IntermediateElement =>
          //Verify if some manoeuvre element in the chain is connected with this manoeuvre element
          if(manoeuvre.elements.exists(_.sourceLinkId == manoeuvreElement.destLinkId))
            checkAdjacency(manoeuvreElement, allRoadLinks)
          else
            false
        case ElementTypes.LastElement =>
          //Verify if some manoeuvre element in the chain is connected with the end roadlink
          manoeuvre.elements.exists(_.destLinkId == manoeuvreElement.sourceLinkId)
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
