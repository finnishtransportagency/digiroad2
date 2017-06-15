package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.RoadLinkType
import fi.liikennevirasto.viite.dao.RoadAddress
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}

/**
  * Created by venholat on 14.6.2017.
  */
package object util {
  // used for debugging when needed
  def prettyPrint(l: RoadAddressLink): String = {

    s"""${if (l.id == -1000) { "NEW!" } else { l.id }} link: ${l.linkId} road address: ${l.roadNumber}/${l.roadPartNumber}/${l.trackCode}/${l.startAddressM}-${l.endAddressM} length: ${l.length} dir: ${l.sideCode}
       |${if (l.startCalibrationPoint.nonEmpty) { " <- " + l.startCalibrationPoint.get.addressMValue + " "} else ""}
       |${if (l.endCalibrationPoint.nonEmpty) { " " + l.endCalibrationPoint.get.addressMValue + " ->"} else ""}
       |${if (l.anomaly != Anomaly.None) { " " + l.anomaly } else ""}
       |${if (l.roadLinkType != RoadLinkType.NormalRoadLinkType) { " " + l.roadLinkType } else ""}
     """.stripMargin.replace("\n", "")
  }

  // used for debugging when needed
  def prettyPrint(l: RoadAddress): String = {

    s"""${if (l.id == -1000) { "NEW!" } else { l.id }} link: ${l.linkId} ${setPrecision(l.startMValue)}-${setPrecision(l.endMValue)} road address: ${l.roadNumber}/${l.roadPartNumber}/${l.track.value}/${l.startAddrMValue}-${l.endAddrMValue} length: ${setPrecision(l.endMValue - l.startMValue)} dir: ${l.sideCode}
       |${if (l.startCalibrationPoint.nonEmpty) { " <- " + l.startCalibrationPoint.get.addressMValue + " "} else ""}
       |${if (l.endCalibrationPoint.nonEmpty) { " " + l.endCalibrationPoint.get.addressMValue + " ->"} else ""}
     """.stripMargin.replace("\n", "")
  }

  private def setPrecision(d: Double) = {
    BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
  }
}
