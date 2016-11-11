package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.VVHRoadlink
import scala.collection.mutable.ListBuffer

/**
  * Class tries to find newest history link with significant change to current road links
  */
class VVHRoadLinkHistoryProcessor() {


  def process(historyLinks:Seq[VVHRoadlink], currentLinks :Seq[VVHRoadlink]) : Seq[VVHRoadlink] ={
    //val groupedRoadLinksList=historyLinks.sortBy(_ .linkId).groupBy(_.linkId)
    val groupedRoadLinksList= historyLinks.groupBy(_.linkId).toSeq.sortBy(_._1)

    var ignorelist = ListBuffer.empty[String]
    var listOfNewestChangedHistoryLinks=scala.collection.mutable.ArrayBuffer.empty[VVHRoadlink]
    for ( groupedRoadLinks<-groupedRoadLinksList)
    {
      if (!ignorelist.contains(groupedRoadLinks._1.toString)) {  //check for already searched ids, which have occured in recursive linked list search
      val newAndChangedLink=getNewestAndChangedLink(groupedRoadLinks._2)
        val link=newAndChangedLink._1
        val changedlink=newAndChangedLink._2
        val newLinkid = link.attributes.getOrElse("LINKID_NEW",None)
        if (newLinkid != None)
        {
          val appendedlistignoreList=(ignorelist++List(link.linkId.toString)).toList
          val newerLink=getNewestId(newLinkid.toString, groupedRoadLinksList.toMap, appendedlistignoreList,currentLinks)
          newerLink._1 match {

            case Some(newestRoadLink:VVHRoadlink) =>
            {

              if (!ignorelist.contains(newestRoadLink.linkId.toString)) // Ignores link chains that have already been processed
              {
                if (!newerLink._3)
                { // check if link with enough coordinate change has been found


                  newerLink._4 match { // no change found yet in recursion
                    case Some(nHlink: VVHRoadlink) =>
                    {
                      if (compareGeoOfLinks(nHlink,link))
                      {//Change between current link value significant enough
                        listOfNewestChangedHistoryLinks += newestRoadLink
                        ignorelist += newestRoadLink.linkId.toString
                        ignorelist ++= newerLink._2
                      }
                      else if (compareGeoOfLinks(nHlink,changedlink))
                      {//  Change significant enough was found in one of the links with current link-id
                        listOfNewestChangedHistoryLinks += newestRoadLink
                        ignorelist += newestRoadLink.linkId.toString
                        ignorelist ++= newerLink._2
                      }
                      else
                      { // no significant change
                        ignorelist += newestRoadLink.linkId.toString
                        ignorelist ++= newerLink._2
                      }
                    }
                    case None => //this when when link chain has not found "current link". This might be because link was deleted
                    {
                      listOfNewestChangedHistoryLinks += newestRoadLink
                      ignorelist += newestRoadLink.linkId.toString
                      ignorelist ++= newerLink._2
                    }
                  }
                }
                else
                { // this when significant coordinate change has been found inside recursion

                  listOfNewestChangedHistoryLinks += newestRoadLink
                  ignorelist += link.linkId.toString
                  ignorelist ++= newerLink._2
                }
              }
            }
            case None => //when cannot find newer link from history list
            {
              newerLink._4 match { //checks if next link exists in (non-history) Link list
                case Some (nHlink: VVHRoadlink) =>
                {
                  if (compareGeoOfLinks(nHlink,link)) //comparison if link-id is close enough to "currents loops newest link
                  {
                    listOfNewestChangedHistoryLinks += link
                    ignorelist += link.linkId.toString
                    ignorelist ++= newerLink._2
                  }


                  else if  (compareGeoOfLinks(nHlink,changedlink)) //comparison if any link with  link-id is close enough to "currents loops newest link
                  {
                    listOfNewestChangedHistoryLinks += changedlink
                    ignorelist += link.linkId.toString
                    ignorelist ++= newerLink._2
                  }
                  else
                  { // No significant change found (Ignore)

                  }
                }
                case None =>
                { // Result has been ignored in recursion. Probably because link chain reached other chain that has been processed
                  // ignored
                }
              }
            }
          }
        }
        if (newLinkid == None)
        {
          // if link does NOT have value for newer versions
          listOfNewestChangedHistoryLinks += link
          ignorelist += link.linkId.toString
        }
      }
    }
    listOfNewestChangedHistoryLinks.toList
  }

  /**
    * Recursive loop  to find newest history change, returns back in recursion until sufficient change to geometry has been found and returns it
    * @param linkid
    * @param groupedRoadLinksList
    * @param ignoreList
    * @param currentLinks
    * @return (newerlink, List of processed links, Change of required magnitude found, comparison link that is not history link. is None if there is no "current link" to history chain)
    */

  def getNewestId(linkid:String,groupedRoadLinksList: Map[Long, Seq[VVHRoadlink]],ignoreList:List[String], currentLinks:Seq[VVHRoadlink]) : (Option[VVHRoadlink],List[String], Boolean, Option[VVHRoadlink]) =
  {
    var loopsIgnoreList=ignoreList.toBuffer
    var chainsNewestLink=  None: Option[VVHRoadlink]
    val ignoredlink= if (loopsIgnoreList.toList.contains(linkid)) true else false

    if (!ignoredlink)
    {
      for ( groupedRoadLinks<-groupedRoadLinksList)
      {
        if(groupedRoadLinks._1.toString==linkid)
        {
          val newestAndChangedLink=getNewestAndChangedLink(groupedRoadLinks._2)
          val link = newestAndChangedLink._1
          val changedlink=newestAndChangedLink._2
          val newLinkid = link.attributes.getOrElse("LINKID_NEW", None)
          if (newLinkid != None)
          {
            val appendedIgnoreList=(loopsIgnoreList++List(linkid)).toList
            val newerLink=getNewestId(newLinkid.toString, groupedRoadLinksList, appendedIgnoreList,currentLinks)
            newerLink._1 match
            {
              case Some(newestRoadLink: VVHRoadlink) =>
              {
                if (!loopsIgnoreList.contains(newestRoadLink.linkId.toString))
                {
                  loopsIgnoreList += link.linkId.toString
                  loopsIgnoreList ++= newerLink._2 //updates ignorelist from recursion
                  if (!newerLink._3) // check if link with enough coordinate change has been found
                  {
                    newerLink._4 match
                    {
                      case Some(nLink:VVHRoadlink) => // inside case we check if newest link, or changed link is changed enough
                      {
                        if (compareGeoOfLinks(nLink,link))
                          return (Some(link), loopsIgnoreList.toList, true, newerLink._4)
                        else if (compareGeoOfLinks(nLink,changedlink))
                          return (Some(changedlink), loopsIgnoreList.toList, true, newerLink._4)
                        else
                          return (Some(link), loopsIgnoreList.toList, false, newerLink._4)
                      }
                      case None => // case where there is no current link for history link (for example deleted link)
                        // check
                        /*val filtterkey=newestRoadLink.linkId
                        val idgroup= groupedRoadLinksList.filterKeys(Set(newestRoadLink.linkId)).toSeq.unzip._2*/
                        //getNewestAndChangedLink(idgroup)
                        return (newerLink._1,loopsIgnoreList.toList,false,newerLink._4)
                    }
                  }
                  else
                  { // just passes values to previous loop when significant coordinate change has been found
                    return (Some(newestRoadLink), loopsIgnoreList.toList, newerLink._3, newerLink._4)
                  }
                }
              }
              case None =>
              {
                //None when cannot find newer link from history list
                newerLink._4 match { //checks if next link exists in (non-history) Link list and if it exists returns changed l
                  case Some (nHlink: VVHRoadlink) =>
                  {
                    if (compareGeoOfLinks(nHlink,link)) //comparison if link-id is close enough to "currents loops newest link
                      return (Some(link),loopsIgnoreList.toList,true,newerLink._4)
                    else if  (compareGeoOfLinks(nHlink,changedlink)) //changed link is link (which same link id as link, but has been updated enough to consider "changed link"
                    {
                      return (Some(changedlink),loopsIgnoreList.toList,true,newerLink._4) // there was link changed enough with same id (just not the newest  one)
                    }
                    else
                    {
                      return (Some(link),loopsIgnoreList.toList,false,newerLink._4) // could not find link-id where coordinates have been changed enough
                    }
                  }
                  case None =>
                  {
                    return (Some(link),loopsIgnoreList.toList,false,None) // could not find link-id for current link
                  }
                }
              }
            }
          }
          if (newLinkid == None)
          {
            //Link has no new link id, this means link has been deleted and not changed
            return (Some(link),loopsIgnoreList.toList,false,None)
          }

        }
      }
      // when cannot find from history, but has new link id
      // check currentlist and return current link
      for (clink<-currentLinks)
      {
        if (clink.linkId.toString==linkid)
        {
          return (chainsNewestLink,loopsIgnoreList.toList,false,Some(clink))
        }
      }
    }
    //if ignored or next link was unreachable return this None object. Ignored when value has already been processed
    return (chainsNewestLink,loopsIgnoreList.toList,false,chainsNewestLink)
  }



/*******************  Helper methods after this           *****/
  /**  Checks if geometry differs more than 3
    *
    * @param hLink link nro 1
    * @param cLink link nro 2
    * @return true when distance is crater than 1 but less than 50 else false. Also true if number of geometries is different
    */
  def compareGeoOfLinks( hLink :VVHRoadlink, cLink:VVHRoadlink): Boolean =
  {
    if (hLink.geometry.size==cLink.geometry.size)
    {
      val geometryComparison =hLink.geometry.zip(cLink.geometry)
      for(comparison  <-geometryComparison)
      {
        // Check if geometry is with in bounds
        if (comparison._1.distance2DTo(comparison._2) >= 5 &&  comparison._1.distance2DTo(comparison._2)<=50)
          return true
        }
    }
    false
  }

  /**
    * Checks which link-id element is the newest and which is first to change enough from first element
    * @return
    */
  def getNewestAndChangedLink(linksOfTheRoad :Seq[VVHRoadlink]) : (VVHRoadlink,VVHRoadlink) = // returns (newest link,first to change)
  {
    def endDate(vVHRoadlink: VVHRoadlink) =
    {
      vVHRoadlink.attributes.getOrElse("END_DATE", BigInt(0)).asInstanceOf[BigInt].longValue()
    }
    def getFirsttoChangedLink(sortedlinklist:Seq[VVHRoadlink], linktocompare:VVHRoadlink) : VVHRoadlink =
    {
      for ( link <-sortedlinklist)
      {
        if (link.geometry.size!= linktocompare.geometry.size) //if geometry is missing "node" it is considered changed
          return link
        if (compareGeoOfLinks(link, linktocompare)) //if changed enough return
          return link
      }
      linktocompare
    }

    val newestLink=linksOfTheRoad.maxBy(endDate)
    val changedLink= if (linksOfTheRoad.size > 1) getFirsttoChangedLink(linksOfTheRoad.sortBy(_.attributes.getOrElse("END_DATE",BigInt(0)).asInstanceOf[BigInt].longValue()).reverse,newestLink)  else {newestLink}
    (newestLink,changedLink)
  }
}