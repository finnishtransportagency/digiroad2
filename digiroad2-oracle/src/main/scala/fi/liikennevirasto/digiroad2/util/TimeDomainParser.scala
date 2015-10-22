package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ProhibitionValidityPeriod}

import scala.util.parsing.combinator.RegexParsers

class TimeDomainParser {
  private object parser extends RegexParsers {
    object TimeUnit extends Enumeration {
      type TimeUnit = Value
      val Hour, DayOfWeek = Value
      def fromChar: PartialFunction[Char, TimeUnit] = {
        case 'h' => Hour
        case 't' => DayOfWeek
      }
    }
    object DurationUnit extends Enumeration {
      type DurationUnit = Value
      val Hours, Days = Value
      def fromChar: PartialFunction[Char, DurationUnit] = {
        case 'h' => Hours
        case 'd' => Days
      }
    }
    case class Time(unit: TimeUnit.TimeUnit, number: Int)
    case class Duration(unit: DurationUnit.DurationUnit, number: Int)

    def number = """\d+""".r ^^ { _.toInt }
    def timeLetter = acceptMatch("time letter", TimeUnit.fromChar)
    def durationLetter = acceptMatch("duration letter", DurationUnit.fromChar)
    def timePart = timeLetter ~ number ^^ { case timeUnit ~ number => Time(timeUnit, number) }
    def durationPart = durationLetter ~ number ^^ { case durationUnit ~ number => Duration(durationUnit, number) }

    def simpleTime = timePart ^^ { case p => (None, p) }
    def complexTime = timePart ~ timePart ^^ { case a ~ b => (Some(a), b) }
    def time = '(' ~> (log(complexTime)("complexTime") | log(simpleTime)("simpleTime")) <~ ')'

    def duration = '{' ~> log(durationPart)("durationPart") <~ '}'  ^^ { case p => p }

    def spec = '[' ~> log(time)("time") ~ log(duration)("duration") <~ ']' ^^ { case time ~ duration =>
      time match {
        case (None, Time(TimeUnit.Hour, hour)) =>
          val endHour = (hour + duration.number) % 24 match { case 0 => 24; case x => x }
          ProhibitionValidityPeriod(hour, endHour, ValidityPeriodDayOfWeek.Weekday)
        case (None, Time(TimeUnit.DayOfWeek, day)) =>
          ProhibitionValidityPeriod(0, 24, ValidityPeriodDayOfWeek.fromTimeDomainValue(day))
        case (Some(Time(TimeUnit.DayOfWeek, day)), Time(TimeUnit.Hour, hour)) =>
          val endHour = (hour + duration.number) % 24 match { case 0 => 24; case x => x }
          ProhibitionValidityPeriod(hour, endHour, ValidityPeriodDayOfWeek.fromTimeDomainValue(day))
      }
    }

    def and = '[' ~> rep1sep(log(spec)("spec"), '*') <~ ']' ^^ { specs => specs.reduce { (a, b) => a.and(b) } }
    def or = '[' ~> rep1sep(log(and)("spec"), '+') <~ ']'

    def expr = log(spec)("spec") | log(and)("and") | log(or)("or")

    def apply(input: String): Option[Seq[ProhibitionValidityPeriod]] = parseAll(expr, input) match {
      case Success(result, _) => Some(result match {
        case p: ProhibitionValidityPeriod => Seq(p)
        case ps: List[Any] => ps.asInstanceOf[List[ProhibitionValidityPeriod]]
      })
      case _ => None
    }
  }

  def parse(s: String): Option[Seq[ProhibitionValidityPeriod]] = {
    parser(s)
  }
}
