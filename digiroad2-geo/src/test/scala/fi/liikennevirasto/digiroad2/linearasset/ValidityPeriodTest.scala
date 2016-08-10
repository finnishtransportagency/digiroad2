package fi.liikennevirasto.digiroad2.linearasset

import org.scalatest.{FunSuite, Matchers}

class ValidityPeriodTest extends FunSuite with Matchers {

  test("testAnd with basic case") {
    def valid1 = ValidityPeriod(0, 5, ValidityPeriodDayOfWeek.Weekday, 0, 0)
    def valid2 = ValidityPeriod(3, 8, ValidityPeriodDayOfWeek.Weekday, 0, 0)
    def hoursOnly = valid1.and(valid2)
    hoursOnly.isEmpty should be (false)
    hoursOnly.get.startMinute should be (0)
    hoursOnly.get.endMinute should be (0)
    hoursOnly.get.startHour should be (3)
    hoursOnly.get.endHour should be (5)
    hoursOnly.get.days should be (ValidityPeriodDayOfWeek.Weekday)
    hoursOnly.get.duration() should be (2)
    hoursOnly.get.preciseDuration() should be ((2, 0))
  }

  test("testAnd with complex case") {
    def valid1 = ValidityPeriod(0, 4, ValidityPeriodDayOfWeek.Weekday, 30, 35)
    def valid2 = ValidityPeriod(3, 8, ValidityPeriodDayOfWeek.Weekday, 5, 55)
    def added = valid1.and(valid2)
    added.isEmpty should be (false)
    added.get.startHour should be (3)
    added.get.endHour should be (4)
    added.get.startMinute should be (5)
    added.get.endMinute should be (35)
    added.get.days should be (ValidityPeriodDayOfWeek.Weekday)
    added.get.duration() should be (2)
    added.get.preciseDuration() should be ((1, 30))
  }

  test("testPreciseDuration") {
    def valid1 = ValidityPeriod(0, 4, ValidityPeriodDayOfWeek.Weekday, 30, 35)
    valid1.preciseDuration() should be ((4, 5))
  }

  test("testDuration") {
    def valid1 = ValidityPeriod(0, 4, ValidityPeriodDayOfWeek.Weekday, 30, 35)
    valid1.duration() should be (5)
  }

}
