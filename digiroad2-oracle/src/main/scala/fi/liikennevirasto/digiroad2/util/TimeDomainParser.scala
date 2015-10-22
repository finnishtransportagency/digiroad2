package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ProhibitionValidityPeriod}

import scala.util.parsing.combinator.RegexParsers

class TimeDomainParser {
  private object parser extends RegexParsers {
    case class Pair(letter: Char, number: Int)

    def number = """\d+""".r ^^ { _.toInt }
    def pair(letter: Char) = letter ~ number ^^ { case l ~ number => Pair(l, number) }

    def start = '(' ~> (opt(pair('t')) ~ pair('h')) <~ ')'  ^^ { case day ~ hour => (day, hour) }
    def duration = '{' ~> pair('h') <~ '}'  ^^ { case p => p }

    def spec = '[' ~> start ~ duration <~ ']' ^^ { case start ~ duration =>
      val (day, hour) = start
      val weekday = if (day.isDefined) ValidityPeriodDayOfWeek.fromTimeDomainValue(day.get.number) else ValidityPeriodDayOfWeek.Weekday
      val endHour = (hour.number + duration.number) % 24 match { case 0 => 24; case x => x }
      ProhibitionValidityPeriod(hour.number, endHour, weekday)
    }

    def apply(input: String): Option[Seq[ProhibitionValidityPeriod]] = parseAll(rep(spec), input) match {
      case Success(result, _) => Some(result)
      case _ => None
    }
  }

  def parse(s: String): Option[Seq[ProhibitionValidityPeriod]] = {
    parser(s)
  }
}
