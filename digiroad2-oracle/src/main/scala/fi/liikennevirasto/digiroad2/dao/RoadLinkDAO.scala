package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, ConstructionType, LinkGeomSource, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase.withDbConnection
import fi.liikennevirasto.digiroad2.util.LogUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.postgis.PGgeometry
import org.postgresql.util.PGobject
import org.slf4j.LoggerFactory
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RoadLinkDAO {
  protected val geometryColumn: String = "shape"

  val logger = LoggerFactory.getLogger(getClass)

  // Query filters methods
  protected def withFilter[T](attributeName: String, ids: Set[T]): String = {
    if (ids.nonEmpty) {
      attributeName match {
        case "LINKID" => s"$attributeName in (${ids.asInstanceOf[Set[String]].map(t=>s"'$t'").mkString(",")})"
        case _ => s"$attributeName in (${ids.mkString(",")})"
      }
    } else ""
  }

  protected def withRoadNameFilter[T](attributeName: String, names: Set[String]): String = {
    if (names.nonEmpty) {
      val nameString = names.map(name =>
      {
        "[\']".r.findFirstMatchIn(name) match {
          case Some(_) => s"'${name.replaceAll("\'","\'\'")}'"
          case None => s"'$name'"
        }
      })
      s"$attributeName in (${nameString.mkString(",")})"
    } else ""
  }

  protected def withLimitFilter(attributeName: String, low: Int, high: Int,
                                          includeAllPublicRoads: Boolean = false): String = {
    if (low < 0 || high < 0 || low > high) {
      ""
    } else {
      if (includeAllPublicRoads) {
        s"ADMINCLASS = 1 OR $attributeName >= $low and $attributeName <= $high)"
      } else {
        s"( $attributeName >= $low and $attributeName <= $high )"
      }
    }
  }

  protected def withRoadNumberFilter(roadNumbers: (Int, Int), includeAllPublicRoads: Boolean): String = {
    withLimitFilter("ROADNUMBER", roadNumbers._1, roadNumbers._2, includeAllPublicRoads)
  }

  protected def withLinkIdFilter(linkIds: Set[String]): String = {
    withFilter("LINKID", linkIds)
  }

  protected def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = {
    withRoadNameFilter(roadNameSource, roadNames)
  }

  protected def withMmlIdFilter(mmlIds: Set[Long]): String = {
    withFilter("MTKID", mmlIds)
  }

  protected def withMtkClassFilter(ids: Set[Long]): String = {
    withFilter("MTKCLASS", ids)
  }

  protected  def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withDateLimitFilter("LAST_EDITED_DATE", lowerDate, higherDate)
  }

  protected def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val since = formatter.print(lowerDate)
    val until = formatter.print(higherDate)

    s"( $attributeName >= date '$since' and $attributeName <=date '$until' )"
  }


  protected def withRoadNumbersFilter(roadNumbers: Seq[(Int, Int)], includeAllPublicRoads: Boolean, filter: String = ""): String = {
    if (roadNumbers.isEmpty) return s"($filter)"
    if (includeAllPublicRoads)
      return withRoadNumbersFilter(roadNumbers, false, "ADMINCLASS = 1")
    val limit = roadNumbers.head
    val filterAdd = s"(ROADNUMBER >= ${limit._1} and ROADNUMBER <= ${limit._2})"
    if (filter == "")
      withRoadNumbersFilter(roadNumbers.tail, includeAllPublicRoads, filterAdd)
    else
      withRoadNumbersFilter(roadNumbers.tail, includeAllPublicRoads, s"""$filter OR $filterAdd""")
  }

  protected def combineFiltersWithAnd(filter1: String, filter2: String): String = {
    (filter1.isEmpty, filter2.isEmpty) match {
      case (true,true) => ""
      case (true,false) => filter2
      case (false,true) => filter1
      case (false,false) => s"$filter1 AND $filter2"
    }
  }
  
  protected def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String = {
    combineFiltersWithAnd(filter2.getOrElse(""), filter1)
  }
  
  implicit val getRoadLink: GetResult[RoadLinkFetched] = new GetResult[RoadLinkFetched] {
    def apply(r: PositionedResult): RoadLinkFetched = {
      val linkId = r.nextString()
      val mtkId = r.nextLong()
      val mtkHereFlip = r.nextInt()
      val municipality = r.nextInt()
      val path = r.nextObjectOption().map(extractGeometry).get
      val administrativeClass = r.nextInt()
      val directionType = r.nextIntOption()
      val mtkClass = r.nextInt()
      val roadNameFi = r.nextStringOption()
      val roadNameSe = r.nextStringOption()
      val roadNameSm = r.nextStringOption()
      val roadNumber = r.nextLongOption()
      val roadPart = r.nextIntOption()
      val constructionType = r.nextInt()
      val verticalLevel = r.nextInt()
      val horizontalAccuracy = r.nextLong()
      val verticalAccuracy = r.nextLong()
      val createdDate = r.nextTimestampOption().map(new DateTime(_))
      val lastEditedDate = r.nextTimestampOption().map(new DateTime(_))
      val fromLeft = r.nextLongOption()
      val toLeft = r.nextLongOption()
      val fromRight = r.nextLongOption()
      val toRight = r.nextLongOption()
      val validFrom = r.nextTimestampOption().map(new DateTime(_))
      val geometryEdited = r.nextTimestampOption().map(new DateTime(_))
      val surfaceType = r.nextInt()
      val subType = r.nextInt()
      val objectId = r.nextLong()
      val startNode = r.nextLong()
      val endNode = r.nextLong()
      val sourceInfo = r.nextInt()
      val length  = r.nextDouble()

      val geometry = path.map(point => Point(point(0), point(1), point(2)))
      val geometryForApi = path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3)))
      val geometryWKT = "LINESTRING ZM (" + path.map(point => s"${point(0)} ${point(1)} ${point(2)} ${point(3)}").mkString(", ") + ")"
      val featureClass = extractFeatureClass(mtkClass)
      val modifiedAt = extractModifiedDate(validFrom, lastEditedDate, geometryEdited)

      val attributes = Map(
        "MTKID" -> mtkId,
        "MTKCLASS" -> mtkClass,
        "HORIZONTALACCURACY" -> horizontalAccuracy,
        "VERTICALACCURACY" -> verticalAccuracy,
        "VERTICALLEVEL" -> BigInt(verticalLevel),
        "CONSTRUCTIONTYPE" -> constructionType,
        "ROADNAME_FI" -> roadNameFi,
        "ROADNAME_SE" -> roadNameSe,
        "ROADNAME_SM" -> roadNameSm,
        "ROADNUMBER" -> roadNumber,
        "ROADPARTNUMBER" -> roadPart,
        "FROM_LEFT" -> fromLeft,
        "TO_LEFT" -> toLeft,
        "FROM_RIGHT" -> fromRight,
        "TO_RIGHT" -> toRight,
        "MUNICIPALITYCODE" -> BigInt(municipality),
        "MTKHEREFLIP" -> mtkHereFlip,
        "VALIDFROM" -> validFrom.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "GEOMETRY_EDITED_DATE" -> geometryEdited.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "CREATED_DATE" -> createdDate.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "LAST_EDITED_DATE" -> lastEditedDate.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "SURFACETYPE" -> BigInt(surfaceType),
        "SUBTYPE" -> subType,
        "OBJECTID" -> objectId,
        "STARTNODE" -> startNode,
        "ENDNODE" -> endNode,
        "points" -> geometryForApi,
        "geometryWKT" -> geometryWKT
      ).collect {
        case (key, Some(value)) => key -> value
        case (key, value) if value != None => key -> value
      }

      RoadLinkFetched(linkId, municipality, geometry, AdministrativeClass.apply(administrativeClass),
        extractTrafficDirection(directionType), featureClass, modifiedAt, attributes,
        ConstructionType.apply(constructionType), LinkGeomSource.apply(sourceInfo), length)
    }
  }

  /**
    * Returns VVH road links. Obtain all RoadLinks changes between two given dates.
    */
  def fetchByChangesDates(lowerDate: DateTime, higherDate: DateTime): Seq[RoadLinkFetched] = {
    withDbConnection {
      getLinksWithFilter(withLastEditedDateFilter(lowerDate, higherDate))
    }
  }
  
  /**
    * Returns VVH road link by mml id.
    * Used by RoadLinkService.getRoadLinkMiddlePointByMmlId
    */
  def fetchByMmlId(mmlId: Long): Option[RoadLinkFetched] = fetchByMmlIds(Set(mmlId)).headOption

  /**
    * Returns VVH road links by mml ids.
    * Used by VVHClient.fetchByMmlId, LinkIdImporter.updateTable and AssetDataImporter.importRoadAddressData.
    */
  def fetchByMmlIds(mmlIds: Set[Long]): Seq[RoadLinkFetched] = {
    getByMultipleValues(mmlIds, withMmlIdFilter)
  }

  /**
    * Returns VVH road links by finnish names.
    * Used by VVHClient.fetchByLinkId,
    */
  def fetchByRoadNames(roadNamePublicId: String, roadNames: Set[String]): Seq[RoadLinkFetched] = {
    getByMultipleValues(roadNames, withFinNameFilter(roadNamePublicId))
  }

  /**
    * Returns VVH road link by linkid
    * Used by Digiroad2Api.createMassTransitStop, Digiroad2Api.validateUserRights, Digiroad2Api &#47;manoeuvres DELETE endpoint, Digiroad2Api manoeuvres PUT endpoint,
    * CsvImporter.updateAssetByExternalIdLimitedByRoadType, RoadLinkService,getRoadLinkMiddlePointByLinkId, RoadLinkService.updateLinkProperties, RoadLinkService.getRoadLinkGeometry,
    * RoadLinkService.updateAutoGeneratedProperties, LinearAssetService.split, LinearAssetService.separate, MassTransitStopService.fetchRoadLink, PointAssetOperations.getById
    * and PostGISLinearAssetDao.createSpeedLimit.
    */
  def fetchByLinkId(linkId: String): Option[RoadLinkFetched] = fetchByLinkIds(Set(linkId)).headOption

  /**
    * Returns VVH road links by link ids.
    * Used by VVHClient.fetchByLinkId, RoadLinkService.fetchVVHRoadlinks, SpeedLimitService.purgeUnknown, PointAssetOperations.getFloatingAssets,
    * PostGISLinearAssetDao.getLinksWithLengthFromVVH, PostGISLinearAssetDao.getSpeedLimitLinksById AssetDataImporter.importEuropeanRoads and AssetDataImporter.importProhibitions
    */
  def fetchByLinkIds(linkIds: Set[String]): Seq[RoadLinkFetched] = {
    getByMultipleValues(linkIds, withLinkIdFilter)
  }

  def fetchByLinkIdsF(linkIds: Set[String]) = {
    Future(fetchByLinkIds(linkIds))
  }

  def fetchByRoadNamesF(roadNamePublicIds: String, roadNameSource: Set[String]) = {
    Future(fetchByRoadNames(roadNamePublicIds, roadNameSource))
  }
  
  /**
    * Returns VVH road links.
    * Used by RoadLinkService.fetchVVHRoadlinks (called from CsvGenerator)
    */
  def fetchVVHRoadlinks[T](linkIds: Set[String],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] = 
    getByMultipleValues(linkIds, withLinkIdFilter)

  def fetchByPolygon(polygon : Polygon): Seq[RoadLinkFetched] = {
    withDbConnection {getByPolygon(polygon)}
  }

  def fetchByPolygonF(polygon : Polygon): Future[Seq[RoadLinkFetched]] = {
    Future(fetchByPolygon(polygon))
  }

  def fetchLinkIdsByPolygonF(polygon : Polygon): Future[Seq[String]] = {
    Future(getLinksIdByPolygons(polygon))
  }

  def fetchLinkIdsByPolygon(polygon : Polygon): Seq[String] = {
   getLinksIdByPolygons(polygon)
  }

  def fetchByMunicipality(municipality: Int): Seq[RoadLinkFetched] = {
    getByMunicipality(municipality)
  }

  def fetchByMunicipalityF(municipality: Int): Future[Seq[RoadLinkFetched]] = {
    Future(getByMunicipality(municipality))
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities),
    * RoadLinkService.getViiteRoadLinksAndChangesFromVVH(bounds, roadNumbers, municipalities, everything, publicRoads).
    */
  def fetchByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[RoadLinkFetched] = {
    getByMunicipalitiesAndBounds(bounds, municipalities,None)
  }

  def fetchByBounds(bounds: BoundingRectangle): Seq[RoadLinkFetched] = {
    getByMunicipalitiesAndBounds(bounds, Set[Int](),None)
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities),
    * RoadLinkService.getViiteRoadLinksAndChangesFromVVH(bounds, roadNumbers, municipalities, everything, publicRoads).
    */
  def fetchByMunicipalitiesAndBoundsF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[RoadLinkFetched]] = {
    Future(getByMunicipalitiesAndBounds(bounds, municipalities,None))
  }

  def fetchWalkwaysByMunicipalities(municipality:Int): Seq[RoadLinkFetched] = {
    getByMunicipality(municipality, Some(withMtkClassFilter(Set(12314))))
  }
  def fetchWalkwaysByMunicipalitiesF(municipality: Int): Future[Seq[RoadLinkFetched]] =
    Future(getByMunicipality(municipality, Some(withMtkClassFilter(Set(12314)))))

  def fetchWalkwaysByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[RoadLinkFetched]] = {
    Future(getByMunicipalitiesAndBounds(bounds, municipalities, Some(withMtkClassFilter(Set(12314)))))
  }

  def fetchWalkwaysByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[RoadLinkFetched] = {
    getByMunicipalitiesAndBounds(bounds, municipalities, Some(withMtkClassFilter(Set(12314))))
  }
  
  /**
    * Returns VVH road links.
    */
  private def getByMultipleValues[T, A](values: Set[A],
                                        filter: Set[A] => String): Seq[T] = {
    if (values.nonEmpty) {
      getLinksWithFilter(filter(values)).asInstanceOf[Seq[T]]
    } else Seq.empty[T]
  }
  
 protected def getLinksWithFilter(filter: String): Seq[RoadLinkFetched] = {
   LogUtils.time(logger,"TEST LOG Getting roadlinks" ){
     sql"""select linkid, mtkid, mtkhereflip, municipalitycode, shape, adminclass, directiontype, mtkclass, roadname_fi,
                 roadname_se, roadname_sm, roadnumber, roadpartnumber, constructiontype, verticallevel, horizontalaccuracy,
                 verticalaccuracy, created_date, last_edited_date, from_left, to_left, from_right, to_right, validfrom,
                 geometry_edited_date, surfacetype, subtype, objectid, startnode, endnode, sourceinfo, geometrylength
          from roadlink
          where #$filter and constructiontype in (${ConstructionType.InUse.value},
                                                  ${ConstructionType.UnderConstruction.value},
                                                  ${ConstructionType.Planned.value})
          """.as[RoadLinkFetched].list
   }
  }

  private def getByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                   filter: Option[String]): Seq[RoadLinkFetched] = {
    val bboxFilter = PostGISDatabase.boundingBoxFilter(bounds, geometryColumn)
    val withFilter = (municipalities.nonEmpty, filter.nonEmpty) match {
      case (true, true) =>  s"and municipalitycode in (${municipalities.mkString(",")}) and ${filter.get}"
      case (true, false) => s"and municipalitycode in (${municipalities.mkString(",")})"
      case (false, true) => s"and ${filter.get}"
      case _ => ""
    }

    getLinksWithFilter(s"$bboxFilter $withFilter")
  }

  private def getByMunicipality(municipality: Int, filter: Option[String] = None): Seq[RoadLinkFetched] = {
    val queryFilter =
      if (filter.nonEmpty) s"and ${filter.get}"
      else ""

    getLinksWithFilter(s"municipalitycode = $municipality $queryFilter")
  }

  private def getByPolygon(polygon: Polygon): Seq[RoadLinkFetched] = {
    if(polygon.getCoordinates.isEmpty) return Seq[RoadLinkFetched]()
    
    val polygonFilter = PostGISDatabase.polygonFilter(polygon, geometryColumn)

    getLinksWithFilter(polygonFilter)
  }

  protected def getLinksIdByPolygons(polygon: Polygon): Seq[String] = {
    if (polygon.getCoordinates.isEmpty) return Seq.empty[String]
    
    val polygonFilter = PostGISDatabase.polygonFilter(polygon, geometryColumn)
    LogUtils.time(logger,"TEST LOG Getting roadlinks by polygon" ){
      sql"""select kmtkid
          from roadlink
          where #$polygonFilter
       """.as[String].list
    }
  }

  protected def extractFeatureClass(code: Int): FeatureClass = {
    code match {
      case 12316 => FeatureClass.TractorRoad
      case 12141 => FeatureClass.DrivePath
      case 12314 => FeatureClass.CycleOrPedestrianPath
      case 12312 => FeatureClass.WinterRoads
      case 12153 => FeatureClass.SpecialTransportWithoutGate
      case 12154 => FeatureClass.SpecialTransportWithGate
      case 12131 => FeatureClass.CarRoad_IIIa
      case 12132 => FeatureClass.CarRoad_IIIb
      case _ => FeatureClass.AllOthers
    }
  }

  protected def extractTrafficDirection(code: Option[Int]): TrafficDirection = {
    code match {
      case Some(0) => TrafficDirection.BothDirections
      case Some(1) => TrafficDirection.TowardsDigitizing
      case Some(2) => TrafficDirection.AgainstDigitizing
      case _ => TrafficDirection.UnknownDirection
    }
  }

  protected def extractModifiedDate(validFrom: Option[DateTime], lastEdited: Option[DateTime],
                                    geometryEdited: Option[DateTime]): Option[DateTime] = {
    val validFromTime = if (validFrom.nonEmpty) validFrom.get.getMillis else 0
    val lastEditedTime = if (lastEdited.nonEmpty) lastEdited.get.getMillis else 0
    val geometryEditedTime = if (geometryEdited.nonEmpty) geometryEdited.get.getMillis else 0

    val lastModification = {
      if (lastEditedTime > geometryEditedTime) Some(lastEditedTime)
      else if (geometryEditedTime > 0) Some(geometryEditedTime)
      else None
    }
    lastModification.orElse(Option(validFromTime)).map(modified => new DateTime(modified))
  }
  
  protected def extractGeometry(data: Object): List[List[Double]] = {
    val geometry = data.asInstanceOf[PGobject]
    if (geometry == null) Nil
    else {
      val geomValue = geometry.getValue
      val geom = PGgeometry.geomFromString(geomValue)
      val listOfPoint= ListBuffer[List[Double]]()
      for (i <- 0 until geom.numPoints() ){
        val point =geom.getPoint(i)
        listOfPoint += List(point.x, point.y, point.z, point.m)
      }
      listOfPoint.toList
    }
  }
}
