package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet

class ProhibitionFiller extends OneWayAssetFiller {
  /*override protected def mergeValuesExistingOnSameRoadLink(roadLink: RoadLink, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val segmenstWitoutDuplicates = segments.distinct

    if (segmenstWitoutDuplicates.size >= 2 &&
      segmenstWitoutDuplicates.count(_.createdBy.contains("automatic_process_prohibitions")) == segmenstWitoutDuplicates.size) {

      val minLengthToZip = 1.0
      val pointsOfInterestOnSegments = (Seq(0, roadLink.length) ++ segments.flatMap(s => Seq(s.startMeasure, s.endMeasure))).distinct.sorted
      val pointsOfInterestOnSegmentsZiped = pointsOfInterestOnSegments.zip(pointsOfInterestOnSegments.tail).filterNot{piece => (piece._2 - piece._1) < minLengthToZip}

      val segmentsInOverlap = pointsOfInterestOnSegmentsZiped.find { poi =>
        val startMeasurePOI = poi._1
        val endMeasurePOI = poi._2
        segmenstWitoutDuplicates.count(s => startMeasurePOI >= s.startMeasure && endMeasurePOI <= s.endMeasure) > 1
      }

      val mergeValues =
        if (segmentsInOverlap.nonEmpty) {
          pointsOfInterestOnSegmentsZiped.map { poi =>
            val startMeasurePOI = poi._1
            val endMeasurePOI = poi._2
            val segmentsInsidePOI = segmenstWitoutDuplicates.filter(s => startMeasurePOI >= s.startMeasure && endMeasurePOI <= s.endMeasure)

            segmentsInsidePOI.size match {
              case 0 =>
                (Seq.empty, Seq.empty)
              case 1 =>
                val segmentToUpdate = segmentsInsidePOI.head.copy(id = 0L, startMeasure = startMeasurePOI, endMeasure = endMeasurePOI)
                (Seq(segmentToUpdate), segmenstWitoutDuplicates.map(_.id))
              case _ =>
                val values =
                  segmentsInsidePOI.flatMap { sPOI =>
                    sPOI.value.get.asInstanceOf[Prohibitions].prohibitions
                  }.toSet
                val segmentToCreate =
                  sortNewestFirst(segmentsInsidePOI).head.copy(id = 0L, startMeasure = startMeasurePOI,
                    endMeasure = endMeasurePOI, value = Some(Prohibitions(values.toSeq).asInstanceOf[Value]))
                (Seq(segmentToCreate), segmenstWitoutDuplicates.map(_.id))
            }
          }
        } else {
          return (segments, changeSet)
        }

      val newSegments = mergeValues.flatMap(_._1)
      val segmentsToExpire = mergeValues.flatMap(_._2).toSet

      (newSegments ++ segments, changeSet.copy(expiredAssetIds = segmentsToExpire))
    } else {
      (segments, changeSet)
    }
  }*/
}
