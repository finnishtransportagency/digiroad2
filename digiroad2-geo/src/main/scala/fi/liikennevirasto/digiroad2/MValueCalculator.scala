package fi.liikennevirasto.digiroad2

import com.sun.org.slf4j.internal.LoggerFactory
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.SideCode

sealed case class AssetLinearReference(id: Long, startMeasure: Double, endMeasure: Double, sideCode: Int)

object MValueCalculator {
  
  val logger = LoggerFactory.getLogger(getClass)

  /**
    *
    *
    * @param asset
    * @param projection
    * @param newLinksLength
    * @return
    */
  def calculateNewMValues(asset: AssetLinearReference, projection: Projection, newLinksLength: Double) = {
    val oldLength = projection.oldEnd - projection.oldStart
    val newLength = projection.newEnd - projection.newStart
    val linksLenght = roundMeasure(newLinksLength)
    val factor = Math.abs(newLength / oldLength)
    val newStartMValue = projection.newStart + (asset.startMeasure - projection.oldStart) * factor
    val newEndMValue = projection.newEnd + (asset.endMeasure - projection.oldEnd) * factor

    println(s"new start $newStartMValue, new end $newEndMValue, factor number $factor")
    if (GeometryUtils.isDirectionChangeProjection(projection)) {
      calculateNewMValuesAndSideCode(asset,projection,linksLenght)
    }else {
      // Test if asset is affected by projection
      if (asset.endMeasure <= projection.oldStart || asset.startMeasure >= projection.oldEnd) {
        (asset.startMeasure, asset.endMeasure, asset.sideCode)
      } else {
        val start = Math.min(linksLenght, Math.max(0.0, newStartMValue)) // take new start if it is greater than zero and smaller than roadLinkLength
        val end = Math.max(0.0, Math.min(linksLenght, newEndMValue)) // take new end if it is greater than zero and smaller than roadLinkLength

        if (end - start <= 0) {
          logger.warn(s"new size is zero")
        }

        if (start > end) {
          logger.warn(s"invalid meters start: ${start} , end ${end}")
        }
        
        logger.debug(s"adjusting asset: ${asset.id}")
        logger.debug(s"old start ${asset.startMeasure}, old end ${asset.endMeasure}, old length ${asset.endMeasure - asset.startMeasure}")
        logger.debug(s"new start $start, new end $end, new length ${end - start}")
        (roundMeasure(start), roundMeasure(end), asset.sideCode)
      }
    }
  }

  def calculateNewMValuesAndSideCode(asset: AssetLinearReference, projection: Projection, newLinksLength: Double) = {
    val newSideCode = sideCodeSwitch(asset.sideCode)
    val oldLength = projection.oldEnd - projection.oldStart
    val newLength = projection.newEnd - projection.newStart
    val factor = Math.abs(newLength / oldLength)

    val newStart = projection.newStart - (asset.endMeasure - projection.oldStart) * factor
    val newEnd = projection.newEnd - (asset.startMeasure - projection.oldEnd) * factor
    logger.debug(s"new start $newStart, new end $newEnd, factor number $factor")

    if (asset.endMeasure <= projection.oldStart || asset.startMeasure >= projection.oldEnd) {
      (asset.startMeasure, asset.endMeasure, newSideCode)
    } else {

      val start = Math.min(newLinksLength, Math.max(0.0, newStart))
      val end = Math.max(0.0, Math.min(newLinksLength, newEnd))

      if (end - start <= 0) {
        logger.warn(s"new size is zero")
      }

      if (start > end) {
        logger.warn(s"invalid meters start: ${start} , end ${end}")
      }

      logger.debug(s"adjusting asset: ${asset.id}")
      logger.debug(s"old start ${asset.startMeasure}, old end ${asset.endMeasure}, old length ${asset.endMeasure - asset.startMeasure}")
      logger.debug(s"new start $start, new end $end, new length ${end - start}")
      (roundMeasure(start), roundMeasure(end), newSideCode)
    }

  }
  def sideCodeSwitch(sideCode: Int): Int = {
    SideCode.apply(sideCode) match {
      case (SideCode.AgainstDigitizing) => SideCode.TowardsDigitizing.value
      case (SideCode.TowardsDigitizing) => SideCode.AgainstDigitizing.value
      case _ => sideCode
    }
  }

  def roundMeasure(measure: Double, numberOfDecimals: Int = 3): Double = {
    val exponentOfTen = Math.pow(10, numberOfDecimals)
    Math.round(measure * exponentOfTen).toDouble / exponentOfTen
  }
}
