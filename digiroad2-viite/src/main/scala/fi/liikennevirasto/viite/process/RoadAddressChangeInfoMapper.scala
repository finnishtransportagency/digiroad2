package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.{ChangeInfo, Point}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress}
import org.slf4j.LoggerFactory

object RoadAddressChangeInfoMapper extends RoadAddressMapper {
  private val logger = LoggerFactory.getLogger(getClass)

  private def createAddressMap(sources: Seq[ChangeInfo]): Seq[RoadAddressMapping] = {
    val pseudoGeom = Seq(Point(0.0, 0.0), Point(1.0, 0.0))
    sources.map(ci => {
      ci.changeType match {
        case 1 =>
          logger.debug("Change info> oldId: "+ci.oldId+" newId: "+ci.newId+" changeType: "+ci.changeType)
          Some(RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
            ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp)))
        case 2 =>
          logger.debug("Change info> oldId: "+ci.oldId+" newId: "+ci.newId+" changeType: "+ci.changeType)
          Some(RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
            ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp)))
        case _ => None
      }
    }).filter(c => c.isDefined).map(_.get)
  }

  private def applyChanges(changes: Seq[Seq[ChangeInfo]], roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changes.isEmpty)
      roadAddresses
    else {
      val mapping = createAddressMap(changes.head)
      val mapped = roadAddresses.mapValues(_.flatMap(ra =>
        if (mapping.exists(_.matches(ra))) {
          val changeVVHTimestamp = mapping.head.vvhTimeStamp.get
          mapRoadAddresses(mapping)(ra).map(_.copy(adjustedTimestamp = changeVVHTimestamp))
        }
        else
          Seq(ra)))
      applyChanges(changes.tail, mapped.values.toSeq.flatten.groupBy(_.linkId))
    }
  }

  def resolveChangesToMap(roadAddresses: Map[Long, Seq[RoadAddress]], changedRoadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Map[Long, Seq[RoadAddress]] = {
    val sections = partition(roadAddresses.values.toSeq.flatten)
    val originalAddressSections = groupByRoadSection(sections, roadAddresses.values.toSeq.flatten)
    preTransferCheckBySection(originalAddressSections)
    val groupedChanges = changes.groupBy(_.vvhTimeStamp).values.toSeq
    val appliedChanges = applyChanges(groupedChanges.sortBy(_.head.vvhTimeStamp), roadAddresses)
    val result = postTransferCheckBySection(groupByRoadSection(sections, appliedChanges.values.toSeq.flatten), originalAddressSections)
    result.values.toSeq.flatten.groupBy(_.linkId)
  }

  private def groupByRoadSection(sections: Seq[RoadAddressSection],
                                 roadAddresses: Seq[RoadAddress]): Map[RoadAddressSection, Seq[RoadAddress]] = {
    sections.map(section => section -> roadAddresses.filter(section.includes)).toMap
  }

  // TODO: Don't try to apply changes to invalid sections
  private def preTransferCheckBySection(sections: Map[RoadAddressSection, Seq[RoadAddress]]) = {
    sections.values.map( seq =>
      try {
        preTransferChecks(seq)
        true
      } catch {
        case ex: InvalidAddressDataException =>
          logger.info(s"Section had invalid road data ${seq.head.roadNumber}/${seq.head.roadPartNumber}: ${ex.getMessage}")
          false
      })
  }

  private def postTransferCheckBySection(sections: Map[RoadAddressSection, Seq[RoadAddress]],
                                         original: Map[RoadAddressSection, Seq[RoadAddress]]): Map[RoadAddressSection, Seq[RoadAddress]] = {
    sections.map(s =>
      try {
        postTransferChecks(s)
        s
      } catch {
        case ex: InvalidAddressDataException =>
          logger.info(s"Invalid address data after transfer on ${s._1}, not applying changes")
          s._1 -> original(s._1)
      }
    )
  }


}
