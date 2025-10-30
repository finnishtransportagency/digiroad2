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

case class ComplementaryLink(vvhid: Option[Int],
                             linkid: String,
                             datasource: Option[Int],
                             adminclass: Int,
                             municipalitycode: Int,
                             roadclass: Int,
                             roadnamefin: Option[String],
                             roadnameswe: Option[String],
                             roadnamesme: Option[String],
                             roadnamesmn: Option[String],
                             roadnamesms: Option[String],
                             roadnumber: Option[Int],
                             roadpartnumber: Option[Int],
                             surfacetype: Int,
                             lifecyclestatus: Int,
                             directiontype: Int,
                             surfacerelation: Int,
                             horizontallength: Float,
                             starttime: DateTime,
                             created_user: String,
                             versionstarttime: Option[DateTime],
                             shape: String,
                             trackcode: Option[Int],
                             cust_owner: Int
                                  )

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
      val path = r.nextObjectOption().map(PostGISDatabase.extractGeometry).get
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

  def insertComplementaryLink(link: ComplementaryLink): Unit = {
    val sqlStartTime = new java.sql.Timestamp(link.starttime.getMillis)
    val sqlVersionStartTime = link.versionstarttime.map(dt => new java.sql.Timestamp(dt.getMillis))
    val geometrySql = s"ST_SetSRID(ST_GeomFromText('${link.shape}'), 3067)"

    sqlu"""
    INSERT INTO qgis_roadlinkex (
      vvh_id, linkid, datasource, adminclass, municipalitycode, roadclass,
      roadnamefin, roadnameswe, roadnamesme, roadnamesmn, roadnamesms,
      roadnumber, roadpartnumber, surfacetype, lifecyclestatus, directiontype,
      surfacerelation, horizontallength, starttime, created_user, versionstarttime,
      shape, track_code, cust_owner
    ) VALUES (
      ${link.vvhid}, ${link.linkid}, ${link.datasource}, ${link.adminclass}, ${link.municipalitycode}, ${link.roadclass},
      ${link.roadnamefin}, ${link.roadnameswe}, ${link.roadnamesme}, ${link.roadnamesmn}, ${link.roadnamesms},
      ${link.roadnumber}, ${link.roadpartnumber}, ${link.surfacetype}, ${link.lifecyclestatus}, ${link.directiontype},
      ${link.surfacerelation}, ${link.horizontallength}, ${sqlStartTime}, ${link.created_user}, ${sqlVersionStartTime},
      #${geometrySql}, ${link.trackcode}, ${link.cust_owner}
    )
  """.execute
  }

  def getComplementaryRoadLinkIdsByMunicipality(municipalities: Seq[Int]): Seq[String] = {
    LogUtils.time(logger, "TEST LOG Getting roadlinks") {
      val municipalityFilter = municipalities.mkString(",")
      sql"""
      select linkid
      from qgis_roadlinkex
      where municipalitycode in (#$municipalityFilter)
    """.as[String].list
    }
  }

  def deleteComplementaryRoadLinksByLinkIds(linkIdsToDelete: Set[String]) = {
    LogUtils.time(logger, "TEST LOG Delete Complementary Road Link Rows") {
      val linkIdFilter = withLinkIdFilter(linkIdsToDelete)
      sqlu"""
      DELETE FROM qgis_roadlinkex
      WHERE #$linkIdFilter
      """.execute
    }
  }

}
