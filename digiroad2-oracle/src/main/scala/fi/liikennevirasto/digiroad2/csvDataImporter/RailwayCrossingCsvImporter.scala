package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.{PropertyValue, RailwayCrossings, SimplePointAssetProperty, State}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingRailwayCrossing, RailwayCrossingService}
import fi.liikennevirasto.digiroad2.user.User

class RailwayCrossingCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override val codeValueFieldsMapping: Map[String, String] = Map("turvavarustus" -> "safety equipment")
  override val intValueFieldsMapping: Map[String, String] = codeValueFieldsMapping
  override val specificFieldsMapping: Map[String, String] = Map("tasoristeystunnus" -> "id")
  override val nonMandatoryFieldsMapping: Map[String, String] = Map("nimi" -> "name")
  override val mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ codeValueFieldsMapping ++ specificFieldsMapping

  lazy val railwayCrossingService: RailwayCrossingService = new RailwayCrossingService(roadLinkService)

  val allowedSafetyEquipmentValues: Seq[Int] = Seq(1,2,3,4,5)

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val notImportedRailwayCrossings = pointAssetAttributes.flatMap { railwayCrossingAttribute =>
      val csvProperties = railwayCrossingAttribute.properties
      val nearbyLinks = railwayCrossingAttribute.roadLink

      val position = getCoordinatesFromProperties(csvProperties)
      val code = getPropertyValue(csvProperties, "id").asInstanceOf[String]
      val safetyEquipment = getPropertyValue(csvProperties, "safety equipment").asInstanceOf[String].toInt
      val optName = getPropertyValueOption(csvProperties, "name").map(_.toString)

      val nearestRoadLink = nearbyLinks.minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val floating = checkMinimumDistanceFromRoadLink(position, nearestRoadLink.geometry)

      val validData =
        if(!allowedSafetyEquipmentValues.contains(safetyEquipment))
          Seq(NotImportedData(reason = s"Railway Crossing safety equipment type $safetyEquipment does not exist.", csvRow = rowToString(csvProperties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
        else
          Seq()

      if (validData.isEmpty) {
        val name =  if (optName.nonEmpty) Seq(SimplePointAssetProperty(railwayCrossingService.namePublicId, Seq(PropertyValue(optName.get)))) else Seq()

        val propertyData = Set(SimplePointAssetProperty(railwayCrossingService.codePublicId, Seq(PropertyValue(code))),
          SimplePointAssetProperty(railwayCrossingService.safetyEquipmentPublicId, Seq(PropertyValue(safetyEquipment.toString)))) ++ name

        railwayCrossingService.createFromCoordinates(IncomingRailwayCrossing(position.x, position.y, nearestRoadLink.linkId, propertyData), nearestRoadLink, user.username, floating)
      }
      validData
    }

    result.copy(notImportedData = notImportedRailwayCrossings.toList ++ result.notImportedData)
  }

  override def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTraffic(user, Point(lon.toLong, lat.toLong), forCarTraffic = false, includeComplementaries = true)
        roadLinks.isEmpty match {
          case true => (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
          case false =>
            if (assetHasEditingRestrictions(RailwayCrossings.typeId, roadLinks)) {
              (List("Asset type editing is restricted within municipality or admininistrative class."), Seq())
            } else {
              (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
            }
        }
      case _ =>
        (Nil, Nil)
    }
  }
}
