package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.service.AssetOnExpiredLink
import org.joda.time.DateTime
import net.postgis.jdbc.geometry.GeometryBuilder
import org.postgresql.util.PGobject
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}

import scala.collection.mutable.ListBuffer

case class AssetOnExpiredRoadLink(
                                   id: Long,
                                   linkId: String,
                                   startMeasure: Double,
                                   endMeasure: Double,
                                   point: Option[Seq[Point]],
                                   linkGeometry: Option[Seq[Point]],
                                   sideCode: SideCode,
                                   bearing: Int,
                                   assetGeometry: Option[Seq[Point]]
                                 )

class AssetsOnExpiredLinksDAO {


  implicit val getAssetOnExpiredRoadLink = new GetResult[AssetOnExpiredRoadLink] {
    def apply(r: PositionedResult): AssetOnExpiredRoadLink = {
      val id = r.nextLong()
      val linkId = r.nextString()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()

      val pointObj = r.nextObjectOption()
      val linkGeomObj = r.nextObjectOption()

      // make sure result is Option[Seq[Point]], not plain Iterable
      val point: Option[Seq[Point]] = pointObj match {
        case Some(obj) =>
          val path = PostGISDatabase.extractGeometry(obj)
          if (path.nonEmpty)
            Some(path.map(p => Point(p(0), p(1), p(2))).toSeq)
          else
            None
        case None => None
      }

      // make sure result is Option[Seq[Point]], not plain Iterable
      val linkGeometry: Option[Seq[Point]] = linkGeomObj match {
        case Some(obj) =>
          val path = PostGISDatabase.extractGeometry(obj)
          if (path.nonEmpty)
            Some(path.map(p => Point(p(0), p(1), p(2))).toSeq)
          else
            None
        case None => None
      }

      val sideCode = r.nextInt()
      val bearing = r.nextIntOption().getOrElse(0)

      val assetGeomObj = r.nextObjectOption()

      val assetGeometry: Option[Seq[Point]] = assetGeomObj match {
        case Some(obj) =>
          val path = PostGISDatabase.extractGeometry(obj)
          if (path.nonEmpty)
            Some(path.map(p => Point(p(0), p(1), p(2))).toSeq)
          else
            None
        case None => None
      }

      AssetOnExpiredRoadLink(id, linkId, startMeasure, endMeasure, point, linkGeometry, SideCode(sideCode), bearing, assetGeometry)
    }
  }

  protected def extractGeometry(data: Object): List[List[Double]] = {
    val geometry = data.asInstanceOf[PGobject]
    if (geometry == null) Nil
    else {
      val geomValue = geometry.getValue
      val geom = GeometryBuilder.geomFromString(geomValue)
      val listOfPoint= ListBuffer[List[Double]]()
      for (i <- 0 until geom.numPoints() ){
        val point =geom.getPoint(i)
        listOfPoint += List(point.x, point.y, point.z, point.m)
      }
      listOfPoint.toList
    }
  }
  implicit val getRoadLink: GetResult[AssetOnExpiredLink] = new GetResult[AssetOnExpiredLink] {
    def apply(r: PositionedResult): AssetOnExpiredLink = {
      val id = r.nextInt()
      val assetTypeId = r.nextInt()
      val linkId = r.nextString()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val roadLinkExpired = r.nextTimestamp()
      val pgAssetGeometry = r.nextObjectOption().map(extractGeometry).get
      val roadLinkExpiredDate = new DateTime(roadLinkExpired)
      val assetGeometry = pgAssetGeometry.map(point => Point(point(0), point(1), point(2)))

      val nationalId = r.nextIntOption()

      AssetOnExpiredLink(id, assetTypeId, linkId, sideCode, startMeasure, endMeasure, assetGeometry, roadLinkExpiredDate, nationalId)
    }
  }

  def fetchWorkListAssets(): Seq[AssetOnExpiredLink] = {
    sql"""SELECT ael.asset_id, ael.asset_type_id, ael.link_id, ael.side_code, ael.start_measure, ael.end_measure, ael.road_link_expired_date, ael.asset_geometry, a.national_id
          FROM assets_on_expired_road_links ael
          LEFT JOIN asset a ON a.id = ael.asset_id
       """.as[AssetOnExpiredLink].list
  }

  def insertToWorkList(asset: AssetOnExpiredLink): Unit = {
    val assetId = asset.id
    val assetTypeId = asset.assetTypeId
    val linkId = asset.linkId
    val sideCode = asset.sideCode
    val startMeasure = asset.startMeasure
    val endMeasure = asset.endMeasure
    val roadLinkExpiredDate = asset.roadLinkExpiredDate.toString
    val geometryWKT = if(asset.geometry.length > 1) {
      GeometryUtils.toWktLineString(asset.geometry).string
    } else if(asset.geometry.length == 1) GeometryUtils.toWktPoint(asset.geometry.head.x, asset.geometry.head.y).string
    else ""
    sqlu"""INSERT INTO assets_on_expired_road_links (asset_id, asset_type_id, link_id, side_code, start_measure, end_measure, road_link_expired_date, asset_geometry)
          VALUES ($assetId,
                  $assetTypeId,
                  $linkId,
                  $sideCode,
                  $startMeasure,
                  $endMeasure,
                  to_timestamp($roadLinkExpiredDate, 'YYYY-MM-DD"T"HH24:MI:SS.FF3'),
                  ST_GeomFromText($geometryWKT))
        """.execute
  }

  def getAssetsOnExpiredRoadLinksById(ids: Set[Long]):Seq[AssetOnExpiredRoadLink] = {
    MassQuery.withIds(ids) { idTableName =>
      sql"""
       SELECT
        aoerl.asset_id,
        aoerl.link_id,
        aoerl.start_measure,
        aoerl.end_measure,
        aoerl.asset_geometry,
        kr.shape AS line_geometry,
        aoerl.side_code,
        a.bearing,
        CASE
          WHEN aoerl.start_measure IS NOT NULL
            AND aoerl.end_measure IS NOT NULL
          THEN ST_LineMerge(
            ST_LocateBetween(
              kr.shape,
              aoerl.start_measure,
              aoerl.end_measure
            )
          )
          ELSE NULL
        END AS asset_geometry
      FROM assets_on_expired_road_links aoerl
      LEFT JOIN asset a ON a.id = aoerl.asset_id
      LEFT JOIN kgv_roadlink kr ON kr.linkid = aoerl.link_id
      JOIN #$idTableName i ON i.id = aoerl.asset_id
    """.as[AssetOnExpiredRoadLink].list
    }
  }

  def deleteFromWorkList(assetIdsToDelete: Set[Long]): Unit = {
    val assetIdFilter = s"(${assetIdsToDelete.mkString(",")})"

    sqlu"""DELETE FROM assets_on_expired_road_links
           WHERE asset_id IN #$assetIdFilter
        """.execute
  }
}
