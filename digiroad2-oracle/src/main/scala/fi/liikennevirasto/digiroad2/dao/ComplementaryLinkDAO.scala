package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.ComplimentaryLinkInterface
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, ConstructionType}
import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkFetched
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.LogUtils
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

class ComplementaryLinkDAO extends RoadLinkDAO {

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
      val sourceInfo = r.nextInt()
      val length  = r.nextDouble()
      val custOwner = r.nextLongOption()

      val geometry = path.map(point => Point(point(0), point(1), point(2)))
      val geometryForApi = path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3)))
      val geometryWKT = "LINESTRING ZM (" + path.map(point => s"${point(0)} ${point(1)} ${point(2)} ${point(3)}").mkString(", ") + ")"
      val featureClass = extractFeatureClass(mtkClass)
      val modifiedAt = extractModifiedDate(validFrom, lastEditedDate, geometryEdited)

      val attributes = Map(
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
        "VALIDFROM" -> validFrom.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "GEOMETRY_EDITED_DATE" -> geometryEdited.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "CREATED_DATE" -> createdDate.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "LAST_EDITED_DATE" -> lastEditedDate.map(time => BigInt(time.toDateTime.getMillis)).getOrElse(None),
        "SURFACETYPE" -> BigInt(surfaceType),
        "SUBTYPE" -> subType,
        "OBJECTID" -> objectId,
        "points" -> geometryForApi,
        "geometryWKT" -> geometryWKT,
        "CUST_OWNER" -> custOwner
      ).collect {
        case (key, Some(value)) => key -> value
        case (key, value) if value != None => key -> value
      }

      RoadLinkFetched(linkId.toString, municipality, geometry, AdministrativeClass.apply(administrativeClass),
        extractTrafficDirection(directionType), featureClass, modifiedAt, attributes,
        ConstructionType.apply(constructionType), ComplimentaryLinkInterface, length)
    }
  }
  
  override def getLinksWithFilter(filter: String): Seq[RoadLinkFetched] = {
    LogUtils.time(logger,"TEST LOG Getting complementery roadlinks" ){
      sql"""select linkid_UUID, municipalitycode, shape, adminclass, directiontype, mtkclass, roadname_fi, roadname_se,
                 roadname_sm, roadnumber, roadpartnumber, constructiontype, verticallevel, horizontalaccuracy,
                 verticalaccuracy, created_date, last_edited_date, from_left, to_left, from_right, to_right, validfrom,
                 geometry_edited_date, surfacetype, subtype, objectid, sourceinfo, geometrylength, cust_owner
          from roadlinkex
          where subtype = 3 and #$filter
          """.as[RoadLinkFetched].list
    }
  }

  override def getLinksIdByPolygons(polygon: Polygon): Seq[String] = {
    val polygonFilter = PostGISDatabase.polygonFilter(polygon, geometryColumn)
    LogUtils.time(logger,"TEST LOG Getting complementery roadlinks by polygon" ){
      sql"""select linkid_UUID
          from roadlinkex
          where subtype = 3 and #$polygonFilter
       """.as[String].list
    }
  }
}