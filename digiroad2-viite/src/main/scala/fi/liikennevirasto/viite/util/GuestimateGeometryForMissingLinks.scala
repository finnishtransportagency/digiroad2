package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.dao.{BaseRoadAddress}

class GuestimateGeometryForMissingLinks {

  /**
    * Loops through geometry and tries to find geometry to missing links
    * @param missingGeometry
    * @param withGeometry
    * @return
    */
  def guestimateGeometry[T <: BaseRoadAddress](missingGeometry:Seq[T], withGeometry:Seq[T]):Seq[T]={
    def matchingPrevious(missingLink: T)(candidate: T) = {
      candidate.endAddrMValue==missingLink.startAddrMValue &&
        candidate.roadNumber==missingLink.roadNumber &&
        candidate.roadPartNumber==missingLink.roadPartNumber &&
        candidate.track==missingLink.track
    }
    def matchingNext(missingLink: T)(candidate: T) = {
      matchingPrevious(candidate)(missingLink)
    }
    def matchingTrack(link: T)(candidate: T) = {
      candidate.roadNumber==link.roadNumber && candidate.roadPartNumber==link.roadPartNumber && candidate.track==link.track
    }
    missingGeometry.lastOption match {
      case Some(missingLink) =>
      val previousLink=withGeometry.find(matchingPrevious(missingLink))
        val nextLink=withGeometry.find(matchingNext(missingLink))
        (previousLink, nextLink) match {
          case (p,n) if p.nonEmpty && n.nonEmpty => //we check if we have links with geometry on both sides
          val addedGeometry = missingLink.copyWithGeometry(Seq(p.head.geometry.last,n.head.geometry.head)).asInstanceOf[T]
            if (missingGeometry.size>1)
              guestimateGeometry(missingGeometry.dropRight(1), withGeometry++Seq(addedGeometry))
            else
              withGeometry++Seq(addedGeometry)

          case (p,n) =>  //if we didnt have geometry on both sides we have to check if we have any any geometry and then find geometry we can use
            val foundGeometryOnRoadPartOrTrack = withGeometry.filter(matchingTrack(missingLink))
            if (foundGeometryOnRoadPartOrTrack.isEmpty)
              throw new RuntimeException(s"Road part or track part did not have geometry to form a guesstimate of missing geometry")
            val missingGeometryOnRoadPartOrTrack = missingGeometry.filter(matchingTrack(missingLink))
            val (adjacentLinksWithNoGeometry, previousLinkToChain) =
              getGeometryLinkToPreviousMissingLinks(missingGeometryOnRoadPartOrTrack, foundGeometryOnRoadPartOrTrack,
                missingLink, Seq.empty[T])
            val adjacentIdSeq = adjacentLinksWithNoGeometry.map(x => x.id)
            // we form geometry to all adjacent link on road or track part here
            val geometrizedLinks = adjacentLinksWithNoGeometry.map(x => x.copyWithGeometry(
              guessGeometryForLink(x, previousLinkToChain, n)).asInstanceOf[T])
            val stillMissingGeometry=missingGeometry.filterNot(x => adjacentIdSeq.contains(x.id))
            if(stillMissingGeometry.isEmpty)
              return withGeometry ++ geometrizedLinks
            guestimateGeometry(stillMissingGeometry, withGeometry ++ geometrizedLinks)
        }
      case None => withGeometry
      case _=>  throw new RuntimeException("Undefined state when guessing geometry") }
  }

  /**
    *
    * @param link one of the links missing geometry
    * @param previousLink  link containing geometry that has same startaddrM value as adjacent seq:s highest endaddrM value
    * @param nextLink      link containing geometry that has same endaddrM value as adjacent seq:s lowest startaddrM value
         Either nextLink or previousLink exists, and error should be throw earlier if no geometry can be used to guess geometry for given links
    * @return              returns links containing geometry
    */
  private def guessGeometryForLink[T <: BaseRoadAddress](link: T, previousLink: Option[T], nextLink: Option[T]): Seq[Point]  ={
    (previousLink, nextLink) match {
      case (Some(p), Some(n)) =>
        val geom = Seq(p.geometry.last, n.geometry.head)
        val geomLength = GeometryUtils.geometryLength(geom)
        val pToNAddrLength = n.startAddrMValue.toDouble-p.endAddrMValue
        GeometryUtils.truncateGeometry2D(geom, geomLength*(link.startAddrMValue - p.endAddrMValue) / pToNAddrLength,
          geomLength*(link.endAddrMValue - p.endAddrMValue) / pToNAddrLength)
      case (None, Some(n)) => {
        val start= n.geometry.head - (n.geometry.last - n.geometry.head).normalize2D().scale(n.startAddrMValue - link.startAddrMValue)
        val end= n.geometry.head - (n.geometry.last - n.geometry.head).normalize2D().scale(n.startAddrMValue -link.endAddrMValue)
        Seq(start,end)
      }
      case (Some(p), None) =>
        val start=p.geometry.last + (p.geometry.last - p.geometry.head).normalize2D().scale(link.startAddrMValue - p.endAddrMValue)
        val end=p.geometry.last + (p.geometry.last - p.geometry.head).normalize2D().scale(link.endAddrMValue - p.endAddrMValue)
        Seq(start,end)
      case _ => throw new RuntimeException(s"Road part or track part did not have geometry to form missing geometry")
    }
  }

  /**
    * Gets link sequence missing geometry
    * @param missingGeometry roadlinks from roadpart or track section that are missing geometry
    * @param projectRoadAddressGeometry Known geometry from roadpart or track section
    * @return
    */
  private def getGeometryLinkToPreviousMissingLinks[T <: BaseRoadAddress](missingGeometry:Seq[T], projectRoadAddressGeometry:Seq[T], currentLink:T, currentMissingLinksSeq:Seq[T]): (Seq[T], Option[T])={
    val nextLinkWithGeom=projectRoadAddressGeometry.filter(x=>x.endAddrMValue==currentLink.startAddrMValue)
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
        getGeometryLinkToPreviousMissingLinks(reducedMissingGeometry,projectRoadAddressGeometry,nextLinkFromMissingList.head,currentMissingLinksSeq++Seq(currentLink))
      }
    }
  }

}
