package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.dao.{BaseRoadAddress}

class GuestimateGeometryForMissingLinks {

  /**
    * Loops through geometry and tries to find geometry to missing links
    * @param missingGeometry
    * @param projectRoadaddressGeometry
    * @return
    */
  def guestimateGeometry[T <: BaseRoadAddress](missingGeometry:Seq[T],projectRoadaddressGeometry:Seq[T]):Seq[T]={
    missingGeometry.lastOption match {
      case Some(missingRoadAddress) =>
      {
        val previousLink=projectRoadaddressGeometry.find(x=>x.endAddrMValue==missingRoadAddress.startAddrMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        val nextLink=projectRoadaddressGeometry.find(x=>x.startAddrMValue==missingRoadAddress.endAddrMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        (previousLink, nextLink) match {
          case (p,n) if p.nonEmpty && n.nonEmpty => //we check if we have links with geometry on both sides
          {
            val roadaddressWithAddedGeometry=missingRoadAddress.copyWithGeometry(Seq(p.head.geometry.last,n.head.geometry.head)).asInstanceOf[T]
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
            val nextLinkGeometry:Option[T]=  if (n.nonEmpty) n else None
            val missingGeometryOnRoadPartOrTrack= missingGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
            val (adjacentLinksWithNoGeometry,previousLinkToChain)=getGeometryLinkToPreviousMissingLinks(missingGeometryOnRoadPartOrTrack,foundGeometryOnRoadPartOrTrack,missingRoadAddress,Seq.empty[T])
            val adjacentIdSeq=adjacentLinksWithNoGeometry.map(x=>x.id)
            // we form geometry to all adjacent link on road or track part here
            val geometrizedLinks=adjacentLinksWithNoGeometry.map(x=>x.copyWithGeometry(guessGeometryForLink(x,previousLinkToChain,nextLinkGeometry)).asInstanceOf[T])
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
  private def guessGeometryForLink[T <: BaseRoadAddress](linkToGenGeometry:T, previousLink:Option[T],nextLink:Option[T]):Seq[Point]  ={
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
