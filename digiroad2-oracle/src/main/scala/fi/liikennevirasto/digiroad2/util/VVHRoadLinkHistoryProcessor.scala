package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{GeometryUtils, VVHRoadlink}
import org.geotools.filter.function.GeometryTransformation
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer

/**
  * This class is used to filter history road links from VVH. Filtered history links are used to show history information on map (DROTH-10).
  *
  * @param includeCurrentLinks If true, compare history with current road links within tolerance
  * @param minimumChange Tolerance minimum value
  * @param maximumChange Tolerance maximum value
  */
class VVHRoadLinkHistoryProcessor(includeCurrentLinks: Boolean = false, minimumChange: Double = 1.0, maximumChange: Double = 50.0) {

  /**
    * This method returns the latest history links that:
    * - have been deleted
    * - have been changed and are within tolerance (when includeCurrentLinks flag is set as true)
    *
    * @param historyRoadLinks All history road links
    * @param roadLinks Current road links
    * @return Filtered history links
    */
  def process(historyRoadLinks:Seq[VVHRoadlink], roadLinks :Seq[VVHRoadlink]) : Seq[VVHRoadlink] ={
    def endDate(vvhRoadlink: VVHRoadlink) =
      vvhRoadlink.attributes.getOrElse("END_DATE", BigInt(0)).asInstanceOf[BigInt].longValue()

    def newLinkId(vvhRoadlink: VVHRoadlink) : Option[BigInt] = {
      vvhRoadlink.attributes.get("LINKID_NEW") match {
        case Some(linkId) =>
          Some(linkId.asInstanceOf[BigInt].longValue())
        case _ =>
          None
      }
    }

    def hasNewLinkId(vvhRoadlink: VVHRoadlink) = newLinkId(vvhRoadlink).isEmpty

    // If several history link items have the same linkId, pick the one with latest endDate
    val latestHistory = historyRoadLinks.groupBy(_.linkId).mapValues(rl => rl.maxBy(endDate)).map(_._2)

    // Deleted = history link has newLinkId and it's linkId is not found in current links
    val deletedRoadlinks = latestHistory.filter(hasNewLinkId).filterNot(rl => roadLinks.exists(rl.linkId == _.linkId))

    // Changed = linkId of history link is found in current links
    val changedRoadlinks = latestHistory.filter{
      rl =>
        val roadlink = roadLinks.find(r => Some(r.linkId) == newLinkId(rl) || (r.linkId == rl.linkId && includeCurrentLinks))
        roadlink.exists(r =>
          //GeometryUtils.withinTolerance(r.geometry, rl.geometry, minimumChange, maximumChange)
          compareGeoOfLinks(r, rl)
        )
    }

    (changedRoadlinks ++ deletedRoadlinks).toSeq
  }

  /**
    * This method is an old version of the process function. It can be used if filtering with date values is needed later.
    *
    * @param historyLinks
    * @param currentLinks
    * @return
    */
  def process1(historyLinks: Seq[VVHRoadlink], currentLinks: Seq[VVHRoadlink]) : Seq[VVHRoadlink] = {
    val groupedRoadLinksList = historyLinks.groupBy(_.linkId).toSeq.sortBy(_._1)
    var ignoreList = ListBuffer.empty[Long]   // Ids of history links already searched, which have occurred in recursive linked list search
    var listOfNewestChangedHistoryLinks = ArrayBuffer.empty[VVHRoadlink]  // History links to be returned

    for (groupedRoadLinks <- groupedRoadLinksList) {
      if (!ignoreList.contains(groupedRoadLinks._1)) {
        val (link, changedLink) = getNewestAndChangedLink(groupedRoadLinks._2)

        link.attributes.get("LINKID_NEW") match {
          case Some(newLinkIdBigInt: BigInt) => {
            val appendedListIgnoreList = (ignoreList ++ List(link.linkId)).toList
            val (newestLink, updatedIgnoreList, isMatchingLinkFound, matchingCurrentLink) = getNewestId(newLinkIdBigInt.toLong, groupedRoadLinksList.toMap, appendedListIgnoreList, currentLinks)

            newestLink match {
              case Some(newestRoadLink: VVHRoadlink) => {
                if (!ignoreList.contains(newestRoadLink.linkId)) { // Ignores link chains that have already been processed
                  if (!isMatchingLinkFound) {
                    // check if link with enough coordinate change has been found
                    matchingCurrentLink match {
                      // no change found yet in recursion
                      case Some(nonHistoryLink: VVHRoadlink) => {
                        if (includeCurrentLinks && compareGeoOfLinks(nonHistoryLink, link)) {
                          //Change between current link value significant enough when undeleted link histories are also showed
                          listOfNewestChangedHistoryLinks += newestRoadLink
                          ignoreList += newestRoadLink.linkId
                          ignoreList ++= updatedIgnoreList
                        }
                        else if (includeCurrentLinks && compareGeoOfLinks(nonHistoryLink, changedLink)) {
                          //  Change significant enough was found in one of the links with current link-id when undeleted link histories are also showed
                          listOfNewestChangedHistoryLinks += newestRoadLink
                          ignoreList += newestRoadLink.linkId
                          ignoreList ++= updatedIgnoreList
                        }
                        else {
                          // no significant change or only deleted links are wanted
                          ignoreList += newestRoadLink.linkId
                          ignoreList ++= updatedIgnoreList
                        }
                      }
                      case None => {
                        //this when when link chain has not found "current link". This might be because link was deleted
                        listOfNewestChangedHistoryLinks += newestRoadLink
                        ignoreList += newestRoadLink.linkId
                        ignoreList ++= updatedIgnoreList
                      }
                    }
                  }
                  else {
                    // this when significant coordinate change has been found inside recursion
                    if (includeCurrentLinks) listOfNewestChangedHistoryLinks += newestRoadLink  // only added if current link changes are wanted

                    ignoreList += newestRoadLink.linkId
                    ignoreList ++= updatedIgnoreList
                  }
                }
              }
              case None => {
                //when cannot find newer link from history list
                matchingCurrentLink match {
                  //checks if next link exists in current (non-history) Link list
                  case Some(nonHistoryLink: VVHRoadlink) => {
                    if (includeCurrentLinks) {
                      if (compareGeoOfLinks(nonHistoryLink, link)) {
                        //comparison if link-id is close enough and not too far from "currents loops newest link
                        listOfNewestChangedHistoryLinks += link
                        ignoreList += link.linkId
                        ignoreList ++= updatedIgnoreList
                      }
                      else if (compareGeoOfLinks(nonHistoryLink, changedLink)) {
                        listOfNewestChangedHistoryLinks += changedLink
                        ignoreList += link.linkId
                        ignoreList ++= updatedIgnoreList
                      }
                      else {
                        // No significant change found (Ignore)
                        ignoreList += link.linkId
                        ignoreList ++= updatedIgnoreList
                      }
                    }
                    else {
                      ignoreList += link.linkId
                      ignoreList ++= updatedIgnoreList
                    }
                  }
                  case None => {
                    // Result has been ignored in recursion. Probably because link chain reached other chain that has been processed
                  }
                }
              }
            }
          }
          case _ => {
            // if link does NOT have value for newer versions
            currentLinks.find(_.linkId == link.linkId) match {
              case Some(currentLink:VVHRoadlink) => {
                //History link-id is found in current-link list
                if(includeCurrentLinks && compareGeoOfLinks(link, currentLink))
                {

                  listOfNewestChangedHistoryLinks += link
                  ignoreList += link.linkId
                }
              }
              case _=> {
                // Link is deleted link
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

  private def getNewestId(linkid: Long,groupedRoadLinksList: Map[Long, Seq[VVHRoadlink]], ignoreList: List[Long], currentLinks: Seq[VVHRoadlink]): (Option[VVHRoadlink], List[Long], Boolean, Option[VVHRoadlink]) =
  {
    var loopsIgnoreList = ignoreList.toBuffer[Long]
    val nullRoadLink = None: Option[VVHRoadlink]
    val ignoredLink = if (loopsIgnoreList.toList.contains(linkid)) true else false

    if (!ignoredLink) {
      val groupLinks = groupedRoadLinksList.find(_._1 == linkid)
      groupLinks match {
        case Some(groupedRoadLinks: (Long,Seq[VVHRoadlink])) => {
          //link with corresponding id found
          if (groupedRoadLinks._1 == linkid) {
            val (link, changedLink) = getNewestAndChangedLink(groupedRoadLinks._2)
            link.attributes.get("LINKID_NEW") match {
              case Some(newLinkIdBigInt: BigInt) => {
                val appendedIgnoreList = (loopsIgnoreList ++ List(linkid)).toList
                val (newestLink, updatedIgnoreList, isMatchingLinkFound, matchingCurrentLink) = getNewestId(newLinkIdBigInt.toLong, groupedRoadLinksList, appendedIgnoreList, currentLinks)
                newestLink match {
                  case Some(newestRoadLink: VVHRoadlink) => {
                    if (!loopsIgnoreList.contains(newestRoadLink.linkId)) {
                      loopsIgnoreList += link.linkId
                      loopsIgnoreList ++= updatedIgnoreList //updates ignoreList from recursion
                      if (!isMatchingLinkFound) {
                        // check if link with enough coordinate change has been found
                        matchingCurrentLink match {
                          case Some(nLink: VVHRoadlink) => {
                            // check if newest link, or changed link is changed enough
                            if (compareGeoOfLinks(nLink, link))
                              return (Some(link), loopsIgnoreList.toList, true, matchingCurrentLink)
                            else if (compareGeoOfLinks(nLink, changedLink))
                              return (Some(changedLink), loopsIgnoreList.toList, true, matchingCurrentLink)
                            else
                              return (Some(newestRoadLink ), loopsIgnoreList.toList, false, matchingCurrentLink)
                          }
                          case None => // case where there is no current link for history link (for example deleted link)
                            return (newestLink, loopsIgnoreList.toList, false, matchingCurrentLink)

                        }
                      }
                      else {
                        // just passes values to previous loop when significant coordinate change has been found
                        return (Some(newestRoadLink), loopsIgnoreList.toList, isMatchingLinkFound, matchingCurrentLink)
                      }
                    }
                  }
                  case None => {
                    //None when cannot find newer link from history list
                    matchingCurrentLink match {
                      //checks if next link exists in (non-history) link list and if it exists returns changed link
                      case Some(nonHistoryLinks: VVHRoadlink) => {
                        if (compareGeoOfLinks(nonHistoryLinks, link)) //comparison if link-id is close enough to "currents loops newest link
                          return (Some(link), loopsIgnoreList.toList, true, matchingCurrentLink)
                        else if (compareGeoOfLinks(nonHistoryLinks, changedLink)){
                          //changed link is link (which same link id as link, but has been updated enough to consider "changed link"
                          return (Some(changedLink), loopsIgnoreList.toList, true, matchingCurrentLink) // there was link changed enough with same id (just not the newest  one)
                        }
                        else {
                          return (Some(link), loopsIgnoreList.toList, false, matchingCurrentLink) // could not find link-id where coordinates have been changed enough
                        }
                      }
                      case None => {
                        // could not find link-id for current link
                        if (!loopsIgnoreList.contains(newLinkIdBigInt.toLong)) //checks link is not ignored
                        return (nullRoadLink, loopsIgnoreList.toList, false, None)
                        else return (Some(link), loopsIgnoreList.toList, false, None)
                      }
                    }
                  }
                }
              }
              case None => {
                //Link has no new link id = either deleted link or link can be found from current links list
                currentLinks.find(_.linkId == linkid) match {
                  case Some(currentLink:VVHRoadlink) => {
                      if (includeCurrentLinks) return (Some(link), loopsIgnoreList.toList, false, Some(currentLink))
                      else {
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
        case None => {
          // Link could not be found from history, checks current links
          return (nullRoadLink,loopsIgnoreList.toList,false,currentLinks.find(_.linkId == linkid))
        }
      }
    }
    //if ignored or next link was unreachable return this None object. Ignored when value has already been processed
    (nullRoadLink, loopsIgnoreList.toList, false, nullRoadLink)
  }
  /**
    *
    * @param historyLink link nro 1
    * @param currentLink link nro 2
    * @return true when distance is greater than minimum but less than maximum else false. Also true if number of geometries is different
    */
  private def compareGeoOfLinks( historyLink: VVHRoadlink, currentLink: VVHRoadlink): Boolean = {
    if (historyLink.geometry.size == currentLink.geometry.size) {
      val geometryComparison = historyLink.geometry.zip(currentLink.geometry)
      for (comparison  <- geometryComparison) {
        // Check if geometry is with in bounds
        if (comparison._1.distance2DTo(comparison._2) >= minimumChange &&  comparison._1.distance2DTo(comparison._2) <= maximumChange)
          return true
      }
    }
    false
  }

  /**
    * Checks which link-id element is the newest and which is first to change enough from first element
    *
    * @param linksOfTheRoad
    * @return
    */
  private def getNewestAndChangedLink(linksOfTheRoad :Seq[VVHRoadlink]): (VVHRoadlink,VVHRoadlink) = {
   // returns (newest link,first to change)
    def endDate(vVHRoadlink: VVHRoadlink) = {
      vVHRoadlink.attributes.getOrElse("END_DATE", BigInt(0)).asInstanceOf[BigInt].longValue()
    }
    def getFirstToChangedLink(sortedLinkList:Seq[VVHRoadlink], linkToCompare:VVHRoadlink): VVHRoadlink = {
      for (link <- sortedLinkList) {
        if (link.geometry.size != linkToCompare.geometry.size) //if geometry is missing "node" it is considered changed
          return link
        if (compareGeoOfLinks(link, linkToCompare)) //if changed enough return
          return link
      }
      linkToCompare
    }

    val newestLink = linksOfTheRoad.maxBy(endDate)
    val changedLink = if (linksOfTheRoad.size > 1) getFirstToChangedLink(linksOfTheRoad.sortBy(_.attributes.getOrElse("END_DATE", BigInt(0)).asInstanceOf[BigInt].longValue()).reverse, newestLink)  else {newestLink}
    (newestLink,changedLink)
  }
}