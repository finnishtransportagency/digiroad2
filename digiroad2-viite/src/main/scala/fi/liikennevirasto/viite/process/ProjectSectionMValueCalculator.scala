package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.viite.dao.LinkStatus._
import fi.liikennevirasto.viite.dao.{CalibrationPoint, LinkStatus, ProjectLink}

object ProjectSectionMValueCalculator {
//  private def assignValues(combinedSection: CombinedSection): AssignedValue = {
//    if (combinedSection.linkStatusCodes.size != 1)
//      return AssignedValue(None, None)
//    combinedSection.linkStatusCodes.head match {
//      case UnChanged | Numbering =>
//        AssignedValue(Some(combinedSection.startAddrM), Some(combinedSection.endAddrM - combinedSection.startAddrM))
//      case Transfer | NotHandled =>
//        AssignedValue(None, Some(combinedSection.endAddrM - combinedSection.startAddrM))
//      case New =>
//        AssignedValue(None, None)
//      case Terminated =>
//        throw new RoadAddressException("Trying to recalculate address for a terminated road section")
//      case _ =>
//        throw new RoadAddressException("Trying to recalculate address for unknown status")
//    }
//  }
//
//  private def splitByUnchanged(sections: Seq[CombinedSection], startM: Long): Seq[(Seq[CombinedSection], Long, Long)] = {
//    val indexOfUnChanged = sections.zipWithIndex.find(_._1.linkStatusCodes.headOption.exists(_ == UnChanged)).map(_._2)
//    if (indexOfUnChanged.nonEmpty) {
//      val index = indexOfUnChanged.get
//      val (before, after) = sections.splitAt(index)
//      val unChanged = before.last
//      if (before.size == 1) {
//        Seq((before, unChanged.startAddrM, unChanged.endAddrM)) ++
//          splitByUnchanged(after, unChanged.endAddrM)
//      } else {
//        Seq((before.take(index - 1), startM, unChanged.endAddrM)) ++
//          Seq((Seq(unChanged), unChanged.startAddrM, unChanged.endAddrM)) ++
//          splitByUnchanged(after, unChanged.endAddrM)
//      }
//    } else {
//      Seq((sections, startM, Long.MaxValue))
//    }
//
//  }
//  def calculateLinkMValues(right: Seq[ProjectLink], left: Seq[ProjectLink]) = {
//    val calibrationPointLocations = (right.zip(right.tail) ++ left.zip(left.tail)).scanLeft(Seq[Long, Long]()){ case (s, (pl1, pl2)) =>
//        if (pl1.track == pl2.track)
//          s
//        else
//          s ++ Seq(pl1.linkId, pl2.linkId)
//    }
//  }
// Before or after this: Check that the end lengths should agree and adjust if needed

  def calculateMValuesForTrack(seq: Seq[ProjectLink]): Seq[ProjectLink] = {
    // That is an address connected extension of this
    def isExtensionOf(ext: ProjectLink)(thisPL: ProjectLink) = {
      thisPL.endAddrMValue == ext.startAddrMValue &&
        (thisPL.track == ext.track || Set(thisPL.track, ext.track).contains(Track.Combined))
    }
    // Group all consecutive links with same status
    val (unchanged, others) = seq.partition(_.status == LinkStatus.UnChanged)
    val mapped = unchanged.groupBy(_.startAddrMValue)
    if (mapped.values.exists(_.size != 1)) {
      println(mapped.values.exists(_.size != 1))
      mapped.values.foreach(println)
      throw new RoadAddressException(s"Multiple unchanged links specified with overlapping address value ${mapped.values.filter(_.size != 1).mkString(", ")}")
    }
    if (unchanged.nonEmpty && mapped.keySet.count(_ == 0L) != 1)
      throw new RoadAddressException("No starting point (Address = 0) found for UnChanged links")
    if (!unchanged.forall(
      pl => {
        val previousLinks = unchanged.filter(isExtensionOf(pl))
        println(s"${previousLinks.size} -> ${pl.startAddrMValue}, ${pl.track}, ${previousLinks.map(_.track).toSet}")
        previousLinks.size match {
          case 0 => pl.startAddrMValue == 0
          case 1 => true
          case 2 => pl.track == Track.Combined && previousLinks.map(_.track).toSet == Set(Track.LeftSide, Track.RightSide)
          case _ => false
        }
      }))
      throw new RoadAddressException(s"Invalid unchanged link found")
    unchanged ++ assignLinkValues(others, unchanged.map(_.endAddrMValue.toDouble).sorted.lastOption)
  }

  def assignLinkValues(seq: Seq[ProjectLink], addr: Option[Double], coEff: Double = 1.0): Seq[ProjectLink] = {
    val newAddressValues = seq.scanLeft(addr.getOrElse(0.0)){ case (m, pl) =>
      pl.status match {
        case LinkStatus.New => m + pl.geometryLength * coEff
        case LinkStatus.Transfer | LinkStatus.NotHandled => m + pl.endAddrMValue - pl.startAddrMValue
        case LinkStatus.UnChanged | LinkStatus.Numbering => pl.endAddrMValue
        case _ => throw new RoadAddressException(s"Invalid status found at value assignment ${pl.status}, linkId: ${pl.linkId}")
      }
    }
    seq.zip(newAddressValues.zip(newAddressValues.tail)).map { case (pl, (st, en)) =>
      pl.copy(startAddrMValue = Math.round(st), endAddrMValue = Math.round(en))}
  }
  def calculateAddressingFactors(seq: Seq[ProjectLink]): TrackAddressingFactors = {
    seq.foldLeft[TrackAddressingFactors](TrackAddressingFactors(0, 0, 0.0)) { case (a, pl) =>
      pl.status match {
        case UnChanged | Numbering => a.copy(unChangedLength = a.unChangedLength + pl.endAddrMValue - pl.startAddrMValue)
        case Transfer | LinkStatus.NotHandled => a.copy(transferLength = a.transferLength + pl.endAddrMValue - pl.startAddrMValue)
        case New => a.copy(newLength = a.newLength + pl.geometryLength)
      }
    }
  }
}

case class TrackAddressingFactors(unChangedLength: Long, transferLength: Long, newLength: Double)
case class CalculatedValue(addressAndGeomLength: Either[Long, Double])
case class AddressingGroup(previous: Option[ProjectLink], group: Seq[ProjectLink])