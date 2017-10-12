package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.linearasset.{AssetTypes, MassLimitationValues, MassLinearAsset}


class LinearMassLimitationService(roadLinkService: RoadLinkService) {
  val MassLimitationAssetTypes = Seq(LinearAssetTypes.TotalWeightLimits,
                                      LinearAssetTypes.TrailerTruckWeightLimits,
                                      LinearAssetTypes.AxleWeightLimits,
                                      LinearAssetTypes.BogieWeightLimits)


  def getMassLimitationByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[MassLinearAsset]] = {

    val geometry = Seq(Point(374195.679, 6677255.286, 25.260999999998603),Point( 374213.968,6677256.785,25.15799999999581),  Point(374233.999,6677257.455, 24.895000000004075), Point(374238.876, 6677257.206, 24.851999999998952))

    val prop1 = AssetTypes(60, "1")
    val prop2 = AssetTypes(40, "2")
    val prop3 = AssetTypes(50, "3")

    val propertiesSeq :Seq[AssetTypes] = List(prop1, prop2, prop3)
    val massLimitation = MassLimitationValues(propertiesSeq)
    val fakeMap =  Seq(MassLinearAsset(geometry,  massLimitation))

    typeId match {
      case typeIdValue if MassLimitationAssetTypes.contains(typeIdValue) =>
        Seq(fakeMap)
      //val massLimitaitionAssets = getMassLimitationByRoadLinks(LinearAssetTypes.MassLimitationAssetTypes, roadLinks)
      //LinearAssetPartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))
      case _ => Seq(Seq())
    }
  }
}
