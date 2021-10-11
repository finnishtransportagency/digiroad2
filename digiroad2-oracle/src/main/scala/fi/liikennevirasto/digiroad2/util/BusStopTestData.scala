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
                    5718,
                    235, 85),
      SimpleBusStop(1, Some(300004), Some(2), Seq(3),
                    27, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374675.043988335,6677274.14596169),
                    2499861,
                    235, 69), // with national bus stop id
      SimpleBusStop(2, Some(300008), Some(85755), Seq(2, 3),
                    36, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374780.259160265,6677546.84962279),
                    5544,
                    235, 30), // with national bus stop id
      SimpleBusStop(2, Some(300006), Some(7), Seq(3),
                    29, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(370675.043988335,6077274.14596169),
                    2499861,
                    235, 69), // to be used on change owner floating reason
      SimpleBusStop(2, Some(300012), Some(8), Seq(3),
                    27, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(370675.043988335,6077274.14596169),
                    2499861,
                    235, 69), // to be used on change owner floating reason
      SimpleBusStop(2, Some(300005), Some(9), Seq(3),
                    28, LocalDate.now().minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(370675.043988335, 6077274.14596169),
                    2499861,
                    235, 69), // to be used on change owner floating reason
      SimpleBusStop(2, Some(300003), Some(4), Seq(2, 3),
                    37, LocalDate.now.plusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374780.259160265,6677546.84962279),
                    5544,
                    235, 30), // in the future
      SimpleBusStop(1, Some(300001), Some(5), Seq(2, 3, 4),
                    38, LocalDate.now.minusYears(2),
                    Some(LocalDate.now().minusYears(1)),
                    Point(374780.259160265,6677546.84962279),
                    5544,
                    235, 30), // in the past
      SimpleBusStop(1, Some(300002), Some(6), Seq(2, 3, 4),
                    38, LocalDate.now.minusYears(1),
                    Some(LocalDate.now().plusYears(3)),
                    Point(374360, 6677347),
                    5544,
                    235, 358) // floating
    )
  }
}

