package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.RoadAddressException
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

  private def groupProjectLinks(seq: Seq[ProjectLink]): Seq[Seq[ProjectLink]] = {
    if (seq.isEmpty || !seq.exists(_.status != seq.head.status))
      Seq(seq)
    else {
      seq.head.status match {
        case LinkStatus.UnChanged =>
          val index = seq.zipWithIndex.find(_._1.status != LinkStatus.UnChanged).get._2 - 1
          val (s, others) = seq.splitAt(index)
          Seq(s) ++ groupProjectLinks(others)
        case _ =>
          val index = seq.zipWithIndex.find(_._1.status == LinkStatus.UnChanged).map(_._2)
          if (index.isEmpty)
            Seq(seq)
          else {
            val (s, others) = seq.splitAt(index.get - 1)
            Seq(s) ++ groupProjectLinks(others)
          }
      }
    }
  }
  def calculateMValuesForTrack(seq: Seq[ProjectLink]): Seq[ProjectLink] = {
    // Group all consecutive links with same status
    val grouped = groupProjectLinks(seq)
//      seq.(Seq(Seq[ProjectLink]())){ case (s, pl) =>
//      (s.head.headOption.map(_.status), pl.status) match {
//        case (None, _) => Seq(Seq(pl))
//        case (Some(LinkStatus.UnChanged), LinkStatus.UnChanged) => Seq(s.head ++ Seq(pl)) ++ s.tail
//        case (Some(LinkStatus.UnChanged), _) => Seq(Seq(pl)) ++ s
//        case (_, LinkStatus.UnChanged) => Seq(Seq(pl)) ++ s
//        case (_, _) => Seq(s.head ++ Seq(pl)) ++ s.tail
//      }
//    }.reverse
    //[(Option[ProjectLink], Seq[ProjectLink])]
    grouped.zip(grouped.tail.map(_.headOption) ++ Seq(None)).scanLeft[AddressingGroup, Seq[AddressingGroup]](AddressingGroup(None, Seq[ProjectLink]())){
      case (prev, (s, next)) =>
        if (s.exists(_.status == LinkStatus.UnChanged))
          AddressingGroup(s.lastOption, s)
        else {
          val calculated = assignLinkValues(s, prev.previous, next)
          AddressingGroup(calculated.lastOption, calculated)
        }
    }.flatMap(_.group)
  }

  private def assignLinkValues(seq: Seq[ProjectLink], prev: Option[ProjectLink], next: Option[ProjectLink]) = {
    val startM = prev.map(_.endAddrMValue).getOrElse(0L)
    val end = next.map(_.startAddrMValue)
    println(s"Assign $startM - $end")
    val (newLinks, transferLinks) = seq.partition(_.status == LinkStatus.New)
    val newLinksGeomLength = newLinks.map(_.geometryLength).sum
    val transferAddressLength = transferLinks.map(l => l.endAddrMValue - l.startAddrMValue).sum
    val coeff = end match {
      case None =>
        1.0
      case Some(endValue) =>
        (endValue - startM - transferAddressLength) / newLinksGeomLength
    }
    val newAddressValues = seq.scanLeft(startM.toDouble){ case (m, pl) =>
        pl.status match {
          case LinkStatus.New => m + pl.geometryLength * coeff
          case LinkStatus.Transfer => m + pl.endAddrMValue - pl.startAddrMValue
          case _ => throw new RoadAddressException(s"Invalid status found at value assignment ${pl.status}, linkId: ${pl.linkId}")
        }
    }
    seq.zip(newAddressValues.zip(newAddressValues.tail)).map { case (pl, (st, en)) =>
      pl.copy(startAddrMValue = Math.round(st), endAddrMValue = Math.round(en))}
  }
}

case class AssignedValue(startAddrM: Option[Long], fixedLength: Option[Long])
case class CalculatedValue(addressAndGeomLength: Either[Long, Double])
case class AddressingGroup(previous: Option[ProjectLink], group: Seq[ProjectLink])