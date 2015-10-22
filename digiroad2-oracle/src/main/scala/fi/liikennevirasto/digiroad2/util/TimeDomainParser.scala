package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ProhibitionValidityPeriod}

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

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

    sealed trait Expr
    case class Scalar(p: ProhibitionValidityPeriod) extends Expr
    case class And(left: Expr, right: Expr) extends Expr
    case class Or(left: Expr, right: Expr) extends Expr

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

    def expr: Parser[Expr] = term ~ rep(or) ^^ { case a~b => (a /: b)((acc,f) => f(acc)) }
    def or: Parser[Expr => Expr] = '+' ~ term ^^ { case '+' ~ b => Or(_, b) }
    def term: Parser[Expr] = factor ~ rep(and) ^^ { case a~b => (a /: b)((acc,f) => f(acc)) }
    def and: Parser[Expr => Expr] = '*' ~ factor ^^ { case '*' ~ b => And(_, b) }
    def factor: Parser[Expr] = spec ^^ Scalar | '[' ~> expr <~ ']'

    def apply(input: String): Option[Seq[ProhibitionValidityPeriod]] = parseAll(phrase(expr), input) match {
      case Success(result, _) => Some(eval(result))
      case _ => None
    }

    private def eval: PartialFunction[Expr, Seq[ProhibitionValidityPeriod]] = {
      case Or(l, r)  => eval(l) ++ eval(r)
      case And(l, r) => distribute(eval(l), eval(r))
      case Scalar(v) => Seq(v)
    }

    private def distribute(left: Seq[ProhibitionValidityPeriod], right: Seq[ProhibitionValidityPeriod]): Seq[ProhibitionValidityPeriod] = {
      // Assume there is only one value in `right`.
      val expr = right.head
      left.map { l => l.and(expr) }
    }
  }

  def parse(s: String): Option[Seq[ProhibitionValidityPeriod]] = {
    parser(s)
  }
}
