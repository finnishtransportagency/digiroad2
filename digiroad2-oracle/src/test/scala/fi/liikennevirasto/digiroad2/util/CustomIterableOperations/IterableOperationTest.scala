package fi.liikennevirasto.digiroad2.util.CustomIterableOperations

import fi.liikennevirasto.digiroad2.asset.{InformationSource, LinkGeomSource}
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

class IterableOperationTest extends FunSuite with Matchers {

  val linkIdsRepeated = Seq.fill(5)("link1") ++ Seq.fill(3)("link2") ++ Seq.fill(2)("link3")
  val dummyAssets: Seq[PersistedLinearAsset] = linkIdsRepeated.zipWithIndex.map { case (linkId, i) =>
    PersistedLinearAsset(
      id = i + 1,
      linkId = linkId,
      sideCode = i % 2, 
      value = Some(NumericValue(0)),
      startMeasure = 0,
      endMeasure =10,
      createdBy = Some(s"Creator_${i + 1}"),
      createdDateTime = Some(DateTime.now()),
      modifiedBy = Some(s"Modifier_${i + 1}"),
      modifiedDateTime = Some(DateTime.now()),
      expired = false,
      typeId = (i + 1) % 3, 
      timeStamp = System.currentTimeMillis(),
      geomModifiedDate = Some(DateTime.now()),
      linkSource = LinkGeomSource(0),
      verifiedBy = Some(s"Verifier_${i + 1}"),
      verifiedDate = Some(DateTime.now()),
      informationSource = Some(InformationSource(0)),
      oldId = (i + 1) * 100 
    )
  }


  test("Make sure that grouping is actually working") {
    val grouped = IterableOperation.groupByPropertyHashMap(dummyAssets, (elem: PersistedLinearAsset) => elem.linkId)
    grouped("link1").size should be(5)
    grouped("link2").size should be(3)
    grouped("link3").size should be(2)
  }

}
