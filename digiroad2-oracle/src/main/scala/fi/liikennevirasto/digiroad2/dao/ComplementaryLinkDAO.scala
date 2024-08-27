package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.locationtech.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.ComplimentaryLinkInterface
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, ConstructionType}
import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.{KgvUtil, LogUtils}
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

class ComplementaryLinkDAO extends RoadLinkDAO {

  protected override def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = {
    val fixedRoadNameSource = roadNameSource match {
      case "ROADNAME_FI" => "ROADNAMEFIN"
      case "ROADNAME_SE" => "ROADNAMESWE"
      case _ => roadNameSource
    }
    withRoadNameFilter(fixedRoadNameSource, roadNames)
  }

  protected override def withMtkClassFilter(ids: Set[Long]): String = {
    withFilter("ROADCLASS", ids)
  }

  protected override def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withDateLimitFilter("VERSIONSTARTTIME", lowerDate, higherDate)
  }

  implicit override val getRoadLink: GetResult[RoadLinkFetched] = new GetResult[RoadLinkFetched] {
    def apply(r: PositionedResult): RoadLinkFetched = {
      val linkId = r.nextString()
      val municipality = r.nextInt()
      val path = r.nextObjectOption().map(extractGeometry).get
      val administrativeClass = r.nextInt()
      val directionType = r.nextIntOption()
      val mtkClass = r.nextInt()
      val roadNameFi = r.nextStringOption()
      val roadNameSe = r.nextStringOption()
      val roadNameSme = r.nextStringOption()
      val roadNameSmn = r.nextStringOption()
      val roadNameSms = r.nextStringOption()
      val roadNumber = r.nextLongOption()
      val roadPart = r.nextIntOption()
      val constructionType = r.nextInt()
      val verticalLevel = r.nextInt()
      val createdDate = r.nextTimestampOption().map(new DateTime(_))
      val lastEditedDate = r.nextTimestampOption().map(new DateTime(_))
      val surfaceType = r.nextInt()
      val sourceInfo = r.nextInt()
      val length  = r.nextDouble()
      val custOwner = r.nextLongOption()

      val geometry = path.map(point => Point(point(0), point(1), point(2)))
      val geometryForApi = path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3)))
      val geometryWKT = "LINESTRING ZM (" + path.map(point => s"${point(0)} ${point(1)} ${point(2)} ${point(3)}").mkString(", ") + ")"
      val featureClass = extractFeatureClass(mtkClass)
      val modifiedAt = extractModifiedDate(createdDate.map(_.getMillis), lastEditedDate.map(_.getMillis))

      val attributes = Map(
        "MTKCLASS" -> mtkClass,
        "VERTICALLEVEL" -> BigInt(verticalLevel),
        "CONSTRUCTIONTYPE" -> constructionType,
        "ROADNAME_FI" -> roadNameFi,
        "ROADNAME_SE" -> roadNameSe,
        "ROADNAMESME" -> roadNameSme,
        "ROADNAMESMN" -> roadNameSmn,
        "ROADNAMESMS" -> roadNameSms,
        "ROADNUMBER" -> roadNumber,
        "ROADPARTNUMBER" -> roadPart,
        "MUNICIPALITYCODE" -> BigInt(municipality),
        "CREATED_DATE" -> createdDate.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "LAST_EDITED_DATE" -> lastEditedDate.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "SURFACETYPE" -> BigInt(surfaceType),
        "points" -> geometryForApi,
        "geometryWKT" -> geometryWKT,
        "CUST_OWNER" -> custOwner
      ).collect {
        case (key, Some(value)) => key -> value
        case (key, value) if value != None => key -> value
      }

      RoadLinkFetched(linkId, municipality, geometry, AdministrativeClass.apply(administrativeClass),
        KgvUtil.extractTrafficDirection(directionType), featureClass, modifiedAt, attributes,
        ConstructionType.apply(constructionType), ComplimentaryLinkInterface, length)
    }
  }
  
  override def getLinksWithFilter(filter: String): Seq[RoadLinkFetched] = {
    LogUtils.time(logger,"TEST LOG Getting complementery roadlinks" ){
      sql"""select linkid, municipalitycode, shape, adminclass, directiontype, roadclass, roadnamefin, roadnameswe,
                 roadnamesme, roadnamesmn, roadnamesms, roadnumber, roadpartnumber, lifecyclestatus, surfacerelation,
                 starttime, versionstarttime, surfacetype, datasource, horizontallength, cust_owner
          from qgis_roadlinkex
          where #$filter
          """.as[RoadLinkFetched].list
    }
  }
  
  override def getLinksIdByPolygons(polygon: Polygon): Seq[String] = {
    val polygonFilter = PostGISDatabase.polygonFilter(polygon, geometryColumn)
    LogUtils.time(logger,"TEST LOG Getting complementery roadlinks by polygon" ){
      sql"""select linkid
          from qgis_roadlinkex
          where #$polygonFilter
       """.as[String].list
    }
  }
}
