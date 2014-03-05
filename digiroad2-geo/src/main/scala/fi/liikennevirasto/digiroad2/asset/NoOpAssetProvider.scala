package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import scala.Some
import fi.liikennevirasto.digiroad2.asset.ValidityPeriod._

class NoOpAssetProvider extends AssetProvider {
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue]) {}
  def deleteAssetProperty(assetId: Long, propertyId: String) {}
  def getRoadLinks(municipalityNumbers: Seq[Int], bounds: Option[BoundingCircle]): Seq[RoadLink] = List()
  def getRoadLinkById(roadLinkId: Long): Option[RoadLink] = None
  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    Some(AssetWithProperties(0, None, 10, 0, 0, 0, propertyData = List(
      Property("4", "Pysäkin saavutettavuus", "text", values = Seq(PropertyValue(0, "", null))),
      Property("5", "Esteettömyystiedot", "text", values = Seq(PropertyValue(0, "", null))),
      Property("6", "Ylläpitäjän tunnus", "text", values = Seq(PropertyValue(0, "", null))),
      Property("validityDirection", "Vaikutussuunta", SingleChoice, values = Seq(
        PropertyValue(1, "Molempiin suuntiin"),
        PropertyValue(2, "Digitointisuuntaan"),
        PropertyValue(3, "Digitointisuuntaa vastaan"))),
      Property("validFrom", "Käytössä alkaen", Date, values = Seq(PropertyValue(0, null))),
      Property("validTo", "Käytössä päättyen", Date, values = Seq(PropertyValue(0, null)))
    )))
  }
  def getAssets(assetTypeId: Long, municipalityNumbers: Seq[Int] = Nil, bounds: Option[BoundingCircle] = None, validFrom: Option[LocalDate] = None, validTo: Option[LocalDate] = None): Seq[Asset] = List()
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, propertyValues: Seq[SimpleProperty]) = {
    AssetWithProperties(0, None, assetTypeId, lon, lat, roadLinkId)
  }
  def getAssetTypes = List()
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    List(EnumeratedPropertyValue("validityDirection", "Vaikutussuunta", SingleChoice, values = Seq(
      PropertyValue(1, "Molempiin suuntiin"),
      PropertyValue(2, "Digitointisuuntaan"),
      PropertyValue(3, "Digitointisuuntaa vastaan"))))
  }
  def updateAssetLocation(asset: Asset): AssetWithProperties = getAssetById(asset.id).get
  def getImage(imageId: Long): Array[Byte] = new Array[Byte](0)
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]) { }
  def availableProperties(assetTypeId: Long): Seq[Property] = Seq()
}