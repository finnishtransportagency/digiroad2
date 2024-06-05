package fi.liikennevirasto.digiroad2.util

import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 30.3.2016.
  */
class LinearAssetUtilsSpec extends FunSuite with Matchers {

  test("timestamp is correctly created") {
    val hours = DateTime.now().getHourOfDay
    val yesterday = LinearAssetUtils.createTimeStamp(hours + 1)
    val today = LinearAssetUtils.createTimeStamp(hours)

    (today % 24*60*60*1000L) should be (0L)
    (yesterday % 24*60*60*1000L) should be (0L)
    today should be > yesterday
    (yesterday + 24*60*60*1000L) should be (today)
  }
}
