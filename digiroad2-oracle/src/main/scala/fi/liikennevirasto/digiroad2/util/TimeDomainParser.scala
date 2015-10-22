package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.linearasset.ProhibitionValidityPeriod

import scala.util.parsing.combinator.RegexParsers

class TimeDomainParser {
  private object parser extends RegexParsers {
    def number = """\d+""".r ^^ { _.toInt }
    def letter = """[h]""".r
    def start = '(' ~> letter ~ number <~ ')' ^^ { case letter ~ number => (letter, number) }
    def duration = '{' ~> letter ~ number <~ '}' ^^ { case letter ~ number => (letter, number) }
    def spec = '[' ~> start ~ duration <~ ']' ^^ { case start ~ duration => ProhibitionValidityPeriod(start._2, start._2 + duration._2) }
    def apply(input: String): Option[Seq[ProhibitionValidityPeriod]] = parseAll(rep(spec), input) match {
      case Success(result, _) => Some(result)
      case _ => None
    }
  }

  def parse(s: String): Option[Seq[ProhibitionValidityPeriod]] = {
    parser(s)
  }
}
