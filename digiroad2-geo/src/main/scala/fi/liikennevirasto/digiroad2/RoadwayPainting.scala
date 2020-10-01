package fi.liikennevirasto.digiroad2

sealed trait WidthOfRoadAxisRegulationNumbers {
  def value: Int
  def description: String
}
object WidthOfRoadAxisRegulationNumbers {
  val values = Set(StopLine, YieldLine, Crosswalk, CyclewayExtension1, CyclewayExtension2, CyclewayExtension3, SpeedBump1, SpeedBump2, RumbleStrips)

  def apply(intValue: Int): WidthOfRoadAxisRegulationNumbers = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: WidthOfRoadAxisRegulationNumbers = Unknown

  case object StopLine extends WidthOfRoadAxisRegulationNumbers { def value = 1; def description = "Pysäytysviiva"  }
  case object YieldLine extends WidthOfRoadAxisRegulationNumbers { def value = 2; def description = "Väistämisviiva" }
  case object Crosswalk extends WidthOfRoadAxisRegulationNumbers { def value = 3; def description = "Suojatie" }
  case object CyclewayExtension1 extends WidthOfRoadAxisRegulationNumbers { def value = 41; def description = "Pyörätien jatke" }
  case object CyclewayExtension2 extends WidthOfRoadAxisRegulationNumbers { def value = 42; def description = "Pyörätien jatke" }
  case object CyclewayExtension3 extends WidthOfRoadAxisRegulationNumbers { def value = 43; def description = "Pyörätien jatke" }
  case object SpeedBump1 extends WidthOfRoadAxisRegulationNumbers { def value = 51; def description = "Töyssy" }
  case object SpeedBump2 extends WidthOfRoadAxisRegulationNumbers { def value = 52; def description = "Töyssy" }
  case object RumbleStrips extends WidthOfRoadAxisRegulationNumbers { def value = 6; def description = "Heräteraidat" }
  case object Unknown extends WidthOfRoadAxisRegulationNumbers { def value = 99; def description = "Ei tiedossa" }
}

sealed trait Milled {
  def value: Int
  def description: String
}
object Milled {
  val values = Set(Unknown, NoMilled, SurfaceMarking, SineWave, CylinderMarking)

  def apply(intValue: Int): Milled = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: Milled = Unknown

  case object NoMilled extends Milled { def value = 1; def description = "Ei"  }
  case object SurfaceMarking extends Milled { def value = 2; def description = "Pintamerkintä" }
  case object SineWave extends Milled { def value = 3; def description = "Siniaalto" }
  case object CylinderMarking extends Milled { def value = 4; def description = "Sylinterimerkintä" }
  case object Unknown extends Milled { def value = 99; def description = "Ei tiedossa" }
}


sealed trait MaterialOfMarking {
  def value: Int
  def description: String
}
object MaterialOfMarking {
  val values = Set(Unknown, Paint, Mass)

  def apply(intValue: Int):MaterialOfMarking = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: MaterialOfMarking = Unknown

  case object Paint extends MaterialOfMarking { def value = 1; def description = "Maali"  }
  case object Mass extends MaterialOfMarking { def value = 2; def description = "Massa" }
  case object Unknown extends MaterialOfMarking { def value = 99; def description = "Ei tiedossa" }
}