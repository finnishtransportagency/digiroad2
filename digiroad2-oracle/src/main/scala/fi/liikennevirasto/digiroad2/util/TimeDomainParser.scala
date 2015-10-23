package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.{ProhibitionValidityPeriod, ValidityPeriodDayOfWeek}

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

    sealed trait Expr
    case class Scalar(ps: Seq[Either[String, ProhibitionValidityPeriod]]) extends Expr
    case class And(left: Expr, right: Expr) extends Expr
    case class Or(left: Expr, right: Expr) extends Expr

    def number = """\d+""".r ^^ { _.toInt }
    def timeLetter = acceptMatch("time letter", TimeUnit.fromChar)
    def durationLetter = acceptMatch("duration letter", DurationUnit.fromChar)
    def timePart = timeLetter ~ number ^^ { case timeUnit ~ number => Time(timeUnit, number) }
    def durationPart = durationLetter ~ number ^^ { case durationUnit ~ number => Duration(durationUnit, number) }

    def simpleTime = timePart ^^ { case p => (None, p) }
    def complexTime = timePart ~ timePart ^^ { case a ~ b => (Some(a), b) }
    def time = '(' ~> (complexTime | simpleTime) <~ ')'

    def duration = '{' ~> durationPart <~ '}'  ^^ { case p => p }

    def spec = (time ~ duration) ^^ { case time ~ duration =>
      time match {
        case (None, Time(TimeUnit.Hour, hour)) =>
          val endHour = (hour + duration.number) % 24 match { case 0 => 24; case x => x }
          Seq(Right(ProhibitionValidityPeriod(hour, endHour, ValidityPeriodDayOfWeek.Weekday)))
        case (None, Time(TimeUnit.DayOfWeek, day)) =>
          val days = durationToDays(day, duration)
          days.fold({ x => Seq(Left(x)) }, _.map { day => Right(ProhibitionValidityPeriod(0, 24, day)) })
        case (Some(Time(TimeUnit.DayOfWeek, day)), Time(TimeUnit.Hour, hour)) =>
          val endHour = (hour + duration.number) % 24 match { case 0 => 24; case x => x }
          val days = durationToDays(day, duration)
          days.fold({ x => Seq(Left(x)) }, _.map { day => Right(ProhibitionValidityPeriod(hour, endHour, day)) })
        case _ =>
          Seq(Left(s"Couldn't parse specification: $time"))
      }
    }

    def durationToDays(day: Int, duration: Duration): Either[String, Seq[ValidityPeriodDayOfWeek]] = {
      (day, duration) match {
        case (7, Duration(DurationUnit.Days, 2)) =>
          Right(Seq(ValidityPeriodDayOfWeek.Saturday, ValidityPeriodDayOfWeek.Sunday))
        case (1, _) =>
          Right(Seq(ValidityPeriodDayOfWeek.Sunday))
        case (2, _) =>
          Right(Seq(ValidityPeriodDayOfWeek.Weekday))
        case (7, _) =>
          Right(Seq(ValidityPeriodDayOfWeek.Saturday))
        case _ =>
          Left(s"Unsupported day, duration combination. day: $day duration: $duration")
      }
    }

    def expr: Parser[Expr] = term ~ rep(or) ^^ { case a~b => (a /: b)((acc,f) => f(acc)) }
    def or: Parser[Expr => Expr] = '+' ~ term ^^ { case '+' ~ b => Or(_, b) }
    def term: Parser[Expr] = factor ~ rep(and) ^^ { case a~b => (a /: b)((acc,f) => f(acc)) }
    def and: Parser[Expr => Expr] = '*' ~ factor ^^ { case '*' ~ b => And(_, b) }
    def factor: Parser[Expr] = spec ^^ Scalar | '[' ~> expr <~ ']'

    def apply(input: String): Seq[Either[String, ProhibitionValidityPeriod]] = parseAll(phrase(expr), input) match {
      case Success(result, _) => eval(result)
      case NoSuccess(msg, _) => Seq(Left(s"Parsing time domain string $input failed with message: $msg"))
    }

    private def eval: PartialFunction[Expr, Seq[Either[String, ProhibitionValidityPeriod]]] = {
      case Or(l, r)  => eval(l) ++ eval(r)
      case And(l, r) => distribute(eval(l), eval(r))
      case Scalar(v) => v
    }

    private def distribute(left: Seq[Either[String, ProhibitionValidityPeriod]], right: Seq[Either[String, ProhibitionValidityPeriod]]): Seq[Either[String, ProhibitionValidityPeriod]] = {
      // Assume there is only one value in `right`.
      // ((1+2)+3)+4
      val expr = right.head
      left.map { l =>
        (l, expr) match {
          case (Right(leftPeriod), Right(rightPeriod)) => Right(leftPeriod.and(rightPeriod))
          case _ => Left(s"Distribution failure. Left value: $l Right value: $expr")
        }
      }
    }
  }

  def parse(s: String): Seq[Either[String, ProhibitionValidityPeriod]] = {
    parser(s)
  }
}
