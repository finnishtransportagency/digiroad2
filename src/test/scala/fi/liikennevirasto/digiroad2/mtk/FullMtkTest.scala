package fi.liikennevirasto.digiroad2.mtk

import org.scalatest.{BeforeAndAfter, MustMatchers, FlatSpec}
import scala.io.Source
import fi.liikennevirasto.digiroad2.feature.oracle.OracleSpatialFeatureProvider

class FullMtkTest extends FlatSpec with MustMatchers with BeforeAndAfter {
  var source: Source = null

  before {
    source = Source.fromInputStream(this.getClass.getResourceAsStream("/kerava.xml"))
  }

  ignore must "parse items with end date correctly" in {
    val roadlinks = MtkMessageParser.parseMtkMessage(source)
    val provider = new OracleSpatialFeatureProvider
    println(roadlinks.groupBy{_.id}.map{_._2.head}.seq.size)
    // provider.updateRoadLinks(roadlinks)
  }
}
