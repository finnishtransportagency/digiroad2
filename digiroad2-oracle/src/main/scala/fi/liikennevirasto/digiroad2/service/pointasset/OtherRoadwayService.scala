package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, PropertyValue, SimplePointAssetProperty, OtherRoadwayMarkings}


case class IncomingOtherRoadwayMarking(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomingPointAsset



