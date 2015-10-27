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
    case class Scalar(ps: Either[String, Seq[ProhibitionValidityPeriod]]) extends Expr
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

    def spec: Parser[Either[String, Seq[ProhibitionValidityPeriod]]] = (time ~ duration) ^^ { case time ~ duration =>
      time match {
        case (None, Time(TimeUnit.Hour, hour)) =>
          val endHour = (hour + duration.number) % 24 match { case 0 => 24; case x => x }
          Right(Seq(
            ProhibitionValidityPeriod(hour, endHour, ValidityPeriodDayOfWeek.Weekday),
            ProhibitionValidityPeriod(hour, endHour, ValidityPeriodDayOfWeek.Saturday),
            ProhibitionValidityPeriod(hour, endHour, ValidityPeriodDayOfWeek.Sunday)))
        case (None, Time(TimeUnit.DayOfWeek, day)) =>
          val days = durationToDays(day, duration)
          days.fold(err => Left(err), ds => Right(ds.map { day => ProhibitionValidityPeriod(0, 24, day) }))
        case (Some(Time(TimeUnit.DayOfWeek, day)), Time(TimeUnit.Hour, hour)) =>
          val endHour = (hour + duration.number) % 24 match { case 0 => 24; case x => x }
          val days = durationToDays(day, duration)
          days.fold(error => Left(error), ds => Right(ds.map { day => ProhibitionValidityPeriod(hour, endHour, day) }))
        case _ =>
          Left(s"Couldn't parse specification: $time")
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

    def expr: Parser[Expr] = factor ~ rep(or | and) ^^ { case a~b => (a /: b)((acc,f) => f(acc)) }
    def or: Parser[Expr => Expr] = '+' ~ factor ^^ { case '+' ~ b => Or(_, b) }
    def and: Parser[Expr => Expr] = '*' ~ factor ^^ { case '*' ~ b => And(_, b) }
    def factor: Parser[Expr] = spec ^^ Scalar | '[' ~> expr <~ ']'

    def apply(input: String): Either[String, Seq[ProhibitionValidityPeriod]] = parseAll(phrase(expr), input) match {
      case Success(result, _) => eval(result)
      case NoSuccess(msg, _) => Left(s"Parsing time domain string $input failed with message: $msg")
    }

    private def eval: PartialFunction[Expr, Either[String, Seq[ProhibitionValidityPeriod]]] = {
      case Or(l, r)  => (eval(l), eval(r)) match {
        case (Right(leftVal), Right(rightVal)) => Right(leftVal ++ rightVal)
        case (Left(errLeft), Left(errRight)) => Left(errLeft ++ errRight)
        case (Left(errLeft), Right(_)) => Left(errLeft)
        case (Right(_), Left(errRight)) => Left(errRight)
      }
      case And(l, r) => distribute(eval(l), eval(r))
      case Scalar(v) => v
    }

    private def distribute(left: Either[String, Seq[ProhibitionValidityPeriod]], right: Either[String, Seq[ProhibitionValidityPeriod]]): Either[String, Seq[ProhibitionValidityPeriod]] = {
      (left, right) match {
        case (Right(ls), Right(rs)) => Right(ls.flatMap { l => rs.flatMap { r => l.and(r) } })
        case (Left(errLeft), Left(errRight)) => Left(errLeft ++ errRight)
        case (Left(err), _) => Left(err)
        case (_, Left(err)) => Left(err)
      }
    }
  }

  def parse(s: String): Either[String, Seq[ProhibitionValidityPeriod]] = {
    parser(s)
  }
}
