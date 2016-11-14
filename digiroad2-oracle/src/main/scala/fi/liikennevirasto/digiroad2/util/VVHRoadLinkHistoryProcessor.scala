package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.VVHRoadlink
import scala.collection.mutable.ListBuffer

/**
  * Class tries to find newest history link with significant change to current road links
  */
class VVHRoadLinkHistoryProcessor(includeCurrentLinks:Boolean=false,minimumChange:Double=1.0, maximumChange:Double=50.0) {


  def process(historyLinks:Seq[VVHRoadlink], currentLinks :Seq[VVHRoadlink]) : Seq[VVHRoadlink] ={
    val groupedRoadLinksList= historyLinks.groupBy(_.linkId).toSeq.sortBy(_._1)
    var ignoreList = ListBuffer.empty[Long]
    var listOfNewestChangedHistoryLinks=scala.collection.mutable.ArrayBuffer.empty[VVHRoadlink]
    for ( groupedRoadLinks<-groupedRoadLinksList)
    {
      if (!ignoreList.contains(groupedRoadLinks._1)) {  //check for already searched ids, which have occurred in recursive linked list search
      val newAndChangedLink=getNewestAndChangedLink(groupedRoadLinks._2)
        val link=newAndChangedLink._1
        val changedLink=newAndChangedLink._2
        link.attributes.get("LINKID_NEW") match {
          case Some(newLinkidB:BigInt) => {
            val appendedlistignoreList = (ignoreList ++ List(link.linkId)).toList
            val newerLink = getNewestId(newLinkidB.toLong, groupedRoadLinksList.toMap, appendedlistignoreList, currentLinks)
            newerLink._1 match {
              case Some(newestRoadLink: VVHRoadlink) => {
                if (!ignoreList.contains(newestRoadLink.linkId)) // Ignores link chains that have already been processed
                {
                  if (!newerLink._3) {
                    // check if link with enough coordinate change has been found
                    newerLink._4 match {
                      // no change found yet in recursion
                      case Some(nHlink: VVHRoadlink) => {

                        if (includeCurrentLinks && compareGeoOfLinks(nHlink, link)) {
                          //Change between current link value significant enough when undeleted link histories are also showed
                          listOfNewestChangedHistoryLinks += newestRoadLink
                          ignoreList += newestRoadLink.linkId
                          ignoreList ++= newerLink._2
                        }
                        else if (includeCurrentLinks && compareGeoOfLinks(nHlink, changedLink)) {
                          //  Change significant enough was found in one of the links with current link-id when undeleted link histories are also showed
                          listOfNewestChangedHistoryLinks += newestRoadLink
                          ignoreList += newestRoadLink.linkId
                          ignoreList ++= newerLink._2
                        }
                        else {
                          // no significant change or only deleted links are wanted
                          ignoreList += newestRoadLink.linkId
                          ignoreList ++= newerLink._2
                        }
                      }
                      case None => //this when when link chain has not found "current link". This might be because link was deleted
                      {
                        listOfNewestChangedHistoryLinks += newestRoadLink
                        ignoreList += newestRoadLink.linkId
                        ignoreList ++= newerLink._2
                      }
                    }
                  }
                  else {
                    // this when significant coordinate change has been found inside recursion
                    if (includeCurrentLinks) listOfNewestChangedHistoryLinks += newestRoadLink  // only added if current link changes are wanted

                    ignoreList += newestRoadLink.linkId
                    ignoreList ++= newerLink._2
                  }
                }
              }
              case None => //when cannot find newer link from history list
              {
                newerLink._4 match {
                  //checks if next link exists in current (non-history) Link list
                  case Some(nHlink: VVHRoadlink) => {
                    if (includeCurrentLinks) {
                      if (compareGeoOfLinks(nHlink, link)) //comparison if link-id is close enough and not too far from "currents loops newest link
                      {
                        listOfNewestChangedHistoryLinks += link
                        ignoreList += link.linkId
                        ignoreList ++= newerLink._2
                      }
                      else if (compareGeoOfLinks(nHlink, changedLink))
                      {
                        listOfNewestChangedHistoryLinks += changedLink
                        ignoreList += link.linkId
                        ignoreList ++= newerLink._2
                      }
                      else {
                        // No significant change found (Ignore)
                        ignoreList += link.linkId
                        ignoreList ++= newerLink._2
                      }
                    }
                    else
                    {
                      ignoreList += link.linkId
                      ignoreList ++= newerLink._2
                    }
                  }
                  case None => {
                    // Result has been ignored in recursion. Probably because link chain reached other chain that has been processed
                  }
                }
              }

            }
          }
          case _ => // if link does NOT have value for newer versions
          {
            currentLinks.find(_.linkId==link.linkId) match {
              case Some(currentLink:VVHRoadlink) =>
              { //History link-id is found in current-link list
                if(includeCurrentLinks && compareGeoOfLinks(link, currentLink))
                {

                  listOfNewestChangedHistoryLinks += link
                  ignoreList += link.linkId
                }
              }
              case _=>
              {// Link is deleted link
                listOfNewestChangedHistoryLinks += link
                ignoreList += link.linkId
              }
            }
          }
        }
      }
    }
    listOfNewestChangedHistoryLinks.toList
  }

  /**
    * Recursive loop  to find newest history change
    * @param linkid  Roadlink's link-id for which newest link is wanted
    * @param groupedRoadLinksList List of all history roadlinks groupped (link-Id,VVHRoadLink) in ascending order
    * @param ignoreList list of all ignored links so far (already processed)
    * @param currentLinks List of link-id's that are still valid thus "current" links
    * @return newest VVHRoadlink of the chain, updated ignore list (processed links), boolean if link matching requirment was found, matching current roadlink if found
    */

  private def getNewestId(linkid:Long,groupedRoadLinksList: Map[Long, Seq[VVHRoadlink]],ignoreList:List[Long], currentLinks:Seq[VVHRoadlink]) : (Option[VVHRoadlink],List[Long], Boolean, Option[VVHRoadlink]) =
  {
    var loopsIgnoreList= ignoreList.toBuffer[Long]
    val nullRoadLink=  None: Option[VVHRoadlink]
    val ignoredLink= if (loopsIgnoreList.toList.contains(linkid)) true else false

    if (!ignoredLink)
    {
      val groupLinks= groupedRoadLinksList.find(_._1 ==linkid)
      groupLinks match {
        case Some(groupedRoadLinks: (Long,Seq[VVHRoadlink])) => { //link with corresponding id found
          if (groupedRoadLinks._1 == linkid) {
            val newestAndChangedLink = getNewestAndChangedLink(groupedRoadLinks._2)
            val link = newestAndChangedLink._1
            val changedlink = newestAndChangedLink._2
            link.attributes.get("LINKID_NEW") match {
              case Some(newLinkidB:BigInt) => {
                val appendedIgnoreList = (loopsIgnoreList ++ List(linkid)).toList
                val newerLink = getNewestId(newLinkidB.toLong, groupedRoadLinksList, appendedIgnoreList, currentLinks)
                newerLink._1 match {
                  case Some(newestRoadLink: VVHRoadlink) => {
                    if (!loopsIgnoreList.contains(newestRoadLink.linkId)) {
                      loopsIgnoreList += link.linkId
                      loopsIgnoreList ++= newerLink._2 //updates ignoreList from recursion
                      if (!newerLink._3) // check if link with enough coordinate change has been found
                      {
                        newerLink._4 match {

                          case Some(nLink: VVHRoadlink) => // check if newest link, or changed link is changed enough
                          {

                            if (compareGeoOfLinks(nLink, link))
                              return (Some(link), loopsIgnoreList.toList, true, newerLink._4)
                            else if (compareGeoOfLinks(nLink, changedlink))
                              return (Some(changedlink), loopsIgnoreList.toList, true, newerLink._4)
                            else
                              return (Some(newestRoadLink ), loopsIgnoreList.toList, false, newerLink._4)
                          }
                          case None => // case where there is no current link for history link (for example deleted link)
                            return (newerLink._1, loopsIgnoreList.toList, false, newerLink._4)

                        }
                      }
                      else {
                        // just passes values to previous loop when significant coordinate change has been found
                        return (Some(newestRoadLink), loopsIgnoreList.toList, newerLink._3, newerLink._4)
                      }
                    }
                  }
                  case None => {
                    //None when cannot find newer link from history list
                    newerLink._4 match {
                      //checks if next link exists in (non-history) link list and if it exists returns changed link
                      case Some(nHlink: VVHRoadlink) => {
                        if (compareGeoOfLinks(nHlink, link)) //comparison if link-id is close enough to "currents loops newest link
                          return (Some(link), loopsIgnoreList.toList, true, newerLink._4)
                        else if (compareGeoOfLinks(nHlink, changedlink)) //changed link is link (which same link id as link, but has been updated enough to consider "changed link"
                        {
                          return (Some(changedlink), loopsIgnoreList.toList, true, newerLink._4) // there was link changed enough with same id (just not the newest  one)
                        }
                        else {
                          return (Some(link), loopsIgnoreList.toList, false, newerLink._4) // could not find link-id where coordinates have been changed enough
                        }
                      }
                      case None => { // could not find link-id for current link
                        if (!loopsIgnoreList.contains(newLinkidB.toLong)) //checks link is not ignored
                        return (nullRoadLink, loopsIgnoreList.toList, false, None)
                        else return (Some(link), loopsIgnoreList.toList, false, None)
                      }
                    }
                  }
                }
              }
              case None => { //Link has no new link id = either deleted link or link can be found from current links list
                currentLinks.find(_.linkId==linkid) match {
                  case Some(currentLink:VVHRoadlink) =>
                    {

                      if (includeCurrentLinks) return (Some(link), loopsIgnoreList.toList, false, Some(currentLink))

                      else
                      {
                        loopsIgnoreList += link.linkId
                        return (Some(link), loopsIgnoreList.toList, false, Some(currentLink))
                      }
                    }
                  case _=>
                    return (Some(link), loopsIgnoreList.toList, false, None) //deleted link
                }
              }
            }
          }
        }
        case None => { // Link could not be found from history, checks current links
          return (nullRoadLink,loopsIgnoreList.toList,false,currentLinks.find(_.linkId==linkid))
        }
      }
    }
    //if ignored or next link was unreachable return this None object. Ignored when value has already been processed
     (nullRoadLink,loopsIgnoreList.toList,false,nullRoadLink)
  }
  /**
    *
    * @param hLink link nro 1
    * @param cLink link nro 2
    * @return true when distance is crater than minimum but less than maximum else false. Also true if number of geometries is different
    */
  private def compareGeoOfLinks( hLink :VVHRoadlink, cLink:VVHRoadlink): Boolean =
  {
    if (hLink.geometry.size==cLink.geometry.size)
    {
      val geometryComparison =hLink.geometry.zip(cLink.geometry)
      for(comparison  <-geometryComparison)
      {
        // Check if geometry is with in bounds
        if (comparison._1.distance2DTo(comparison._2) >= minimumChange &&  comparison._1.distance2DTo(comparison._2)<=maximumChange)
          return true
      }
    }
    false
  }

  /**
    * Checks which link-id element is the newest and which is first to change enough from first element
    * @return
    */
 private def getNewestAndChangedLink(linksOfTheRoad :Seq[VVHRoadlink]) : (VVHRoadlink,VVHRoadlink) = // returns (newest link,first to change)
  {
    def endDate(vVHRoadlink: VVHRoadlink) =
    {
      vVHRoadlink.attributes.getOrElse("END_DATE", BigInt(0)).asInstanceOf[BigInt].longValue()
    }
    def getFirstToChangedLink(sortedLinkList:Seq[VVHRoadlink], linkToCompare:VVHRoadlink) : VVHRoadlink =
    {
      for ( link <-sortedLinkList)
      {
        if (link.geometry.size!= linkToCompare.geometry.size) //if geometry is missing "node" it is considered changed
          return link
        if (compareGeoOfLinks(link, linkToCompare)) //if changed enough return
          return link
      }
      linkToCompare
    }

    val newestLink=linksOfTheRoad.maxBy(endDate)
    val changedLink= if (linksOfTheRoad.size > 1) getFirstToChangedLink(linksOfTheRoad.sortBy(_.attributes.getOrElse("END_DATE",BigInt(0)).asInstanceOf[BigInt].longValue()).reverse,newestLink)  else {newestLink}
    (newestLink,changedLink)
  }
}