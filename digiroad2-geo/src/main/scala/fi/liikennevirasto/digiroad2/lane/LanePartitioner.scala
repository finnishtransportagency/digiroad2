package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.{GraphPartitioner, RoadLink}


object LanePartitioner extends GraphPartitioner {


  protected def partitionAdditionalLanes(additionalLanesOnRoad: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
    val additionalLanesMapped = additionalLanesOnRoad.groupBy(lane =>
      lane.laneAttributes.find(_.publicId == "lane_code").get.values.headOption match {
      case Some(laneValue) =>
        laneValue.value
      case _ => false
    })
    additionalLanesMapped.values.toSeq.flatten
  }

  protected def partitionByMainLane(lanes: Seq[PieceWiseLane]): (Seq[PieceWiseLane], Seq[PieceWiseLane]) = {
    val (mainLanes, additionalLanes) = lanes.partition({ lane =>
      lane.laneAttributes.find(_.publicId == "lane_code").get.values.headOption match {
        case Some(laneValue) =>
          LaneNumberOneDigit.isMainLane(laneValue.value.asInstanceOf[Int])
        case _ => false
      }
    })
    (mainLanes, additionalLanes)
  }

  def partitionBySideCodeAndLaneCode(linearAssets: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
    val (lanesOnOneDirectionRoad, lanesOnTwoDirectionRoad) = linearAssets.partition(_.sideCode == SideCode.BothDirections.value)
    val (lanesOnTowards, lanesOnAgainst) = lanesOnTwoDirectionRoad.partition(_.sideCode == SideCode.TowardsDigitizing.value)

    val (mainLanesOnOneDirectionRoad, additionalLanesOnOneDirectionRoad) = partitionByMainLane(lanesOnOneDirectionRoad)
    val (mainLanesOnTowards, additionalLanesOnTowards) = partitionByMainLane(lanesOnTowards)
    val (mainLanesOnAgainst, additionalLanesOnAgainst) = partitionByMainLane(lanesOnAgainst)

    val partitionedAdditionalLanesOne = partitionAdditionalLanes(additionalLanesOnOneDirectionRoad)
    val partitionedAdditionalLanesTowards = partitionAdditionalLanes(additionalLanesOnTowards)
    val partitionedAdditionalLanesAgainst = partitionAdditionalLanes(additionalLanesOnAgainst)

    Seq(mainLanesOnOneDirectionRoad, mainLanesOnTowards, mainLanesOnAgainst, partitionedAdditionalLanesOne,
      partitionedAdditionalLanesTowards, partitionedAdditionalLanesAgainst)

  }



  def partition(allLanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[Seq[PieceWiseLane]] = {
    def groupLanes(lanes: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
      val adjustedRoadLinks = Seq[Long]()

//      def groupWithoutIdentifier(lanes: Seq[PieceWiseLane]): Map[AnyVal, Seq[PieceWiseLane]] = {
//        var groupedAlrady = Seq[Long]()
//        lanes.groupBy(lane => {
//          val potentialLane = getContinuingWithoutIdentifier(lane)
//          potentialLane match {
//            case Some(_) =>
//              val laneBearing = GeometryUtils.calculateBearing(lane.endpoints.toSeq)
//              val potentialLaneBearing = GeometryUtils.calculateBearing(potentialLane.get.endpoints.toSeq)
//
//              (laneBearing - potentialLaneBearing).abs < 45
//            case None =>
//          }
//        })
//
//      }

      //Returns option of lane continuing from lane that we are inspecting
      def getContinuingWithIdentifier(lane: PieceWiseLane, laneRoadIdentifier: Option[Either[Int,String]]): Option[PieceWiseLane] = {
        lanes.find(potentialLane => potentialLane.endpoints.map(point =>
          point.round()).exists(lane.endpoints.map(point =>
          point.round()).contains)
          && potentialLane.id != lane.id && !adjustedRoadLinks.contains(potentialLane.linkId) &&
          laneRoadIdentifier == roadLinks.values.find(roadLink => roadLink.linkId == potentialLane.linkId).get.roadIdentifier)
      }

      //TODO katkeaa haarautuessa risteyspaikassa, jos risteää nimellisen kanssa, niin valinta menee yli
      def getContinuingWithoutIdentifier(lane: PieceWiseLane): Option[PieceWiseLane] = {
        lanes.find(potentialLane => potentialLane.endpoints.map(
          point => point.round()).exists(lane.endpoints.map(point => point.round()).contains)
          && potentialLane.id != lane.id && !adjustedRoadLinks.contains(potentialLane.linkId))
      }

      //replaces lane if its sideCode is not correct. SideCode is tied to digitizing direction,
      //so two adjacent lanes with same sideCode can be on the opposite sides of the road
      def replaceLanesWithWrongSideCode(): Seq[PieceWiseLane] = {
        lanes.map(lane => {
          val laneRoadIdentifier = roadLinks.values.find(roadLink => roadLink.linkId == lane.linkId).get.roadIdentifier
          val continuingLane = laneRoadIdentifier match {
            case Some(_) => getContinuingWithIdentifier(lane, laneRoadIdentifier)
            case None => getContinuingWithoutIdentifier(lane)
          }

          continuingLane match {
            case Some(continuingLane) =>
              val connectionPoint = continuingLane.endpoints.find(point =>
                point.round() == lane.endpoints.head.round() || point.round() == lane.endpoints.last.round()).get.round()

              val laneStartPoint = lane.endpoints.minBy(_.y).round()
              val laneEndPoint = lane.endpoints.maxBy(_.y).round()
              val continuingLaneStartPoint = continuingLane.endpoints.minBy(_.y).round()
              val continuingLaneEndPoint = continuingLane.endpoints.maxBy(_.y).round()

              if ((laneEndPoint == connectionPoint && continuingLaneStartPoint == connectionPoint)
                || (laneStartPoint == connectionPoint && continuingLaneEndPoint == connectionPoint)) {
                lane
              }
              else {
                val replacement = allLanes.find(replacementLane => replacementLane.linkId == lane.linkId && replacementLane.sideCode != lane.sideCode)
                replacement match {
                  case Some(replacement) =>
                    adjustedRoadLinks :+ replacement.linkId
                    replacement
                  case None => lane
                }
              }
            case None => lane
          }
        })
      }

      val lanesAdjusted = replaceLanesWithWrongSideCode()
      val groupedLanesByRoadLinkAndSideCode = lanesAdjusted.groupBy(lane => lane.linkId)

      val (cutLaneConfiguration, uncutLaneConfiguration) = groupedLanesByRoadLinkAndSideCode.partition { case ((roadLinkId), lanes) =>
        roadLinks.get(roadLinkId) match {
          case Some(roadLink) =>
            lanes.exists { lane =>

              //end measures(have max 3 decimal digits) and roadlink length have different number of decimal digits
              val roadLinkLength = Math.round(roadLink.length * 1000).toDouble / 1000

              lane.startMeasure != 0.0d || lane.endMeasure != roadLinkLength
            }
          case _ => false
        }
      }

      val linkGroups = uncutLaneConfiguration.groupBy { case (roadLinkId, lanes) =>
        val allLanesAttributes =
          lanes.flatMap(_.laneAttributes).sortBy { laneProp =>
            val lanePropValue = laneProp.values match {
              case laneProps if laneProps.nonEmpty => laneProps.head.value.toString
              case _ => ""
            }

            (laneProp.publicId, lanePropValue)
          }

        val roadLink = roadLinks.get(roadLinkId)
        val roadIdentifier = roadLink.flatMap(_.roadIdentifier)

        (roadIdentifier, roadLink.map(_.administrativeClass), allLanesAttributes, roadLinkId == 0)
      }

//      val lanesWithOutIdentifier = linkGroups.filterNot { case ((roadIdentifier, _, _, _), _) => roadIdentifier.isDefined }.values.flatMap(_.values).flatten.toSeq
//      val lanesWithOutIdentifierGrouped = groupWithoutIdentifier(lanesWithOutIdentifier)
      val (linksToPartition, linksToPass) = linkGroups.partition { case ((roadIdentifier, _, _, _), _) => roadIdentifier.isDefined }
      val clustersAux = linksToPartition.values.map(_.values.flatten.filter(lane =>
        LaneNumberOneDigit.isMainLane(lane.laneAttributes.find(_.publicId == "lane_code").get.values.head.value.asInstanceOf[Int]))
      )

      val clusters = for (linkGroup <- clustersAux.asInstanceOf[Seq[Seq[PieceWiseLane]]];
                          cluster <- clusterLinks(linkGroup)) yield cluster

      clusters.map(linksFromCluster) ++ linksToPass.values.flatMap(_.values).toSeq ++ cutLaneConfiguration.values.toSeq
    }

    val partitionedLanes = partitionBySideCodeAndLaneCode(allLanes)
    partitionedLanes.flatMap(lane => groupLanes(lane))
  }

}
