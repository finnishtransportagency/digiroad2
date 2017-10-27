package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.dao.RoadAddress
import fi.liikennevirasto.viite.dao.ProjectLink

class GuestimateGeometryForMissingLinks {

  /**
    * Loops through geometry and tries to find geometry to missing links
    * @param missingGeometry
    * @param projectRoadaddressGeometry
    * @return
    */

  def guestimateGeometry(missingGeometry:Seq[RoadAddress],projectRoadaddressGeometry:Seq[RoadAddress]):Seq[RoadAddress]={
    missingGeometry.lastOption match {
      case Some(missingRoadAddress) =>
      {
        val previousLink=projectRoadaddressGeometry.find(x=>x.endAddrMValue==missingRoadAddress.startAddrMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        val nextLink=projectRoadaddressGeometry.find(x=>x.startAddrMValue==missingRoadAddress.endAddrMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        (previousLink, nextLink) match {
          case (p,n) if p.nonEmpty && n.nonEmpty => //we check if we have links with geometry on both sides
          {
            val roadaddressWithAddedGeometry=missingRoadAddress.copy(geometry=Seq(p.head.geometry.last,n.head.geometry.head))
            if (missingGeometry.size>1)
              guestimateGeometry(missingGeometry.dropRight(1), projectRoadaddressGeometry++Seq(roadaddressWithAddedGeometry) )
            else
              projectRoadaddressGeometry++Seq(roadaddressWithAddedGeometry)
          }
          case (p,n) =>  //if we didnt have geometry on both sides we have to check if we have any any geometry and then find geometry we can use
          {
            val foundGeometryOnRoadPartOrTrack= projectRoadaddressGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber &&x.track==missingRoadAddress.track)
            if (foundGeometryOnRoadPartOrTrack.isEmpty)
              throw new RuntimeException(s"RoadPart or trackpart did not have geometry to form guestimate of missing geometry")
            val nextLinkGeometry:Option[RoadAddress]=  if (n.nonEmpty) n else None
            val missingGeometryOnRoadPartOrTrack= missingGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
            val (adjacentLinksWithNoGeometry,previousLinkToChain)=getGeometryLinkToPreviousMissingLinks(missingGeometryOnRoadPartOrTrack,foundGeometryOnRoadPartOrTrack,missingRoadAddress,Seq.empty[RoadAddress])
            val adjacentIdSeq=adjacentLinksWithNoGeometry.map(x=>x.id)
            // we form geometry to all adjacent link on road or track part here
            val geometrizedLinks=adjacentLinksWithNoGeometry.map(x=>x.copy(geometry=guessGeometryForLink(x,previousLinkToChain,nextLinkGeometry)))
            val stillMissingGeometry=missingGeometry.filterNot(x=>adjacentIdSeq.contains(x.id))
            if(stillMissingGeometry.isEmpty)
              return projectRoadaddressGeometry++geometrizedLinks
            guestimateGeometry(stillMissingGeometry, projectRoadaddressGeometry++geometrizedLinks)
          }
        }
      }
      case None => projectRoadaddressGeometry
      case _=>  throw new RuntimeException("Undefined state when guessing geometry") }
  }

  /**
    *
    * @param linkToGenGeometry one of the links missing geometry
    * @param previousLink  link containing geometry that has same startaddrM value as adjacent seq:s highest endaddrM value
    * @param nextLink      link containing geometry that has same endaddrM value as adjacent seq:s lowest startaddrM value
         Either nextLink or previousLink exists, and error should be throw earlier if no geometry can be used to guess geometry for given links
    * @return              returns links containing geometry
    */
  private def guessGeometryForLink(linkToGenGeometry:RoadAddress, previousLink:Option[RoadAddress],nextLink:Option[RoadAddress]):Seq[Point]  ={
    (previousLink, nextLink) match {
      case (Some(p), Some(n)) =>
        val geom = Seq(p.geometry.last, n.geometry.head)
        val geomLegth = GeometryUtils.geometryLength(geom)
        val pToNAddrLength = n.startAddrMValue.toDouble-p.endAddrMValue
        GeometryUtils.truncateGeometry2D(geom, geomLegth*(linkToGenGeometry.startAddrMValue - p.endAddrMValue) / pToNAddrLength,
          geomLegth*(linkToGenGeometry.endAddrMValue - p.endAddrMValue) / pToNAddrLength)
      case (None, Some(n)) => {
        val guestimateStartPoint= n.geometry.head - (n.geometry.last - n.geometry.head).normalize2D().scale(n.startAddrMValue - linkToGenGeometry.startAddrMValue)
        val guestimateEndPoint= n.geometry.head - (n.geometry.last - n.geometry.head).normalize2D().scale(n.startAddrMValue -linkToGenGeometry.endAddrMValue)
        Seq(guestimateStartPoint,guestimateEndPoint)
      }
      case (Some(p), None) =>
        val guestimateStartPoint=p.geometry.last + (p.geometry.last - p.geometry.head).normalize2D().scale(linkToGenGeometry.startAddrMValue - p.endAddrMValue)
        val guestimateEndPoint=p.geometry.last + (p.geometry.last - p.geometry.head).normalize2D().scale(linkToGenGeometry.endAddrMValue - p.endAddrMValue)
        Seq(guestimateStartPoint,guestimateEndPoint)
      case _ => throw new RuntimeException(s"RoadPart or trackpart did not have geometry to form missing geometry")
    }
  }

  /**
    * Gets link sequence missing geometry
    * @param missingGeometry roadlinks from roadpart or track section that are missing geometry
    * @param projectRoadaddressGeometry Known geometry from roadpart or track section
    * @return
    */
  private def getGeometryLinkToPreviousMissingLinks(missingGeometry:Seq[RoadAddress],projectRoadaddressGeometry:Seq[RoadAddress],currentLink:RoadAddress,currentMissingLinksSeq:Seq[RoadAddress]): (Seq[RoadAddress], Option[RoadAddress])={
    val nextLinkWithGeom=projectRoadaddressGeometry.filter(x=>x.endAddrMValue==currentLink.startAddrMValue)
    if (nextLinkWithGeom.nonEmpty) //found link with geometry
      (currentMissingLinksSeq++Seq(currentLink),Some(nextLinkWithGeom.head))
    else
    {
      val nextLinkFromMissingList=missingGeometry.filter(x=>x.endAddrMValue==currentLink.startAddrMValue)
      if (nextLinkFromMissingList.isEmpty) // we are probably on last link
        (currentMissingLinksSeq++Seq(currentLink),None)
      else //if we didnt find geometry, but found next missing geometry we search geometry from  next links next link
      {
        val reducedMissingGeometry=missingGeometry.filterNot(x=>x.id==currentLink.id)
        getGeometryLinkToPreviousMissingLinks(reducedMissingGeometry,projectRoadaddressGeometry,nextLinkFromMissingList.head,currentMissingLinksSeq++Seq(currentLink))
      }
    }
  }

  /**
    * Loops through geometry and tries to find geometry to missing Project Links
    * @param missingGeometry
    * @param projectRoadaddressGeometry
    * @return
    */
  def guestimateProjectLinkGeometry(missingGeometry:Seq[ProjectLink],projectRoadaddressGeometry:Seq[ProjectLink]):Seq[ProjectLink]={
    missingGeometry.lastOption match {
      case Some(missingRoadAddress) =>
      {
        val previousLink=projectRoadaddressGeometry.find(x=>x.endAddrMValue==missingRoadAddress.startAddrMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        val nextLink=projectRoadaddressGeometry.find(x=>x.startAddrMValue==missingRoadAddress.endAddrMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        (previousLink, nextLink) match {
          case (p,n) if p.nonEmpty && n.nonEmpty => //we check if we have links with geometry on both sides
          {
            val roadaddressWithAddedGeometry=missingRoadAddress.copy(geometry=Seq(p.head.geometry.last,n.head.geometry.head))
            if (missingGeometry.size>1)
              guestimateProjectLinkGeometry(missingGeometry.dropRight(1), projectRoadaddressGeometry++Seq(roadaddressWithAddedGeometry) )
            else
              projectRoadaddressGeometry++Seq(roadaddressWithAddedGeometry)
          }
          case (p,n) =>  //if we didnt have geometry on both sides we have to check if we have any any geometry and then find geometry we can use
          {
            val foundGeometryOnRoadPartOrTrack= projectRoadaddressGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber &&x.track==missingRoadAddress.track)
            if (foundGeometryOnRoadPartOrTrack.isEmpty)
              throw new RuntimeException(s"RoadPart or trackpart did not have geometry to form guestimate of missing geometry")
            val nextLinkGeometry:Option[ProjectLink]=  if (n.nonEmpty) n else None
            val missingGeometryOnRoadPartOrTrack= missingGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
            val (adjacentLinksWithNoGeometry,previousLinkToChain)=getGeometryLinkToPreviousMissingProjectLinks(missingGeometryOnRoadPartOrTrack,foundGeometryOnRoadPartOrTrack,missingRoadAddress,Seq.empty[ProjectLink])
            val adjacentIdSeq=adjacentLinksWithNoGeometry.map(x=>x.id)
            // we form geometry to all adjacent link on road or track part here
            val geometrizedLinks=adjacentLinksWithNoGeometry.map(x=>x.copy(geometry=guessGeometryForProjectLink(x,previousLinkToChain,nextLinkGeometry)))
            val stillMissingGeometry=missingGeometry.filterNot(x=>adjacentIdSeq.contains(x.id))
            if(stillMissingGeometry.isEmpty)
              return projectRoadaddressGeometry++geometrizedLinks
            guestimateProjectLinkGeometry(stillMissingGeometry, projectRoadaddressGeometry++geometrizedLinks)
          }
        }
      }
      case None => projectRoadaddressGeometry
      case _=>  throw new RuntimeException("Undefined state when guessing geometry") }
  }

  /**
    * Gets link sequence missing geometry
    * @param missingGeometry roadlinks from roadpart or track section that are missing geometry
    * @param projectRoadaddressGeometry Known geometry from roadpart or track section
    * @return
    */
  private def getGeometryLinkToPreviousMissingProjectLinks(missingGeometry:Seq[ProjectLink],projectRoadaddressGeometry:Seq[ProjectLink],currentLink:ProjectLink,currentMissingLinksSeq:Seq[ProjectLink]): (Seq[ProjectLink], Option[ProjectLink])={
    val nextLinkWithGeom=projectRoadaddressGeometry.filter(x=>x.endAddrMValue==currentLink.startAddrMValue)
    if (nextLinkWithGeom.nonEmpty) //found link with geometry
      (currentMissingLinksSeq++Seq(currentLink),Some(nextLinkWithGeom.head))
    else
    {
      val nextLinkFromMissingList=missingGeometry.filter(x=>x.endAddrMValue==currentLink.startAddrMValue)
      if (nextLinkFromMissingList.isEmpty) // we are probably on last link
        (currentMissingLinksSeq++Seq(currentLink),None)
      else //if we didnt find geometry, but found next missing geometry we search geometry from  next links next link
      {
        val reducedMissingGeometry=missingGeometry.filterNot(x=>x.id==currentLink.id)
        getGeometryLinkToPreviousMissingProjectLinks(reducedMissingGeometry,projectRoadaddressGeometry,nextLinkFromMissingList.head,currentMissingLinksSeq++Seq(currentLink))
      }
    }
  }

  /**
    *
    * @param linkToGenGeometry one of the links missing geometry
    * @param previousLink  link containing geometry that has same startaddrM value as adjacent seq:s highest endaddrM value
    * @param nextLink      link containing geometry that has same endaddrM value as adjacent seq:s lowest startaddrM value
         Either nextLink or previousLink exists, and error should be throw earlier if no geometry can be used to guess geometry for given links
    * @return              returns links containing geometry
    */
  private def guessGeometryForProjectLink(linkToGenGeometry:ProjectLink, previousLink:Option[ProjectLink],nextLink:Option[ProjectLink]):Seq[Point]  ={
    (previousLink, nextLink) match {
      case (Some(p), Some(n)) =>
        val geom = Seq(p.geometry.last, n.geometry.head)
        val geomLegth = GeometryUtils.geometryLength(geom)
        val pToNAddrLength = n.startAddrMValue.toDouble-p.endAddrMValue
        GeometryUtils.truncateGeometry2D(geom, geomLegth*(linkToGenGeometry.startAddrMValue - p.endAddrMValue) / pToNAddrLength,
          geomLegth*(linkToGenGeometry.endAddrMValue - p.endAddrMValue) / pToNAddrLength)
      case (None, Some(n)) => {
        val guestimateStartPoint= n.geometry.head - (n.geometry.last - n.geometry.head).normalize2D().scale(n.startAddrMValue - linkToGenGeometry.startAddrMValue)
        val guestimateEndPoint= n.geometry.head - (n.geometry.last - n.geometry.head).normalize2D().scale(n.startAddrMValue -linkToGenGeometry.endAddrMValue)
        Seq(guestimateStartPoint,guestimateEndPoint)
      }
      case (Some(p), None) =>
        val guestimateStartPoint=p.geometry.last + (p.geometry.last - p.geometry.head).normalize2D().scale(linkToGenGeometry.startAddrMValue - p.endAddrMValue)
        val guestimateEndPoint=p.geometry.last + (p.geometry.last - p.geometry.head).normalize2D().scale(linkToGenGeometry.endAddrMValue - p.endAddrMValue)
        Seq(guestimateStartPoint,guestimateEndPoint)
      case _ => throw new RuntimeException(s"RoadPart or trackpart did not have geometry to form missing geometry")
    }
  }


}
