package fi.liikennevirasto.digiroad2.util

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.SimpleBusStop
import fi.liikennevirasto.digiroad2.Point

object BusStopTestData {
  def generateTestData = {
    List(
      SimpleBusStop(2, Some(300000), Some(1), Seq(2),
                    17, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374443.764141219,6677245.28337185),
                    5718),
      SimpleBusStop(1, Some(300004), Some(2), Seq(3),
                    27, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374675.043988335,6677274.14596169),
                    2499861), // with national bus stop id
      SimpleBusStop(2, Some(300008), Some(85755), Seq(2, 3),
                    36, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374780.259160265,6677546.84962279),
                    5544), // with national bus stop id
      SimpleBusStop(2, Some(300003), Some(4), Seq(2, 3),
                    37, LocalDate.now.plusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374780.259160265,6677546.84962279),
                    5544), // in the future
      SimpleBusStop(1, Some(300001), Some(5), Seq(2, 3, 4),
                    38, LocalDate.now.minusYears(2),
                    Some(LocalDate.now().minusYears(1)),
                    Point(374780.259160265,6677546.84962279),
                    5544) // in the past
    )
  }
}

