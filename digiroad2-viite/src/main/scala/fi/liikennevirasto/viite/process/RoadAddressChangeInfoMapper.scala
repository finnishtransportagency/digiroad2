package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.ChangeType._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.{ChangeInfo, ChangeType, Point}
import fi.liikennevirasto.viite.dao.RoadAddress
import org.slf4j.LoggerFactory

object RoadAddressChangeInfoMapper extends RoadAddressMapper {
  private val logger = LoggerFactory.getLogger(getClass)

  private def isLengthChange(ci: ChangeInfo) = {
    Set(LengthenedCommonPart.value, LengthenedNewPart.value, ShortenedCommonPart.value, ShortenedRemovedPart.value).contains(ci.changeType)
  }

  private def isFloatingChange(ci: ChangeInfo) = {
    Set(Removed.value, ReplacedCommonPart.value, ReplacedNewPart.value, ReplacedRemovedPart.value).contains(ci.changeType)
  }

  private def max(doubles: Double*) = {
    doubles.max
  }

  private def min(doubles: Double*) = {
    doubles.min
  }

  private def fuseLengthChanges(sources: Seq[ChangeInfo]) = {
    val (lengthened, rest) = sources.partition(ci => ci.changeType == LengthenedCommonPart.value ||
    ci.changeType == LengthenedNewPart.value)
    val (shortened, others) = rest.partition(ci => ci.changeType == ShortenedRemovedPart.value ||
      ci.changeType == ShortenedCommonPart.value)
    others ++
      lengthened.groupBy(ci => (ci.newId, ci.vvhTimeStamp)).mapValues{ s =>
        val common = s.find(_.changeType == LengthenedCommonPart.value)
        val added = s.find(_.changeType == LengthenedNewPart.value)
        (common, added) match {
          case (Some(c), Some(a)) =>
            val (expStart, expEnd) = if (c.newStartMeasure.get > c.newEndMeasure.get)
              (max(c.newStartMeasure.get, a.newStartMeasure.get, a.newEndMeasure.get), min(c.newEndMeasure.get, a.newStartMeasure.get, a.newEndMeasure.get))
            else
              (min(c.newStartMeasure.get, a.newStartMeasure.get, a.newEndMeasure.get), max(c.newEndMeasure.get, a.newEndMeasure.get, a.newStartMeasure.get))
            Some(c.copy(newStartMeasure = Some(expStart), newEndMeasure = Some(expEnd)))
          case _ => None
        }
      }.values.flatten.toSeq ++
      shortened.groupBy(ci => (ci.oldId, ci.vvhTimeStamp)).mapValues{ s =>
        val common = s.find(_.changeType == ShortenedCommonPart.value)
        val removed = s.find(_.changeType == ShortenedRemovedPart.value)
        (common, removed) match {
          case (Some(c), Some(r)) =>
            val (expStart, expEnd) = if (c.oldStartMeasure.get > c.oldEndMeasure.get)
              (max(c.oldStartMeasure.get, r.newStartMeasure.get, r.newEndMeasure.get), min(c.oldEndMeasure.get, c.newStartMeasure.get, r.newEndMeasure.get))
            else
              (min(c.oldStartMeasure.get, r.newStartMeasure.get, r.newEndMeasure.get), max(c.oldEndMeasure.get, r.newEndMeasure.get, r.newStartMeasure.get))
            Some(c.copy(oldStartMeasure = Some(expStart), oldEndMeasure = Some(expEnd)))
          case _ => None
        }
      }.values.flatten.toSeq
  }

  private def createAddressMap(sources: Seq[ChangeInfo]): Seq[RoadAddressMapping] = {
    val pseudoGeom = Seq(Point(0.0, 0.0), Point(1.0, 0.0))
    fuseLengthChanges(sources).map(ci => {
      ChangeType.apply(ci.changeType) match {
        case CombinedModifiedPart | CombinedRemovedPart | DividedModifiedPart | DividedNewPart =>
          logger.debug("Change info> oldId: "+ci.oldId+" newId: "+ci.newId+" changeType: "+ci.changeType)
          Some(RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
            ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp)))
        case LengthenedCommonPart | LengthenedNewPart | ShortenedCommonPart | ShortenedRemovedPart =>
          logger.debug("Change info, length change > oldId: "+ci.oldId+" newId: "+ci.newId+" changeType: "+ci.changeType + s" $ci")
          Some(RoadAddressMapping(ci.oldId.get, ci.newId.get, ci.oldStartMeasure.get, ci.oldEndMeasure.get,
            ci.newStartMeasure.get, ci.newEndMeasure.get, pseudoGeom, pseudoGeom, Some(ci.vvhTimeStamp)))
        case _ => None
      }
    }).filter(c => c.isDefined).map(_.get)
  }

  private def applyChanges(changes: Seq[Seq[ChangeInfo]], roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    changes.foldLeft(roadAddresses) { case (addresses, changeInfo) =>
      val (toFloat, other) = changeInfo.partition(isFloatingChange)
      val (length, maps) = other.partition(isLengthChange)
      val changeOperations: Seq[Map[Long, Seq[RoadAddress]] => Map[Long, Seq[RoadAddress]]] = Seq(
        applyFloating(toFloat),
        applyMappedChanges(maps),
        applyLengthChanges(length)
      )
      changeOperations.foldLeft(addresses){ case (addrMap, op) => op(addrMap)}
    }
  }

  private def mapAddress(mapping: Seq[RoadAddressMapping])(ra: RoadAddress) = {
    if (!ra.floating && mapping.exists(_.matches(ra))) {
      val changeVVHTimestamp = mapping.head.vvhTimeStamp.get
      mapRoadAddresses(mapping)(ra).map(_.copy(adjustedTimestamp = changeVVHTimestamp))
    } else
      Seq(ra)
  }

  private def applyMappedChanges(changes: Seq[ChangeInfo])(roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changes.isEmpty)
      roadAddresses
    else {
      val mapping = createAddressMap(changes)
      val mapped = roadAddresses.mapValues(_.flatMap(mapAddress(mapping)))
      mapped.values.toSeq.flatten.groupBy(_.linkId)
    }
  }

  private def applyLengthChanges(changes: Seq[ChangeInfo])(roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changes.isEmpty)
      roadAddresses
    else {
      val mapping = createAddressMap(changes)
      val mapped = roadAddresses.mapValues(_.flatMap(ra =>
        // If change is not within maximum allowed then float the address
        if (mapping.exists(m => m.matches(ra) && Math.abs(m.sourceLen - m.targetLen) > fi.liikennevirasto.viite.MaxLengthChange)) {
          Seq(ra.copy(floating = true))
        } else
          mapAddress(mapping)(ra)
      ))
      mapped.values.toSeq.flatten.groupBy(_.linkId)
    }
  }

  private def applyFloating(changes: Seq[ChangeInfo])(roadAddresses: Map[Long, Seq[RoadAddress]]): Map[Long, Seq[RoadAddress]] = {
    if (changes.isEmpty)
      roadAddresses
    else {
      val mapped = roadAddresses.mapValues(_.map(ra =>
        if (changes.exists(c => c.oldId.contains(ra.linkId) && c.vvhTimeStamp > ra.adjustedTimestamp)) {
          ra.copy(floating = true)
        } else
          ra
      ))
      mapped.values.toSeq.flatten.groupBy(_.linkId)
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
